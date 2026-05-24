const TABLE_SEPARATOR_PATTERN = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/;
const XLSX_CONTENT_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

export function extractMarkdownTables(source) {
  if (!source) {
    return [];
  }
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const tables = [];
  let index = 0;
  while (index < lines.length) {
    if (!isTableStart(lines, index)) {
      index += 1;
      continue;
    }
    const tableLines = [lines[index], lines[index + 1]];
    index += 2;
    while (index < lines.length && isTableRow(lines[index])) {
      tableLines.push(lines[index]);
      index += 1;
    }
    const headers = splitTableRow(tableLines[0]);
    const rows = tableLines.slice(2)
      .map(splitTableRow)
      .filter((row) => row.some(Boolean));
    if (headers.length > 0 && rows.length > 0) {
      tables.push({ headers, rows });
    }
  }
  return tables;
}

export function buildTableCharts(source) {
  return extractMarkdownTables(source).flatMap((table, tableIndex) => {
    const labelColumnIndex = findLabelColumnIndex(table);
    return table.headers
      .map((header, columnIndex) => buildChart(table, tableIndex, header, columnIndex, labelColumnIndex))
      .filter(Boolean);
  });
}

export function downloadMarkdown(content, filename) {
  const safeFilename = ensureExtension(filename, 'md');
  triggerTextDownload(String(content || '当前消息暂无可导出的内容。'), safeFilename, 'text/markdown;charset=utf-8');
}

export function downloadExcelFromTables(tables, filename) {
  const safeFilename = ensureExtension(filename, 'xlsx');
  downloadBlob(
    new Blob([buildXlsxWorkbook(tables)], { type: XLSX_CONTENT_TYPE }),
    safeFilename,
    {
      description: 'Excel 工作簿',
      accept: { [XLSX_CONTENT_TYPE]: ['.xlsx'] }
    }
  );
}

export function sanitizeFilename(value) {
  const safeValue = (value || 'assistant-response')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, '_')
    .slice(0, 80);
  return safeValue || 'assistant-response';
}

function buildChart(table, tableIndex, header, columnIndex, labelColumnIndex) {
  const points = table.rows
    .map((row) => ({
      label: row[labelColumnIndex] || `第 ${table.rows.indexOf(row) + 1} 行`,
      value: parseNumber(row[columnIndex])
    }))
    .filter((point) => Number.isFinite(point.value));
  if (points.length === 0) {
    return null;
  }
  const maxValue = Math.max(...points.map((point) => Math.abs(point.value)), 1);
  return {
    id: `${tableIndex}-${columnIndex}`,
    title: header || `指标 ${columnIndex + 1}`,
    points: points.slice(0, 10).map((point) => ({
      ...point,
      percent: Math.max(4, Math.round((Math.abs(point.value) / maxValue) * 100))
    }))
  };
}

function findLabelColumnIndex(table) {
  const numericColumnIndexes = new Set();
  table.headers.forEach((header, index) => {
    if (table.rows.some((row) => Number.isFinite(parseNumber(row[index])))) {
      numericColumnIndexes.add(index);
    }
  });
  return table.headers.findIndex((header, index) => header && !numericColumnIndexes.has(index)) >= 0
    ? table.headers.findIndex((header, index) => header && !numericColumnIndexes.has(index))
    : 0;
}

function parseNumber(value) {
  if (!value) {
    return Number.NaN;
  }
  const normalizedValue = String(value).replace(/,/g, '').replace(/%$/, '').trim();
  if (!/^-?\d+(\.\d+)?$/.test(normalizedValue)) {
    return Number.NaN;
  }
  return Number(normalizedValue);
}

function downloadBlob(blob, filename, pickerOptions = {}) {
  if (typeof window.showSaveFilePicker === 'function') {
    try {
      window.showSaveFilePicker({
        suggestedName: filename,
        types: [
          {
            description: pickerOptions.description || '下载文件',
            accept: pickerOptions.accept || { [blob.type || 'application/octet-stream']: [`.${filename.split('.').pop()}`] }
          }
        ]
        })
        .then((fileHandle) => fileHandle.createWritable())
        .then(async (writableStream) => {
          await writableStream.write(await blob.arrayBuffer());
          await writableStream.close();
        })
        .catch((error) => {
          if (error?.name !== 'AbortError') {
            console.warn('文件保存失败，已切换到浏览器下载模式', error);
            triggerAnchorDownload(blob, filename);
          }
        });
      return;
    } catch (error) {
      console.warn('文件保存入口不可用，已切换到浏览器下载模式', error);
    }
  }
  triggerAnchorDownload(blob, filename);
}

function triggerTextDownload(content, filename, mimeType) {
  const dataUrl = `data:${mimeType},${encodeURIComponent(content)}`;
  const link = document.createElement('a');
  link.href = dataUrl;
  link.download = filename;
  link.rel = 'noopener';
  link.style.position = 'fixed';
  link.style.left = '-9999px';
  link.style.top = '-9999px';
  link.style.width = '1px';
  link.style.height = '1px';
  document.body.appendChild(link);
  link.dispatchEvent(new MouseEvent('click', {
    bubbles: true,
    cancelable: true,
    view: window
  }));
  window.setTimeout(() => link.remove(), 1000);
}

function triggerAnchorDownload(blob, filename) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  link.rel = 'noopener';
  link.style.position = 'fixed';
  link.style.left = '-9999px';
  link.style.top = '-9999px';
  link.style.width = '1px';
  link.style.height = '1px';
  document.body.appendChild(link);
  link.dispatchEvent(new MouseEvent('click', {
    bubbles: true,
    cancelable: true,
    view: window
  }));
  window.setTimeout(() => {
    link.remove();
    URL.revokeObjectURL(objectUrl);
  }, 15000);
}

function ensureExtension(filename, extension) {
  return filename.toLowerCase().endsWith(`.${extension}`) ? filename : `${filename}.${extension}`;
}

function isTableStart(lines, index) {
  return index + 1 < lines.length && isTableRow(lines[index]) && TABLE_SEPARATOR_PATTERN.test(lines[index + 1]);
}

function isTableRow(line) {
  return line.includes('|') && line.trim().replace(/^\|/, '').replace(/\|$/, '').includes('|');
}

function splitTableRow(line) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
}

function buildXlsxWorkbook(tables) {
  const sheetFiles = tables.map((table, index) => ({
    path: `xl/worksheets/sheet${index + 1}.xml`,
    content: renderWorksheetXml(table)
  }));
  const workbookSheets = tables.map((table, index) => {
    const sheetName = sanitizeSheetName(table.headers[0] || `Table ${index + 1}`, index);
    return `<sheet name="${escapeXml(sheetName)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>`;
  }).join('');
  const workbookRelationships = tables.map((table, index) => `
    <Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>
  `).join('');
  const worksheetOverrides = tables.map((table, index) => `
    <Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  `).join('');

  return createZip([
    {
      path: '[Content_Types].xml',
      content: xmlDocument(`
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          ${worksheetOverrides}
        </Types>
      `)
    },
    {
      path: '_rels/.rels',
      content: xmlDocument(`
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
      `)
    },
    {
      path: 'xl/workbook.xml',
      content: xmlDocument(`
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>${workbookSheets}</sheets>
        </workbook>
      `)
    },
    {
      path: 'xl/_rels/workbook.xml.rels',
      content: xmlDocument(`
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          ${workbookRelationships}
          <Relationship Id="rId${tables.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
      `)
    },
    {
      path: 'xl/styles.xml',
      content: xmlDocument(`
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
      `)
    },
    ...sheetFiles
  ]);
}

function renderWorksheetXml(table) {
  const rows = [table.headers, ...table.rows]
    .map((row, rowIndex) => renderWorksheetRow(row, rowIndex + 1, rowIndex === 0))
    .join('');
  return xmlDocument(`
    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
      <sheetData>${rows}</sheetData>
    </worksheet>
  `);
}

function renderWorksheetRow(row, rowNumber, isHeader) {
  const cells = row
    .map((value, columnIndex) => renderWorksheetCell(value, columnIndex, rowNumber, isHeader))
    .join('');
  return `<row r="${rowNumber}">${cells}</row>`;
}

function renderWorksheetCell(value, columnIndex, rowNumber, isHeader) {
  const cellReference = `${columnName(columnIndex + 1)}${rowNumber}`;
  const normalizedValue = String(value ?? '').trim();
  const numericValue = parseNumber(normalizedValue);
  if (!isHeader && Number.isFinite(numericValue)) {
    return `<c r="${cellReference}"><v>${numericValue}</v></c>`;
  }
  return `<c r="${cellReference}" t="inlineStr" s="${isHeader ? 1 : 0}"><is><t>${escapeXml(normalizedValue)}</t></is></c>`;
}

function sanitizeSheetName(value, index) {
  const cleanedValue = String(value || `Table ${index + 1}`)
    .replace(/[\[\]:*?/\\]/g, ' ')
    .trim()
    .slice(0, 31);
  return cleanedValue || `Table ${index + 1}`;
}

function columnName(index) {
  let columnIndex = index;
  let name = '';
  while (columnIndex > 0) {
    columnIndex -= 1;
    name = String.fromCharCode(65 + (columnIndex % 26)) + name;
    columnIndex = Math.floor(columnIndex / 26);
  }
  return name;
}

function xmlDocument(content) {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>${content.replace(/>\s+</g, '><').trim()}`;
}

function createZip(files) {
  const encoder = new TextEncoder();
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  files.forEach((file) => {
    const pathBytes = encoder.encode(file.path);
    const contentBytes = typeof file.content === 'string' ? encoder.encode(file.content) : file.content;
    const checksum = crc32(contentBytes);
    const localHeader = buildLocalFileHeader(pathBytes, contentBytes, checksum);
    const centralHeader = buildCentralDirectoryHeader(pathBytes, contentBytes, checksum, offset);
    localParts.push(localHeader, contentBytes);
    centralParts.push(centralHeader);
    offset += localHeader.length + contentBytes.length;
  });

  const centralDirectory = concatUint8Arrays(centralParts);
  const endRecord = buildEndOfCentralDirectory(files.length, centralDirectory.length, offset);
  return concatUint8Arrays([...localParts, centralDirectory, endRecord]);
}

function buildLocalFileHeader(pathBytes, contentBytes, checksum) {
  const header = new Uint8Array(30 + pathBytes.length);
  const view = new DataView(header.buffer);
  view.setUint32(0, 0x04034b50, true);
  view.setUint16(4, 20, true);
  view.setUint16(6, 0, true);
  view.setUint16(8, 0, true);
  view.setUint16(10, 0, true);
  view.setUint16(12, 0, true);
  view.setUint32(14, checksum, true);
  view.setUint32(18, contentBytes.length, true);
  view.setUint32(22, contentBytes.length, true);
  view.setUint16(26, pathBytes.length, true);
  view.setUint16(28, 0, true);
  header.set(pathBytes, 30);
  return header;
}

function buildCentralDirectoryHeader(pathBytes, contentBytes, checksum, offset) {
  const header = new Uint8Array(46 + pathBytes.length);
  const view = new DataView(header.buffer);
  view.setUint32(0, 0x02014b50, true);
  view.setUint16(4, 20, true);
  view.setUint16(6, 20, true);
  view.setUint16(8, 0, true);
  view.setUint16(10, 0, true);
  view.setUint16(12, 0, true);
  view.setUint16(14, 0, true);
  view.setUint32(16, checksum, true);
  view.setUint32(20, contentBytes.length, true);
  view.setUint32(24, contentBytes.length, true);
  view.setUint16(28, pathBytes.length, true);
  view.setUint16(30, 0, true);
  view.setUint16(32, 0, true);
  view.setUint16(34, 0, true);
  view.setUint16(36, 0, true);
  view.setUint32(38, 0, true);
  view.setUint32(42, offset, true);
  header.set(pathBytes, 46);
  return header;
}

function buildEndOfCentralDirectory(fileCount, centralDirectorySize, centralDirectoryOffset) {
  const record = new Uint8Array(22);
  const view = new DataView(record.buffer);
  view.setUint32(0, 0x06054b50, true);
  view.setUint16(4, 0, true);
  view.setUint16(6, 0, true);
  view.setUint16(8, fileCount, true);
  view.setUint16(10, fileCount, true);
  view.setUint32(12, centralDirectorySize, true);
  view.setUint32(16, centralDirectoryOffset, true);
  view.setUint16(20, 0, true);
  return record;
}

function concatUint8Arrays(parts) {
  const totalLength = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(totalLength);
  let offset = 0;
  parts.forEach((part) => {
    output.set(part, offset);
    offset += part.length;
  });
  return output;
}

function crc32(bytes) {
  let checksum = 0xffffffff;
  for (const byte of bytes) {
    checksum = (checksum >>> 8) ^ CRC32_TABLE[(checksum ^ byte) & 0xff];
  }
  return (checksum ^ 0xffffffff) >>> 0;
}

const CRC32_TABLE = Array.from({ length: 256 }, (_, tableIndex) => {
  let value = tableIndex;
  for (let bitIndex = 0; bitIndex < 8; bitIndex += 1) {
    value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  return value >>> 0;
});

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
