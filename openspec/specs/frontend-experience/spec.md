# Frontend Experience Spec

## Technology

- The frontend MUST use Vue 3 and Vite.
- The frontend MUST keep API access centralized in `frontend/src/api/client.js`.
- The same frontend codebase MUST support both Java and Python backends through `VITE_API_BASE_URL` or Vite proxy configuration.

## Layout

- The application MUST show a left conversation list and a main chat panel.
- The sidebar MUST support create, switch, delete, and numbered conversations.
- The main chat panel MUST show numbered user and assistant messages.
- The composer MUST remain visible and usable during long conversations.
- The UI MUST remain visually stable with many conversations or long messages.

## Interaction

- The send button MUST send the current draft.
- `Enter` MUST send the current draft.
- `Shift+Enter` SHOULD insert a newline.
- Skill management MUST open a modal.
- The Skill modal MUST show Skill names, descriptions, loaded state, and selection state.
- The file upload area MUST be inside the Skill modal and support drag-and-drop.
- The model selector MUST allow users to select among enabled models returned by `/api/models`.

## Message Rendering

- Assistant message content MUST use the Markdown renderer in `frontend/src/utils/markdown.js`.
- The renderer MUST support paragraphs, headings, ordered lists, unordered lists, blockquotes, code fences, inline code, bold, emphasis, and Markdown tables.
- The renderer MUST normalize model output that omits spaces after heading markers.
- The renderer MUST split heading text from inline table starts.
- The renderer MUST NOT alter code fence content while normalizing prose.

## Download Behavior

- Markdown and Excel downloads MUST call backend export endpoints.
- The frontend SHOULD use same-origin proxy or direct `VITE_API_BASE_URL` consistently.
- Downloaded Markdown MUST contain the visible assistant content.
- Downloaded Excel MUST contain parsed Markdown tables when present.
- Agent evaluation reports MUST expose downloadable JSON and Markdown files after an evaluation run completes.

## Agent Evaluation

- The sidebar SHOULD provide an Agent evaluation entry under conversation tools.
- The evaluation panel SHOULD allow selecting dataset, optional model override, semantic evaluator mode, and pass threshold.
- Running an evaluation MUST call the backend evaluation API and display average score, pass count, error count, case-level status, and raw command output.
- Evaluation report downloads MUST use backend-generated report files rather than reconstructing content in the browser.

## Visual Quality

- The UI MUST be modern, calm, and work-focused.
- The UI MUST avoid overlapping text and controls.
- Text MUST fit within buttons, cards, sidebars, and message bubbles across common desktop and mobile widths.
- Cards MUST be used only where they frame discrete items, modals, or tools.
