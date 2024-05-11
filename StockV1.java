package com.mycompany.stockv1;

/**
 *
 * @author brian
 */
import com.bloomberglp.blpapi.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.text.SimpleDateFormat;

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
            //request.append("eventTypes", "BID"); //A request can have multiple eventTypes
            //Note 1) refDataService.ToString() using the Bloomberg API indicates an additional eventType called "SETTLE".  "SETTLE" doesn't seem to produce any results.
            //Note 2) If you request an eventType that isn't supported, the API will throw a KeyNotSupportedException at the "request.Append("eventType", "XXX")" line
            //Note 3) eventType values are case-sensitive.  Requesting "bid" instead of "BID" will throw a KeyNotSupportedException at the "request.Append("eventType", "bid")" line
            
            { //dates
            	
            	//goes back at most 140 days (documentation section 7.2.3)
	            Calendar cStart = Calendar.getInstance();
	            cStart.add(Calendar.DAY_OF_MONTH, -3);
	            cStart.set(Calendar.HOUR, 2);
	            cStart.set(Calendar.MINUTE, 0);
				Datetime dtStart = new Datetime(cStart);	
				request.set("startDateTime", dtStart);		
	
	            Calendar cEnd = Calendar.getInstance();
	            cEnd.add(Calendar.DAY_OF_MONTH, -3);
	            cEnd.set(Calendar.HOUR, 3);
	            cEnd.set(Calendar.MINUTE, 0);
				Datetime dtEnd = new Datetime(cEnd);
				request.set("endDateTime", dtEnd);
            }

//            //A comma delimited list of exchange condition codes associated with the event. Review QR<GO> for more information on each code returned.
//            request.set("includeConditionCodes", false); //Optional bool. Valid values are true and false (default = false)
//
//            //Returns all ticks, including those with condition codes.
//            request.set("includeNonPlottableEvents", false); //Optional bool. Valid values are true and false (default = false)
//
//            //The exchange code where this tick originated. Review QR<GO> for more information.
//            request.set("includeExchangeCodes", false); //Optional bool. Valid values are true and false (default = false)
//
//            //Option on whether to return EIDs for the security.
//            request.set("returnEids", false); //Optional bool. Valid values are true and false (default = false)
//
//            //The broker code for Canadian, Finnish, Mexican, Philippine, and Swedish equities only.
//            //  The Market Maker Lookup screen, MMTK<GO>, displays further information on market makers and their corresponding codes.
//            request.set("includeBrokerCodes", false); //Optional bool. Valid values are true and false (default = false)
//
//            //The Reporting Party Side. The following values appear:
//            //  -B: A customer transaction where the dealer purchases securities from the customer.
//            //  -S: A customer transaction where the dealer sells securities to the customer.
//            //  -D: An inter-dealer transaction (always from the sell side).
//            request.set("includeRpsCodes", false); //Optional bool. Valid values are true and false (default = false)
//
//            //The BIC, or Bank Identifier Code, as a 4-character unique identifier for each bank that executed and reported the OTC trade, as required by MiFID.
//            //  BICs are assigned and maintained by SWIFT (Society for Worldwide Interbank Financial Telecommunication).
//            //  The MIC is the Market Identifier Code, and this indicates the venue on which the trade was executed.
//            request.set("includeBicMicCodes", false); //Optional bool. Valid values are true and false (default = false)
            

            // CorrelationID corr = new CorrelationID(17);
            session.sendRequest(request, null); //(request, corr);

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

        // Print transactions
        System.out.println("Printing transactions...");
        printTransactions(stock);
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

//public class StockV1 {
//    // MariaDB Database Credentials
//    private static final String DB_URL = "jdbc:mariadb://localhost:3306/market_data";
//    private static final String DB_USER = "your_username";
//    private static final String DB_PASSWORD = "your_password";
//
//    public static void main(String[] args) {
//        // Create sector and stock
//        Sector sector = new Sector("S&P 500");
//        Stock stock = new Stock("AAPL");
//
//        // Fetch data from Bloomberg API
//        try {
//            List<TickData> tickDataList = BloombergDataFetcher.fetchTickData("AAPL US Equity");
//            stock.setTickData(tickDataList);
//        } catch (Exception e) {
//            System.err.println("Error fetching Bloomberg data: " + e.getMessage());
//            return;
//        }
//
//        // Classify tick data
//        stock.classifyTickData();
//
//        // Add stock to sector
//        sector.addStock(stock);
//
//        // Insert classified tick data into MariaDB
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            for (Stock s : sector.getStocks()) {
//                s.saveToDatabase(conn);
//            }
//            System.out.println("Data has been successfully inserted into the database.");
//        } catch (SQLException e) {
//            System.err.println("SQL Exception: " + e.getMessage());
//        }
//    }
//}
