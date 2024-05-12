package com.mycompany.stockv1;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.net.Socket;

public class ClientV1 extends JFrame {
    private JList<Transaction> transactionsList;
    private DefaultListModel<Transaction> listModel;
    private JPanel detailsPanel;
    private StockGraphPanel graphPanel;
    private JTextField searchBar;
    private boolean sortCostAsc = false;
    private boolean sortSizeAsc = false;
    private boolean sortDateAsc = false;
    private boolean sortBotAsc = false;

    // Custom Stock price over time graph
    public class StockGraphPanel extends JPanel {
        private List<Transaction> transactions = new ArrayList<>();
        private boolean visible = true;
        private static final int PADDING = 25;
        private static final int LABEL_PADDING = 25;
        private static final String TITLE = "Stock Price Over Time";
        private static final String X_AXIS_LABEL = "Time (Transactions)";
        private static final String Y_AXIS_LABEL = "Cost per Share ($)";

        public StockGraphPanel() {
            setPreferredSize(new Dimension(400, 200));
            setBorder(BorderFactory.createLineBorder(Color.BLACK));
        }

        public void setTransactions(List<Transaction> transactions) {
            this.transactions = transactions;
            repaint(); // Redraw the panel with new data
        }

        public void setGraphVisible(boolean visible) {
            this.visible = visible;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!visible || transactions.isEmpty()) return;

            // Initialize the graphics and dimensions
            g.setColor(Color.BLUE);
            int width = getWidth();
            int height = getHeight();
            int graphWidth = width - 2 * (PADDING + LABEL_PADDING);
            int graphHeight = height - 2 * (PADDING + LABEL_PADDING);

            // Find min and max values
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;
            for (Transaction transaction : transactions) {
                double price = transaction.costPerShare;
                minPrice = Math.min(minPrice, price);
                maxPrice = Math.max(maxPrice, price);
            }

            // Draw title
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(TITLE, (width - g.getFontMetrics().stringWidth(TITLE)) / 2, PADDING);

            // Draw axes
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawLine(PADDING + LABEL_PADDING, height - PADDING - LABEL_PADDING, PADDING + LABEL_PADDING, PADDING);
            g.drawLine(PADDING + LABEL_PADDING, height - PADDING - LABEL_PADDING, width - PADDING, height - PADDING - LABEL_PADDING);

            // Draw axis labels
            g.drawString(Y_AXIS_LABEL, PADDING, (height - graphHeight) / 2);
            g.drawString(X_AXIS_LABEL, (width - g.getFontMetrics().stringWidth(X_AXIS_LABEL)) / 2, height - PADDING);

            // Draw the line graph between points
            int numPoints = transactions.size();
            for (int i = 0; i < numPoints - 1; i++) {
                int x1 = PADDING + LABEL_PADDING + (i * graphWidth / (numPoints - 1));
                int x2 = PADDING + LABEL_PADDING + ((i + 1) * graphWidth / (numPoints - 1));
                int y1 = height - PADDING - LABEL_PADDING - (int)((transactions.get(i).costPerShare - minPrice) * graphHeight / (maxPrice - minPrice));
                int y2 = height - PADDING - LABEL_PADDING - (int)((transactions.get(i + 1).costPerShare - minPrice) * graphHeight / (maxPrice - minPrice));
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }

    // Custom cell renderer for transactions to add colored indicators
    private static class TransactionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Transaction) {
                Transaction transaction = (Transaction) value;
                label.setText(transaction.toString());
                // Add a small indicator color circle
                label.setIcon(new IndicatorIcon(transaction.bot));
            }
            return label;
        }
    }

    // Custom icon that changes color based on whether it's a bot or not
    private static class IndicatorIcon implements Icon {
        private final int size = 10;
        private final boolean bot;

        public IndicatorIcon(boolean bot) {
            this.bot = bot;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(bot ? Color.GREEN : Color.RED);
            g.fillOval(x, y, size, size);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    public ClientV1() {
        // Initialize main frame
        setTitle("Stock Market Analysis");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));

        // Left Panel: Sorting buttons, search bar, and transactions list
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(400, 600));

        // Sorting buttons
        JPanel sortButtons = new JPanel(new GridLayout(1, 4, 5, 0));
        JButton sortByBot = new JButton("Bot or not");
        JButton sortByCost = new JButton("Cost/Share");
        JButton sortBySize = new JButton("Order Size");
        JButton sortByDateTime = new JButton("Date/Time");
        sortByBot.addActionListener(e -> sortByBot());
        sortByCost.addActionListener(e -> sortByCostPerShare());
        sortBySize.addActionListener(e -> sortByOrderSize());
        sortByDateTime.addActionListener(e -> sortByDateTime());
        sortButtons.add(sortByBot);
        sortButtons.add(sortByCost);
        sortButtons.add(sortBySize);
        sortButtons.add(sortByDateTime);

        // Search bar
        JPanel searchBarPanel = new JPanel(new BorderLayout(5, 5));
        searchBar = new JTextField();
        searchBar.setToolTipText("Search by Ticker Symbol...");
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> {
            String query = searchBar.getText().trim().toUpperCase();
            filterTransactions(query);
        });
        searchBarPanel.add(searchBar, BorderLayout.CENTER);
        searchBarPanel.add(searchButton, BorderLayout.EAST);

        // List of transactions
        listModel = new DefaultListModel<>();
        transactionsList = new JList<>(listModel);
        transactionsList.setCellRenderer(new TransactionCellRenderer());
        transactionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transactionsList.setVisibleRowCount(-1);
        transactionsList.addListSelectionListener(e -> updateDetailsPanel(transactionsList.getSelectedIndex()));
        JScrollPane transactionScrollPane = new JScrollPane(transactionsList);       
        
        // Combine sorting buttons, search bar, and transaction list
        JPanel sortingAndSearchPanel = new JPanel(new BorderLayout(5, 5));
        sortingAndSearchPanel.add(sortButtons, BorderLayout.NORTH);
        sortingAndSearchPanel.add(searchBarPanel, BorderLayout.SOUTH);
        leftPanel.add(sortingAndSearchPanel, BorderLayout.NORTH);
        leftPanel.add(transactionScrollPane, BorderLayout.CENTER);

        // Right Panel: Graph and transaction details
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(400, 600));

        // Placeholder for graph
        graphPanel = new StockGraphPanel();
        graphPanel.setGraphVisible(false);

        // Placeholder for details
        detailsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        detailsPanel.add(new JLabel("Select a transaction to see details."));

        JScrollPane detailsScrollPane = new JScrollPane(detailsPanel);

        // "Back" button to clear selection and return to default screen
        JButton backButton = new JButton("Back");
        backButton.addActionListener(e -> {
            transactionsList.clearSelection();
            updateDetailsPanel(-1);
        });
        JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        backButtonPanel.add(backButton);

        rightPanel.add(graphPanel, BorderLayout.NORTH);
        rightPanel.add(detailsScrollPane, BorderLayout.CENTER);
        rightPanel.add(backButtonPanel, BorderLayout.SOUTH);

        // Add left and right panels to the frame
        add(leftPanel);
        add(rightPanel);
        
        // Fetch data from the server
        fetchTransactionsFromServer();
    }
    
    // Function to fetch transactions from the server and populate the list
    private void fetchTransactionsFromServer() {
        String serverAddress = "localhost"; // Update with actual server address if necessary
        int serverPort = 12345; // Ensure the server port matches your server configuration

        try (Socket socket = new Socket(serverAddress, serverPort);
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Read the list of transactions from the server
            List<Transaction> transactions = (List<Transaction>) in.readObject();

            // Update the UI list model with the transactions received
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                for (Transaction transaction : transactions) {
                    listModel.addElement(transaction);
                }
                graphPanel.setTransactions(transactions);
            });

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Unable to fetch transactions from server.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Updates the details panel with data from the selected transaction
    private void updateDetailsPanel(int index) {
        detailsPanel.removeAll();
        if (index >= 0 && index < listModel.size()) {
            Transaction transaction = listModel.get(index);
            detailsPanel.add(new JLabel("Transaction Details:"));
            detailsPanel.add(new JLabel("Ticker: " + transaction.ticker));
            detailsPanel.add(new JLabel("Bot: " + (transaction.bot ? "Yes" : "No")));
            detailsPanel.add(new JLabel("Cost/Share: $" + transaction.costPerShare));
            detailsPanel.add(new JLabel("Order Size: " + transaction.orderSize));
            detailsPanel.add(new JLabel("Date/Time: " + transaction.dateTime));
            detailsPanel.add(new JLabel("Transaction ID: " + (1000 + index)));
            detailsPanel.add(new JLabel("Company Name: Apple Inc."));
            detailsPanel.add(new JLabel("Additional metrics can go here."));

            graphPanel.setGraphVisible(true);
        } else {
            detailsPanel.add(new JLabel("Select a transaction to see details."));
            graphPanel.setGraphVisible(false);
        }
        detailsPanel.revalidate();
        detailsPanel.repaint();
    }

    // Filters transactions based on a ticker symbol query
    private void filterTransactions(String query) {
        DefaultListModel<Transaction> filteredModel = new DefaultListModel<>();
        for (int i = 0; i < listModel.size(); i++) {
            Transaction transaction = listModel.get(i);
            if (transaction.ticker.contains(query)) {
                filteredModel.addElement(transaction);
            }
        }
        transactionsList.setModel(filteredModel);
    }

    // Sorting by different attributes
    private void sortByBot() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            transactions.add(listModel.get(i));
        }

        Comparator<Transaction> comparator = Comparator.comparing(t -> t.bot);
        transactions.sort(sortBotAsc ? comparator : comparator.reversed());
        sortBotAsc = !sortBotAsc;

        listModel.clear();
        for (Transaction t : transactions) {
            listModel.addElement(t);
        }
    }

    private void sortByCostPerShare() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            transactions.add(listModel.get(i));
        }

        Comparator<Transaction> comparator = Comparator.comparingDouble(t -> t.costPerShare);
        transactions.sort(sortCostAsc ? comparator : comparator.reversed());
        sortCostAsc = !sortCostAsc;

        listModel.clear();
        for (Transaction t : transactions) {
            listModel.addElement(t);
        }
    }

    private void sortByOrderSize() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            transactions.add(listModel.get(i));
        }

        Comparator<Transaction> comparator = Comparator.comparingInt(t -> t.orderSize);
        transactions.sort(sortSizeAsc ? comparator : comparator.reversed());
        sortSizeAsc = !sortSizeAsc;

        listModel.clear();
        for (Transaction t : transactions) {
            listModel.addElement(t);
        }
    }

    private void sortByDateTime() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            transactions.add(listModel.get(i));
        }

        Comparator<Transaction> comparator = Comparator.comparing(t -> t.dateTime);
        transactions.sort(sortDateAsc ? comparator : comparator.reversed());
        sortDateAsc = !sortDateAsc;

        listModel.clear();
        for (Transaction t : transactions) {
            listModel.addElement(t);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientV1 app = new ClientV1();
            app.setVisible(true);
        });
    }
}
