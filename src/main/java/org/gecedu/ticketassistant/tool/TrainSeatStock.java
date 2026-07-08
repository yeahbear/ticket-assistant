package org.gecedu.ticketassistant.tool;

public record TrainSeatStock(String type, int stock) {

    public boolean available() {
        return stock != 0;
    }

    public String stockText() {
        if (stock < 0) {
            return "有票";
        }
        if (stock == 0) {
            return "无票";
        }
        return "余" + stock + "张";
    }
}
