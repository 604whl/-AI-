package com.shortvideoscripagent.xhsagentyunying;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.shortvideoscripagent.xhsagentyunying.domain.mapper",
        "com.shortvideoscripagent.xhsagentyunying.quota.domain.mapper",
        // S4-1 套餐 / 订阅 Mapper
        "com.shortvideoscripagent.xhsagentyunying.billing.plan.domain.mapper",
        "com.shortvideoscripagent.xhsagentyunying.billing.subscription.domain.mapper",
        // S4-2 订单 / 支付事件 / 对账 Mapper
        "com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper",
        // S4-3 价格表 / 余额 / 账单 Mapper
        "com.shortvideoscripagent.xhsagentyunying.billing.pricing.domain.mapper",
        "com.shortvideoscripagent.xhsagentyunying.billing.balance.domain.mapper",
        "com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper",
        "com.shortvideoscripagent.xhsagentyunying.freeze"
})
@EnableScheduling
@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration.class
})
public class XhsAgentYunyingApplication {

    public static void main(String[] args) {
        SpringApplication.run(XhsAgentYunyingApplication.class, args);
    }

}
