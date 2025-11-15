package com.ivanka.audioeditor.client.net;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class ApiClient {

    private static ApiClient INSTANCE;

    private final String base;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private ApiClient(String baseUrl) {
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static synchronized ApiClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ApiClient("http://localhost:8080/api");
        }
        return INSTANCE;
    }
    public String getBaseUrl() {
        return base;
    }

    private HttpResponse<String> safeSend(HttpRequest req)
            throws IOException, InterruptedException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new RuntimeException("Сервер недоступний за адресою: " + base +
                    "\nПереконайся, що Spring Boot запущено (порт 8080).", e);
        }
    }

    public String postJson(String path, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> res = safeSend(req);
        if (res.statusCode() >= 400)
            throw new RuntimeException("POST " + path + " -> " + res.statusCode() + ": " + res.body());
        return res.body();
    }

    public String postForm(String path, String query) throws Exception {
        String encodedQuery = query.contains("&") ? query : encodeQuery(query);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodedQuery))
                .build();
        HttpResponse<String> res = safeSend(req);
        if (res.statusCode() >= 400)
            throw new RuntimeException("POST " + path + " -> " + res.statusCode() + ": " + res.body());
        return res.body();
    }

    public String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .GET()
                .build();
        HttpResponse<String> res = safeSend(req);
        if (res.statusCode() >= 400)
            throw new RuntimeException("GET " + path + " -> " + res.statusCode() + ": " + res.body());
        return res.body();
    }

    public String postMultipart(String path, Map<String, String> params, File file) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        var byteStream = new ByteArrayOutputStream();
        var writer = new PrintWriter(new OutputStreamWriter(byteStream, StandardCharsets.UTF_8), true);
        String LINE_FEED = "\r\n";

        for (var entry : params.entrySet()) {
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
            writer.append(LINE_FEED).append(entry.getValue()).append(LINE_FEED);
            writer.flush();
        }

        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(file.getName()).append("\"").append(LINE_FEED);
        writer.append("Content-Type: ").append(Files.probeContentType(file.toPath()))
                .append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();

        Files.copy(file.toPath(), byteStream);
        byteStream.flush();
        writer.append(LINE_FEED).flush();
        writer.append("--").append(boundary).append("--").append(LINE_FEED);
        writer.close();

        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(byteStream.toByteArray()))
                .build();

        HttpResponse<String> response = safeSend(request);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("POST multipart " + path + " -> " +
                    response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String encodeQuery(String query) {
        if (query == null || !query.contains("=")) return query;
        String[] parts = query.split("=", 2);
        String key = parts[0];
        String value = parts.length > 1 ? parts[1] : "";
        return key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }


}