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
    private ProbabilityGraphPanel graphPanel;
    private JTextField searchBar;
    private boolean sortCostAsc = false;
    private boolean sortSizeAsc = false;
    private boolean sortDateAsc = false;
    private boolean sortBotAsc = false;

    // Custom panel to represent the placeholder graph
    private static class ProbabilityGraphPanel extends JPanel {
        private boolean visible = true;

        public ProbabilityGraphPanel() {
            setPreferredSize(new Dimension(400, 200));
            setBorder(BorderFactory.createLineBorder(Color.BLACK));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (visible) {
                drawPlaceholderGraph(g);
            }
        }

        public void setGraphVisible(boolean visible) {
            this.visible = visible;
            repaint();
        }

        private void drawPlaceholderGraph(Graphics g) {
            g.setColor(Color.BLACK);
            g.drawString("Probability Distribution Graph", 110, 20);

            // Simulate a normal distribution curve
            g.setColor(Color.BLUE);
            int[] xPoints = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340, 350, 360, 370, 380, 390};
            int[] yPoints = {190, 180, 170, 155, 135, 120, 100, 90, 70, 60, 55, 60, 70, 90, 100, 120, 135, 155, 170, 180, 190, 180, 170, 155, 135, 120, 100, 90, 70, 60, 55, 60, 70, 90, 100, 120, 135, 155, 170};
            g.drawPolyline(xPoints, yPoints, xPoints.length);
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
        transactionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transactionsList.setVisibleRowCount(-1);
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
        graphPanel = new ProbabilityGraphPanel();
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
            detailsPanel.add(new JLabel("Company Name: ABC Corporation"));
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
