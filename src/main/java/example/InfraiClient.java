package example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class InfraiClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String bucket;

    InfraiClient(String baseUrl, String apiKey, String bucket) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.bucket = bucket;
    }

    void createBucket() throws IOException, InterruptedException {
        request("POST", "/v1/storage/bucket/create", "{\"bucket\":\"" + json(bucket) + "\"}");
    }

    String sign(String pdfBase64, String certPem, String keyPem) throws IOException, InterruptedException {
        String body = "{\"pdf\":\"" + json(pdfBase64) + "\",\"cert_pem\":\"" + json(certPem)
                + "\",\"key_pem\":\"" + json(keyPem) + "\",\"reason\":\"logistics approval\",\"location\":\"warehouse\"}";
        return extractString(request("POST", "/v1/pdf/sign", body), "signed_pdf");
    }

    String put(String key, String dataBase64) throws IOException, InterruptedException {
        String body = "{\"data_base64\":\"" + json(dataBase64)
                + "\",\"content_type\":\"application/pdf\",\"idempotency_key\":\"" + json(key) + "\"}";
        return request("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + segment(key), body);
    }

    String presign(String key) throws IOException, InterruptedException {
        String body = "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\"}";
        return extractString(request("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key), body), "url");
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        int attempts = 0;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":false")) throw new IOException("Infrai request rejected: " + envelope);
            if (response.statusCode() == 429 && attempts++ < 3) { Thread.sleep(250L * (1L << attempts)); continue; }
            if (response.statusCode() >= 500) throw new IOException("Infrai service response: " + response.statusCode());
            return envelope;
        }
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String segment(String value) { return value.replace("/", "%2F"); }

    private static String extractString(String body, String field) throws IOException {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) throw new IOException("Infrai response missing " + field);
        start += marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (escaped) {
                if (current == 'n') value.append('\n');
                else value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '\"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        throw new IOException("Infrai response has an unterminated " + field);
    }
}
