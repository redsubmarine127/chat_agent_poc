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


class AgentStepFailedError(AssistantError):
    status_code = 500
    code = "AGENT_STEP_FAILED"

    def __init__(self, step_name: str, attempts: int, cause: Exception) -> None:
        super().__init__(f"{step_name} 连续重试 {attempts} 次仍未成功，已主动停止流程。请检查相关配置或稍后重试。")
        self.step_name = step_name
        self.attempts = attempts
        self.cause = cause
