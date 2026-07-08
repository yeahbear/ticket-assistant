package org.gecedu.ticketassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ApiHzTicketClient {

    private static final String DEFAULT_BASE_URL = "http://101.35.2.25/api/12306";

    private final WebClient webClient;
    private final String developerId;
    private final String developerKey;
    private final String baseUrl;
    private final Duration timeout;

    public ApiHzTicketClient(
            WebClient.Builder webClientBuilder,
            @Value("${railway.apihz.developer-id:}") String developerId,
            @Value("${railway.apihz.developer-key:}") String developerKey,
            @Value("${railway.apihz.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${railway.apihz.timeout-seconds:12}") long timeoutSeconds
    ) {
        this.webClient = webClientBuilder.build();
        this.developerId = developerId == null ? "" : developerId.trim();
        this.developerKey = developerKey == null ? "" : developerKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
    }

    public boolean enabled() {
        return !developerId.isBlank() && !developerKey.isBlank();
    }

    public List<TrainTicketOption> search(String depart, String arrive, LocalDate date) {
        if (!enabled()) {
            throw new IllegalStateException("接口盒子 12306 开发者凭据未配置");
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api.php")
                .queryParam("id", developerId)
                .queryParam("key", developerKey)
                .queryParam("add", depart)
                .queryParam("end", arrive)
                .queryParam("y", date.getYear())
                .queryParam("m", date.getMonthValue())
                .queryParam("d", date.getDayOfMonth())
                .build()
                .encode()
                .toUri();
        JsonNode root = request(uri);
        JsonNode datas = root.path("datas");
        if (!datas.isArray()) {
            return List.of();
        }
        List<TrainTicketOption> options = new ArrayList<>();
        for (JsonNode item : datas) {
            options.add(toOption(item));
        }
        return options;
    }

    public TrainTicketOption findTrain(String depart, String arrive, LocalDate date, String trainNo) {
        return search(depart, arrive, date).stream()
                .filter(option -> option.trainNo().equalsIgnoreCase(trainNo))
                .findFirst()
                .orElse(null);
    }

    public BigDecimal queryPrice(TrainTicketOption option, String seatType) {
        if (!enabled()) {
            throw new IllegalStateException("接口盒子 12306 开发者凭据未配置");
        }
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api2.php")
                .queryParam("id", developerId)
                .queryParam("key", developerKey)
                .queryParam("train_order", option.trainOrder())
                .queryParam("depart_index", option.departIndex())
                .queryParam("arrive_index", option.arriveIndex())
                .queryParam("seatcode", option.seatCode())
                .queryParam("y", option.date().getYear())
                .queryParam("m", option.date().getMonthValue())
                .queryParam("d", option.date().getDayOfMonth())
                .queryParam("ck", "")
                .build()
                .encode()
                .toUri();
        JsonNode root = request(uri);
        return parsePrice(root.path(priceField(seatType)).asText(""));
    }

    private JsonNode request(URI uri) {
        JsonNode root = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(timeout);
        if (root == null) {
            throw new IllegalStateException("接口盒子 12306 返回为空");
        }
        int code = root.path("code").asInt(400);
        if (code != 200) {
            String message = root.path("msg").asText("接口盒子 12306 查询失败");
            throw new IllegalStateException(message);
        }
        return root;
    }

    private TrainTicketOption toOption(JsonNode item) {
        List<TrainSeatStock> seats = new ArrayList<>();
        JsonNode seatNodes = item.path("seats");
        if (seatNodes.isArray()) {
            for (JsonNode seatNode : seatNodes) {
                seats.add(new TrainSeatStock(
                        seatNode.path("type").asText(""),
                        seatNode.path("stock").asInt(0)
                ));
            }
        }
        return new TrainTicketOption(
                item.path("train_number").asText(""),
                item.path("train_order").asText(""),
                item.path("depart_index").asText(""),
                item.path("arrive_index").asText(""),
                item.path("depart_name").asText(""),
                item.path("arrive_name").asText(""),
                item.path("depart_time").asText(""),
                item.path("arrive_time").asText(""),
                item.path("duration").asText(""),
                item.path("seatcode").asText(""),
                LocalDate.parse(item.path("date").asText()),
                seats
        );
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        String number = value.replace("￥", "").replace("¥", "").trim();
        return new BigDecimal(number).setScale(2);
    }

    private String priceField(String seatType) {
        return switch (seatType) {
            case "商务座" -> "tdz";
            case "一等座" -> "ydz";
            case "二等座" -> "edz";
            case "软卧" -> "rw";
            case "硬卧" -> "yw";
            case "软座" -> "rz";
            case "硬座" -> "yz";
            case "无座" -> "wz";
            default -> throw new IllegalArgumentException("座位类型必须是：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座");
        };
    }

    public static String apiSeatName(String seatType) {
        return switch (Objects.toString(seatType, "")) {
            case "商务座" -> "商务座";
            case "一等座" -> "一等座";
            case "二等座" -> "二等座";
            case "软卧" -> "软卧";
            case "硬卧" -> "硬卧";
            case "软座" -> "软座";
            case "硬座" -> "硬座";
            case "无座" -> "无座";
            default -> seatType;
        };
    }

    private String normalizeBaseUrl(String value) {
        String clean = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
