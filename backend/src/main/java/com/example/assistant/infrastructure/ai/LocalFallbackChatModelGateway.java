package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class LocalFallbackChatModelGateway implements ChatModelGateway {

    @Override
    public Flux<ChatModelChunk> stream(ChatPrompt prompt) {
        String answer = buildAnswer(prompt);
        return Flux.concat(
                Flux.just(ChatModelChunk.reasoning("""
                        已读取当前 Skill、模型、历史上下文、RAG 检索结果和 MCP 工具清单，准备生成本地回退答复。
                        """)),
                Flux.fromArray(answer.split("(?<=\\G.{12})")).map(ChatModelChunk::answer)
        )
                .delayElements(Duration.ofMillis(30));
    }

    private String buildAnswer(ChatPrompt prompt) {
        if (prompt.userMessage().contains("图表") || prompt.userMessage().contains("表格") || prompt.userMessage().contains("Excel")) {
            return """
                    下面是一个可用于图表展示和 Excel 导出的 Markdown 表格。

                    | 阶段 | 任务数 | 风险数 |
                    | --- | ---: | ---: |
                    | 需求分析 | 5 | 1 |
                    | 接口设计 | 8 | 2 |
                    | 数据建模 | 6 | 3 |
                    | 联调测试 | 7 | 2 |

                    当前模型：%s
                    Skill 指令：%s
                    RAG 上下文数量：%d
                    MCP 工具数量：%d
                    """.formatted(
                    prompt.model().name(),
                    prompt.skillInstruction(),
                    prompt.ragContexts().size(),
                    prompt.mcpTools().size()
            );
        }
        return """
                当前模型使用本地回退响应；配置对应模型 API Key 后即可切换到远程推理。

                当前模型：%s
                你的问题是：%s
                Skill 指令：%s
                RAG 上下文数量：%d
                MCP 工具数量：%d
                """.formatted(
                prompt.model().name(),
                prompt.userMessage(),
                prompt.skillInstruction(),
                prompt.ragContexts().size(),
                prompt.mcpTools().size()
        );
    }
}
