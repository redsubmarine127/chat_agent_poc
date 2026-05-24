const TABLE_SEPARATOR_PATTERN = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/;

export function renderMarkdown(source) {
  if (!source) {
    return '';
  }

  const blocks = splitMarkdownBlocks(normalizeMarkdownSource(source.replace(/\r\n/g, '\n')));
  return blocks.map(renderBlock).join('');
}

function normalizeMarkdownSource(source) {
  const lines = source.split('\n');
  let insideCodeFence = false;

  return lines
    .flatMap((line) => {
      if (line.trimStart().startsWith('```')) {
        insideCodeFence = !insideCodeFence;
        return line;
      }
      if (insideCodeFence) {
        return line;
      }
      return normalizeProseLine(line).split('\n');
    })
    .join('\n');
}

function normalizeProseLine(line) {
  return line
    .replace(/([^\s#])(?=#{2,6}(?!#)\S)/g, '$1\n')
    .split('\n')
    .flatMap(normalizeProseSegment)
    .join('\n');
}

function normalizeProseSegment(segment) {
  const headingMatch = segment.match(/^(\s*#{1,6})\s*(\S.*)$/);
  if (!headingMatch) {
    return segment;
  }

  const headingMarker = headingMatch[1];
  const headingContent = headingMatch[2];
  const tableStartIndex = findInlineTableStart(headingContent);
  if (tableStartIndex < 0) {
    return `${headingMarker} ${headingContent}`;
  }

  return [
    `${headingMarker} ${headingContent.slice(0, tableStartIndex).trim()}`,
    headingContent.slice(tableStartIndex).trim()
  ];
}

function findInlineTableStart(content) {
  const firstPipeIndex = content.indexOf('|');
  if (firstPipeIndex <= 0) {
    return -1;
  }

  const tableCandidate = content.slice(firstPipeIndex);
  const pipeCount = (tableCandidate.match(/\|/g) || []).length;
  if (pipeCount < 2) {
    return -1;
  }
  return firstPipeIndex;
}

function splitMarkdownBlocks(source) {
  const lines = source.split('\n');
  const blocks = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }

    if (line.trimStart().startsWith('```')) {
      const fenceInfo = parseFenceInfo(line.trim().slice(3).trim());
      const codeLines = [];
      if (fenceInfo.inlineCode) {
        codeLines.push(fenceInfo.inlineCode);
      }
      index += 1;
      while (index < lines.length && !lines[index].trimStart().startsWith('```')) {
        codeLines.push(lines[index]);
        index += 1;
      }
      blocks.push({ type: 'code', language: fenceInfo.language, content: codeLines.join('\n') });
      index += index < lines.length ? 1 : 0;
      continue;
    }

    if (isTableStart(lines, index)) {
      const tableLines = [lines[index], lines[index + 1]];
      index += 2;
      while (index < lines.length && isTableRow(lines[index])) {
        tableLines.push(lines[index]);
        index += 1;
      }
      blocks.push({ type: 'table', lines: tableLines });
      continue;
    }

    if (isHeadingLine(line)) {
      blocks.push(parseHeading(line));
      index += 1;
      continue;
    }

    if (/^\s*[-*]\s*/.test(line) && line.trim().length > 1) {
      const items = [];
      while (index < lines.length && /^\s*[-*]\s*/.test(lines[index]) && lines[index].trim().length > 1) {
        items.push(lines[index].replace(/^\s*[-*]\s*/, ''));
        index += 1;
      }
      blocks.push({ type: 'list', ordered: false, items });
      continue;
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      const items = [];
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^\s*\d+\.\s+/, ''));
        index += 1;
      }
      blocks.push({ type: 'list', ordered: true, items });
      continue;
    }

    if (/^\s*>/.test(line)) {
      const quotes = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) {
        quotes.push(lines[index].replace(/^\s*>\s?/, ''));
        index += 1;
      }
      blocks.push({ type: 'quote', content: quotes.join('\n') });
      continue;
    }

    const paragraphLines = [];
    while (index < lines.length && lines[index].trim() && !startsSpecialBlock(lines, index)) {
      paragraphLines.push(lines[index]);
      index += 1;
    }
    blocks.push({ type: 'paragraph', content: paragraphLines.join('\n') });
  }

  return blocks;
}

function renderBlock(block) {
  switch (block.type) {
    case 'code':
      return renderCodeBlock(block);
    case 'table':
      return renderTable(block.lines);
    case 'list':
      return renderList(block);
    case 'quote':
      return `<blockquote>${renderInline(block.content).replace(/\n/g, '<br>')}</blockquote>`;
    case 'heading':
      return renderHeading(block);
    case 'paragraph':
    default:
      return renderParagraph(block.content);
  }
}

function renderCodeBlock(block) {
  const language = escapeHtml(block.language || 'text');
  return `
    <div class="code-block">
      <div class="code-block-header">
        <span>${language}</span>
      </div>
      <pre><code>${escapeHtml(block.content)}</code></pre>
    </div>
  `;
}

function renderTable(lines) {
  const headers = splitTableRow(lines[0]);
  const rows = lines.slice(2).map(splitTableRow);
  const head = headers.map((header) => `<th>${renderInline(header)}</th>`).join('');
  const body = rows
    .map((row) => `<tr>${row.map((cell) => `<td>${renderInline(cell)}</td>`).join('')}</tr>`)
    .join('');
  return `<div class="table-scroll"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

function renderList(block) {
  const tag = block.ordered ? 'ol' : 'ul';
  const items = block.items.map((item) => `<li>${renderInline(item)}</li>`).join('');
  return `<${tag}>${items}</${tag}>`;
}

function renderParagraph(content) {
  const headingMatch = content.match(/^\s*(#{1,6})\s*(.+)$/);
  if (headingMatch) {
    return renderHeading({
      type: 'heading',
      level: headingMatch[1].length,
      content: headingMatch[2]
    });
  }
  return `<p>${renderInline(content).replace(/\n/g, '<br>')}</p>`;
}

function renderHeading(block) {
  const level = Math.min(block.level + 1, 6);
  return `<h${level}>${renderInline(block.content)}</h${level}>`;
}

function renderInline(source) {
  let html = escapeHtml(source);
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/__([^_]+)__/g, '<strong>$1</strong>');
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  return html;
}

function startsSpecialBlock(lines, index) {
  const line = lines[index];
  return line.trimStart().startsWith('```')
    || isTableStart(lines, index)
    || isHeadingLine(line)
    || (/^\s*[-*]\s*/.test(line) && line.trim().length > 1)
    || /^\s*\d+\.\s+/.test(line)
    || /^\s*>/.test(line);
}

function isHeadingLine(line) {
  return /^\s*#{1,6}\s*\S+/.test(line);
}

function parseHeading(line) {
  const headingMatch = line.match(/^\s*(#{1,6})\s*(.+)$/);
  return {
    type: 'heading',
    level: headingMatch[1].length,
    content: headingMatch[2].trim()
  };
}

function parseFenceInfo(value) {
  if (!value) {
    return { language: 'text', inlineCode: '' };
  }
  const match = value.match(/^([A-Za-z][\w+#.-]*)(.*)$/);
  if (!match) {
    return { language: 'text', inlineCode: value };
  }
  return {
    language: match[1],
    inlineCode: match[2].trimStart()
  };
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

function escapeHtml(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
