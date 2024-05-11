/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.stockv1;

/**
 *
 * @author hemgr
 */
import com.bloomberglp.blpapi.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

class BloombergDataFetcher {
    private static final String BLOOMBERG_SERVER = "localhost";
    private static final int BLOOMBERG_PORT = 8194;

    public static List<TickData> fetchTickData(String ticker) throws Exception {
        List<TickData> tickDataList = new ArrayList<>();
        SessionOptions options = new SessionOptions();
        options.setServerHost(BLOOMBERG_SERVER);
        options.setServerPort(BLOOMBERG_PORT);

        Session session = new Session(options);
        if (!session.start()) {
            throw new Exception("Failed to start session.");
        }

        try {
            Service refDataService = session.getService("//blp/refdata");
            Request request = refDataService.createRequest("IntradayTickRequest");
            request.set("security", ticker);
            request.append("eventTypes", "TRADE");
            request.set("startDateTime", "2024-05-01T09:30:00");
            request.set("endDateTime", "2024-05-01T16:00:00");

            session.sendRequest(request, null);

            boolean continueLoop = true;
            while (continueLoop) {
                Event event = session.nextEvent();
                MessageIterator msgIterator = event.messageIterator();
                while (msgIterator.hasNext()) {
                    Message msg = msgIterator.next();
                    if (event.eventType() == Event.EventType.RESPONSE || event.eventType() == Event.EventType.PARTIAL_RESPONSE) {
                        Element data = msg.getElement("tickData").getElement("tickData");
                        for (int i = 0; i < data.numValues(); i++) {
                            Element tick = data.getValueAsElement(i);
                            long timestamp = tick.getElementAsDatetime("time").calendar().getTimeInMillis();
                            double price = tick.getElementAsFloat64("value");
                            int volume = tick.getElementAsInt32("size");
                            tickDataList.add(new TickData(timestamp, price, volume));
                        }
                        continueLoop = event.eventType() != Event.EventType.RESPONSE;
                    }
                }
            }
        } finally {
            session.stop();
        }

        return tickDataList;
    }
}

class Sector {
    private String name;
    private List<Stock> stocks;

    public Sector(String name) {
        this.name = name;
        this.stocks = new ArrayList<>();
    }

    public void addStock(Stock stock) {
        stocks.add(stock);
    }

    public List<Stock> getStocks() {
        return stocks;
    }

    public String getName() {
        return name;
    }
}

class Stock {
    private String tickerSymbol;
    private List<TickData> tickData;

    public Stock(String tickerSymbol) {
        this.tickerSymbol = tickerSymbol;
        this.tickData = new ArrayList<>();
    }

    public void setTickData(List<TickData> data) {
        this.tickData = data;
    }

    public List<TickData> getTickData() {
        return tickData;
    }

    public String getTickerSymbol() {
        return tickerSymbol;
    }

    public void classifyTickData() {
        for (TickData tick : tickData) {
            tick.setClassification(tick.getVolume() > 1000 ? "Machine" : "Human");
        }
    }

    public void saveToDatabase(Connection conn) throws SQLException {
        String insertSQL = "INSERT INTO tick_data (ticker, timestamp, price, volume, is_machine) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (TickData tick : tickData) {
                pstmt.setString(1, tickerSymbol);
                pstmt.setLong(2, tick.getTimestamp());
                pstmt.setDouble(3, tick.getPrice());
                pstmt.setInt(4, tick.getVolume());
                pstmt.setBoolean(5, tick.getClassification().equals("Machine"));
                pstmt.executeUpdate();
            }
        }
    }
}

class TickData {
    private long timestamp;
    private double price;
    private int volume;
    private String classification;

    public TickData(long timestamp, double price, int volume) {
        this.timestamp = timestamp;
        this.price = price;
        this.volume = volume;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getPrice() {
        return price;
    }

    public int getVolume() {
        return volume;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    @Override
    public String toString() {
        return String.format("Time: %d, Price: %.2f, Volume: %d, Classification: %s",
                             timestamp, price, volume, classification);
    }
}

public class StockV1 {
    // MariaDB Database Credentials
    private static final String DB_URL = "jdbc:mariadb://localhost:3306/market_data";
    private static final String DB_USER = "your_username";
    private static final String DB_PASSWORD = "your_password";

    public static void main(String[] args) {
        // Create sector and stock
        Sector sector = new Sector("S&P 500");
        Stock stock = new Stock("AAPL");

        // Fetch data from Bloomberg API
        try {
            List<TickData> tickDataList = BloombergDataFetcher.fetchTickData("AAPL US Equity");
            stock.setTickData(tickDataList);
        } catch (Exception e) {
            System.err.println("Error fetching Bloomberg data: " + e.getMessage());
            return;
        }

        // Classify tick data
        stock.classifyTickData();

        // Add stock to sector
        sector.addStock(stock);

        // Insert classified tick data into MariaDB
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (Stock s : sector.getStocks()) {
                s.saveToDatabase(conn);
            }
            System.out.println("Data has been successfully inserted into the database.");
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }
}

