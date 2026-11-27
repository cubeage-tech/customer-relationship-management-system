package com.company.crm.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The JSON Schema of an answer record, for providers that take one (xAI structured outputs): every
 * property required and no extra properties, so the model must return exactly the record's shape.
 * Field descriptions come from {@code @JsonPropertyDescription}.
 */
public final class JsonSchemas {

    private static final SchemaGenerator GENERATOR = new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                    .with(new JacksonModule())
                    .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                    .without(Option.SCHEMA_VERSION_INDICATOR)
                    .build());

    private static final Map<Class<?>, ObjectNode> CACHE = new ConcurrentHashMap<>();

    private JsonSchemas() {
    }

    public static ObjectNode of(Class<?> type) {
        return CACHE.computeIfAbsent(type, t -> {
            ObjectNode schema = GENERATOR.generateSchema(t);
            requireAll(schema);
            return schema;
        }).deepCopy();
    }

    /** Every object's properties become required (strict structured output). */
    private static void requireAll(JsonNode node) {
        if (node instanceof ObjectNode object) {
            JsonNode properties = object.get("properties");
            if (properties instanceof ObjectNode props) {
                ArrayNode required = object.putArray("required");
                props.fieldNames().forEachRemaining(required::add);
                object.put("additionalProperties", false);
            }
        }
        if (node != null && node.isContainerNode()) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                requireAll(it.next());
            }
        }
    }
}
