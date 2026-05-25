# Java 后端

Java 后端基于 Java 21、Spring Boot 3.x、WebFlux、R2DBC 和 OpenAI-compatible 模型网关。

## 快速启动

默认使用内存持久化，不需要启动数据库：

```bash
cd backend
export ASSISTANT_PERSISTENCE_MODE=memory
mvn spring-boot:run
```

服务默认端口为 `8080`。

## openGauss 数据库模式

需要持久化数据时，先在仓库根目录启动 openGauss：

```bash
docker compose up -d
```

再启动 Java 后端：

```bash
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

数据库迁移只会在 `ASSISTANT_PERSISTENCE_MODE=database` 时执行。

## 模型配置

```bash
export ASSISTANT_DEFAULT_MODEL_ID=deepseek-v4-flash
export DEEPSEEK_API_KEY=your-api-key
export DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com/v1
```

未配置模型 API Key 时，后端会走本地回退模型，方便快速验证接口和前端页面。

## 测试

```bash
cd backend
mvn test
```
