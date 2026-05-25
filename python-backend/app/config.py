from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


@dataclass(frozen=True, slots=True)
class SkillConfig:
    id: str
    name: str
    description: str
    enabled: bool = True


@dataclass(frozen=True, slots=True)
class ModelConfig:
    id: str
    name: str
    provider: str
    model_name: str
    base_url: str = ""
    api_key: str = ""
    enabled: bool = True


@dataclass(frozen=True, slots=True)
class RagDocumentConfig:
    id: str
    title: str
    content: str


@dataclass(frozen=True, slots=True)
class RagConfig:
    enabled: bool
    top_k: int
    documents: tuple[RagDocumentConfig, ...]


@dataclass(frozen=True, slots=True)
class McpToolConfig:
    name: str
    description: str
    enabled: bool = True


@dataclass(frozen=True, slots=True)
class McpConfig:
    enabled: bool
    tools: tuple[McpToolConfig, ...]


@dataclass(frozen=True, slots=True)
class LangfuseConfig:
    enabled: bool
    host: str
    public_key: str
    secret_key: str
    environment: str


@dataclass(frozen=True, slots=True)
class Settings:
    server_host: str
    server_port: int
    persistence_mode: str
    db_dsn: str
    storage_root: Path
    max_file_size: int
    allowed_content_types: frozenset[str]
    default_model_id: str
    skills: tuple[SkillConfig, ...]
    models: tuple[ModelConfig, ...]
    rag: RagConfig
    mcp: McpConfig
    langfuse: LangfuseConfig


def load_dotenv_file() -> None:
    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        stripped_line = line.strip()
        if not stripped_line or stripped_line.startswith("#") or "=" not in stripped_line:
            continue
        key, value = stripped_line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def env(name: str, default: str = "") -> str:
    return os.getenv(name, default)


def env_bool(name: str, default: bool) -> bool:
    value = env(name, str(default)).strip().lower()
    return value in {"1", "true", "yes", "on"}


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    load_dotenv_file()
    deepseek_api_key = env("DEEPSEEK_API_KEY", "")
    return Settings(
        server_host=env("SERVER_HOST", "0.0.0.0"),
        server_port=int(env("SERVER_PORT", "8090")),
        persistence_mode=env("ASSISTANT_PERSISTENCE_MODE", "memory").strip().lower(),
        db_dsn=env("DB_DSN", "postgresql://assistant:OpenGauss%40123@127.0.0.1:5432/assistant"),
        storage_root=Path(env("ASSISTANT_STORAGE_ROOT", "./data/uploads")).resolve(),
        max_file_size=int(env("ASSISTANT_MAX_FILE_SIZE", "10485760")),
        allowed_content_types=frozenset(
            {
                "text/plain",
                "text/markdown",
                "application/pdf",
                "image/png",
                "image/jpeg",
            }
        ),
        default_model_id=env("ASSISTANT_DEFAULT_MODEL_ID", "deepseek-v4-flash"),
        skills=(
            SkillConfig("general", "通用助手", "适合日常问答、总结和分析。"),
            SkillConfig("code-review", "代码审查", "聚焦缺陷、风险、可维护性和测试缺口。"),
            SkillConfig("java-architect", "Java 架构师", "针对 Java、Spring Boot、微服务和 DDD 提供架构建议。"),
        ),
        models=(
            ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback"),
            ModelConfig(
                "gpt-5.5",
                "GPT-5.5",
                "openai",
                env("OPENAI_MODEL_NAME", "gpt-5.5"),
                env("OPENAI_BASE_URL", "https://api.openai.com/v1"),
                env("OPENAI_API_KEY", ""),
            ),
            ModelConfig(
                "minimax-2.7",
                "MiniMax 2.7",
                "openai-compatible",
                env("MINIMAX_MODEL_NAME", "minimax-2.7"),
                env("MINIMAX_BASE_URL", "https://api.minimax.chat/v1"),
                env("MINIMAX_API_KEY", ""),
            ),
            ModelConfig(
                "deepseek-v4-pro",
                "DeepSeek V4 Pro",
                "openai-compatible",
                env("DEEPSEEK_PRO_MODEL_NAME", "deepseek-v4-pro"),
                env("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
                deepseek_api_key,
            ),
            ModelConfig(
                "deepseek-v4-flash",
                "DeepSeek V4 Flash",
                "openai-compatible",
                env("DEEPSEEK_FLASH_MODEL_NAME", "deepseek-v4-flash"),
                env("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
                deepseek_api_key,
            ),
            ModelConfig(
                "glm-5.1",
                "GLM 5.1",
                "openai-compatible",
                env("GLM_MODEL_NAME", "glm-5.1"),
                env("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4"),
                env("GLM_API_KEY", ""),
            ),
        ),
        rag=RagConfig(
            enabled=env_bool("ASSISTANT_RAG_ENABLED", True),
            top_k=max(1, int(env("ASSISTANT_RAG_TOP_K", "5"))),
            documents=(
                RagDocumentConfig(
                    "product-capabilities",
                    "智能对话助手能力边界",
                    "支持对话创建、切换、删除、清空上下文、流式输出、Skill 管理、文件上传、Markdown 与 Excel 导出、图表展示和模型动态选择。",
                ),
                RagDocumentConfig(
                    "backend-architecture",
                    "后端架构说明",
                    "Java 后端采用 Spring Boot WebFlux、轻量 DDD 分层、R2DBC 数据访问、OpenAI-compatible 模型网关、本地回退模型和统一异常处理。",
                ),
                RagDocumentConfig(
                    "python-langgraph",
                    "Python LangGraph 后端说明",
                    "Python 后端采用 FastAPI、LangGraph、PostgreSQL/openGauss 兼容存储，并保持与 Java 后端一致的前端 API 契约。",
                ),
            ),
        ),
        mcp=McpConfig(
            enabled=env_bool("ASSISTANT_MCP_ENABLED", True),
            tools=(
                McpToolConfig("context.search", "从当前项目配置的 RAG 知识源中检索相关上下文，入参包含 query 与可选 limit。"),
                McpToolConfig("artifact.export", "预留导出工具边界，后续可接入 Markdown、Excel、PPT 或其他文件生成器。"),
            ),
        ),
        langfuse=LangfuseConfig(
            enabled=env_bool("LANGFUSE_ENABLED", True),
            host=env("LANGFUSE_HOST", "https://cloud.langfuse.com"),
            public_key=env("LANGFUSE_PUBLIC_KEY", ""),
            secret_key=env("LANGFUSE_SECRET_KEY", ""),
            environment=env("LANGFUSE_ENVIRONMENT", "local"),
        ),
    )
