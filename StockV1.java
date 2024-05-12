package com.mycompany.stockv1;


import com.bloomberglp.blpapi.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

class BloombergDataFetcher {
    private static final String BLOOMBERG_SERVER = "127.0.0.1";
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
            request.set("includeExchangeCodes", true);  // Include exchange codes
            request.set("includeBrokerCodes", true);    // Include broker codes

            Calendar cStart = Calendar.getInstance();
            cStart.add(Calendar.DAY_OF_MONTH, -2);
            cStart.add(Calendar.MINUTE, -10);
            request.set("startDateTime", new Datetime(cStart));

            Calendar cEnd = Calendar.getInstance();
            cEnd.add(Calendar.DAY_OF_MONTH, -1);
            request.set("endDateTime", new Datetime(cEnd));

            CorrelationID correlationId = new CorrelationID(1);
            session.sendRequest(request, correlationId);

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
                            String exchangeCode = tick.hasElement("exchangeCode") ? tick.getElementAsString("exchangeCode") : "N/A";
                            String brokerCode = tick.hasElement("brokerCode") ? tick.getElementAsString("brokerCode") : "N/A";

                            tickDataList.add(new TickData(timestamp, price, volume, exchangeCode, brokerCode));
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
            tick.setClassification(tick.getVolume() >= 400 ? "Machine" : "Human");
        }
    }

    public void saveToDatabase(Connection conn) throws SQLException {
    String insertSQL = "INSERT INTO tick_data (ticker, timestamp, price, volume, exchange_code, broker_code, is_machine) VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
        for (TickData tick : tickData) {
            pstmt.setString(1, tickerSymbol);
            pstmt.setLong(2, tick.getTimestamp());
            pstmt.setDouble(3, tick.getPrice());
            pstmt.setInt(4, tick.getVolume());
            pstmt.setString(5, tick.getExchangeCode());
            pstmt.setString(6, tick.getBrokerCode());
            pstmt.setBoolean(7, tick.getClassification().equals("Machine"));
            pstmt.executeUpdate();
        }
    }
}

    }


class TickData {
    private long timestamp;
    private double price;
    private int volume;
    private String exchangeCode;
    private String brokerCode;
    private String classification;

    public TickData(long timestamp, double price, int volume, String exchangeCode, String brokerCode) {
        this.timestamp = timestamp;
        this.price = price;
        this.volume = volume;
        this.exchangeCode = exchangeCode;
        this.brokerCode = brokerCode;
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

    public String getExchangeCode() {
        return exchangeCode;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    @Override
    public String toString() {
        return String.format("Time: %d, Price: %.2f, Volume: %d, Exchange: %s, Broker: %s, Classification: %s",
                             timestamp, price, volume, exchangeCode, brokerCode, classification);
    }
}

class Transaction implements Serializable {
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
}

public class StockV1 {
    // MariaDB Database Credentials
    private static final String DB_URL = "jdbc:mariadb://localhost:3306/StockV1";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Sinatra147!";
    
    // Networking Port
    private static final int PORT = 12345;
    
    public static void main(String[] args) {
        // Create sector and stock
        Sector sector = new Sector("S&P 500");
        Stock stock = new Stock("AAPL");

        // Fetch data from Bloomberg API
        try {
            System.out.println("Fetching data from Bloomberg API...");
            List<TickData> tickDataList = BloombergDataFetcher.fetchTickData("AAPL US Equity");
            if (tickDataList.isEmpty()) {
                System.out.println("No data received from Bloomberg API.");
            } else {
                System.out.println("Data received: " + tickDataList.size() + " ticks");
            }
            stock.setTickData(tickDataList);
        } catch (Exception e) {
            System.err.println("Error fetching Bloomberg data: " + e.getMessage());
            return;
        }

        // Classify tick data
        System.out.println("Classifying tick data...");
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
        
        // Print transactions
        System.out.println("Printing transactions...");
        printTransactions(stock);
        
        // Send data to client
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                    System.out.println("Client connected. Fetching data from database...");

                    List<Transaction> transactions = fetchTransactionsFromDatabase();
                    out.writeObject(transactions);
                    out.flush();
                    System.out.println("Data sent to client.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Fetch data back from database
    private static List<Transaction> fetchTransactionsFromDatabase() {
        List<Transaction> transactions = new ArrayList<>();
        String url = "jdbc:mariadb://localhost:3306/StockV1";
        String username = "root";
        String password = "Sinatra147!";

        String query = "SELECT ticker, timestamp, price, volume, is_machine FROM tick_data";

        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String ticker = rs.getString("ticker");
                String dateTime = rs.getString("timestamp");
                double costPerShare = rs.getDouble("price");
                int orderSize = rs.getInt("volume");
                boolean bot = rs.getBoolean("is_machine");

                transactions.add(new Transaction(ticker, dateTime, costPerShare, orderSize, bot));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return transactions;
    }

    // Function to print transactions of the given stock
    private static void printTransactions(Stock stock) {
        System.out.println("Transactions for ticker: " + stock.getTickerSymbol());
        List<TickData> tickDataList = stock.getTickData();
        for (TickData tickData : tickDataList) {
            System.out.println(tickData);
        }
        if (tickDataList.isEmpty()) {
            System.out.println("No transaction data available to print.");
        }
    }
}
