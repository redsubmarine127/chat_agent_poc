const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: '请求失败' }));
    throw new Error(errorBody.message || '请求失败');
  }

  if (response.status === 204) {
    return null;
  }
  const responseText = await response.text();
  if (!responseText) {
    return null;
  }
  return JSON.parse(responseText);
}

export function listConversations() {
  return request('/api/conversations');
}

export function createConversation(title = '新的对话') {
  return request('/api/conversations', {
    method: 'POST',
    body: JSON.stringify({ title })
  });
}

export function deleteConversation(conversationId) {
  return request(`/api/conversations/${conversationId}`, {
    method: 'DELETE'
  });
}

export function clearConversationMessages(conversationId) {
  return request(`/api/conversations/${conversationId}/messages`, {
    method: 'DELETE'
  });
}

export function listMessages(conversationId) {
  return request(`/api/conversations/${conversationId}/messages`);
}

export function listSkills() {
  return request('/api/skills');
}

export function extractSkillFromConversation(conversationId, name = '') {
  return request('/api/skills/extractions', {
    method: 'POST',
    body: JSON.stringify({ conversationId, name })
  });
}

export function listModels() {
  return request('/api/models');
}

export function searchRagContexts(query, limit = 5) {
  const params = new URLSearchParams({ query, limit: String(limit) });
  return request(`/api/rag/search?${params.toString()}`);
}

export function listMcpTools() {
  return request('/api/mcp/tools');
}

export function invokeMcpTool(toolName, argumentsPayload = {}) {
  return request(`/api/mcp/tools/${encodeURIComponent(toolName)}/invoke`, {
    method: 'POST',
    body: JSON.stringify({ arguments: argumentsPayload })
  });
}

export function downloadMarkdownFile(content, filename) {
  submitDownloadForm('/api/exports/markdown', { content, filename });
}

export function downloadExcelFile(content, filename) {
  submitDownloadForm('/api/exports/excel', { content, filename });
}

export async function uploadFile(file) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`${API_BASE_URL}/api/files`, {
    method: 'POST',
    body: formData
  });
  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: '上传失败' }));
    throw new Error(errorBody.message || '上传失败');
  }
  return response.json();
}

export async function streamMessage(conversationId, payload, handlers, options = {}) {
  const response = await fetch(`${API_BASE_URL}/api/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(payload),
    signal: options.signal
  });

  if (!response.ok || !response.body) {
    const errorBody = await response.json().catch(() => ({ message: '对话请求失败' }));
    throw new Error(errorBody.message || '对话请求失败');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split('\n\n');
    buffer = chunks.pop() || '';
    for (const chunk of chunks) {
      await consumeSseChunk(chunk, handlers);
    }
  }

  if (buffer.trim()) {
    await consumeSseChunk(buffer, handlers);
  }
}

async function consumeSseChunk(chunk, handlers) {
  const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'));
  if (!dataLine) {
    return;
  }
  const event = JSON.parse(dataLine.slice(5).trim());
  if (event.type === 'delta') {
    await handlers.onDelta?.(event);
    return;
  }
  if (event.type === 'reasoning') {
    await handlers.onReasoning?.(event);
    return;
  }
  if (event.type === 'started') {
    await handlers.onStarted?.(event);
    return;
  }
  if (event.type === 'failed') {
    await handlers.onFailed?.(event);
    return;
  }
  if (event.type === 'completed') {
    await handlers.onCompleted?.(event);
  }
}

function submitDownloadForm(path, fields) {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = `${API_BASE_URL}${path}`;
  form.acceptCharset = 'UTF-8';
  form.style.display = 'none';

  Object.entries(fields).forEach(([name, value]) => {
    const input = document.createElement('textarea');
    input.name = name;
    input.value = value ?? '';
    form.appendChild(input);
  });

  document.body.appendChild(form);
  form.submit();
  window.setTimeout(() => {
    form.remove();
  }, 60_000);
}
