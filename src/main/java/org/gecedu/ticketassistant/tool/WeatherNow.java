package org.gecedu.ticketassistant.tool;

public record WeatherNow(
        String city,
        String text,
        String temp,
        String windDir,
        String windScale,
        String humidity
) {

    public String toTravelMessage() {
        StringBuilder message = new StringBuilder();
        message.append(city).append("实时天气：")
                .append(text)
                .append("，气温").append(temp).append("摄氏度");
        if (!windDir.isBlank() || !windScale.isBlank()) {
            message.append("，").append(windDir);
            if (!windScale.isBlank()) {
                message.append(windScale);
                if (windScale.chars().allMatch(Character::isDigit)) {
                    message.append("级");
                }
            }
        }
        if (!humidity.isBlank()) {
            message.append("，湿度").append(humidity).append("%");
        }
        message.append("。请结合实际路况和车站通知安排出行。");
        return message.toString();
    }
}
