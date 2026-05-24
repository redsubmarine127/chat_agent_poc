from __future__ import annotations

from app.config import ModelConfig, Settings
from app.errors import NotFoundError
from app.models import ModelResponse


class ModelService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def list_models(self) -> list[ModelResponse]:
        enabled_models = [item for item in self._settings.models if item.enabled]
        ordered_models = sorted(
            enabled_models,
            key=lambda item: (item.id != self._settings.default_model_id, item.provider == "local", item.name),
        )
        return [
            ModelResponse(
                id=item.id,
                name=item.name,
                provider=item.provider,
                modelName=item.model_name,
                enabled=item.enabled,
            )
            for item in ordered_models
        ]

    def get_model(self, model_id: str) -> ModelConfig:
        resolved_id = model_id or self._settings.default_model_id
        for item in self._settings.models:
            if item.id == resolved_id and item.enabled:
                return item
        raise NotFoundError("模型不存在或未启用")
