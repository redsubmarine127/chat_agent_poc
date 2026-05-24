from __future__ import annotations

import re
from uuid import UUID

from app.config import Settings
from app.errors import NotFoundError
from app.models import SkillResponse
from app.repositories import DynamicSkillRepository, MessageRepository


class SkillService:
    def __init__(self, settings: Settings, dynamic_skill_repository: DynamicSkillRepository, message_repository: MessageRepository) -> None:
        self._settings = settings
        self._dynamic_skill_repository = dynamic_skill_repository
        self._message_repository = message_repository

    async def list_enabled_skills(self) -> list[SkillResponse]:
        built_in = [
            SkillResponse(id=item.id, name=item.name, description=item.description, enabled=item.enabled)
            for item in self._settings.skills
            if item.enabled
        ]
        dynamic = await self._dynamic_skill_repository.list_enabled()
        return [*dynamic, *built_in]

    async def get_skill(self, skill_id: str) -> SkillResponse:
        for item in await self.list_enabled_skills():
            if item.id == skill_id:
                return item
        raise NotFoundError("Skill 不存在或未启用")

    async def extract_from_conversation(self, conversation_id: UUID, name: str = "") -> SkillResponse:
        messages = await self._message_repository.list(conversation_id)
        if not messages:
            raise NotFoundError("当前对话暂无可抽取内容")
        corpus = "\n".join(item.content for item in messages)[0:4000]
        normalized_name = (name or self._guess_name(corpus)).strip()[0:128] or "对话抽取 Skill"
        skill_id = "dynamic-" + re.sub(r"[^a-zA-Z0-9]+", "-", normalized_name.lower()).strip("-")[0:64]
        description = (
            "从当前对话自动抽取的 Skill。请优先复用以下上下文风格、约束和业务偏好：\n"
            + corpus[:1500]
        )
        return await self._dynamic_skill_repository.save(skill_id, normalized_name, description)

    def _guess_name(self, corpus: str) -> str:
        for line in corpus.splitlines():
            value = line.strip().strip("#*- ")
            if len(value) >= 4:
                return value[:32]
        return "对话抽取 Skill"

