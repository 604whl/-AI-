package com.shortvideoscripagent.xhsagentyunying;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.shortvideoscripagent.xhsagentyunying.domain.mapper")
@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class
})
public class XhsAgentYunyingApplication {

    public static void main(String[] args) {
        SpringApplication.run(XhsAgentYunyingApplication.class, args);
    }

}
