<template>
  <main class="shell">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="brand-mark">AI</div>
        <div class="brand-copy">
          <h1>智能对话</h1>
          <span>Workspace Assistant</span>
        </div>
        <button class="icon-button" title="新建对话" @click="handleCreateConversation">
          <Plus :size="18" />
        </button>
      </div>

      <button class="new-chat-button" @click="handleCreateConversation">
        <Plus :size="17" />
        新建对话
      </button>

      <div class="conversation-list">
        <div class="section-label">最近对话</div>
        <button
          v-for="(conversation, conversationIndex) in conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === activeConversationId }"
          @click="selectConversation(conversation.id)"
        >
          <span class="conversation-number">{{ formatSequenceNumber(conversationIndex + 1) }}</span>
          <span class="conversation-title">{{ conversation.title }}</span>
          <span class="conversation-time">{{ formatConversationTime(conversation.updatedAt) }}</span>
          <Trash2
            class="delete-icon"
            :size="16"
            @click.stop="handleDeleteConversation(conversation.id)"
          />
        </button>
        <div v-if="conversations.length === 0" class="sidebar-empty">
          暂无对话
        </div>
      </div>

      <div class="sidebar-tools">
        <div class="section-label sidebar-tool-label">会话工具</div>
        <button class="sidebar-tool-button skill-config-button" type="button" @click="openSkillSettings">
          <Sparkles :size="16" />
          <span>Skill 管理</span>
          <small>{{ loadedSkills.length ? `${loadedSkills.length} 个已加载` : '默认不加载' }}</small>
          <Settings2 :size="15" />
        </button>
        <button class="sidebar-tool-button scheduled-session-button" type="button" @click="openScheduledSessions">
          <Clock :size="16" />
          <span>定时任务</span>
          <small>{{ scheduledSessions.length }}/10</small>
          <Settings2 :size="15" />
        </button>
        <button class="sidebar-tool-button evaluation-tool-button" type="button" @click="openEvaluationPanel">
          <ClipboardCheck :size="16" />
          <span>Agent 评估</span>
          <small>{{ evaluationResult ? `${evaluationResult.averageScore} 分` : '未执行' }}</small>
          <Settings2 :size="15" />
        </button>
      </div>
    </aside>

    <section class="chat-panel">
      <header class="topbar">
        <div class="conversation-meta">
          <strong>{{ activeTitle }}</strong>
          <span>{{ activeSubtitle }}</span>
        </div>
        <div class="mobile-topbar-actions" aria-label="会话工具">
          <button type="button" aria-label="历史会话" title="历史会话" @click="openMobileHistory">
            <History :size="16" />
          </button>
          <button type="button" aria-label="新建对话" title="新建对话" @click="handleCreateConversation">
            <Plus :size="16" />
          </button>
          <button type="button" aria-label="Skill 管理" title="Skill 管理" @click="openSkillSettings">
            <Sparkles :size="16" />
          </button>
          <button type="button" aria-label="定时任务" title="定时任务" @click="openScheduledSessions">
            <Clock :size="16" />
          </button>
          <button type="button" aria-label="Agent 评估" title="Agent 评估" @click="openEvaluationPanel">
            <ClipboardCheck :size="16" />
          </button>
        </div>
      </header>

      <section ref="messageListRef" class="message-list">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-icon">
            <Sparkles :size="28" />
          </div>
          <h2>今天想推进什么？</h2>
          <p>{{ selectedModelName }} 已就绪</p>
          <div class="prompt-grid">
            <button v-for="prompt in promptSuggestions" :key="prompt" @click="usePrompt(prompt)">
              {{ prompt }}
            </button>
          </div>
        </div>
        <article
          v-for="(message, messageIndex) in messages"
          :key="message.id"
          class="message"
          :class="messageClass(message)"
        >
          <template v-if="isContextNotice(message)">
            <div class="context-divider">
              <span>{{ message.content }}</span>
            </div>
          </template>
          <template v-else>
            <div class="avatar">{{ message.role === 'USER' ? '你' : 'AI' }}</div>
            <div class="message-bubble">
              <div class="message-heading">
                <div class="message-identity">
                  <span class="message-sequence">第 {{ messageDisplayIndex(messageIndex) }} 条</span>
                  <span class="role">{{ message.role === 'USER' ? '你' : '智能助手' }}</span>
                </div>
                <span class="message-meta">{{ messageMeta(message) }}</span>
              </div>
              <div v-if="shouldShowReasoning(message)" class="reasoning-panel" :class="{ active: message.reasoningActive }">
                <button class="reasoning-toggle" @click="toggleReasoning(message)">
                  <BrainCircuit :size="15" />
                  <span>思考过程</span>
                  <small>{{ reasoningStatus(message) }}</small>
                  <ChevronDown :size="15" class="reasoning-chevron" :class="{ open: message.reasoningOpen }" />
                </button>
                <div
                  v-show="message.reasoningOpen"
                  class="reasoning-content"
                  v-html="renderReasoningContent(message.reasoningContent || '正在分析问题、整理上下文并规划回答结构...')"
                ></div>
              </div>
              <template v-if="message.content || isFailedMessage(message)">
                <div
                  class="markdown-body"
                  :class="{ streaming: message.streaming, failed: isFailedMessage(message) }"
                  v-html="renderMessageContent(messageDisplayContent(message))"
                ></div>
                <div v-if="message.role === 'ASSISTANT' && message.content" class="message-artifacts">
                  <div v-if="messageCharts(message.content).length" class="chart-panel">
                    <div class="artifact-heading">
                      <BarChart3 :size="16" />
                      <span>图表视图</span>
                    </div>
                    <div class="chart-grid">
                      <section v-for="chart in messageCharts(message.content)" :key="chart.id" class="chart-card">
                        <h4>{{ chart.title }}</h4>
                        <div class="bar-list">
                          <div v-for="point in chart.points" :key="`${chart.id}-${point.label}`" class="bar-row">
                            <span class="bar-label">{{ point.label }}</span>
                            <div class="bar-track">
                              <i :style="{ width: `${point.percent}%` }"></i>
                            </div>
                            <strong>{{ point.value }}</strong>
                          </div>
                        </div>
                      </section>
                    </div>
                  </div>

                  <div class="export-actions">
                    <button type="button" @click="downloadMessageMarkdown(message, $event)">
                      <Download :size="14" />
                      Markdown
                    </button>
                    <button
                      type="button"
                      :disabled="messageTables(message.content).length === 0"
                      @click="downloadMessageExcel(message)"
                    >
                      <FileSpreadsheet :size="14" />
                      Excel
                    </button>
                  </div>
                </div>
              </template>
              <div v-else class="typing-indicator" aria-label="生成中">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </template>
        </article>
      </section>

      <footer class="composer">
        <textarea
          v-model="draft"
          rows="3"
          placeholder="输入你的问题..."
          @keydown.enter.exact.prevent="handleComposerSubmit"
          @keydown.shift.enter.stop
          @keydown.meta.enter.prevent="handleComposerSubmit"
          @keydown.ctrl.enter.prevent="handleComposerSubmit"
        />

        <div class="composer-bottom">
          <div class="composer-actions">
            <label class="select-control model-control compact-model-control" aria-label="选择模型">
              <Cpu :size="14" />
              <select v-model="selectedModelId" aria-label="选择模型">
                <option value="" disabled>{{ models.length ? '选择模型' : '模型加载中' }}</option>
                <option v-for="model in models" :key="model.id" :value="model.id">
                  {{ model.name }}
                </option>
              </select>
            </label>
            <button
              class="extract-skill-button"
              type="button"
              aria-label="抽取为 Skill"
              :disabled="sending || !activeConversationId || conversationMessageCount === 0"
              @click="handleExtractSkill"
            >
              <Sparkles :size="14" />
            </button>
            <button
              class="clear-context-button"
              type="button"
              aria-label="清除上下文"
              :disabled="sending || !activeConversationId || conversationMessageCount === 0"
              @click="handleClearContext"
            >
              <Eraser :size="14" />
            </button>
          </div>

          <button
            class="send-button"
            :class="{ stopping: sending }"
            :disabled="!sending && (!draft.trim() || !activeConversationId)"
            :aria-label="sending ? '停止当前对话' : '发送'"
            @click="handleComposerSubmit"
          >
            <Square v-if="sending" :size="14" />
            <Send v-else :size="17" />
          </button>
        </div>
      </footer>
    </section>

    <Teleport to="body">
      <div v-if="mobileHistoryOpen" class="mobile-history-backdrop" @click.self="closeMobileHistory">
        <aside class="mobile-history-drawer" aria-label="历史会话">
          <header class="mobile-history-header">
            <div>
              <span>History</span>
              <strong>历史会话</strong>
            </div>
            <button type="button" aria-label="关闭历史会话" @click="closeMobileHistory">
              <X :size="18" />
            </button>
          </header>

          <button class="mobile-history-new" type="button" @click="handleCreateMobileConversation">
            <Plus :size="16" />
            新建对话
          </button>

          <div class="mobile-history-list">
            <div
              v-for="(conversation, conversationIndex) in conversations"
              :key="conversation.id"
              class="mobile-history-item"
              :class="{ active: conversation.id === activeConversationId }"
            >
              <button type="button" class="mobile-history-select" @click="selectMobileConversation(conversation.id)">
                <span class="conversation-number">{{ formatSequenceNumber(conversationIndex + 1) }}</span>
                <span class="mobile-history-main">
                  <strong>{{ conversation.title }}</strong>
                  <small>{{ formatConversationTime(conversation.updatedAt) }}</small>
                </span>
              </button>
              <button
                type="button"
                class="mobile-history-delete"
                aria-label="删除会话"
                @click="deleteMobileConversation(conversation.id)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
            <div v-if="conversations.length === 0" class="mobile-history-empty">
              暂无历史会话
            </div>
          </div>
        </aside>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="skillSettingsOpen" class="modal-backdrop" @click.self="closeSkillSettings">
        <section class="settings-modal" role="dialog" aria-modal="true" aria-labelledby="skill-modal-title">
          <header class="modal-header">
            <div>
              <span class="modal-kicker">Context setup</span>
              <h2 id="skill-modal-title">Skill 管理</h2>
            </div>
            <button class="modal-close-button" type="button" aria-label="关闭弹窗" @click="closeSkillSettings">
              <X :size="18" />
            </button>
          </header>

          <div class="settings-grid">
            <section class="settings-section">
              <div class="settings-section-heading">
                <div>
                  <h3>加载 Skill</h3>
                  <p>勾选后可作为当前对话上下文，点击卡片设为本次发送使用的 Skill。</p>
                </div>
                <span>{{ loadedSkills.length }}/{{ skills.length }}</span>
              </div>

              <div class="skill-card-list">
                <article
                  v-for="skill in skills"
                  :key="skill.id"
                  class="skill-card"
                  :class="{ active: selectedSkillId === skill.id && isSkillLoaded(skill.id), disabled: !isSkillLoaded(skill.id) }"
                  :aria-disabled="!isSkillLoaded(skill.id)"
                  :title="isSkillLoaded(skill.id) ? '设为本次发送使用的 Skill' : '请先打开右侧开关加载 Skill'"
                  @click="selectSkillFromModal(skill.id)"
                >
                  <div class="skill-card-icon">
                    <Sparkles :size="18" />
                  </div>
                  <div class="skill-card-copy">
                    <div class="skill-card-title">
                      <strong>{{ skill.name }}</strong>
                      <span v-if="selectedSkillId === skill.id && isSkillLoaded(skill.id)">当前使用</span>
                    </div>
                    <p>{{ skill.description }}</p>
                  </div>
                  <label class="skill-switch" :aria-label="`${skill.name} 是否加载`" @click.stop>
                    <input
                      type="checkbox"
                      :checked="isSkillLoaded(skill.id)"
                      @change="toggleSkillLoad(skill.id, $event.target.checked)"
                    />
                    <span></span>
                  </label>
                </article>
              </div>
            </section>

            <section class="settings-section">
              <div class="settings-section-heading">
                <div>
                  <h3>上传附件</h3>
                  <p>支持拖拽文件到此处，也可以点击选择文件。</p>
                </div>
                <span>{{ attachments.length }} 个</span>
              </div>

              <label
                class="dropzone"
                :class="{ dragging: dragOver }"
                @dragenter.prevent="dragOver = true"
                @dragover.prevent="dragOver = true"
                @dragleave.prevent="dragOver = false"
                @drop.prevent="handleFileDrop"
              >
                <UploadCloud :size="30" />
                <strong>{{ uploading ? '上传中...' : '拖拽文件到这里' }}</strong>
                <span>{{ uploadHint }}</span>
                <input ref="fileInputRef" multiple type="file" @change="handleFileSelection" />
              </label>

              <div class="attachment-list">
                <div v-if="attachments.length === 0" class="attachment-empty">
                  暂无附件
                </div>
                <div v-for="attachment in attachments" :key="attachment.id" class="attachment-item">
                  <FileText :size="16" />
                  <div>
                    <strong>{{ attachment.originalFilename }}</strong>
                    <span>{{ formatFileSize(attachment.sizeInBytes) }} · {{ attachment.contentType }}</span>
                  </div>
                  <button type="button" aria-label="移除附件" @click="removeAttachment(attachment.id)">
                    <X :size="15" />
                  </button>
                </div>
              </div>
            </section>
          </div>

          <footer class="modal-footer">
            <span>{{ loadedSkills.length ? selectedSkillName : '未加载 Skill' }} · {{ attachments.length }} 个附件待发送</span>
            <button type="button" @click="closeSkillSettings">
              完成
            </button>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="scheduledSessionsOpen" class="modal-backdrop" @click.self="closeScheduledSessions">
        <section class="schedule-modal" role="dialog" aria-modal="true" aria-labelledby="schedule-modal-title">
          <header class="modal-header">
            <div>
              <span class="modal-kicker">Automation</span>
              <h2 id="schedule-modal-title">定时会话</h2>
            </div>
            <button class="modal-close-button" type="button" aria-label="关闭弹窗" @click="closeScheduledSessions">
              <X :size="18" />
            </button>
          </header>

          <div class="schedule-grid">
            <section class="schedule-column">
              <div class="settings-section-heading">
                <div>
                  <h3>会话内容</h3>
                  <p>最多 10 条，页面保持打开时按频率自动执行。</p>
                </div>
                <span>{{ scheduledSessions.length }}/10</span>
              </div>

              <form class="schedule-form" @submit.prevent="saveScheduledSession">
                <label>
                  <span>名称</span>
                  <input v-model.trim="scheduledSessionDraft.title" maxlength="80" placeholder="例如：日报生成" />
                </label>
                <label>
                  <span>内容</span>
                  <textarea
                    v-model="scheduledSessionDraft.content"
                    class="schedule-textarea"
                    rows="6"
                    maxlength="8000"
                    placeholder="输入定时触发的会话内容..."
                  ></textarea>
                </label>
                <div class="schedule-form-row">
                  <label>
                    <span>频率</span>
                    <select v-model.number="scheduledSessionDraft.frequencyMinutes">
                      <option v-for="option in scheduleFrequencyOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                  <label>
                    <span>模型</span>
                    <select v-model="scheduledSessionDraft.modelId">
                      <option value="" disabled>{{ models.length ? '选择模型' : '模型加载中' }}</option>
                      <option v-for="model in models" :key="model.id" :value="model.id">
                        {{ model.name }}
                      </option>
                    </select>
                  </label>
                </div>
                <label class="schedule-enable-row">
                  <input v-model="scheduledSessionDraft.enabled" type="checkbox" />
                  <span>启用定时触发</span>
                </label>
                <div class="schedule-form-actions">
                  <button type="submit">
                    <Save :size="15" />
                    {{ scheduledSessionDraft.id ? '保存修改' : '新增会话' }}
                  </button>
                  <button type="button" class="secondary-action" @click="resetScheduledSessionDraft">
                    重置
                  </button>
                </div>
                <p v-if="scheduledSessionMessage" class="schedule-message">{{ scheduledSessionMessage }}</p>
              </form>

              <div class="schedule-list">
                <article
                  v-for="session in scheduledSessions"
                  :key="session.id"
                  class="schedule-card"
                  :class="{ disabled: !session.enabled, running: isScheduledSessionRunning(session.id) }"
                >
                  <div class="schedule-card-main">
                    <div class="schedule-card-title">
                      <strong>{{ session.title }}</strong>
                      <span>{{ session.enabled ? '运行中' : '已暂停' }}</span>
                    </div>
                    <p>{{ session.content }}</p>
                    <div class="schedule-card-meta">
                      <span>{{ frequencyLabel(session.frequencyMinutes) }}</span>
                      <span>下次 {{ formatScheduleTime(session.nextRunAt) }}</span>
                      <span>{{ scheduledSessionHistoryCount(session.id) }} 条历史</span>
                      <span v-if="latestScheduledHistory(session.id)">
                        最近 {{ latestScheduledHistory(session.id).status === 'COMPLETED' ? '完成' : '失败' }}
                      </span>
                    </div>
                  </div>
                  <div class="schedule-card-actions">
                    <button type="button" title="立即执行" @click="runScheduledSession(session, true)">
                      <Play :size="14" />
                    </button>
                    <button type="button" :title="session.enabled ? '暂停' : '启用'" @click="toggleScheduledSession(session.id)">
                      <Pause :size="14" />
                    </button>
                    <button type="button" title="编辑" @click="editScheduledSession(session)">
                      <Settings2 :size="14" />
                    </button>
                    <button
                      type="button"
                      title="执行历史"
                      :disabled="scheduledSessionHistoryCount(session.id) === 0"
                      @click="openScheduledHistory(session.id)"
                    >
                      <FileText :size="14" />
                    </button>
                    <button type="button" title="删除" @click="deleteScheduledSession(session.id)">
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </article>
                <div v-if="scheduledSessions.length === 0" class="schedule-empty">
                  暂无定时会话
                </div>
              </div>
            </section>

            <section class="schedule-column schedule-history-summary">
              <div class="settings-section-heading">
                <div>
                  <h3>执行历史</h3>
                  <p>历史已收纳到对应定时任务中，点击任务卡片的历史按钮查看。</p>
                </div>
                <span>{{ scheduledSessionHistories.length }} 条</span>
              </div>
              <div class="schedule-history-summary-panel">
                <FileText :size="24" />
                <strong>按任务归档</strong>
                <p>每条历史会保留执行内容、模型、状态、结果，并支持单独下载 Markdown 或 Excel。</p>
              </div>
            </section>
          </div>

          <footer class="modal-footer">
            <span>{{ scheduledSessionRuntimeText }}</span>
            <button type="button" @click="closeScheduledSessions">
              完成
            </button>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="scheduledHistoryOpen" class="schedule-history-backdrop" @click.self="closeScheduledHistory">
        <section class="schedule-history-modal" role="dialog" aria-modal="true" aria-labelledby="schedule-history-title">
          <header class="modal-header">
            <div>
              <span class="modal-kicker">Execution history</span>
              <h2 id="schedule-history-title">{{ selectedScheduledSession?.title || '执行历史' }}</h2>
            </div>
            <button class="modal-close-button" type="button" aria-label="关闭执行历史" @click="closeScheduledHistory">
              <X :size="18" />
            </button>
          </header>

          <div class="schedule-history-list scoped">
            <article v-for="history in selectedScheduledHistories" :key="history.id" class="schedule-history-item">
              <header>
                <div>
                  <strong>{{ history.status === 'COMPLETED' ? '执行完成' : '执行失败' }}</strong>
                  <span>{{ formatScheduleTime(history.startedAt) }} · {{ models.find((item) => item.id === history.modelId)?.name || history.modelId || '默认模型' }}</span>
                </div>
                <div class="history-actions">
                  <button type="button" title="下载 Markdown" @click="downloadScheduledHistoryMarkdown(history)">
                    <Download :size="14" />
                  </button>
                  <button
                    type="button"
                    title="下载 Excel"
                    :disabled="messageTables(history.result || '').length === 0"
                    @click="downloadScheduledHistoryExcel(history)"
                  >
                    <FileSpreadsheet :size="14" />
                  </button>
                </div>
              </header>
              <div class="schedule-history-prompt">
                {{ history.content }}
              </div>
              <div
                class="schedule-history-result markdown-body"
                :class="{ failed: history.status === 'FAILED' }"
                v-html="renderMessageContent(history.result || '暂无执行结果')"
              ></div>
            </article>
            <div v-if="selectedScheduledHistories.length === 0" class="schedule-empty">
              当前任务暂无执行历史
            </div>
          </div>

          <footer class="modal-footer">
            <span>{{ selectedScheduledHistories.length }} 条历史记录</span>
            <button type="button" @click="closeScheduledHistory">
              完成
            </button>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="evaluationPanelOpen" class="modal-backdrop" @click.self="closeEvaluationPanel">
        <section class="evaluation-modal" role="dialog" aria-modal="true" aria-labelledby="evaluation-modal-title">
          <header class="modal-header">
            <div>
              <span class="modal-kicker">Agent evaluation</span>
              <h2 id="evaluation-modal-title">Agent 评估</h2>
            </div>
            <button class="modal-close-button" type="button" aria-label="关闭评估面板" @click="closeEvaluationPanel">
              <X :size="18" />
            </button>
          </header>

          <div class="evaluation-layout">
            <section class="evaluation-config">
              <div class="settings-section-heading">
                <div>
                  <h3>执行方案</h3>
                  <p>调用后端真实 SSE 接口，自动生成 JSON 与 Markdown 报告。</p>
                </div>
                <span>{{ evaluationRunning ? '执行中' : '就绪' }}</span>
              </div>

              <label>
                <span>数据集</span>
                <select v-model="evaluationForm.dataset" :disabled="evaluationRunning">
                  <option value="smoke_langgraph">冒烟评估</option>
                  <option value="assistant_quality">质量评估</option>
                </select>
              </label>
              <label>
                <span>模型</span>
                <select v-model="evaluationForm.modelId" :disabled="evaluationRunning">
                  <option value="">使用数据集默认模型</option>
                  <option v-for="model in models" :key="model.id" :value="model.id">
                    {{ model.name }}
                  </option>
                </select>
              </label>
              <label>
                <span>语义评分</span>
                <select v-model="evaluationForm.semanticEvaluator" :disabled="evaluationRunning">
                  <option value="none">关闭 DeepEval</option>
                  <option value="auto">按用例自动启用</option>
                  <option value="deepeval">强制 DeepEval</option>
                </select>
              </label>
              <label>
                <span>通过阈值</span>
                <input v-model.number="evaluationForm.failUnder" type="number" min="0" max="100" step="1" :disabled="evaluationRunning" />
              </label>

              <button class="evaluation-run-button" type="button" :disabled="evaluationRunning" @click="handleRunEvaluation">
                <LoaderCircle v-if="evaluationRunning" :size="16" class="spin-icon" />
                <ClipboardCheck v-else :size="16" />
                {{ evaluationRunning ? '评估执行中' : '开始评估' }}
              </button>
              <p v-if="evaluationError" class="evaluation-error">{{ evaluationError }}</p>
            </section>

            <section class="evaluation-result-panel">
              <div class="settings-section-heading">
                <div>
                  <h3>测试结果</h3>
                  <p>{{ evaluationResult ? '已生成评估报告，可下载查看完整明细。' : '执行后将在这里展示评分、用例状态和报告入口。' }}</p>
                </div>
                <span>{{ evaluationResult?.status || '暂无' }}</span>
              </div>

              <div v-if="evaluationResult" class="evaluation-score-grid">
                <article>
                  <span>平均分</span>
                  <strong>{{ evaluationResult.averageScore }}</strong>
                </article>
                <article>
                  <span>用例数</span>
                  <strong>{{ evaluationResult.caseCount }}</strong>
                </article>
                <article>
                  <span>通过</span>
                  <strong>{{ evaluationResult.passCount }}</strong>
                </article>
                <article>
                  <span>错误</span>
                  <strong>{{ evaluationResult.errorCount }}</strong>
                </article>
              </div>

              <div v-if="evaluationResult" class="evaluation-case-list">
                <article v-for="item in evaluationCases" :key="item.caseId" class="evaluation-case-card">
                  <div>
                    <strong>{{ item.name || item.caseId }}</strong>
                    <span>{{ item.caseId }} · {{ item.status }}</span>
                  </div>
                  <b>{{ item.score?.total ?? 0 }}</b>
                </article>
              </div>

              <div v-if="evaluationResult" class="evaluation-downloads">
                <a
                  v-for="file in evaluationResult.files"
                  :key="file.filename"
                  :href="evaluationDownloadUrl(file.downloadUrl)"
                  target="_blank"
                  rel="noreferrer"
                >
                  <Download :size="14" />
                  下载 {{ file.type === 'json' ? 'JSON' : 'Markdown' }}
                </a>
              </div>

              <pre v-if="evaluationResult?.output" class="evaluation-output">{{ evaluationResult.output }}</pre>
              <div v-if="!evaluationResult && !evaluationRunning" class="evaluation-empty">
                <ClipboardCheck :size="28" />
                <strong>尚未执行评估</strong>
                <p>建议先运行冒烟评估，确认流式协议、导出和后端兼容性。</p>
              </div>
            </section>
          </div>

          <footer class="modal-footer">
            <span>{{ evaluationFooterText }}</span>
            <button type="button" @click="closeEvaluationPanel">完成</button>
          </footer>
        </section>
      </div>
    </Teleport>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import {
  BrainCircuit,
  BarChart3,
  ChevronDown,
  ClipboardCheck,
  Clock,
  Cpu,
  Download,
  Eraser,
  FileSpreadsheet,
  FileText,
  History,
  LoaderCircle,
  Pause,
  Play,
  Plus,
  Save,
  Send,
  Settings2,
  Sparkles,
  Square,
  Trash2,
  UploadCloud,
  X
} from 'lucide-vue-next';
import {
  createConversation,
  clearConversationMessages,
  deleteConversation,
  downloadExcelFile,
  downloadMarkdownFile,
  evaluationReportDownloadUrl,
  extractSkillFromConversation,
  listConversations,
  listModels,
  listMessages,
  listSkills,
  runAgentEvaluation,
  streamMessage,
  uploadFile
} from './api/client';
import { renderMarkdown } from './utils/markdown';
import {
  buildTableCharts,
  extractMarkdownTables,
  sanitizeFilename
} from './utils/messageArtifacts';
import {
  normalizeSkillSelection,
  selectLoadedSkill,
  toggleSkillLoad as toggleSkillLoadState
} from './utils/skillSelection';

const conversations = ref([]);
const messages = ref([]);
const skills = ref([]);
const models = ref([]);
const attachments = ref([]);
const loadedSkillIds = ref([]);
const activeConversationId = ref('');
const selectedSkillId = ref('');
const selectedModelId = ref('');
const draft = ref('');
const sending = ref(false);
const uploading = ref(false);
const dragOver = ref(false);
const skillSettingsOpen = ref(false);
const statusText = ref('就绪');
const messageListRef = ref(null);
const fileInputRef = ref(null);
const activeTextWriters = new Set();
const activeStreamAbortController = ref(null);
const activeStreamingAssistantMessage = ref(null);
const mobileHistoryOpen = ref(false);
const scheduledSessionsOpen = ref(false);
const scheduledSessions = ref([]);
const scheduledSessionHistories = ref([]);
const selectedScheduledHistorySessionId = ref('');
const scheduledRunningIds = ref([]);
const scheduledSessionMessage = ref('');
const evaluationPanelOpen = ref(false);
const evaluationRunning = ref(false);
const evaluationResult = ref(null);
const evaluationError = ref('');
let scheduleTimerId = 0;
const promptSuggestions = [
  '帮我设计一个高并发接口方案',
  '审查这段代码的风险点',
  '把需求拆成后端任务清单'
];
const SCHEDULED_SESSIONS_STORAGE_KEY = 'assistant-scheduled-sessions';
const SCHEDULED_SESSION_HISTORIES_STORAGE_KEY = 'assistant-scheduled-session-histories';
const scheduleFrequencyOptions = [
  { label: '每 1 分钟', value: 1 },
  { label: '每 5 分钟', value: 5 },
  { label: '每 15 分钟', value: 15 },
  { label: '每 30 分钟', value: 30 },
  { label: '每 1 小时', value: 60 },
  { label: '每 6 小时', value: 360 },
  { label: '每天', value: 1440 }
];
const scheduledSessionDraft = reactive({
  id: '',
  title: '',
  content: '',
  frequencyMinutes: 15,
  modelId: '',
  enabled: true
});
const evaluationForm = reactive({
  dataset: 'smoke_langgraph',
  modelId: '',
  semanticEvaluator: 'none',
  failUnder: 80
});

const activeTitle = computed(() => {
  return conversations.value.find((item) => item.id === activeConversationId.value)?.title || '未选择对话';
});

const activeSubtitle = computed(() => {
  const model = models.value.find((item) => item.id === selectedModelId.value);
  const skillText = loadedSkills.value.length ? `${loadedSkills.value.length} 个 Skill 已加载` : '未加载 Skill';
  return [skillText, model?.name, `${conversationMessageCount.value} 条消息`]
    .filter(Boolean)
    .join(' · ');
});

const conversationMessageCount = computed(() => {
  return messages.value.filter((message) => !isContextNotice(message)).length;
});

const selectedModelName = computed(() => {
  return models.value.find((item) => item.id === selectedModelId.value)?.name || '模型';
});

const loadedSkills = computed(() => {
  return skills.value.filter((skill) => loadedSkillIds.value.includes(skill.id));
});

const selectedSkillName = computed(() => {
  if (!isSkillLoaded(selectedSkillId.value)) {
    return '选择 Skill';
  }
  return skills.value.find((item) => item.id === selectedSkillId.value)?.name || '选择 Skill';
});

const uploadHint = computed(() => {
  if (uploading.value) {
    return '正在保存文件，请稍候';
  }
  return '支持 PDF、图片、文本与 Markdown，最多携带 8 个附件';
});

const selectedScheduledSession = computed(() => {
  return scheduledSessions.value.find((session) => session.id === selectedScheduledHistorySessionId.value) || null;
});

const selectedScheduledHistories = computed(() => {
  if (!selectedScheduledHistorySessionId.value) {
    return [];
  }
  return scheduledSessionHistories.value
    .filter((history) => history.sessionId === selectedScheduledHistorySessionId.value)
    .slice()
    .sort((first, second) => new Date(second.startedAt).getTime() - new Date(first.startedAt).getTime());
});

const scheduledHistoryOpen = computed(() => {
  return Boolean(selectedScheduledHistorySessionId.value);
});

const scheduledSessionRuntimeText = computed(() => {
  if (scheduledRunningIds.value.length > 0) {
    return `${scheduledRunningIds.value.length} 个定时会话执行中`;
  }
  return scheduledSessions.value.length ? '定时会话已就绪' : '尚未配置定时会话';
});

const evaluationCases = computed(() => {
  return evaluationResult.value?.report?.results || [];
});

const evaluationFooterText = computed(() => {
  if (evaluationRunning.value) {
    return '正在执行评估，请保持页面打开';
  }
  if (!evaluationResult.value) {
    return '评估结果会保存为可下载报告';
  }
  return `${evaluationResult.value.dataset} · ${evaluationResult.value.averageScore} 分 · ${evaluationResult.value.status}`;
});

watch(messages, scrollToLatestMessage, { deep: true });

onMounted(async () => {
  loadScheduledSessionState();
  await Promise.all([loadSkills(), loadModels(), loadConversations()]);
  if (!scheduledSessionDraft.modelId) {
    scheduledSessionDraft.modelId = selectedModelId.value;
  }
  if (conversations.value.length === 0) {
    await handleCreateConversation();
  } else {
    await selectConversation(conversations.value[0].id);
  }
  startScheduledSessionTimer();
});

onBeforeUnmount(() => {
  stopCurrentConversation();
  activeTextWriters.forEach((writer) => writer.cancel());
  activeTextWriters.clear();
  stopScheduledSessionTimer();
});

function loadScheduledSessionState() {
  scheduledSessions.value = readStoredArray(SCHEDULED_SESSIONS_STORAGE_KEY)
    .map(normalizeScheduledSession)
    .filter(Boolean)
    .slice(0, 10);
  scheduledSessionHistories.value = readStoredArray(SCHEDULED_SESSION_HISTORIES_STORAGE_KEY)
    .map(normalizeScheduledHistory)
    .filter(Boolean)
    .slice(0, 200);
}

function readStoredArray(key) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(value) ? value : [];
  } catch (error) {
    console.warn('read local storage failed', key, error);
    return [];
  }
}

function normalizeScheduledSession(session) {
  if (!session || !String(session.content || '').trim()) {
    return null;
  }
  const frequencyMinutes = Math.max(1, Number(session.frequencyMinutes) || 15);
  return {
    id: session.id || crypto.randomUUID(),
    title: String(session.title || '定时会话').trim() || '定时会话',
    content: String(session.content || '').trim(),
    frequencyMinutes,
    modelId: session.modelId || '',
    enabled: session.enabled !== false,
    nextRunAt: session.nextRunAt || nextScheduleTime(frequencyMinutes),
    lastRunAt: session.lastRunAt || '',
    createdAt: session.createdAt || new Date().toISOString(),
    updatedAt: session.updatedAt || new Date().toISOString()
  };
}

function normalizeScheduledHistory(history) {
  if (!history || !history.id) {
    return null;
  }
  return {
    id: history.id,
    sessionId: history.sessionId || '',
    title: history.title || '定时会话',
    content: history.content || '',
    result: history.result || '',
    reasoning: history.reasoning || '',
    status: history.status === 'FAILED' ? 'FAILED' : 'COMPLETED',
    modelId: history.modelId || '',
    conversationId: history.conversationId || '',
    startedAt: history.startedAt || new Date().toISOString(),
    completedAt: history.completedAt || history.startedAt || new Date().toISOString()
  };
}

function persistScheduledSessions() {
  localStorage.setItem(SCHEDULED_SESSIONS_STORAGE_KEY, JSON.stringify(scheduledSessions.value));
}

function persistScheduledHistories() {
  localStorage.setItem(SCHEDULED_SESSION_HISTORIES_STORAGE_KEY, JSON.stringify(scheduledSessionHistories.value.slice(0, 200)));
}

function startScheduledSessionTimer() {
  stopScheduledSessionTimer();
  scheduleTimerId = window.setInterval(checkDueScheduledSessions, 15_000);
  window.setTimeout(checkDueScheduledSessions, 1000);
}

function stopScheduledSessionTimer() {
  if (!scheduleTimerId) {
    return;
  }
  window.clearInterval(scheduleTimerId);
  scheduleTimerId = 0;
}

function checkDueScheduledSessions() {
  const now = Date.now();
  scheduledSessions.value
    .filter((session) => session.enabled)
    .filter((session) => !isScheduledSessionRunning(session.id))
    .filter((session) => new Date(session.nextRunAt).getTime() <= now)
    .forEach((session) => runScheduledSession(session, false));
}

function usePrompt(prompt) {
  draft.value = prompt;
}

async function loadSkills() {
  skills.value = await listSkills();
  applySkillSelectionState(normalizeSkillSelection(skills.value, [], ''));
}

async function loadModels() {
  models.value = await listModels();
  selectedModelId.value = models.value[0]?.id || '';
}

async function loadConversations() {
  conversations.value = await listConversations();
}

async function handleCreateConversation() {
  const conversation = await createConversation('新的对话');
  conversations.value = [conversation, ...conversations.value];
  await selectConversation(conversation.id);
}

async function handleCreateMobileConversation() {
  await handleCreateConversation();
  closeMobileHistory();
}

async function selectConversation(conversationId) {
  activeConversationId.value = conversationId;
  messages.value = await listMessages(conversationId);
  statusText.value = '就绪';
  await scrollToLatestMessage();
}

async function selectMobileConversation(conversationId) {
  await selectConversation(conversationId);
  closeMobileHistory();
}

async function handleDeleteConversation(conversationId) {
  await deleteConversation(conversationId);
  conversations.value = conversations.value.filter((item) => item.id !== conversationId);
  if (activeConversationId.value === conversationId) {
    messages.value = [];
    activeConversationId.value = conversations.value[0]?.id || '';
    if (activeConversationId.value) {
      await selectConversation(activeConversationId.value);
    }
  }
}

async function deleteMobileConversation(conversationId) {
  await handleDeleteConversation(conversationId);
  if (conversations.value.length === 0) {
    closeMobileHistory();
  }
}

function openMobileHistory() {
  mobileHistoryOpen.value = true;
}

function closeMobileHistory() {
  mobileHistoryOpen.value = false;
}

async function handleClearContext() {
  if (sending.value || !activeConversationId.value || conversationMessageCount.value === 0) {
    return;
  }
  statusText.value = '清理中';
  try {
    await clearConversationMessages(activeConversationId.value);
    messages.value = [createContextNotice()];
    attachments.value = [];
    statusText.value = '上下文已清除';
  } catch (error) {
    statusText.value = error.message;
  }
}

async function handleExtractSkill() {
  if (sending.value || !activeConversationId.value || conversationMessageCount.value === 0) {
    return;
  }
  statusText.value = '抽取 Skill 中';
  try {
    const skill = await extractSkillFromConversation(activeConversationId.value);
    skills.value = [skill, ...skills.value.filter((item) => item.id !== skill.id)];
    loadedSkillIds.value = [...new Set([...loadedSkillIds.value, skill.id])];
    selectedSkillId.value = skill.id;
    skillSettingsOpen.value = true;
    statusText.value = 'Skill 已保存';
  } catch (error) {
    statusText.value = error.message;
  }
}

function createContextNotice() {
  return {
    id: crypto.randomUUID(),
    conversationId: activeConversationId.value,
    role: 'CONTEXT_NOTICE',
    content: '已清除当前上下文，后续对话将从这里重新开始。',
    status: 'COMPLETED',
    createdAt: new Date().toISOString()
  };
}

function openSkillSettings() {
  skillSettingsOpen.value = true;
}

function closeSkillSettings() {
  skillSettingsOpen.value = false;
  dragOver.value = false;
}

function openScheduledSessions() {
  scheduledSessionsOpen.value = true;
  if (!scheduledSessionDraft.modelId) {
    scheduledSessionDraft.modelId = selectedModelId.value;
  }
}

function closeScheduledSessions() {
  scheduledSessionsOpen.value = false;
  scheduledSessionMessage.value = '';
  closeScheduledHistory();
}

function openScheduledHistory(sessionId) {
  selectedScheduledHistorySessionId.value = sessionId;
}

function closeScheduledHistory() {
  selectedScheduledHistorySessionId.value = '';
}

function openEvaluationPanel() {
  evaluationForm.modelId = '';
  evaluationForm.semanticEvaluator = 'none';
  evaluationPanelOpen.value = true;
}

function closeEvaluationPanel() {
  evaluationPanelOpen.value = false;
  evaluationError.value = '';
}

async function handleRunEvaluation() {
  if (evaluationRunning.value) {
    return;
  }
  evaluationRunning.value = true;
  evaluationError.value = '';
  statusText.value = '评估执行中';
  try {
    evaluationResult.value = await runAgentEvaluation({
      dataset: evaluationForm.dataset,
      modelId: evaluationForm.modelId,
      semanticEvaluator: evaluationForm.semanticEvaluator,
      failUnder: Number(evaluationForm.failUnder) || 0
    });
    statusText.value = evaluationResult.value.status === 'PASS' ? '评估通过' : '评估需复核';
  } catch (error) {
    evaluationError.value = error.message || '评估执行失败';
    statusText.value = '评估失败';
  } finally {
    evaluationRunning.value = false;
  }
}

function evaluationDownloadUrl(downloadUrl) {
  return evaluationReportDownloadUrl(downloadUrl);
}

function scheduledSessionHistoryCount(sessionId) {
  return scheduledSessionHistories.value.filter((history) => history.sessionId === sessionId).length;
}

function latestScheduledHistory(sessionId) {
  return scheduledSessionHistories.value
    .filter((history) => history.sessionId === sessionId)
    .slice()
    .sort((first, second) => new Date(second.startedAt).getTime() - new Date(first.startedAt).getTime())[0] || null;
}

function resetScheduledSessionDraft() {
  scheduledSessionDraft.id = '';
  scheduledSessionDraft.title = '';
  scheduledSessionDraft.content = '';
  scheduledSessionDraft.frequencyMinutes = 15;
  scheduledSessionDraft.modelId = selectedModelId.value;
  scheduledSessionDraft.enabled = true;
  scheduledSessionMessage.value = '';
}

function saveScheduledSession() {
  const content = scheduledSessionDraft.content.trim();
  const title = scheduledSessionDraft.title.trim() || content.slice(0, 24) || '定时会话';
  if (!content) {
    scheduledSessionMessage.value = '请填写会话内容';
    return;
  }
  if (!scheduledSessionDraft.id && scheduledSessions.value.length >= 10) {
    scheduledSessionMessage.value = '最多只能设置 10 个定时会话';
    return;
  }
  const now = new Date().toISOString();
  const frequencyMinutes = Math.max(1, Number(scheduledSessionDraft.frequencyMinutes) || 15);
  if (scheduledSessionDraft.id) {
    scheduledSessions.value = scheduledSessions.value.map((session) => {
      if (session.id !== scheduledSessionDraft.id) {
        return session;
      }
      return {
        ...session,
        title,
        content,
        frequencyMinutes,
        modelId: scheduledSessionDraft.modelId || selectedModelId.value,
        enabled: scheduledSessionDraft.enabled,
        nextRunAt: scheduledSessionDraft.enabled ? nextScheduleTime(frequencyMinutes) : session.nextRunAt,
        updatedAt: now
      };
    });
    scheduledSessionMessage.value = '定时会话已更新';
  } else {
    scheduledSessions.value = [
      {
        id: crypto.randomUUID(),
        title,
        content,
        frequencyMinutes,
        modelId: scheduledSessionDraft.modelId || selectedModelId.value,
        enabled: scheduledSessionDraft.enabled,
        nextRunAt: nextScheduleTime(frequencyMinutes),
        lastRunAt: '',
        createdAt: now,
        updatedAt: now
      },
      ...scheduledSessions.value
    ];
    scheduledSessionMessage.value = '定时会话已新增';
  }
  persistScheduledSessions();
  resetScheduledSessionDraft();
}

function editScheduledSession(session) {
  scheduledSessionDraft.id = session.id;
  scheduledSessionDraft.title = session.title;
  scheduledSessionDraft.content = session.content;
  scheduledSessionDraft.frequencyMinutes = session.frequencyMinutes;
  scheduledSessionDraft.modelId = session.modelId || selectedModelId.value;
  scheduledSessionDraft.enabled = session.enabled;
  scheduledSessionMessage.value = '正在编辑定时会话';
}

function deleteScheduledSession(sessionId) {
  scheduledSessions.value = scheduledSessions.value.filter((session) => session.id !== sessionId);
  scheduledSessionHistories.value = scheduledSessionHistories.value.filter((history) => history.sessionId !== sessionId);
  if (scheduledSessionDraft.id === sessionId) {
    resetScheduledSessionDraft();
  }
  if (selectedScheduledHistorySessionId.value === sessionId) {
    closeScheduledHistory();
  }
  persistScheduledSessions();
  persistScheduledHistories();
}

function toggleScheduledSession(sessionId) {
  scheduledSessions.value = scheduledSessions.value.map((session) => {
    if (session.id !== sessionId) {
      return session;
    }
    const enabled = !session.enabled;
    return {
      ...session,
      enabled,
      nextRunAt: enabled ? nextScheduleTime(session.frequencyMinutes) : session.nextRunAt,
      updatedAt: new Date().toISOString()
    };
  });
  persistScheduledSessions();
}

function isSkillLoaded(skillId) {
  return loadedSkillIds.value.includes(skillId);
}

function toggleSkillLoad(skillId, checked) {
  applySkillSelectionState(toggleSkillLoadState(
    skills.value,
    loadedSkillIds.value,
    selectedSkillId.value,
    skillId,
    checked
  ));
}

function selectSkillFromModal(skillId) {
  applySkillSelectionState(selectLoadedSkill(
    skills.value,
    loadedSkillIds.value,
    selectedSkillId.value,
    skillId
  ));
}

function applySkillSelectionState(state) {
  loadedSkillIds.value = state.loadedSkillIds;
  selectedSkillId.value = state.selectedSkillId;
}

async function handleFileSelection(event) {
  await uploadSelectedFiles(Array.from(event.target.files || []));
  event.target.value = '';
}

async function handleFileDrop(event) {
  dragOver.value = false;
  await uploadSelectedFiles(Array.from(event.dataTransfer?.files || []));
}

async function uploadSelectedFiles(files) {
  if (files.length === 0) {
    return;
  }
  const remainingSlots = Math.max(8 - attachments.value.length, 0);
  if (remainingSlots === 0) {
    statusText.value = '附件已达到 8 个上限';
    return;
  }
  const filesToUpload = files.slice(0, remainingSlots);
  statusText.value = '上传中';
  uploading.value = true;
  try {
    for (const file of filesToUpload) {
      const attachment = await uploadFile(file);
      attachments.value.push(attachment);
    }
    statusText.value = '上传完成';
  } catch (error) {
    statusText.value = error.message;
  } finally {
    uploading.value = false;
  }
}

function removeAttachment(attachmentId) {
  attachments.value = attachments.value.filter((attachment) => attachment.id !== attachmentId);
}

function formatFileSize(value) {
  if (!Number.isFinite(value)) {
    return '未知大小';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

async function runScheduledSession(session, manualRun) {
  if (!session || isScheduledSessionRunning(session.id)) {
    return;
  }
  setScheduledSessionRunning(session.id, true);
  const startedAt = new Date().toISOString();
  updateScheduledSessionAfterTrigger(session.id);
  let conversation = null;
  let result = '';
  let reasoning = '';
  let status = 'COMPLETED';
  try {
    conversation = await createConversation(`定时会话：${session.title}`);
    conversations.value = [conversation, ...conversations.value];
    await streamMessage(
      conversation.id,
      {
        content: session.content,
        skillId: resolveActiveSkillId(),
        modelId: session.modelId || selectedModelId.value,
        attachmentIds: []
      },
      {
        onReasoning: (event) => {
          reasoning += event.content || '';
        },
        onDelta: (event) => {
          result += event.content || '';
        },
        onFailed: (event) => {
          status = 'FAILED';
          result += event.content || '定时会话执行失败';
        }
      }
    );
    if (!result.trim()) {
      result = status === 'FAILED' ? '定时会话执行失败。' : '定时会话执行完成，但模型未返回内容。';
    }
  } catch (error) {
    status = 'FAILED';
    result = error.message || '定时会话执行失败';
  } finally {
    appendScheduledHistory({
      id: crypto.randomUUID(),
      sessionId: session.id,
      title: session.title,
      content: session.content,
      result,
      reasoning,
      status,
      modelId: session.modelId || selectedModelId.value,
      conversationId: conversation?.id || '',
      startedAt,
      completedAt: new Date().toISOString(),
      manualRun
    });
    setScheduledSessionRunning(session.id, false);
  }
}

function updateScheduledSessionAfterTrigger(sessionId) {
  const now = new Date().toISOString();
  scheduledSessions.value = scheduledSessions.value.map((session) => {
    if (session.id !== sessionId) {
      return session;
    }
    return {
      ...session,
      lastRunAt: now,
      nextRunAt: nextScheduleTime(session.frequencyMinutes),
      updatedAt: now
    };
  });
  persistScheduledSessions();
}

function appendScheduledHistory(history) {
  scheduledSessionHistories.value = [history, ...scheduledSessionHistories.value].slice(0, 200);
  persistScheduledHistories();
}

function setScheduledSessionRunning(sessionId, running) {
  if (running) {
    scheduledRunningIds.value = [...new Set([...scheduledRunningIds.value, sessionId])];
    return;
  }
  scheduledRunningIds.value = scheduledRunningIds.value.filter((id) => id !== sessionId);
}

function isScheduledSessionRunning(sessionId) {
  return scheduledRunningIds.value.includes(sessionId);
}

function nextScheduleTime(frequencyMinutes) {
  return new Date(Date.now() + Math.max(1, Number(frequencyMinutes) || 15) * 60_000).toISOString();
}

function handleComposerSubmit() {
  if (sending.value) {
    stopCurrentConversation();
    return;
  }
  return handleSend();
}

function stopCurrentConversation() {
  if (!activeStreamAbortController.value) {
    return;
  }
  const assistantMessage = activeStreamingAssistantMessage.value;
  if (assistantMessage) {
    assistantMessage.content = assistantMessage.content || '已停止生成。';
    assistantMessage.reasoningContent += assistantMessage.reasoningContent
      ? '\n已手动停止当前对话。'
      : '已手动停止当前对话。';
    assistantMessage.reasoningActive = false;
    assistantMessage.streaming = false;
  }
  activeStreamAbortController.value.abort();
  activeStreamAbortController.value = null;
  statusText.value = '已停止';
}

async function handleSend() {
  if (sending.value || !draft.value.trim() || !activeConversationId.value) {
    return;
  }

  const userContent = draft.value.trim();
  const activeSkillId = resolveActiveSkillId();
  const userMessage = {
    id: crypto.randomUUID(),
    conversationId: activeConversationId.value,
    role: 'USER',
    content: userContent,
    skillId: activeSkillId,
    modelId: selectedModelId.value,
    attachmentIds: attachments.value.map((item) => item.id),
    status: 'COMPLETED',
    createdAt: new Date().toISOString()
  };
  const assistantMessage = reactive({
    id: crypto.randomUUID(),
    conversationId: activeConversationId.value,
    role: 'ASSISTANT',
    content: '',
    skillId: activeSkillId,
    modelId: selectedModelId.value,
    reasoningContent: '',
    reasoningOpen: true,
    reasoningActive: true,
    answerStarted: false,
    streaming: true,
    attachmentIds: [],
    status: 'COMPLETED',
    createdAt: new Date().toISOString()
  });

  messages.value.push(userMessage, assistantMessage);
  draft.value = '';
  sending.value = true;
  statusText.value = '生成中';

  const reasoningWriter = createTextStreamWriter((content) => {
    assistantMessage.reasoningContent += content;
  });
  const answerWriter = createTextStreamWriter((content) => {
    assistantMessage.content += content;
  });
  const abortController = new AbortController();
  activeStreamAbortController.value = abortController;
  activeStreamingAssistantMessage.value = assistantMessage;

  try {
    await streamMessage(
      activeConversationId.value,
      {
        content: userContent,
        skillId: activeSkillId,
        modelId: selectedModelId.value,
        attachmentIds: attachments.value.map((item) => item.id)
      },
      {
        onStarted: (event) => {
          assistantMessage.id = event.messageId;
          assistantMessage.reasoningActive = true;
          reasoningWriter.enqueue('已收到问题，正在分析上下文、Skill 和模型配置...\n');
          statusText.value = '思考中';
        },
        onReasoning: (event) => {
          assistantMessage.id = event.messageId;
          reasoningWriter.enqueue(event.content);
          assistantMessage.reasoningActive = true;
          statusText.value = '思考中';
        },
        onDelta: (event) => {
          assistantMessage.id = event.messageId;
          assistantMessage.reasoningActive = false;
          if (!assistantMessage.answerStarted) {
            reasoningWriter.enqueue('已形成回答方向，开始流式输出正式内容。\n');
            assistantMessage.answerStarted = true;
          }
          answerWriter.enqueue(event.content);
          statusText.value = '生成中';
        },
        onFailed: (event) => {
          reasoningWriter.cancel();
          answerWriter.cancel();
          assistantMessage.content += event.content || '生成失败';
          assistantMessage.reasoningActive = false;
          assistantMessage.streaming = false;
          statusText.value = '生成失败';
        },
        onCompleted: async () => {
          await Promise.all([reasoningWriter.drain(), answerWriter.drain()]);
          assistantMessage.reasoningActive = false;
          assistantMessage.streaming = false;
          statusText.value = '完成';
        }
      },
      { signal: abortController.signal }
    );
    attachments.value = [];
  } catch (error) {
    reasoningWriter.cancel();
    answerWriter.cancel();
    if (abortController.signal.aborted || error.name === 'AbortError') {
      assistantMessage.content = assistantMessage.content || '已停止生成。';
      assistantMessage.reasoningContent += assistantMessage.reasoningContent ? '\n已手动停止当前对话。' : '已手动停止当前对话。';
      statusText.value = '已停止';
    } else {
      assistantMessage.content = error.message;
      statusText.value = '请求失败';
    }
    assistantMessage.reasoningActive = false;
    assistantMessage.streaming = false;
  } finally {
    if (activeStreamAbortController.value === abortController) {
      activeStreamAbortController.value = null;
    }
    if (activeStreamingAssistantMessage.value === assistantMessage) {
      activeStreamingAssistantMessage.value = null;
    }
    activeTextWriters.delete(reasoningWriter);
    activeTextWriters.delete(answerWriter);
    sending.value = false;
  }
}

function resolveActiveSkillId() {
  if (loadedSkillIds.value.includes(selectedSkillId.value)) {
    return selectedSkillId.value;
  }
  return skills.value[0]?.id || selectedSkillId.value;
}

function createTextStreamWriter(appendText, options = {}) {
  const intervalMs = options.intervalMs ?? 18;
  const pendingCharacters = [];
  const drainResolvers = [];
  let timerId = 0;
  let cancelled = false;

  const stopTimer = () => {
    if (!timerId) {
      return;
    }
    clearInterval(timerId);
    timerId = 0;
  };

  const resolveDrainResolvers = () => {
    if (pendingCharacters.length > 0 || timerId) {
      return;
    }
    drainResolvers.splice(0).forEach((resolve) => resolve());
  };

  const tick = () => {
    if (cancelled) {
      stopTimer();
      pendingCharacters.splice(0);
      resolveDrainResolvers();
      return;
    }
    const nextCharacter = pendingCharacters.shift();
    if (nextCharacter) {
      appendText(nextCharacter);
    }
    if (pendingCharacters.length === 0) {
      stopTimer();
      resolveDrainResolvers();
    }
  };

  const start = () => {
    if (!timerId) {
      timerId = setInterval(tick, intervalMs);
    }
  };

  const writer = {
    enqueue(content) {
      if (!content || cancelled) {
        return;
      }
      pendingCharacters.push(...Array.from(content));
      start();
    },
    drain() {
      if (pendingCharacters.length === 0 && !timerId) {
        return Promise.resolve();
      }
      return new Promise((resolve) => drainResolvers.push(resolve));
    },
    cancel() {
      cancelled = true;
      pendingCharacters.splice(0);
      stopTimer();
      resolveDrainResolvers();
    }
  };

  activeTextWriters.add(writer);
  return writer;
}

function formatConversationTime(value) {
  if (!value) {
    return '';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function formatSequenceNumber(value) {
  return String(value).padStart(2, '0');
}

function messageClass(message) {
  if (isContextNotice(message)) {
    return 'notice';
  }
  return String(message.role || '').toLowerCase();
}

function isContextNotice(message) {
  return message.role === 'CONTEXT_NOTICE';
}

function messageDisplayIndex(messageIndex) {
  return messages.value
    .slice(0, messageIndex + 1)
    .filter((message) => !isContextNotice(message))
    .length;
}

function messageMeta(message) {
  const parts = [];
  if (message.role === 'ASSISTANT') {
    const modelName = models.value.find((item) => item.id === message.modelId)?.name;
    if (modelName) {
      parts.push(modelName);
    }
  }
  if (message.createdAt) {
    parts.push(formatConversationTime(message.createdAt));
  }
  return parts.join(' · ');
}

function renderMessageContent(content) {
  return renderMarkdown(content);
}

function messageDisplayContent(message) {
  if (message.content) {
    return message.content;
  }
  if (isFailedMessage(message)) {
    return '对话流生成失败，请检查模型配置或稍后重试。';
  }
  return '';
}

function isFailedMessage(message) {
  return message.status === 'FAILED';
}

function messageTables(content) {
  return extractMarkdownTables(content);
}

function messageCharts(content) {
  return buildTableCharts(content);
}

function downloadMessageMarkdown(message, event) {
  const visibleContent = event?.currentTarget
    ?.closest('.message-bubble')
    ?.querySelector('.markdown-body')
    ?.innerText
    ?.trim();
  const exportContent = buildMarkdownExportContent(message, visibleContent);
  downloadMarkdownFile(exportContent, `${sanitizeFilename(activeTitle.value)}-${formatDownloadTime(message.createdAt)}.md`);
}

function downloadMessageExcel(message) {
  const tables = messageTables(message.content);
  if (tables.length === 0) {
    return;
  }
  downloadExcelFile(message.content, `${sanitizeFilename(activeTitle.value)}-${formatDownloadTime(message.createdAt)}.xlsx`);
}

function formatDownloadTime(value) {
  const date = value ? new Date(value) : new Date();
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0')
  ].join('');
}

function formatScheduleTime(value) {
  if (!value) {
    return '待定';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function frequencyLabel(frequencyMinutes) {
  const option = scheduleFrequencyOptions.find((item) => item.value === Number(frequencyMinutes));
  if (option) {
    return option.label;
  }
  return `每 ${frequencyMinutes} 分钟`;
}

function downloadScheduledHistoryMarkdown(history) {
  const sections = [
    `# ${history.title}`,
    '',
    `- 类型：定时会话`,
    `- 状态：${history.status === 'COMPLETED' ? '完成' : '失败'}`,
    `- 开始时间：${formatScheduleTime(history.startedAt)}`,
    `- 完成时间：${formatScheduleTime(history.completedAt)}`,
    history.modelId ? `- 模型：${models.value.find((item) => item.id === history.modelId)?.name || history.modelId}` : '',
    '',
    '## 会话内容',
    '',
    history.content || '暂无内容',
    ''
  ].filter((line) => line !== '');

  if (history.reasoning?.trim()) {
    sections.push('## 思考过程', '', history.reasoning.trim(), '');
  }
  sections.push('## 执行结果', '', history.result || '暂无执行结果');
  downloadMarkdownFile(sections.join('\n'), `${sanitizeFilename(history.title)}-${formatDownloadTime(history.startedAt)}.md`);
}

function downloadScheduledHistoryExcel(history) {
  if (messageTables(history.result || '').length === 0) {
    return;
  }
  downloadExcelFile(history.result, `${sanitizeFilename(history.title)}-${formatDownloadTime(history.startedAt)}.xlsx`);
}

function buildMarkdownExportContent(message, visibleContent = '') {
  const messageContent = String(messageDisplayContent(message) || '').trim();
  const bodyContent = messageContent || visibleContent || '当前消息暂无可导出的内容。';
  const sections = [
    `# ${activeTitle.value}`,
    '',
    `- 角色：${message.role === 'ASSISTANT' ? '智能助手' : '用户'}`,
    `- 时间：${message.createdAt ? formatConversationTime(message.createdAt) : formatConversationTime(new Date().toISOString())}`,
    message.modelId ? `- 模型：${models.value.find((item) => item.id === message.modelId)?.name || message.modelId}` : '',
    ''
  ].filter((line) => line !== '');

  if (message.reasoningContent?.trim()) {
    sections.push('## 思考过程', '', message.reasoningContent.trim(), '');
  }

  sections.push('## 回复内容', '', bodyContent);
  return sections.join('\n');
}

function renderReasoningContent(content) {
  const steps = content
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.replace(/^\d+[.、)]\s*/, ''));
  return renderMarkdown(steps.map((step, index) => `${index + 1}. ${step}`).join('\n'));
}

function shouldShowReasoning(message) {
  return message.role === 'ASSISTANT' && Boolean(message.reasoningContent || message.reasoningActive);
}

function reasoningStatus(message) {
  if (message.reasoningActive) {
    return '流式思考中';
  }
  return message.reasoningContent ? '已完成' : '准备中';
}

function toggleReasoning(message) {
  message.reasoningOpen = !message.reasoningOpen;
}

async function scrollToLatestMessage() {
  await nextTick();
  const messageList = messageListRef.value;
  if (!messageList) {
    return;
  }
  messageList.scrollTo({
    top: messageList.scrollHeight,
    behavior: 'smooth'
  });
}
</script>
