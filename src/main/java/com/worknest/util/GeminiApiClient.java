package com.worknest.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small server-side REST client shared by WorkNest's Gemini features. */
public final class GeminiApiClient {
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private GeminiApiClient() { }

    public static String generateText(String prompt, int maxOutputTokens)
            throws IOException, InterruptedException, ApiException {
        return generate(prompt, null, maxOutputTokens);
    }

    public static String generateJson(String prompt, JSONObject responseSchema,
                                      int maxOutputTokens)
            throws IOException, InterruptedException, ApiException {
        if (responseSchema == null) {
            throw new IllegalArgumentException("A JSON response schema is required.");
        }
        return generate(prompt, responseSchema, maxOutputTokens);
    }

    private static String generate(String prompt, JSONObject responseSchema,
                                   int maxOutputTokens)
            throws IOException, InterruptedException, ApiException {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("AI drafting is not configured on the server.", 0);
        }
        if (prompt == null || prompt.isBlank()
                || maxOutputTokens < 1 || maxOutputTokens > 4096) {
            throw new IllegalArgumentException("Invalid Gemini request.");
        }

        String model = System.getenv("GEMINI_MODEL");
        if (model == null || model.isBlank()) model = DEFAULT_MODEL;
        if (!model.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new ApiException("The configured AI model is invalid.", 0);
        }

        JSONObject generationConfig = new JSONObject()
                .put("temperature", 0.2)
                .put("maxOutputTokens", maxOutputTokens);
        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema);
        }

        JSONObject payload = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(
                                new JSONObject().put("text", prompt)))))
                .put("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent"))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiError(response.statusCode());
        }

        final JSONObject providerResponse;
        try {
            providerResponse = new JSONObject(response.body());
        } catch (RuntimeException exception) {
            throw new ApiException("Gemini returned an unreadable response.",
                    response.statusCode());
        }

        JSONArray candidates = providerResponse.optJSONArray("candidates");
        JSONObject candidate = candidates == null ? null : candidates.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        String text = parts == null || parts.length() == 0
                ? "" : parts.optJSONObject(0) == null ? ""
                        : parts.optJSONObject(0).optString("text", "");
        if (text.isBlank()) {
            throw new ApiException("Gemini could not generate a response for that request.",
                    response.statusCode());
        }
        return text.trim();
    }

    private static ApiException apiError(int status) {
        if (status == 429) {
            return new ApiException(
                    "Gemini is temporarily rate-limited. Wait a little and try again.", status);
        }
        if (status == 401 || status == 403) {
            return new ApiException(
                    "Gemini rejected the key or model access. Check the server configuration.", status);
        }
        return new ApiException(
                "Gemini is temporarily unavailable. Please try again later.", status);
    }

    public static final class ApiException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        public ApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
