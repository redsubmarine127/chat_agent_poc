#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional
from urllib import error, parse, request

from evaluators import evaluate_semantic_quality


TABLE_PATTERN = re.compile(r"^\s*\|.+\|\s*$\n^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$", re.MULTILINE)
CODE_BLOCK_PATTERN = re.compile(r"```[\s\S]+?```")
ORDERED_LIST_PATTERN = re.compile(r"^\s*\d+[.)、]\s+\S+", re.MULTILINE)


def main() -> int:
    args = parse_args()
    dataset_path = Path(args.dataset)
    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    started_at = datetime.now(timezone.utc)
    results = []
    for case in dataset.get("cases", []):
        result = run_case(
            base_url=args.base_url.rstrip("/"),
            dataset=dataset,
            case=case,
            model_override=args.model_id,
            skill_override=args.skill_id,
            timeout=args.timeout,
            keep_conversations=args.keep_conversations,
            semantic_evaluator=args.semantic_evaluator,
        )
        results.append(result)
        print(f"{result['caseId']}: {result['score']['total']:.1f}/100 {result['status']}")

    report = {
        "dataset": dataset.get("name", dataset_path.stem),
        "description": dataset.get("description", ""),
        "baseUrl": args.base_url.rstrip("/"),
        "startedAt": started_at.isoformat(),
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "summary": summarize(results),
        "results": results,
    }

    timestamp = started_at.strftime("%Y%m%d%H%M%S")
    safe_name = re.sub(r"[^a-zA-Z0-9_.-]+", "-", report["dataset"])
    json_path = output_dir / f"{safe_name}-{timestamp}.json"
    markdown_path = output_dir / f"{safe_name}-{timestamp}.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    markdown_path.write_text(render_markdown_report(report), encoding="utf-8")

    print(f"\nJSON report: {json_path}")
    print(f"Markdown report: {markdown_path}")

    if report["summary"]["averageScore"] < args.fail_under:
        print(f"average score below threshold: {report['summary']['averageScore']:.1f} < {args.fail_under}", file=sys.stderr)
        return 2
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run automated Agent evals against a compatible assistant backend.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8090", help="Backend base URL, for example http://127.0.0.1:8090")
    parser.add_argument("--dataset", default="agent-evals/datasets/smoke_langgraph.json", help="Dataset JSON path")
    parser.add_argument("--output-dir", default="agent-evals/reports", help="Report output directory")
    parser.add_argument("--model-id", default="", help="Override modelId for all cases")
    parser.add_argument("--skill-id", default="", help="Override skillId for all cases")
    parser.add_argument("--timeout", type=float, default=120.0, help="HTTP timeout seconds")
    parser.add_argument("--fail-under", type=float, default=0.0, help="Exit with code 2 when average score is below this value")
    parser.add_argument("--keep-conversations", action="store_true", help="Keep eval conversations instead of deleting them")
    parser.add_argument(
        "--semantic-evaluator",
        choices=("none", "auto", "deepeval"),
        default="auto",
        help="Semantic quality evaluator. auto uses DeepEval only when the dataset case declares expected.semantic.",
    )
    return parser.parse_args()


def run_case(
    base_url: str,
    dataset: dict[str, Any],
    case: dict[str, Any],
    model_override: str,
    skill_override: str,
    timeout: float,
    keep_conversations: bool,
    semantic_evaluator: str,
) -> dict[str, Any]:
    case_id = case["id"]
    model_id = model_override or case.get("modelId") or dataset.get("defaultModelId") or "local-fallback"
    skill_id = skill_override or case.get("skillId") or dataset.get("defaultSkillId") or "general"
    expected = case.get("expected", {})
    conversation_id = None
    started_at = time.perf_counter()

    try:
        conversation = request_json(
            base_url,
            "/api/conversations",
            method="POST",
            body={"title": f"Eval - {case_id}"},
            timeout=timeout,
        )
        conversation_id = conversation["id"]
        stream_result = stream_message(
            base_url,
            conversation_id,
            {
                "content": case["prompt"],
                "skillId": skill_id,
                "modelId": model_id,
                "attachmentIds": case.get("attachmentIds", []),
            },
            timeout=timeout,
        )
        export_results = run_exports(base_url, stream_result["answer"], expected.get("exports", []), timeout)
        semantic_result = evaluate_semantic_quality(
            mode=semantic_evaluator,
            prompt=case["prompt"],
            answer=stream_result["answer"],
            expected=expected,
        ).as_dict()
        score = score_case(stream_result, export_results, expected, semantic_result=semantic_result)
        status = "PASS" if score["total"] >= case.get("passScore", 75) else "NEEDS_REVIEW"
        error_message = ""
    except Exception as exc:  # noqa: BLE001 - eval runner must continue across cases
        stream_result = empty_stream_result()
        export_results = {}
        semantic_result = {"enabled": False, "provider": "none", "score": None, "reason": "", "skipped": False, "error": ""}
        score = score_case(stream_result, export_results, expected, request_error=str(exc))
        status = "ERROR"
        error_message = str(exc)
    finally:
        if conversation_id and not keep_conversations:
            try:
                request_json(base_url, f"/api/conversations/{conversation_id}", method="DELETE", timeout=timeout)
            except Exception:
                pass

    return {
        "caseId": case_id,
        "name": case.get("name", case_id),
        "status": status,
        "modelId": model_id,
        "skillId": skill_id,
        "conversationId": conversation_id,
        "durationMs": round((time.perf_counter() - started_at) * 1000, 2),
        "error": error_message,
        "answerPreview": stream_result["answer"][:500],
        "reasoningPreview": stream_result["reasoning"][:500],
        "events": stream_result["events"],
        "metrics": stream_result["metrics"],
        "exports": export_results,
        "semantic": semantic_result,
        "checks": score["checks"],
        "score": score,
    }


def request_json(base_url: str, path: str, method: str = "GET", body: Optional[dict[str, Any]] = None, timeout: float = 120.0) -> Any:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = request.Request(base_url + path, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as response:
            raw = response.read()
            if not raw:
                return None
            return json.loads(raw.decode("utf-8"))
    except error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} failed: {exc.code} {raw}") from exc


def stream_message(base_url: str, conversation_id: str, payload: dict[str, Any], timeout: float) -> dict[str, Any]:
    req = request.Request(
        f"{base_url}/api/conversations/{conversation_id}/messages/stream",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
        method="POST",
    )
    start = time.perf_counter()
    first_delta_at = None
    events: list[str] = []
    answer_parts: list[str] = []
    reasoning_parts: list[str] = []
    failed_content = ""
    current_event = ""
    current_data_lines: list[str] = []

    try:
        with request.urlopen(req, timeout=timeout) as response:
            while True:
                line = response.readline()
                if not line:
                    consume_sse_event(current_event, current_data_lines, events, answer_parts, reasoning_parts, start, first_delta_at_holder := {})
                    if first_delta_at is None:
                        first_delta_at = first_delta_at_holder.get("firstDeltaAt")
                    break
                decoded = line.decode("utf-8", errors="replace").rstrip("\n")
                if decoded.endswith("\r"):
                    decoded = decoded[:-1]
                if decoded == "":
                    holder = {"firstDeltaAt": first_delta_at}
                    failed_content = consume_sse_event(current_event, current_data_lines, events, answer_parts, reasoning_parts, start, holder) or failed_content
                    first_delta_at = holder.get("firstDeltaAt")
                    current_event = ""
                    current_data_lines = []
                    continue
                if decoded.startswith("event:"):
                    current_event = decoded[6:].strip()
                elif decoded.startswith("data:"):
                    current_data_lines.append(decoded[5:].strip())
    except error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"stream failed: {exc.code} {raw}") from exc

    total_ms = (time.perf_counter() - start) * 1000
    return {
        "answer": "".join(answer_parts),
        "reasoning": "".join(reasoning_parts),
        "failedContent": failed_content,
        "events": events,
        "metrics": {
            "firstTokenMs": round(first_delta_at * 1000, 2) if first_delta_at is not None else None,
            "totalMs": round(total_ms, 2),
            "answerChars": len("".join(answer_parts)),
            "reasoningChars": len("".join(reasoning_parts)),
        },
    }


def consume_sse_event(
    event_name: str,
    data_lines: list[str],
    events: list[str],
    answer_parts: list[str],
    reasoning_parts: list[str],
    start: float,
    holder: dict[str, Optional[float]],
) -> str:
    if not data_lines:
        return ""
    data = "\n".join(data_lines)
    try:
        payload = json.loads(data)
    except json.JSONDecodeError:
        return ""
    event_type = payload.get("type") or event_name
    events.append(event_type)
    content = str(payload.get("content") or "")
    if event_type == "delta":
        if holder.get("firstDeltaAt") is None:
            holder["firstDeltaAt"] = time.perf_counter() - start
        answer_parts.append(content)
    elif event_type == "reasoning":
        reasoning_parts.append(content)
    elif event_type == "failed":
        return content
    return ""


def run_exports(base_url: str, answer: str, exports: list[str], timeout: float) -> dict[str, Any]:
    results: dict[str, Any] = {}
    for export_type in exports:
        if export_type not in {"markdown", "excel"}:
            continue
        form = parse.urlencode({"content": answer or "暂无内容", "filename": f"eval.{ 'xlsx' if export_type == 'excel' else 'md'}"}).encode("utf-8")
        req = request.Request(
            f"{base_url}/api/exports/{export_type}",
            data=form,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        try:
            with request.urlopen(req, timeout=timeout) as response:
                content = response.read()
                valid = len(content) > 0 and (export_type != "excel" or content.startswith(b"PK"))
                results[export_type] = {"ok": valid, "size": len(content), "status": response.status}
        except Exception as exc:  # noqa: BLE001
            results[export_type] = {"ok": False, "size": 0, "error": str(exc)}
    return results


def score_case(
    stream_result: dict[str, Any],
    export_results: dict[str, Any],
    expected: dict[str, Any],
    request_error: str = "",
    semantic_result: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    answer = stream_result["answer"]
    reasoning = stream_result["reasoning"]
    events = stream_result["events"]
    metrics = stream_result["metrics"]
    requires = expected.get("requires", {})
    keywords = expected.get("keywords", [])
    forbidden = expected.get("forbidden", [])

    checks: dict[str, bool] = {
        "noRequestError": not request_error,
        "startedEvent": "started" in events,
        "completedEvent": "completed" in events if requires.get("completedEvent", True) else True,
        "noFailedEvent": "failed" not in events and not stream_result.get("failedContent"),
        "minAnswerChars": metrics["answerChars"] >= expected.get("minAnswerChars", 1),
        "reasoning": bool(reasoning.strip()) if requires.get("reasoning") else True,
        "table": bool(TABLE_PATTERN.search(answer)) if requires.get("table") else True,
        "codeBlock": bool(CODE_BLOCK_PATTERN.search(answer)) if requires.get("codeBlock") else True,
        "orderedList": bool(ORDERED_LIST_PATTERN.search(answer)) if requires.get("orderedList") else True,
        "firstTokenLatency": latency_ok(metrics.get("firstTokenMs"), expected.get("maxFirstTokenMs")),
        "totalLatency": latency_ok(metrics.get("totalMs"), expected.get("maxTotalMs")),
    }

    keyword_hits = [keyword for keyword in keywords if keyword.lower() in answer.lower()]
    forbidden_hits = [word for word in forbidden if word.lower() in (answer + reasoning + request_error).lower()]
    checks["forbiddenClean"] = not forbidden_hits
    checks["exports"] = all(item.get("ok") for item in export_results.values()) if export_results else True
    semantic_score = semantic_result.get("score") if semantic_result and semantic_result.get("enabled") else None
    if semantic_score is not None:
        checks["semanticQuality"] = semantic_score >= float(expected.get("semantic", {}).get("threshold", 0.7))

    completion = 0
    completion += 15 if checks["noFailedEvent"] and checks["noRequestError"] else 0
    completion += 10 if checks["minAnswerChars"] else 0
    completion += 10 if checks["completedEvent"] else 0
    completion += 5 if checks["startedEvent"] else 0

    if semantic_score is not None:
        accuracy = 20 * max(0.0, min(1.0, float(semantic_score)))
    else:
        accuracy = 0
        accuracy += 15 * (len(keyword_hits) / len(keywords)) if keywords else 15
        accuracy += 5 if checks["forbiddenClean"] else 0

    format_checks = ["reasoning", "table", "codeBlock", "orderedList", "exports"]
    active_format_checks = [
        name
        for name in format_checks
        if name == "exports" and export_results
        or name != "exports" and requires.get(name)
    ]
    if active_format_checks:
        format_score = 15 * (sum(1 for name in active_format_checks if checks[name]) / len(active_format_checks))
    else:
        format_score = 15

    stability = 0
    stability += 7 if checks["noRequestError"] else 0
    stability += 5 if checks["startedEvent"] and checks["completedEvent"] else 0
    stability += 3 if event_order_ok(events) else 0

    performance = 0
    performance += 5 if checks["firstTokenLatency"] else 0
    performance += 5 if checks["totalLatency"] else 0

    dimensions = {
        "completion": round(completion, 2),
        "accuracy": round(accuracy, 2),
        "format": round(format_score, 2),
        "stability": round(stability, 2),
        "performance": round(performance, 2),
    }
    total = sum(dimensions.values())
    return {
        "total": round(total, 2),
        "dimensions": dimensions,
        "checks": checks,
        "keywordHits": keyword_hits,
        "forbiddenHits": forbidden_hits,
        "requestError": request_error,
        "semantic": semantic_result or {"enabled": False, "provider": "none"},
    }


def latency_ok(value: Optional[float], max_value: Optional[float]) -> bool:
    if max_value is None:
        return True
    return value is not None and value <= max_value


def event_order_ok(events: list[str]) -> bool:
    if not events:
        return False
    if "started" in events and events.index("started") != 0:
        return False
    if "completed" in events and "failed" in events:
        return False
    if "completed" in events and events[-1] != "completed":
        return False
    if "failed" in events and events[-1] != "failed":
        return False
    return True


def empty_stream_result() -> dict[str, Any]:
    return {
        "answer": "",
        "reasoning": "",
        "failedContent": "",
        "events": [],
        "metrics": {"firstTokenMs": None, "totalMs": None, "answerChars": 0, "reasoningChars": 0},
    }


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    if not results:
        return {"caseCount": 0, "averageScore": 0, "passCount": 0, "errorCount": 0}
    scores = [item["score"]["total"] for item in results]
    return {
        "caseCount": len(results),
        "averageScore": round(sum(scores) / len(scores), 2),
        "minScore": round(min(scores), 2),
        "maxScore": round(max(scores), 2),
        "passCount": sum(1 for item in results if item["status"] == "PASS"),
        "errorCount": sum(1 for item in results if item["status"] == "ERROR"),
    }


def render_markdown_report(report: dict[str, Any]) -> str:
    lines = [
        f"# Agent Eval Report - {report['dataset']}",
        "",
        f"- Base URL: `{report['baseUrl']}`",
        f"- Started: `{report['startedAt']}`",
        f"- Finished: `{report['finishedAt']}`",
        f"- Average Score: **{report['summary']['averageScore']}**",
        "",
        "| Case | Status | Score | First Token | Total | Notes |",
        "| --- | --- | ---: | ---: | ---: | --- |",
    ]
    for item in report["results"]:
        metrics = item["metrics"]
        notes = []
        if item["score"].get("forbiddenHits"):
            notes.append("forbidden hit")
        if item.get("semantic", {}).get("enabled"):
            notes.append(f"semantic={item['semantic'].get('score')}")
        if item.get("semantic", {}).get("skipped"):
            notes.append("semantic skipped")
        if item["error"]:
            notes.append(item["error"][:80].replace("|", "/"))
        lines.append(
            "| {case} | {status} | {score:.1f} | {first} | {total} | {notes} |".format(
                case=item["caseId"],
                status=item["status"],
                score=item["score"]["total"],
                first=metrics.get("firstTokenMs"),
                total=metrics.get("totalMs"),
                notes=", ".join(notes),
            )
        )
    lines.append("")
    for item in report["results"]:
        lines.extend(
            [
                f"## {item['caseId']} - {item['name']}",
                "",
                f"- Score: `{item['score']['total']}`",
                f"- Dimensions: `{json.dumps(item['score']['dimensions'], ensure_ascii=False)}`",
                f"- Semantic: `{json.dumps(item.get('semantic', {}), ensure_ascii=False)}`",
                f"- Events: `{', '.join(item['events'])}`",
                "",
                "Answer preview:",
                "",
                "```markdown",
                item["answerPreview"],
                "```",
                "",
            ]
        )
    return "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
