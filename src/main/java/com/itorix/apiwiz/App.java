package com.itorix.apiwiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


public class App {


    public static void main(String[] args) throws IOException {

        final String prompt = "You are an API documentation expert and OpenAPI 3.0 rigorist. Your task is to ENHANCE the provided OpenAPI/Swagger specification WITHOUT breaking behavior.\n"
                + "                    \n"
                + "                    Start from the given spec and do the following (preserve all existing paths/operations/schemas; only add or fix issues; do not invent new endpoints unless obviously missing from context like a standard 404/429 error model):\n"
                + "                    \n"
                + "                    1) Keep all existing goals and expand them:\n"
                + "                       - Add meaningful, concise descriptions for paths, operations, parameters, request bodies, headers, and schemas.\n"
                + "                       - Include realistic example values (request and response). Prefer complete, coherent examples over placeholders.\n"
                + "                       - Introduce enums where applicable with clear descriptions; keep values stable and add x-enumDescriptions if helpful.\n"
                + "                       - Improve parameter descriptions with type, units, constraints, and examples.\n"
                + "                       - Add rich response examples for each status code and media type.\n"
                + "                       - Ensure security definitions are correct and consistently applied.\n"
                + "                    \n"
                + "                    2) Conform to OpenAPI 3.0.0 and JSON Schema:\n"
                + "                       - Set \"openapi\": \"3.0.0\".\n"
                + "                       - Ensure all component schemas are valid JSON Schema dialect (use \"$schema\" only if required).\n"
                + "                       - Use readOnly/writeOnly appropriately on properties.\n"
                + "                    \n"
                + "                    3) Operation quality:\n"
                + "                       - Ensure every operation has:\n"
                + "                         * a unique, stable operationId in lowerCamelCase matching ^[a-z][A-Za-z0-9]*$.\n"
                + "                           - If missing, generate from {resource}{Action} (e.g., getUserById, listOrders, createInvoice).\n"
                + "                           - If duplicates exist, disambiguate deterministically (e.g., add By{ParamName} or With{Qualifier}).\n"
                + "                         * summary (≤120 chars) and a helpful description.\n"
                + "                         * proper tags (group by resource or domain); define tag descriptions in top-level \"tags\".\n"
                + "                    \n"
                + "                    4) Parameters & requests:\n"
                + "                       - For every parameter, set: in, name, required, schema (with type/format), description, example.\n"
                + "                       - Use consistent casing conventions (header names case-insensitive but document in Title-Case, e.g., X-Request-Id).\n"
                + "                       - Validate path templating: every {param} in path must have a corresponding \"in: path\" parameter marked required: true.\n"
                + "                    \n"
                + "                    5) Responses:\n"
                + "                       - Prefer RFC 7807 problem details for errors (application/problem+json). Define a reusable ProblemDetail schema and reference it.\n"
                + "                       - Include useful headers where applicable (RateLimit-Limit, RateLimit-Remaining, RateLimit-Reset, Retry-After, ETag, Location, X-Request-Id) and document them.\n"
                + "                    \n"
                + "                    6) Schemas & reuse:\n"
                + "                       - Deduplicate inline schemas into components.schemas and reference via $ref.( Should be inside the schema object )\n"
                + "                       - Components schemas should not have the $schema keyword"
                + "                       - Add \"title\" and \"description\" to all schemas and important properties.\n"
                + "                       - Provide default values where safe; otherwise prefer examples.\n"
                + "                    \n"
                + "                    7) Security:\n"
                + "                       - Define securitySchemes only once in components.securitySchemes:\n"
                + "                       - Apply security globally via top-level \"security\" unless specific operations are intentionally public (then set an empty array on those operations).\n"
                + "                    \n"
                + "                    8) Servers & environments:\n"
                + "                       - Define at least one server with a clear url and description.\n"
                + "                       - Prefer templated servers with variables (e.g., {scheme}, {host}, {basePath}) and defaults for prod; optionally add staging/sandbox with descriptions.\n"
                + "                       - Ensure paths don't duplicate basePath.\n"
                + "                    \n"
                + "                    9) Versioning and compatibility:\n"
                + "                        - DO NOT make breaking changes to request/response shapes. Changes must be additive or corrective (e.g., fixing obvious schema mismatches).\n"
                + "                        - If a breaking fix is unavoidable, note it in the description and prefer adding new fields over changing types.\n"
                + "                    \n"
                + "                    Finally, return ONLY the enhanced OpenAPI specification in JSON format, nothing else. Make it error free in compliant with openapi specification";


        String filesLocation = args[0];
        String openaiApiKey = args[1];
        String openAiUrl = args[2];
        String openAIModel = args[3];
        String customPrompt = "";
        if(args[4] != null) {
            customPrompt = args[4];
        }
        String customPromptString = (!customPrompt.isEmpty())
                ? ("\nAdditional requirements from user context:\n" + customPrompt)
                : "";
        String finalPrompt = prompt + customPromptString;
        File directory = new File(filesLocation);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", openAIModel);
        requestBody.put("temperature", 0.7);
        ArrayNode messages = objectMapper.createArrayNode();
        RestTemplate restTemplate = new RestTemplate();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", finalPrompt));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    messages.add(objectMapper.createObjectNode()
                            .put("role", "user")
                            .put("content", content));
                    requestBody.set("messages", messages);
                    HttpEntity<?> request = new HttpEntity<>(requestBody, headers);
                    ResponseEntity<String> exchange = restTemplate.exchange(openAiUrl,
                            HttpMethod.POST, request, String.class);
                    if(exchange.getStatusCode().is2xxSuccessful()) {
                        JsonNode responseNode = objectMapper.readValue(exchange.getBody(), JsonNode.class);
                        JsonNode choicesNode = responseNode.get("choices");
                        JsonNode firstChoice = choicesNode.get(0);
                        JsonNode contentNode = firstChoice.get("message");
                        String responseContent = contentNode.get("content").asText();
                        if(!responseContent.contains("```json")){
                            try {
                                JsonNode jsonNode = objectMapper.readValue(responseContent,JsonNode.class);
                                String title = jsonNode.get("info").get("title").asText();
                                try (FileWriter writer = new FileWriter(file)) {
                                    writer.write(responseContent);
                                }
                                File renamedFile = new File(file.getParent() + File.separator +  title + ".json");
                                file.renameTo(renamedFile);
                            }catch (Exception ex){
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        System.exit(200);
    }
}
