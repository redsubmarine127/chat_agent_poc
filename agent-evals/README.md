# Agent Evals

这个目录用于评估智能对话 Agent 的效果，支持同时评估 Java 后端和 Python LangGraph 后端，只要目标服务兼容当前 `/api` 接口即可。

## 简化评估体系

当前项目保留三层评估能力：

| 层级 | 工具 | 职责 |
| --- | --- | --- |
| 项目适配层 | `agent-evals/` | 调用真实 HTTP/SSE 接口、校验事件协议、验证导出文件、清理测试会话、生成报告 |
| 质量评分层 | DeepEval | 对回答质量、任务完成度、指令遵循、多轮一致性等语义维度做 LLM-as-judge 评分 |
| 线上观测层 | Langfuse | 记录生产对话 trace、模型/Skill/RAG/MCP 元数据、用户反馈，并沉淀可回归样本 |

不默认引入 `promptfoo` 和 `ragas`。如果后续确实需要模型矩阵测试或 RAG 专项指标，应先更新 `openspec/specs/evaluation/spec.md`。

## DeepEval 与 agent-evals 的边界

DeepEval 不能直接完全替代 `agent-evals`，因为本项目必须验证真实产品链路：

- SSE 事件顺序必须保持 `started -> reasoning* -> delta* -> completed|failed`。
- Java 与 Python 后端必须保持同一套前端 API 兼容。
- Markdown、Excel 导出必须返回真实且非空的文件字节。
- 测试会话必须默认自动清理，避免污染最近会话列表。

因此推荐做法是：`agent-evals` 继续负责“真实接口适配与协议回归”，DeepEval 作为可插拔质量评分器加入到 `agent-evals` 的评分阶段。

## Langfuse 的定位

Langfuse 用于生产环境观测和反馈闭环，而不是替代本地回归测试。建议记录：

- `conversationId`、`messageId`
- 后端类型：`java` 或 `python-langgraph`
- `modelId`、加载的 Skill、RAG context、MCP tool
- stream 终态：`completed` 或 `failed`
- 首 token 延迟、总耗时、错误上下文
- 用户点赞/点踩或人工评分

低分或用户反馈异常的 trace 可以脱敏后转为 `agent-evals/datasets/` 中的回归用例。

## 快速运行

Python LangGraph 后端冒烟评估：

```bash
python3 agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8090 \
  --dataset agent-evals/datasets/smoke_langgraph.json
```

真实质量评估：

```bash
python3 agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8090 \
  --dataset agent-evals/datasets/assistant_quality.json \
  --model-id deepseek-v4-flash
```

启用 DeepEval 语义评分：

```bash
python3 -m pip install -r agent-evals/requirements.txt

python3 agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8090 \
  --dataset agent-evals/datasets/assistant_quality.json \
  --model-id deepseek-v4-flash \
  --semantic-evaluator deepeval \
  --fail-under 80
```

对比 Java 后端：

```bash
python3 agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8080 \
  --dataset agent-evals/datasets/assistant_quality.json
```

## 评分维度

总分 100：

| 维度 | 分值 | 说明 |
| --- | ---: | --- |
| 任务完成 | 40 | 有回答、无 failed、达到最小长度、包含关键内容 |
| 准确性 | 20 | 关键词命中、禁用词未出现 |
| 格式与交互 | 15 | reasoning、表格、代码块、导出能力 |
| 稳定性 | 15 | SSE 事件完整、无异常、完成事件正常 |
| 性能 | 10 | 首 token 延迟、总耗时 |

当用例声明 `expected.semantic` 且启用 DeepEval 时，准确性维度会优先使用 DeepEval 的 0-1 语义评分折算为 20 分；否则继续使用关键词和禁用词做确定性评分。

报告会输出到 `agent-evals/reports/`，包含 JSON 明细和 Markdown 汇总。
