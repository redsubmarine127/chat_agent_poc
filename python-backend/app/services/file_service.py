from __future__ import annotations

import os
from pathlib import Path
from uuid import uuid4

from fastapi import UploadFile

from app.config import Settings
from app.errors import PayloadTooLargeError, UnsupportedMediaTypeError
from app.models import AttachmentResponse
from app.repositories import AttachmentRepository


class FileService:
    def __init__(self, settings: Settings, attachment_repository: AttachmentRepository) -> None:
        self._settings = settings
        self._attachment_repository = attachment_repository

    async def upload(self, file: UploadFile) -> AttachmentResponse:
        content_type = file.content_type or "application/octet-stream"
        if content_type not in self._settings.allowed_content_types:
            raise UnsupportedMediaTypeError("不支持的文件类型")

        safe_name = Path(file.filename or "upload.bin").name
        storage_key = f"{uuid4()}-{safe_name}"
        destination = self._settings.storage_root / storage_key
        destination.parent.mkdir(parents=True, exist_ok=True)

        size = 0
        try:
            with destination.open("wb") as output:
                while chunk := await file.read(1024 * 1024):
                    size += len(chunk)
                    if size > self._settings.max_file_size:
                        raise PayloadTooLargeError("文件大小超过限制")
                    output.write(chunk)
        except Exception:
            if destination.exists():
                destination.unlink()
            raise
        finally:
            await file.close()

        os.chmod(destination, 0o600)
        return await self._attachment_repository.save(safe_name, content_type, storage_key, size)

