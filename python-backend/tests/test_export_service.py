from app.services.export_service import ExportService


def test_markdown_export_should_keep_content():
    service = ExportService()

    result = service.markdown("# 标题\n正文", "report.md")

    assert result.filename == "report.md"
    assert result.content_type.startswith("text/markdown")
    assert result.content.decode("utf-8") == "# 标题\n正文"


def test_excel_export_should_generate_xlsx_bytes():
    service = ExportService()

    result = service.excel("| 名称 | 数量 |\n| --- | ---: |\n| A | 12 |", "table.xlsx")

    assert result.filename == "table.xlsx"
    assert result.content.startswith(b"PK")
    assert len(result.content) > 1000

