package com.gnanadhan.app.service.extractor;

import com.gnanadhan.app.entity.DatabaseEngine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SchemaExtractorFactory {
    
    private final List<SchemaExtractor> extractors;

    public SchemaExtractorFactory(List<SchemaExtractor> extractors) {
        this.extractors = extractors;
    }

    public SchemaExtractor getExtractor(DatabaseEngine engine) {
        return extractors.stream()
                .filter(e -> e.supports(engine))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported database engine: " + engine));
    }
}

