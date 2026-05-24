package com.example.assistant.application.export;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportService {

    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    public ExportFile markdown(String content, String filename) {
        String safeContent = StringUtils.hasText(content) ? content : "当前消息暂无可导出的内容。";
        return new ExportFile(
                ensureExtension(sanitizeFilename(filename, "assistant-response"), ".md"),
                "text/markdown;charset=UTF-8",
                safeContent.getBytes(StandardCharsets.UTF_8)
        );
    }

    public ExportFile excel(String content, String filename) {
        List<MarkdownTable> tables = extractMarkdownTables(content);
        if (tables.isEmpty()) {
            tables = List.of(new MarkdownTable(List.of("内容"), List.of(List.of(StringUtils.hasText(content) ? content : "当前消息暂无可导出的内容。"))));
        }
        return new ExportFile(
                ensureExtension(sanitizeFilename(filename, "assistant-tables"), ".xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildWorkbook(tables)
        );
    }

    private byte[] buildWorkbook(List<MarkdownTable> tables) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "[Content_Types].xml", contentTypesXml(tables.size()));
            writeEntry(zipOutputStream, "_rels/.rels", rootRelationshipsXml());
            writeEntry(zipOutputStream, "xl/workbook.xml", workbookXml(tables));
            writeEntry(zipOutputStream, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml(tables.size()));
            writeEntry(zipOutputStream, "xl/styles.xml", stylesXml());
            for (int index = 0; index < tables.size(); index++) {
                writeEntry(zipOutputStream, "xl/worksheets/sheet" + (index + 1) + ".xml", worksheetXml(tables.get(index)));
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("构建 Excel 文件失败", exception);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private String contentTypesXml(int sheetCount) {
        StringBuilder overrides = new StringBuilder();
        for (int index = 1; index <= sheetCount; index++) {
            overrides.append("""
                    <Override PartName="/xl/worksheets/sheet%s.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    """.formatted(index));
        }
        return xmlDocument("""
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                  %s
                </Types>
                """.formatted(overrides));
    }

    private String rootRelationshipsXml() {
        return xmlDocument("""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """);
    }

    private String workbookXml(List<MarkdownTable> tables) {
        StringBuilder sheets = new StringBuilder();
        for (int index = 0; index < tables.size(); index++) {
            String sheetName = sanitizeSheetName(tables.get(index).headers().isEmpty() ? "" : tables.get(index).headers().getFirst(), index);
            sheets.append("""
                    <sheet name="%s" sheetId="%s" r:id="rId%s"/>
                    """.formatted(escapeXml(sheetName), index + 1, index + 1));
        }
        return xmlDocument("""
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>%s</sheets>
                </workbook>
                """.formatted(sheets));
    }

    private String workbookRelationshipsXml(int sheetCount) {
        StringBuilder relationships = new StringBuilder();
        for (int index = 1; index <= sheetCount; index++) {
            relationships.append("""
                    <Relationship Id="rId%s" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet%s.xml"/>
                    """.formatted(index, index));
        }
        relationships.append("""
                <Relationship Id="rId%s" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                """.formatted(sheetCount + 1));
        return xmlDocument("""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  %s
                </Relationships>
                """.formatted(relationships));
    }

    private String stylesXml() {
        return xmlDocument("""
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="2">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                  </fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                  </cellXfs>
                </styleSheet>
                """);
    }

    private String worksheetXml(MarkdownTable table) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(table.headers());
        rows.addAll(table.rows());
        StringBuilder sheetData = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            sheetData.append("<row r=\"").append(rowIndex + 1).append("\">");
            List<String> row = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                sheetData.append(cellXml(row.get(columnIndex), columnIndex, rowIndex + 1, rowIndex == 0));
            }
            sheetData.append("</row>");
        }
        return xmlDocument("""
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>%s</sheetData>
                </worksheet>
                """.formatted(sheetData));
    }

    private String cellXml(String value, int columnIndex, int rowNumber, boolean header) {
        String cellReference = columnName(columnIndex + 1) + rowNumber;
        String normalizedValue = value == null ? "" : value.trim();
        if (!header && normalizedValue.matches("-?\\d+(\\.\\d+)?")) {
            return "<c r=\"%s\"><v>%s</v></c>".formatted(cellReference, normalizedValue);
        }
        return "<c r=\"%s\" t=\"inlineStr\" s=\"%s\"><is><t>%s</t></is></c>"
                .formatted(cellReference, header ? 1 : 0, escapeXml(normalizedValue));
    }

    private List<MarkdownTable> extractMarkdownTables(String source) {
        if (!StringUtils.hasText(source)) {
            return List.of();
        }
        String[] lines = source.replace("\r\n", "\n").split("\n");
        List<MarkdownTable> tables = new ArrayList<>();
        int index = 0;
        while (index < lines.length) {
            if (!isTableStart(lines, index)) {
                index++;
                continue;
            }
            List<String> tableLines = new ArrayList<>();
            tableLines.add(lines[index]);
            tableLines.add(lines[index + 1]);
            index += 2;
            while (index < lines.length && isTableRow(lines[index])) {
                tableLines.add(lines[index]);
                index++;
            }
            List<String> headers = splitTableRow(tableLines.getFirst());
            List<List<String>> rows = tableLines.stream().skip(2).map(this::splitTableRow).filter(row -> row.stream().anyMatch(StringUtils::hasText)).toList();
            if (!headers.isEmpty() && !rows.isEmpty()) {
                tables.add(new MarkdownTable(headers, rows));
            }
        }
        return tables;
    }

    private boolean isTableStart(String[] lines, int index) {
        return index + 1 < lines.length && isTableRow(lines[index]) && TABLE_SEPARATOR_PATTERN.matcher(lines[index + 1]).matches();
    }

    private boolean isTableRow(String line) {
        String trimmedLine = line.trim().replaceFirst("^\\|", "").replaceFirst("\\|$", "");
        return line.contains("|") && trimmedLine.contains("|");
    }

    private List<String> splitTableRow(String line) {
        String normalizedLine = line.trim().replaceFirst("^\\|", "").replaceFirst("\\|$", "");
        return Pattern.compile("\\|").splitAsStream(normalizedLine).map(String::trim).toList();
    }

    private String sanitizeFilename(String filename, String fallback) {
        String candidate = StringUtils.hasText(filename) ? filename : fallback;
        String safeFilename = ILLEGAL_FILENAME_CHARS.matcher(candidate).replaceAll("_").replaceAll("\\s+", "_");
        if (!StringUtils.hasText(safeFilename)) {
            return fallback + "-" + UUID.randomUUID();
        }
        return safeFilename.length() > 120 ? safeFilename.substring(0, 120) : safeFilename;
    }

    private String ensureExtension(String filename, String extension) {
        return filename.toLowerCase(Locale.ROOT).endsWith(extension) ? filename : filename + extension;
    }

    private String sanitizeSheetName(String value, int index) {
        String sheetName = StringUtils.hasText(value) ? value : "Table " + (index + 1);
        String safeSheetName = sheetName.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (!StringUtils.hasText(safeSheetName)) {
            return "Table " + (index + 1);
        }
        return safeSheetName.length() > 31 ? safeSheetName.substring(0, 31) : safeSheetName;
    }

    private String columnName(int index) {
        int columnIndex = index;
        StringBuilder name = new StringBuilder();
        while (columnIndex > 0) {
            columnIndex--;
            name.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex /= 26;
        }
        return name.toString();
    }

    private String xmlDocument(String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + content.replaceAll(">\\s+<", "><").trim();
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record MarkdownTable(List<String> headers, List<List<String>> rows) {
    }

    public record ExportFile(String filename, String contentType, byte[] content) {
    }
}
