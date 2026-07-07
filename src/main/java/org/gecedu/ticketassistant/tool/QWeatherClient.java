package org.gecedu.ticketassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public class QWeatherClient {

    private static final String DEFAULT_API_HOST = "https://devapi.qweather.com";
    private static final Map<String, String> COMMON_CITY_LOCATION_IDS = Map.ofEntries(
            Map.entry("北京", "101010100"),
            Map.entry("上海", "101020100"),
            Map.entry("广州", "101280101"),
            Map.entry("深圳", "101280601"),
            Map.entry("杭州", "101210101"),
            Map.entry("南京", "101190101"),
            Map.entry("武汉", "101200101"),
            Map.entry("成都", "101270101"),
            Map.entry("重庆", "101040100"),
            Map.entry("西安", "101110101"),
            Map.entry("长沙", "101250101"),
            Map.entry("郑州", "101180101"),
            Map.entry("天津", "101030100"),
            Map.entry("苏州", "101190401"),
            Map.entry("佛山", "101280800")
    );

    private final WebClient webClient;
    private final String apiKey;
    private final String apiHost;
    private final Duration timeout;

    public QWeatherClient(
            WebClient.Builder webClientBuilder,
            @Value("${weather.qweather.api-key:}") String apiKey,
            @Value("${weather.qweather.api-host:}") String apiHost,
            @Value("${weather.qweather.timeout-seconds:8}") long timeoutSeconds
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiHost = normalizeHost(apiHost);
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
    }

    public WeatherNow queryNow(String city) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("和风天气 API KEY 未配置");
        }
        String cleanCity = city == null || city.isBlank() ? "广州" : city.trim();
        String locationId = lookupLocationId(cleanCity);
        JsonNode now = requestJson("/v7/weather/now", locationId).path("now");
        String text = requiredText(now, "text");
        String temp = requiredText(now, "temp");
        String windDir = now.path("windDir").asText("");
        String windScale = now.path("windScale").asText("");
        String humidity = now.path("humidity").asText("");
        return new WeatherNow(cleanCity, text, temp, windDir, windScale, humidity);
    }

    private String lookupLocationId(String city) {
        try {
            JsonNode root = requestJson("/geo/v2/city/lookup", city);
            JsonNode locations = root.path("location");
            if (!locations.isArray() || locations.isEmpty()) {
                throw new IllegalStateException("和风天气未找到城市：" + city);
            }
            return requiredText(locations.get(0), "id");
        } catch (RuntimeException exception) {
            String fallbackId = COMMON_CITY_LOCATION_IDS.get(city);
            if (fallbackId == null) {
                throw exception;
            }
            return fallbackId;
        }
    }

    private JsonNode requestJson(String path, String location) {
        URI uri = buildUri(path, location, false);
        try {
            return doRequest(uri, true);
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode() != HttpStatus.UNAUTHORIZED && exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
            URI uriWithKey = buildUri(path, location, true);
            return doRequest(uriWithKey, false);
        }
    }

    private URI buildUri(String path, String location, boolean includeKeyParam) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiHost + path)
                .queryParam("location", location)
                .queryParam("lang", "zh");
        if (includeKeyParam) {
            builder.queryParam("key", apiKey);
        }
        return builder.build().encode().toUri();
    }

    private JsonNode doRequest(URI uri, boolean useHeader) {
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri(uri);
        if (useHeader) {
            request = request.header("X-QW-Api-Key", apiKey);
        }
        JsonNode root = request.retrieve()
                .bodyToMono(JsonNode.class)
                .block(timeout);
        if (root == null) {
            throw new IllegalStateException("和风天气返回为空");
        }
        String code = root.path("code").asText("");
        if (!Objects.equals("200", code)) {
            throw new IllegalStateException("和风天气返回状态码：" + code);
        }
        return root;
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return DEFAULT_API_HOST;
        }
        String cleanHost = host.trim();
        if (!cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
            cleanHost = "https://" + cleanHost;
        }
        while (cleanHost.endsWith("/")) {
            cleanHost = cleanHost.substring(0, cleanHost.length() - 1);
        }
        return cleanHost;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalStateException("和风天气缺少字段：" + field);
        }
        return value;
    }
}
