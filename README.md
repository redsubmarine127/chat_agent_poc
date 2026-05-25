# 智能对话助手

一个前后端分离的智能对话助手工程，包含 Java Spring Boot 后端、Python LangGraph 后端和共享 Vue 3 前端。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Java 后端 | Java 21、Spring Boot 3.x、WebFlux、R2DBC、Spring AI Alibaba、OpenAI-compatible Gateway |
| Python 后端 | Python 3.11+、FastAPI、LangGraph、asyncpg、OpenAI-compatible Gateway |
| 前端 | Vue 3、Vite、lucide-vue-next |
| 持久化 | 默认内存模式；可切换到 openGauss 数据库模式 |

## 持久化模式

项目默认使用内存存储，适合快速启动和功能验证，不需要安装或启动数据库。

```bash
export ASSISTANT_PERSISTENCE_MODE=memory
```

内存模式会把对话、消息、附件元信息和动态 Skill 保存在当前进程中，服务重启后数据会丢失。上传文件本体仍写入本地 `data/uploads`，避免大文件占用进程内存。

需要持久化数据时切换到 openGauss：

```bash
export ASSISTANT_PERSISTENCE_MODE=database
docker compose up -d
```

## Java 后端启动

要求 Java 21 和 Maven。

快速内存模式：

```bash
cd backend
export ASSISTANT_PERSISTENCE_MODE=memory
mvn spring-boot:run
```

数据库模式：

```bash
docker compose up -d

cd backend
export ASSISTANT_PERSISTENCE_MODE=database
export DB_R2DBC_URL=r2dbc:postgresql://localhost:5432/assistant
export DB_JDBC_URL=jdbc:opengauss://localhost:5432/assistant
export DB_JDBC_DRIVER=org.opengauss.Driver
export DB_USERNAME=assistant
export DB_PASSWORD=OpenGauss@123
export DB_FLYWAY_USERNAME=assistant
export DB_FLYWAY_PASSWORD=OpenGauss@123
mvn spring-boot:run
```

常用模型配置：

```bash
export ASSISTANT_DEFAULT_MODEL_ID=deepseek-v4-flash
export DEEPSEEK_API_KEY=your-api-key
export DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com/v1
```

未配置模型 API Key 时，后端会使用本地回退模型，方便先验证前后端链路。

## Python LangGraph 后端启动

要求 Python 3.11 及以上。

```bash
cd python-backend
python3.11 -m venv .venv
source .venv/bin/activate
pip install -e ".[test]"
cp .env.example .env
```

快速内存模式：

```bash
export ASSISTANT_PERSISTENCE_MODE=memory
uvicorn app.main:app --host 127.0.0.1 --port 8090 --reload
```

数据库模式：

```bash
cd ..
docker compose up -d

cd python-backend
export ASSISTANT_PERSISTENCE_MODE=database
export DB_DSN=postgresql://assistant:OpenGauss%40123@127.0.0.1:5432/assistant
uvicorn app.main:app --host 127.0.0.1 --port 8090 --reload
```

## 前端启动

Java 后端页面，默认连接 `http://127.0.0.1:8080`：

```bash
cd frontend
npm install
VITE_API_BASE_URL=http://127.0.0.1:8080 npm run dev -- --host 127.0.0.1 --port 5173
```

Python 后端页面，默认连接 `http://127.0.0.1:8090`：

```bash
cd frontend
npm install
VITE_API_BASE_URL=http://127.0.0.1:8090 npm run dev -- --host 127.0.0.1 --port 5175
```

## 验证命令

```bash
cd frontend
npm run build
```

```bash
cd backend
mvn test
```

```bash
cd python-backend
.venv/bin/pytest
```

Agent 冒烟评估：

```bash
python-backend/.venv/bin/python agent-evals/run_evals.py \
  --base-url http://127.0.0.1:8090 \
  --dataset agent-evals/datasets/smoke_langgraph.json \
  --fail-under 80
```

## OpenSpec

长期需求和工程约束已沉淀到 OpenSpec：

- `openspec/project.md`
- `openspec/specs/product-capabilities/spec.md`
- `openspec/specs/frontend-experience/spec.md`
- `openspec/specs/java-backend/spec.md`
- `openspec/specs/python-langgraph-backend/spec.md`
- `openspec/specs/evaluation/spec.md`
- `openspec/specs/operations/spec.md`

未来使用 Code Agent 修改项目时，请先阅读 `AGENTS.md` 和 `openspec/`。
