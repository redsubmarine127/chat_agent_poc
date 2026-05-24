from __future__ import annotations

import re
from dataclasses import dataclass
from io import BytesIO

from openpyxl import Workbook


@dataclass(frozen=True, slots=True)
class ExportFile:
    filename: str
    content_type: str
    content: bytes


class ExportService:
    def markdown(self, content: str, filename: str) -> ExportFile:
        return ExportFile(
            filename=self._safe_filename(filename, ".md"),
            content_type="text/markdown; charset=utf-8",
            content=content.encode("utf-8"),
        )

    def excel(self, content: str, filename: str) -> ExportFile:
        workbook = Workbook()
        workbook.remove(workbook.active)
        tables = self._extract_tables(content)
        if not tables:
            table = [["内容"], [content or "暂无内容"]]
            tables = [table]
        for index, table in enumerate(tables, start=1):
            sheet = workbook.create_sheet(title=f"Sheet{index}")
            for row in table:
                sheet.append(row)
        buffer = BytesIO()
        workbook.save(buffer)
        return ExportFile(
            filename=self._safe_filename(filename, ".xlsx"),
            content_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            content=buffer.getvalue(),
        )

    def _extract_tables(self, content: str) -> list[list[list[str]]]:
        lines = content.splitlines()
        tables: list[list[list[str]]] = []
        index = 0
        while index + 1 < len(lines):
            if self._is_table_row(lines[index]) and self._is_separator(lines[index + 1]):
                table = [self._split_row(lines[index])]
                index += 2
                while index < len(lines) and self._is_table_row(lines[index]):
                    table.append(self._split_row(lines[index]))
                    index += 1
                tables.append(table)
                continue
            index += 1
        return tables

    def _is_table_row(self, line: str) -> bool:
        value = line.strip()
        return "|" in value and value.strip("|").count("|") >= 1

    def _is_separator(self, line: str) -> bool:
        return bool(re.match(r"^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$", line))

    def _split_row(self, line: str) -> list[str]:
        return [cell.strip() for cell in line.strip().strip("|").split("|")]

    def _safe_filename(self, value: str, suffix: str) -> str:
        name = re.sub(r"[^\w.\-\u4e00-\u9fff]+", "_", value.strip())[:120] or "assistant-export"
        return name if name.endswith(suffix) else f"{name}{suffix}"

