/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.stockv1;

/**
 *
 * @author hemgr
 */


/**
 *
 * @author brian
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class ClientV1 extends JFrame {
    private JList<Transaction> transactionsList;
    private final DefaultListModel<Transaction> listModel;
    private final JPanel detailsPanel;
    private final ProbabilityGraphPanel graphPanel;
    private JTextField searchBar;
    private boolean sortProbAsc = false;
    private boolean sortCostAsc = false;
    private boolean sortSizeAsc = false;
    private boolean sortDateAsc = false;

    // Mock transaction class to hold relevant data
    private static class Transaction {
        String ticker;
        int probability; // 0-100
        double costPerShare;
        int orderSize;
        String dateTime;
        boolean highProbability; // for indicator color

        public Transaction(String ticker, int probability, double costPerShare, int orderSize, String dateTime, boolean highProbability) {
            this.ticker = ticker;
            this.probability = probability;
            this.costPerShare = costPerShare;
            this.orderSize = orderSize;
            this.dateTime = dateTime;
            this.highProbability = highProbability;
        }

        @Override
        public String toString() {
            return String.format("%s | Prob: %d%% | Cost/Share: $%.2f | Size: %d | Date/Time: %s",
                    ticker, probability, costPerShare, orderSize, dateTime);
        }
    }

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
            g.drawString("Probability Distribution Graph", 140, 20);

            // Simulate a normal distribution curve
            g.setColor(Color.BLUE);
            // int[] xPoints = {110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310};
            int[] xPoints = {115, 125, 135, 145, 155, 165, 175, 185, 195, 205, 215, 225, 235, 245, 255, 265, 275, 285, 295, 305, 315};
            int[] yPoints = {190, 180, 170, 155, 135, 120, 100, 90, 70, 60, 55, 60, 70, 90, 100, 120, 135, 155, 170, 180, 190};
            g.drawPolyline(xPoints, yPoints, xPoints.length);
        }
    }

    // Custom cell renderer for transactions to add colored indicators
    private static class TransactionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Transaction transaction) {
                label.setText(transaction.toString());
                label.setIcon(new IndicatorIcon(transaction.probability));
            }
            return label;
        }
    }

    // Custom icon that changes color based on the probability value
    private static class IndicatorIcon implements Icon {
        private final int size = 10;
        private final int probability;

        public IndicatorIcon(int probability) {
            this.probability = probability;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(getColorByProbability(probability));
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

        // Determines color based on the probability
        private Color getColorByProbability(int probability) {
            if (probability >= 50) {
                int lightnessFactor = (int) ((probability - 50) * 5.1); // 0 to 255
                return new Color(255 - lightnessFactor, 255, 255 - lightnessFactor); // Light green to dark green
            } else {
                int lightnessFactor = (int) ((50 - probability) * 5.1); // 0 to 255
                return new Color(255, 255 - lightnessFactor, 255 - lightnessFactor); // Light red to dark red
            }
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
        JButton sortByProb = new JButton("Probability");
        JButton sortByCost = new JButton("Cost/Share");
        JButton sortBySize = new JButton("Order Size");
        JButton sortByDateTime = new JButton("Date/Time");
        sortByProb.addActionListener(e -> sortByProbability());
        sortByCost.addActionListener(e -> sortByCostPerShare());
        sortBySize.addActionListener(e -> sortByOrderSize());
        sortByDateTime.addActionListener(e -> sortByDateTime());
        sortButtons.add(sortByProb);
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
        for (int i = 1; i <= 10; i++) {
            boolean highProbability = (i * 10) > 50;
            listModel.addElement(new Transaction("ABC" + i, i * 10, 20.0 + i, 100 + i * 10, "2024-05-01 14:00", highProbability));
        }

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
    }

    // Updates the details panel with data from the selected transaction
    private void updateDetailsPanel(int index) {
        detailsPanel.removeAll();
        if (index >= 0 && index < listModel.size()) {
            Transaction transaction = listModel.get(index);
            detailsPanel.add(new JLabel("Transaction Details:"));
            detailsPanel.add(new JLabel("Ticker: " + transaction.ticker));
            detailsPanel.add(new JLabel("Probability: " + transaction.probability + "%"));
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
    private void sortByProbability() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            transactions.add(listModel.get(i));
        }

        Comparator<Transaction> comparator = Comparator.comparingInt(t -> t.probability);
        transactions.sort(sortProbAsc ? comparator : comparator.reversed());
        sortProbAsc = !sortProbAsc;

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