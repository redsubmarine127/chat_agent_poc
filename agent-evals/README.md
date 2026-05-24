# Agent Evals

这个目录用于评估智能对话 Agent 的效果，支持同时评估 Java 后端和 Python LangGraph 后端，只要目标服务兼容当前 `/api` 接口即可。

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

报告会输出到 `agent-evals/reports/`，包含 JSON 明细和 Markdown 汇总。

