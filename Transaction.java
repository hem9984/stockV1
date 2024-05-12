package com.mycompany.stockv1;

import java.io.Serializable;

public class Transaction implements Serializable {
    String ticker;
    String dateTime;
    double costPerShare;
    int orderSize;
    boolean bot;

    public Transaction(String ticker, String dateTime, double costPerShare, int orderSize, boolean bot) {
        this.ticker = ticker;
        this.dateTime = dateTime;
        this.costPerShare = costPerShare;
        this.orderSize = orderSize;
        this.bot = bot;
    }

    @Override
    public String toString() {
        return String.format("%s | Bot: %s | Cost/Share: $%.2f | Size: %d | Date/Time: %s",
                    ticker, bot ? "Yes" : "No", costPerShare, orderSize, dateTime);
    }
}
