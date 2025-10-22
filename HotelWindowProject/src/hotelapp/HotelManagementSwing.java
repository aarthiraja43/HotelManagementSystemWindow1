
package hotelapp;


import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Vector;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Font;


public class HotelManagementSwing {

    // Database credentials - adjust as needed
    private static final String URL = "jdbc:mysql://localhost:3306/hotel_management";
    private static final String USER = "root";
    private static final String PASSWORD = "system";

    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JTextField custNameField, custPhoneField, custRoomField, custCheckInField, custCheckOutField, custEmailField, roomTypeField, totalAmountField;
	

	// Admin components
    private JTable adminBookingTable;
    private DefaultTableModel adminBookingModel;
    private JTable staffTable;
    private DefaultTableModel staffModel;
    private String loggedCustomerEmail;


    // Staff components (can reuse booking table)
    private JTable staffBookingTable;
    private DefaultTableModel staffBookingModel;

    // Customer components
    private JTextField custNameField1, custPhoneField1, custRoomField1, custCheckInField1, custCheckOutField1, custEmailField1;

    // Logged role
    private String loggedRole;

    public static void main(String[] args) {
    	SwingUtilities.invokeLater(() -> new HotelManagementSwing().showLogin());
        
        
    }
    


    private void showLogin() {
        JDialog dlg = new JDialog((Frame) null, "Login", true);
        dlg.setSize(420, 260);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        dlg.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> roleBox = new JComboBox<>(new String[]{"Admin","Staff","Customer"});
        dlg.add(roleBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        dlg.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        JTextField emailField = new JTextField();
        dlg.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        dlg.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        JPasswordField passField = new JPasswordField();
        dlg.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        JButton loginBtn = new JButton("Login");
        dlg.add(loginBtn, gbc);

        gbc.gridx = 1;
        JButton cancelBtn = new JButton("Cancel");
        dlg.add(cancelBtn, gbc);

        JLabel msg = new JLabel();
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        dlg.add(msg, gbc);

        loginBtn.addActionListener(e -> {
            String role = (String) roleBox.getSelectedItem();
            String email = emailField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            if (email.isEmpty() || password.isEmpty()) {
                msg.setText("Please enter email and password.");
                return;
            }
            boolean ok = checkLogin(role, email, password);
            if (ok) {
                loggedRole = role;
                if (role.equalsIgnoreCase("Customer")) {
                    loggedCustomerEmail = email;  // store customer's email
                }
                dlg.dispose();
                createAndShowGUI();
            }
 else {
                msg.setText("Invalid credentials for role " + role);
            }
        });

        cancelBtn.addActionListener(e -> System.exit(0));

        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    private boolean checkLogin(String role, String email, String password) {
        String table = role.equalsIgnoreCase("Admin") ? "admin" : role.equalsIgnoreCase("Staff") ? "staff" : "";
        if (role.equalsIgnoreCase("Customer")) {
            // For customer, there may not be a login table; allow guest-like access
            return true; // open customer tab without DB credentials
        }
        if (table.isEmpty()) return false;
        String query = "SELECT * FROM " + table + " WHERE email=? AND password=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, email);
            stmt.setString(2, hashPassword(password));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void createAndShowGUI() {
        frame = new JFrame("AR Hotel Management System - Swing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();

        JPanel adminPanel = buildAdminPanel();
        JPanel staffPanel = buildStaffPanel();
        JPanel customerPanel = buildCustomerPanel();

        tabbedPane.addTab("Admin", adminPanel);
        tabbedPane.addTab("Staff", staffPanel);
        tabbedPane.addTab("Customer", customerPanel);

        // Enable tabs based on logged role
        if ("Admin".equalsIgnoreCase(loggedRole)) {
            tabbedPane.setEnabledAt(0, true);
            tabbedPane.setSelectedIndex(0);
        } else tabbedPane.setEnabledAt(0, false);

        if ("Staff".equalsIgnoreCase(loggedRole)) {
            tabbedPane.setEnabledAt(1, true);
            tabbedPane.setSelectedIndex(1);
        } else tabbedPane.setEnabledAt(1, false);

        // Customers can access tab 2
        tabbedPane.setEnabledAt(2, true);
        if ("Customer".equalsIgnoreCase(loggedRole)) tabbedPane.setSelectedIndex(2);

        frame.getContentPane().add(tabbedPane);
        frame.setVisible(true);

        // initial loads
        loadBookingsToTable(adminBookingModel);
        loadBookingsToTable(staffBookingModel);
        loadStaffsToTable();
    }

    // ------------------ Admin Tab ------------------
    private JPanel buildAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Top: Buttons
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBookingsBtn = new JButton("Refresh Bookings");
        JButton generateBillBtn = new JButton("Generate Bill (selected)");
        JButton deleteBookingBtn = new JButton("Delete Booking (selected)");
        top.add(refreshBookingsBtn);
        top.add(generateBillBtn);
        top.add(deleteBookingBtn);

        panel.add(top, BorderLayout.NORTH);

        // center: split pane bookings / staff management
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Bookings table
        adminBookingModel = new DefaultTableModel(new String[]{"ID","Customer","Room","Phone","Check-in","Check-out", "email"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        adminBookingTable = new JTable(adminBookingModel);
        JScrollPane bookingScroll = new JScrollPane(adminBookingTable);
        split.setTopComponent(bookingScroll);

        // Staff panel (below)
        JPanel staffPanel = new JPanel(new BorderLayout());
        staffModel = new DefaultTableModel(new String[]{"ID","Name","Email","Role","Phone","Salary","Password"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        staffTable = new JTable(staffModel);
        staffPanel.add(new JScrollPane(staffTable), BorderLayout.CENTER);

        // Staff controls
        JPanel staffControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStaffBtn = new JButton("Add Staff");
        JButton updateStaffBtn = new JButton("Update Staff (selected)");
        JButton deleteStaffBtn = new JButton("Delete Staff (selected)");
        staffControls.add(addStaffBtn);
        staffControls.add(updateStaffBtn);
        staffControls.add(deleteStaffBtn);
        staffPanel.add(staffControls, BorderLayout.SOUTH);

        split.setBottomComponent(staffPanel);
        split.setDividerLocation(280);

        panel.add(split, BorderLayout.CENTER);

        // Actions
        refreshBookingsBtn.addActionListener(e -> loadBookingsToTable(adminBookingModel));
        generateBillBtn.addActionListener(e -> {
            int sel = adminBookingTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select a booking first."); return; }
            int id = Integer.parseInt(adminBookingModel.getValueAt(sel,0).toString());
            generateBillForId(id);
        });
        deleteBookingBtn.addActionListener(e -> {
            int sel = adminBookingTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select a booking first."); return; }
            int id = Integer.parseInt(adminBookingModel.getValueAt(sel,0).toString());
            deleteBookingById(id);
            loadBookingsToTable(adminBookingModel);
            loadBookingsToTable(staffBookingModel);
        });

        addStaffBtn.addActionListener(e -> showAddStaffDialog());
        updateStaffBtn.addActionListener(e -> {
            int sel = staffTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select staff row to update."); return; }
            int id = Integer.parseInt(staffModel.getValueAt(sel,0).toString());
            showUpdateStaffDialog(id);
        });
        deleteStaffBtn.addActionListener(e -> {
            int sel = staffTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select staff to delete."); return; }
            int id = Integer.parseInt(staffModel.getValueAt(sel,0).toString());
            deleteStaffById(id);
            loadStaffsToTable();
        });

        return panel;
    }

    // ------------------ Staff Tab ------------------
    private JPanel buildStaffPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh Bookings");
        JButton genBillBtn = new JButton("Generate Bill (selected)");
        top.add(refreshBtn); top.add(genBillBtn);
        panel.add(top, BorderLayout.NORTH);

        staffBookingModel = new DefaultTableModel(new String[]{"ID","Customer","Room","Phone","Check-in","Check-out"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        staffBookingTable = new JTable(staffBookingModel);
        panel.add(new JScrollPane(staffBookingTable), BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> loadBookingsToTable(staffBookingModel));
        genBillBtn.addActionListener(e -> {
            int sel = staffBookingTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select a booking first."); return; }
            int id = Integer.parseInt(staffBookingModel.getValueAt(sel,0).toString());
            generateBillForId(id);
        });

        return panel;
    }

    // ------------------ Customer Tab ------------------
    private JPanel buildCustomerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("Customer Name:"), gbc);
        gbc.gridx = 1; custNameField = new JTextField(20); form.add(custNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1; custPhoneField = new JTextField(20); form.add(custPhoneField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Room Type (Deluxe/Standard):"), gbc);
        gbc.gridx = 1; custRoomField = new JTextField(20); form.add(custRoomField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel("Check-in (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; custCheckInField = new JTextField(20); form.add(custCheckInField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; form.add(new JLabel("Check-out (YYYY-MM-DD):"), gbc);
        gbc.gridx = 1; custCheckOutField = new JTextField(20); form.add(custCheckOutField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 5; form.add(new JLabel("Customer Email:"), gbc);
        gbc.gridx = 1; custEmailField = new JTextField(20); form.add(custEmailField, gbc);
        
        if ("Customer".equalsIgnoreCase(loggedRole) && loggedCustomerEmail != null) {
            custEmailField.setText(loggedCustomerEmail);
            custEmailField.setEditable(false);
        }


        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        JButton addBookingBtn = new JButton("Add Booking");
        form.add(addBookingBtn, gbc);

        panel.add(form, BorderLayout.NORTH);

        // below: bookings table for the customer to see
        DefaultTableModel custModel = new DefaultTableModel(new String[]{"ID","Customer","Room","Phone","Check-in","Check-out"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable custTable = new JTable(custModel);
        panel.add(new JScrollPane(custTable), BorderLayout.CENTER);

        // allow customer to download bill for selected booking
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton downloadBtn = new JButton("Download Bill (selected)");
        bottom.add(downloadBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        addBookingBtn.addActionListener(e -> {
            addBookingFromCustomer();
            loadBookingsToTable(adminBookingModel);
            loadBookingsToTable(staffBookingModel);
            // refresh customer view
            loadBookingsToModel(custModel);
        });

        downloadBtn.addActionListener(e -> {
            int sel = custTable.getSelectedRow();
            if (sel == -1) { JOptionPane.showMessageDialog(frame, "Select a booking first."); return; }
            int id = Integer.parseInt(custModel.getValueAt(sel,0).toString());
            generateBillForId(id);
        });

        // initial load for customer
        loadBookingsToModel(custModel);

        return panel;
    }

    // ------------------ DB Actions ------------------
    private void loadBookingsToTable(DefaultTableModel model) {
        model.setRowCount(0);
        String sql = "SELECT * FROM bookings ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("customer_name"));
                row.add(rs.getString("room_type"));
                row.add(rs.getString("phone"));
                row.add(rs.getDate("check_in"));
                row.add(rs.getDate("check_out"));
                row.add(rs.getString("email"));
                model.addRow(row);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadBookingsToModel(DefaultTableModel model) {
        model.setRowCount(0);
        String sql;

        // if customer logged in, show only their bookings
        if ("Customer".equalsIgnoreCase(loggedRole) && loggedCustomerEmail != null) {
            sql = "SELECT * FROM bookings WHERE email=? ORDER BY id DESC";
        } else {
            sql = "SELECT * FROM bookings ORDER BY id DESC";
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if ("Customer".equalsIgnoreCase(loggedRole) && loggedCustomerEmail != null) {
                ps.setString(1, loggedCustomerEmail);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("customer_name"));
                    row.add(rs.getString("room_type"));
                    row.add(rs.getString("phone"));
                    row.add(rs.getDate("check_in"));
                    row.add(rs.getDate("check_out"));
                    model.addRow(row);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }


    private void loadStaffsToTable() {
        staffModel.setRowCount(0);
        String sql = "SELECT id,name,email,role,phone,salary,password FROM staff ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("name"));
                row.add(rs.getString("email"));
                row.add(rs.getString("role"));
                row.add(rs.getString("phone"));
                row.add(rs.getDouble("salary"));
                row.add(rs.getString("password"));
                staffModel.addRow(row);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
    
   

    private void addBookingFromCustomer() {
        String name = custNameField.getText().trim();
        String phone = custPhoneField.getText().trim();
        String room = custRoomField.getText().trim();
        String checkIn = custCheckInField.getText().trim();
        String checkOut = custCheckOutField.getText().trim();
        String email = custEmailField.getText().trim();

        if (name.isEmpty() || phone.isEmpty() || room.isEmpty() || checkIn.isEmpty() || checkOut.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please fill all details.");
            return;
        }

        String sql = "INSERT INTO bookings(customer_name, room_type, phone, check_in, check_out, email) VALUES(?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, room);
            ps.setString(3, phone);
            ps.setDate(4, Date.valueOf(checkIn));
            ps.setDate(5, Date.valueOf(checkOut));
            ps.setString(6, email);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(frame, "Booking added successfully!");
            // clear fields
            custNameField.setText(""); custPhoneField.setText(""); custRoomField.setText(""); custCheckInField.setText(""); custCheckOutField.setText(""); custEmailField.setText("");
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error adding booking: " + ex.getMessage());
        }
    }

    private void deleteBookingById(int id) {
        String sql = "DELETE FROM bookings WHERE id=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            JOptionPane.showMessageDialog(frame, rows>0?"Booking deleted":"Booking not found");
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void generateBillForId(int id) {
        String sql = "SELECT * FROM bookings WHERE id=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String fileName = "Bill_" + id + ".pdf";
                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(fileName));
                document.open();
                Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
                Paragraph title = new Paragraph("Hotel Stay Bill", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);
                document.add(new Paragraph("------------------------------------------------------------"));
                document.add(new Paragraph("\n"));

                document.add(new Paragraph("Booking ID: " + rs.getInt("id")));
                document.add(new Paragraph("Customer Name: " + rs.getString("customer_name")));
                document.add(new Paragraph("Room Type: " + rs.getString("room_type")));
                document.add(new Paragraph("Check-in: " + rs.getDate("check_in")));
                document.add(new Paragraph("Check-out: " + rs.getDate("check_out")));
                document.add(new Paragraph("Phone: " + rs.getString("phone")));
                document.add(new Paragraph("Email:" +rs.getString("email")));
                document.add(new Paragraph("\n"));

                int pricePerDay = rs.getString("room_type").equalsIgnoreCase("Deluxe") ? 1000 : 700;
                long days = (rs.getDate("check_out").getTime() - rs.getDate("check_in").getTime()) / (1000L * 60 * 60 * 24);
                int totalAmount = (int) (days * pricePerDay);

                document.add(new Paragraph("Room Price per Day: ₹" + pricePerDay));
                document.add(new Paragraph("Total Days: " + days));
                document.add(new Paragraph("------------------------------------------------------------"));
                document.add(new Paragraph("Total Amount: ₹" + totalAmount));
                document.add(new Paragraph("\nThank you for staying with us!"));

                document.close();
                JOptionPane.showMessageDialog(frame, "Bill generated: " + fileName);
            } else {
                JOptionPane.showMessageDialog(frame, "Booking ID not found.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error generating bill: " + ex.getMessage());
        }
    }

    // ------------------ Staff CRUD ------------------
    private void showAddStaffDialog() {
        JDialog dlg = new JDialog(frame, "Add Staff", true);
        dlg.setSize(400,350);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx=0; gbc.gridy=0; dlg.add(new JLabel("Name:"), gbc); gbc.gridx=1; JTextField nameF=new JTextField(20); dlg.add(nameF,gbc);
        gbc.gridx=0; gbc.gridy=1; dlg.add(new JLabel("Email:"), gbc); gbc.gridx=1; JTextField emailF=new JTextField(20); dlg.add(emailF,gbc);
        gbc.gridx=0; gbc.gridy=2; dlg.add(new JLabel("Password:"), gbc); gbc.gridx=1; JPasswordField passF=new JPasswordField(20); dlg.add(passF,gbc);
        gbc.gridx=0; gbc.gridy=3; dlg.add(new JLabel("Role:"), gbc); gbc.gridx=1; JTextField roleF=new JTextField(20); dlg.add(roleF,gbc);
        gbc.gridx=0; gbc.gridy=4; dlg.add(new JLabel("Phone:"), gbc); gbc.gridx=1; JTextField phoneF=new JTextField(20); dlg.add(phoneF,gbc);
        gbc.gridx=0; gbc.gridy=5; dlg.add(new JLabel("Salary:"), gbc); gbc.gridx=1; JTextField salF=new JTextField(20); dlg.add(salF,gbc);

        gbc.gridx=0; gbc.gridy=6; gbc.gridwidth=2; JButton addBtn=new JButton("Add Staff"); dlg.add(addBtn,gbc);

        addBtn.addActionListener(e -> {
            String name=nameF.getText().trim(); String email=emailF.getText().trim(); String pass=new String(passF.getPassword()).trim();
            String role=roleF.getText().trim(); String phone=phoneF.getText().trim(); double salary=0;
            try { salary = Double.parseDouble(salF.getText().trim()); } catch (Exception ex) {}
            if (name.isEmpty()||email.isEmpty()||pass.isEmpty()) { JOptionPane.showMessageDialog(dlg,"Name, Email and Password required"); return; }
            String sql = "INSERT INTO staff(name,email,password,role,phone,salary) VALUES(?,?,?,?,?,?)";
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1,name);
                ps.setString(2,email);
                ps.setString(3,hashPassword(pass));
                ps.setString(4,role);
                ps.setString(5,phone);
                ps.setDouble(6,salary);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(dlg,"Staff added successfully");
                dlg.dispose();
                loadStaffsToTable();
            } catch (SQLException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(dlg,"Error: "+ex.getMessage()); }
        });

        dlg.setLocationRelativeTo(frame); dlg.setVisible(true);
    }

    private void showUpdateStaffDialog(int id) {
        // fetch record
        String sql = "SELECT * FROM staff WHERE id=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { JOptionPane.showMessageDialog(frame,"Staff not found"); return; }

            JDialog dlg = new JDialog(frame,"Update Staff", true);
            dlg.setSize(420,360);
            dlg.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6,6,6,6);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;


            gbc.gridx=0; gbc.gridy=0; dlg.add(new JLabel("Name:"),gbc); gbc.gridx=1; JTextField nameF=new JTextField(rs.getString("name"),20); dlg.add(nameF,gbc);
            gbc.gridx=0; gbc.gridy=1; dlg.add(new JLabel("Email:"),gbc); gbc.gridx=1; JTextField emailF=new JTextField(rs.getString("email"),20); dlg.add(emailF,gbc);
            gbc.gridx=0; gbc.gridy=2; dlg.add(new JLabel("Password (leave blank to keep):"),gbc); gbc.gridx=1; JPasswordField passF=new JPasswordField(20); dlg.add(passF,gbc);
            gbc.gridx=0; gbc.gridy=3; dlg.add(new JLabel("Role:"),gbc); gbc.gridx=1; JTextField roleF=new JTextField(rs.getString("role"),20); dlg.add(roleF,gbc);
            gbc.gridx=0; gbc.gridy=4; dlg.add(new JLabel("Phone:"),gbc); gbc.gridx=1; JTextField phoneF=new JTextField(rs.getString("phone"),20); dlg.add(phoneF,gbc);
            gbc.gridx=0; gbc.gridy=5; dlg.add(new JLabel("Salary:"),gbc); gbc.gridx=1; JTextField salF=new JTextField(String.valueOf(rs.getDouble("salary")),20); dlg.add(salF,gbc);

            gbc.gridx=0; gbc.gridy=6; gbc.gridwidth=2; JButton updBtn=new JButton("Update"); dlg.add(updBtn,gbc);

            updBtn.addActionListener(ev -> {
                try {
                    String name = nameF.getText().trim();
                    String email = emailF.getText().trim();
                    String pass = new String(passF.getPassword()).trim();
                    String role = roleF.getText().trim();
                    String phone = phoneF.getText().trim();
                    double salary = Double.parseDouble(salF.getText().trim());

                    String updateSql;
                    if (pass.isEmpty()) {
                        updateSql = "UPDATE staff SET name=?, email=?, role=?, phone=?, salary=? WHERE id=?";
                        try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                            ups.setString(1, name);
                            ups.setString(2, email);
                            ups.setString(3, role);
                            ups.setString(4, phone);
                            ups.setDouble(5, salary);
                            ups.setInt(6, id);
                            int rows = ups.executeUpdate();
                            JOptionPane.showMessageDialog(dlg, rows>0?"Updated":"No changes made");
                        }
                    } else {
                        updateSql = "UPDATE staff SET name=?, email=?, password=?, role=?, phone=?, salary=? WHERE id=?";
                        try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                            ups.setString(1, name);
                            ups.setString(2, email);
                            ups.setString(3, hashPassword(pass));
                            ups.setString(4, role);
                            ups.setString(5, phone);
                            ups.setDouble(6, salary);
                            ups.setInt(7, id);
                            int rows = ups.executeUpdate();
                            JOptionPane.showMessageDialog(dlg, rows>0?"Updated":"No changes made");
                        }
                    }

                    dlg.dispose();
                    loadStaffsToTable();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage());
                }
            });

            dlg.setLocationRelativeTo(frame); dlg.setVisible(true);

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error fetching staff: " + ex.getMessage());
        }
    }

    private void deleteStaffById(int id) {
        String sql = "DELETE FROM staff WHERE id=?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            JOptionPane.showMessageDialog(frame, rows>0?"Staff deleted":"No such staff");
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    // ------------------ Utilities ------------------
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}




