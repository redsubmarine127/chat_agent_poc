# 智能对话助手

一个前后端分离的智能对话助手工程：

- 后端：Java 21、Spring Boot 3.x、WebFlux、R2DBC、openGauss、Spring AI Alibaba DashScope。
- 前端：Vue 3、Vite、lucide-vue-next。
- 能力：对话创建/切换/删除、消息列表、文件上传、Skill 选择、SSE 流式输出。

## 项目规格

长期需求和工程约束已沉淀到 OpenSpec：

- `openspec/project.md`
- `openspec/specs/product-capabilities/spec.md`
- `openspec/specs/frontend-experience/spec.md`
- `openspec/specs/java-backend/spec.md`
- `openspec/specs/python-langgraph-backend/spec.md`
- `openspec/specs/evaluation/spec.md`
- `openspec/specs/operations/spec.md`

未来使用 Code Agent 修改项目时，请先阅读 `AGENTS.md` 和 `openspec/`。

## 启动依赖

```bash
docker compose up -d
```

## 后端

```bash
cd backend
mvn spring-boot:run
```

常用环境变量：

```bash
export DB_R2DBC_URL=r2dbc:postgresql://localhost:5432/assistant
export DB_JDBC_URL=jdbc:opengauss://localhost:5432/assistant
export DB_JDBC_DRIVER=org.opengauss.Driver
export DB_USERNAME=assistant
export DB_PASSWORD=OpenGauss@123
export DB_FLYWAY_USERNAME=assistant
export DB_FLYWAY_PASSWORD=OpenGauss@123
export DASHSCOPE_API_KEY=your-api-key
export DASHSCOPE_MODEL=qwen-plus
```

未配置 `DASHSCOPE_API_KEY` 时，后端会使用本地回退网关，方便先验证前后端链路。

默认数据库已切换为 openGauss。Compose 基于 `opengauss/opengauss:5.0.0` 构建本地开发镜像，初始化数据库为 `assistant`，创建 `assistant` 业务用户，并使用 `md5` host auth 兼容当前 PostgreSQL 协议 R2DBC/JDBC 驱动。
应用仍使用 PostgreSQL 协议的 R2DBC/JDBC 驱动连接 openGauss，以保持 WebFlux 反应式链路和 Flyway 迁移能力。

## 前端

```bash
cd frontend
npm install
npm run dev
```

默认后端地址为 `http://localhost:8080`，可通过 `VITE_API_BASE_URL` 覆盖。

## 测试

```bash
cd backend
mvn test
```
