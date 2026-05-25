# Python LangGraph 后端

这是智能对话助手的 Python 后端实现，独立放在 `python-backend/`，不影响当前 Java 后端。

## 技术选型

| 能力 | 实现 |
| --- | --- |
| Web 框架 | FastAPI |
| 对话编排 | LangGraph |
| 流式输出 | SSE `text/event-stream` |
| 数据库 | openGauss，使用 PostgreSQL 协议连接 |
| 模型接入 | OpenAI-compatible HTTP 流式接口，本地回退模型 |
| 文件导出 | Markdown / XLSX |

## 启动

要求 Python 3.11 及以上版本。

```bash
cd python-backend
python3.11 -m venv .venv
source .venv/bin/activate
pip install -e ".[test]"
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

如果本机默认 `python3` 已经是 3.11 及以上，也可以使用 `python3 -m venv .venv`。

数据库沿用根目录的 openGauss：

```bash
cd ..
docker-compose up -d
```

## 前端切换

将 Vite 代理或环境变量切到 Python 后端：

```bash
VITE_API_BASE_URL=http://127.0.0.1:8090 npm run dev
```

接口路径保持兼容：

- `GET /api/conversations`
- `POST /api/conversations`
- `DELETE /api/conversations/{conversationId}`
- `GET /api/conversations/{conversationId}/messages`
- `DELETE /api/conversations/{conversationId}/messages`
- `POST /api/conversations/{conversationId}/messages/stream`
- `GET /api/skills`
- `POST /api/skills/extractions`
- `GET /api/models`
- `POST /api/files`
- `POST /api/exports/markdown`
- `POST /api/exports/excel`

## LangGraph 流程

```mermaid
flowchart TD
    A["POST /api/conversations/{id}/messages/stream"] --> B["保存用户消息"]
    B --> C["LangGraph: compose_prompt"]
    C --> D["LangGraph: summarize_context"]
    D --> E["RoutingChatModelGateway"]
    E --> F{"模型类型"}
    F -->|"local 或未配置 key"| G["LocalFallbackChatModelGateway"]
    F -->|"openai-compatible"| H["OpenAiCompatibleChatModelGateway"]
    G --> I["SSE: reasoning / delta"]
    H --> I
    I --> J["保存助手消息"]
    J --> K["SSE: completed"]
    E -->|"异常，最多重试 3 次"| L["保存 FAILED 助手消息"]
    L --> M["SSE: failed"]
```

关键链路会输出结构化上下文日志，覆盖数据库读写、Skill 加载、模型解析、RAG 检索、MCP 工具加载、LangGraph 编排、LLM 流式调用与助手消息落库。单个关键节点连续 3 次失败后会主动停止当前流，尽量保存 `FAILED` 助手消息，并通过 `failed` SSE 事件给前端返回可读提示。

## 测试

```bash
pytest
```

## Langfuse 观测

Langfuse 是可选能力，默认开启。未配置凭据时会自动降级为 Noop，不影响对话链路。需要生产 Trace 时安装可选依赖并配置环境变量：

```bash
pip install -e ".[observability]"

export LANGFUSE_ENABLED=true
export LANGFUSE_HOST=https://cloud.langfuse.com
export LANGFUSE_PUBLIC_KEY=your-public-key
export LANGFUSE_SECRET_KEY=your-secret-key
export LANGFUSE_ENVIRONMENT=local
```

开启后，Python LangGraph 后端会为每次流式对话记录 `conversationId`、`messageId`、`modelId`、`skillId`、RAG context、MCP tool、终态和输出摘要。Langfuse 初始化失败时会自动降级为 Noop，不影响对话链路。
