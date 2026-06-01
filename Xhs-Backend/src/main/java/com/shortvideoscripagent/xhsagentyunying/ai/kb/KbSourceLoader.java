package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import com.shortvideoscripagent.xhsagentyunying.config.AppKbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbSourceLoader {

    private final AppKbProperties kbProperties;

    public List<KbSourceDescriptor> loadAll() throws IOException {
        Path sourceDir = Path.of(kbProperties.getSourceDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            log.warn("KB source directory does not exist: {}", sourceDir);
            return List.of();
        }
        List<KbSourceDescriptor> descriptors = new ArrayList<>();
        try (Stream<Path> paths = Files.list(sourceDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .sorted()
                    .forEach(path -> {
                        try {
                            descriptors.add(loadFile(path));
                        } catch (IOException ex) {
                            log.warn("Skip KB source file {}: {}", path.getFileName(), ex.getMessage());
                        }
                    });
        }
        return descriptors;
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".txt");
    }

    private KbSourceDescriptor loadFile(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        KbMarkdownFrontMatter.Parsed parsed = KbMarkdownFrontMatter.parse(raw);
        Map<String, String> fm = parsed.fields();

        String baseName = stripExtension(path.getFileName().toString());
        String docId = firstNonBlank(fm.get("doc_id"), fm.get("docId"), baseName);
        String docType = firstNonBlank(fm.get("doc_type"), fm.get("docType"), kbProperties.getDefaultDocType());
        String contentType = blankToNull(fm.get("content_type"), fm.get("contentType"));
        String title = firstNonBlank(fm.get("title"), docId);

        List<String> tags = KbMarkdownFrontMatter.splitList(fm.get("tags"));
        List<String> persona = KbMarkdownFrontMatter.splitList(fm.get("persona"));

        Map<String, Object> extra = new HashMap<>();
        extra.put("sourceFile", path.getFileName().toString());
        extra.put("sourcePath", path.toString());

        return new KbSourceDescriptor(
                docId,
                docType,
                contentType,
                title,
                tags,
                persona,
                parsed.body(),
                path,
                extra
        );
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String... values) {
        String v = firstNonBlank(values);
        return v.isBlank() ? null : v;
    }
}
