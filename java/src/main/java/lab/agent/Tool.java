package lab.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A function the model may ask to call.
 *
 * <p>The model never sees the Java code. It sees only three things: the name,
 * the description, and the parameter schema. Those are the whole contract, so
 * they're spelled out here by hand. Python builds them from type hints and the
 * docstring; C# builds them from parameter types and [Description] attributes.
 * Both do by reflection what this record makes you write.
 *
 * @param name        what the model calls the tool, e.g. "check_quota"
 * @param description when and why to use it; the model decides from this
 * @param parameters  a JSON Schema object describing the arguments
 * @param handler     the Java code that runs; receives the parsed arguments,
 *                    returns text the model will read
 */
public record Tool(
        String name,
        String description,
        Map<String, Object> parameters,
        Function<Map<String, Object>, String> handler) {

    /** One string parameter: its name and the description the model reads. */
    public record Param(String name, String description) {
    }

    /**
     * Build the JSON Schema for a tool whose parameters are all required strings.
     * That covers every tool in this lab. The result, for one parameter, is:
     *
     * <pre>
     * { "type": "object",
     *   "properties": { "order_id": { "type": "string", "description": "..." } },
     *   "required": ["order_id"],
     *   "additionalProperties": false }
     * </pre>
     */
    public static Map<String, Object> stringParams(Param... params) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Param p : params) {
            properties.put(p.name(), Map.of("type", "string", "description", p.description()));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(params).stream().map(Param::name).toList());
        schema.put("additionalProperties", false);
        return schema;
    }

    /** A tool that takes no arguments. */
    public static Map<String, Object> noParams() {
        return stringParams();
    }
}
