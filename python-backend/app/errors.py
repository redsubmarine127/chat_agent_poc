from __future__ import annotations


class AssistantError(Exception):
    status_code = 400
    code = "BAD_REQUEST"

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class NotFoundError(AssistantError):
    status_code = 404
    code = "NOT_FOUND"


class ConflictError(AssistantError):
    status_code = 409
    code = "CONFLICT"


class PayloadTooLargeError(AssistantError):
    status_code = 413
    code = "PAYLOAD_TOO_LARGE"


class UnsupportedMediaTypeError(AssistantError):
    status_code = 415
    code = "UNSUPPORTED_MEDIA_TYPE"

