package org.gecedu.ticketassistant.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentParser {

    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)[1-9]\\d{16}[0-9Xx](?!\\d)");
    private static final Pattern TRAIN_NO = Pattern.compile("(?<![A-Za-z0-9])[GDKZTC][0-9]{1,5}(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(?<!\\d)20\\d{2}-\\d{2}-\\d{2}(?!\\d)");
    private static final Pattern ORDER_NO = Pattern.compile("(?<![A-Za-z0-9])TA\\d{10,}(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("(?:乘车人姓名|旅客姓名|姓名|乘车人|旅客|我是|我叫)[:：是\\s]*([\\u4e00-\\u9fa5]{2,6})");
    private static final Pattern CITY = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})(?:市)?(?:今天|明天|后天)?天气");
    private static final Pattern ROUTE = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})(?:到|至|去)([\\u4e00-\\u9fa5]{2,8}?)(?:的)?(?:火车票|车票|余票|票|\\s|，|,|$)");

    private IntentParser() {
    }

    public static boolean isBookingIntent(String text) {
        return text.contains("购票") || text.contains("买票") || text.contains("订票");
    }

    public static boolean isRefundIntent(String text) {
        return text.contains("退票") || text.contains("退款") || text.contains("退订");
    }

    public static boolean isOrderQueryIntent(String text) {
        return text.contains("查询订单")
                || text.contains("查订单")
                || text.contains("我的订单")
                || text.contains("查询预订")
                || text.contains("查询订票")
                || text.contains("订单记录")
                || text.contains("有哪些订单");
    }

    public static boolean isWeatherIntent(String text) {
        return text.contains("天气");
    }

    public static boolean isTrainTicketSearchIntent(String text) {
        return parseRoute(text) != null
                && (text.contains("火车票") || text.contains("车票") || text.contains("余票") || text.contains("查票"));
    }

    public static boolean isIdentityQuestion(String text) {
        return text.contains("我是谁")
                || text.contains("我叫什么")
                || text.contains("我的名字")
                || text.contains("知道我叫")
                || text.contains("记得我叫");
    }

    public static boolean hasBookingInfo(String text) {
        if (isIdentityQuestion(text)) {
            return false;
        }
        return lastMatch(NAME, text) != null
                || lastMatch(ID_CARD, text) != null
                || lastMatch(TRAIN_NO, text) != null
                || lastMatch(DATE, text) != null
                || parseSeatType(text) != null;
    }

    public static boolean hasRefundInfo(String text) {
        if (isIdentityQuestion(text)) {
            return false;
        }
        return lastMatch(ORDER_NO, text) != null
                || lastMatch(NAME, text) != null
                || lastMatch(ID_CARD, text) != null;
    }

    public static boolean isSelectionReply(String text) {
        return text.matches(".*第?[1-9]个?.*")
                || text.contains("第一")
                || text.contains("第二")
                || text.contains("第三")
                || text.contains("第四")
                || text.contains("第五");
    }

    public static String parsePersonName(String text) {
        return lastMatch(NAME, text);
    }

    public static BookingInfo parseBooking(String text) {
        String passengerName = lastMatch(NAME, text);
        String idCard = lastMatch(ID_CARD, text);
        String trainNo = upper(lastMatch(TRAIN_NO, text));
        LocalDate travelDate = parseDate(lastMatch(DATE, text));
        String seatType = parseSeatType(text);
        RouteInfo route = parseRoute(text);

        List<String> missing = new ArrayList<>();
        if (passengerName == null) {
            missing.add("乘车人姓名");
        }
        if (idCard == null) {
            missing.add("身份证号");
        }
        if (trainNo == null) {
            missing.add("车次");
        }
        if (travelDate == null) {
            missing.add("乘车日期，格式如2026-06-25");
        }
        if (seatType == null) {
            missing.add("座位类型：商务座/一等座/二等座/硬座/软座/硬卧/软卧/无座");
        }
        return new BookingInfo(
                passengerName,
                idCard,
                trainNo,
                travelDate,
                seatType,
                route == null ? null : route.depart(),
                route == null ? null : route.arrive(),
                missing
        );
    }

    public static RouteInfo parseRoute(String text) {
        Matcher matcher = ROUTE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String depart = cleanRouteCity(matcher.group(1));
        String arrive = cleanRouteCity(matcher.group(2));
        if (depart.isBlank() || arrive.isBlank() || depart.equals(arrive)) {
            return null;
        }
        return new RouteInfo(depart, arrive);
    }

    public static RefundInfo parseRefund(String text) {
        String orderNo = upper(lastMatch(ORDER_NO, text));
        if (orderNo != null) {
            return new RefundInfo(orderNo, null, null, List.of());
        }
        String passengerName = lastMatch(NAME, text);
        String idCard = lastMatch(ID_CARD, text);

        List<String> missing = new ArrayList<>();
        if (orderNo == null && passengerName == null) {
            missing.add("订单号，或乘车人姓名");
        }
        if (orderNo == null && idCard == null) {
            missing.add("身份证号");
        }
        return new RefundInfo(orderNo, passengerName, idCard, missing);
    }

    public static String parseWeatherCity(String text) {
        Matcher matcher = CITY.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replace("查询", "").replace("一下", "");
        }
        if (text.contains("广州")) {
            return "广州";
        }
        if (text.contains("深圳")) {
            return "深圳";
        }
        if (text.contains("北京")) {
            return "北京";
        }
        if (text.contains("上海")) {
            return "上海";
        }
        return null;
    }

    private static String parseSeatType(String text) {
        String latest = null;
        int latestIndex = -1;
        for (String seatType : List.of("商务座", "一等座", "二等座", "硬座", "软座", "硬卧", "软卧", "无座")) {
            int index = text.lastIndexOf(seatType);
            if (index > latestIndex) {
                latest = seatType;
                latestIndex = index;
            }
        }
        return latest;
    }

    private static String cleanRouteCity(String value) {
        return value == null ? "" : value
                .replace("查询", "")
                .replace("查", "")
                .replace("我要买", "")
                .replace("我要订", "")
                .replace("我要购", "")
                .replace("从", "")
                .replace("出发", "")
                .replace("市", "")
                .trim();
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String lastMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        String last = null;
        while (matcher.find()) {
            last = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        }
        return last;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase();
    }
}
