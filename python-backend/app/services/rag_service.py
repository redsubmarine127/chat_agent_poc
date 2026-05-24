from __future__ import annotations

import re
from dataclasses import dataclass

from app.config import RagDocumentConfig, Settings


@dataclass(frozen=True, slots=True)
class RagContext:
    source_id: str
    title: str
    content: str
    score: float


class RagService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def retrieve(self, query: str, limit: int | None = None) -> list[RagContext]:
        if not self._settings.rag.enabled or not query.strip():
            return []
        effective_limit = min(max(limit or self._settings.rag.top_k, 1), self._settings.rag.top_k)
        terms = self._tokenize(query)
        contexts = [
            RagContext(document.id, document.title, document.content, self._score(document, terms))
            for document in self._settings.rag.documents
        ]
        return sorted(
            (context for context in contexts if context.score > 0),
            key=lambda item: item.score,
            reverse=True,
        )[:effective_limit]

    def _tokenize(self, query: str) -> set[str]:
        return {term.lower() for term in re.split(r"[^\w\u4e00-\u9fff]+", query) if term.strip()}

    def _score(self, document: RagDocumentConfig, terms: set[str]) -> float:
        searchable_content = f"{document.title}\n{document.content}".lower()
        matched_count = sum(1 for term in terms if term in searchable_content)
        if matched_count == 0:
            return 0.0
        title_boost = sum(0.5 for term in terms if term in document.title.lower())
        return matched_count + title_boost
