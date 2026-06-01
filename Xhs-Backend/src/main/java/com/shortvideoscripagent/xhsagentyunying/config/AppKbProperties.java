package com.shortvideoscripagent.xhsagentyunying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kb")
public class AppKbProperties {

    /**
     * 知识库原始文档目录（.md / .txt），后期将文档放入此目录后执行 reindex。
     */
    private String sourceDir = "./data/kb-sources";

    private String defaultDocType = "viral_case";

    private Chunking chunking = new Chunking();

    private Ingest ingest = new Ingest();

    @Data
    public static class Chunking {
        /**
         * 目标切片 token 数（Spring AI TokenTextSplitter，CL100K_BASE）。
         */
        private int chunkSizeTokens = 400;
        /** 预留：Spring AI 1.1.2 的 TokenTextSplitter 尚未暴露 overlap API。 */
        private int chunkOverlapTokens = 50;
        private int minChunkSizeChars = 100;
        private int minChunkLengthToEmbed = 10;
        private int maxNumChunks = 500;
        private boolean keepSeparator = true;
    }

    @Data
    public static class Ingest {
        /**
         * 单次 reindex 是否调用 Embedding 并写入 pgvector。
         */
        private boolean embedOnIngest = true;
        private int embeddingBatchSize = 16;
    }
}
