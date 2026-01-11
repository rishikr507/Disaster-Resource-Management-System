
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private static final int PORT = 12345;
    private static Connection dbConnection;
    private static Map<Integer, ClientHandler> campClients = new ConcurrentHashMap<>();
    private static ClientHandler hqClient = null;

    public static void main(String[] args) {
        System.out.println("Disaster Resource Management Server Starting...");

        // Initialize database connection
        initializeDatabase();

        // Start server
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDatabase() {
        try {
            // MySQL Connector/J 9.x automatically loads the driver
            dbConnection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/disaster_management?useSSL=false&allowPublicKeyRetrieval=true",
                    "root",
                    "2004" // Change to your MySQL password
            );
            System.out.println("Database connected successfully!");
        } catch (Exception e) {
            System.out.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getDbConnection() {
        return dbConnection;
    }

    public static void registerCamp(int campNumber, ClientHandler handler) {
        campClients.put(campNumber, handler);
        System.out.println("Camp " + campNumber + " registered. Total camps: " + campClients.size());
    }

    public static void unregisterCamp(int campNumber) {
        campClients.remove(campNumber);
        System.out.println("Camp " + campNumber + " unregistered.");
    }

    public static void registerHQ(ClientHandler handler) {
        hqClient = handler;
        System.out.println("Headquarters registered.");
    }

    public static void unregisterHQ() {
        hqClient = null;
        System.out.println("Headquarters unregistered.");
    }

    public static void broadcastToCamps(String message) {
        for (ClientHandler client : campClients.values()) {
            client.sendMessage(message);
        }
    }

    public static void sendToHQ(String message) {
        if (hqClient != null) {
            hqClient.sendMessage(message);
        }
    }

    public static void sendToCamp(int campNumber, String message) {
        ClientHandler client = campClients.get(campNumber);
        if (client != null) {
            client.sendMessage(message);
        }
    }

    public static Set<Integer> getActiveCamps() {
        return campClients.keySet();
    }
}

class ClientHandler extends Thread {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private int campNumber = -1;
    private String clientType;
    private String campName;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
                processMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void processMessage(String message) {
        String[] parts = message.split("\\|");
        String command = parts[0];

        try {
            switch (command) {
                case "REGISTER_CAMP":
                    registerCamp(parts[1]);
                    break;
                case "REGISTER_HQ":
                    Server.registerHQ(this);
                    clientType = "HQ";
                    sendMessage("REGISTERED_HQ|Success");
                    break;
                case "GET_RESOURCES":
                    sendResources();
                    break;
                case "SEND_REQUEST":
                    sendRequest(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    break;
                case "GET_REQUESTS":
                    sendRequests();
                    break;
                case "GET_LOGS":
                    sendLogs(Integer.parseInt(parts[1]));
                    break;
                case "GET_ACTIVE_CAMPS":
                    sendActiveCamps();
                    break;
                case "APPROVE_REQUEST":
                    approveRequest(Integer.parseInt(parts[1]));
                    break;
                case "REJECT_REQUEST":
                    rejectRequest(Integer.parseInt(parts[1]));
                    break;
            }
        } catch (Exception e) {
            sendMessage("ERROR|" + e.getMessage());
        }
    }

    private void registerCamp(String name) {
        this.campName = name;
        this.clientType = "CAMP";

        try {
            Connection conn = Server.getDbConnection();

            // Insert camp
            String sql = "INSERT INTO camps (camp_name, camp_number) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, name);
            stmt.setInt(2, (int) (Math.random() * 1000) + 1);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                campNumber = rs.getInt(1);
                Server.registerCamp(campNumber, this);

                // Give initial resources to the camp
                giveInitialResources(conn, campNumber);

                sendMessage("REGISTERED_CAMP|" + campNumber);
                logAction("Camp registered: " + name);
            }
        } catch (SQLException e) {
            sendMessage("ERROR|Registration failed: " + e.getMessage());
        }
    }

    private void giveInitialResources(Connection conn, int campId) throws SQLException {
        // Give some initial resources to each new camp
        String[][] initialResources = {
            {"Water", "100", "liters"},
            {"Food", "50", "kgs"},
            {"Medicines", "20", "boxes"},
            {"Blankets", "30", "pieces"},
            {"Tents", "5", "units"},
            {"First Aid Kits", "10", "kits"}
        };

        String sql = "INSERT INTO resources (camp_id, resource_name, quantity, unit) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);

        for (String[] resource : initialResources) {
            stmt.setInt(1, campId);
            stmt.setString(2, resource[0]);
            stmt.setInt(3, Integer.parseInt(resource[1]));
            stmt.setString(4, resource[2]);
            stmt.addBatch();
        }
        stmt.executeBatch();
    }

    private void sendResources() {
        try {
            Connection conn = Server.getDbConnection();
            String sql = "SELECT resource_name, quantity, unit FROM resources WHERE camp_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, campNumber);
            ResultSet rs = stmt.executeQuery();

            StringBuilder resources = new StringBuilder("RESOURCES_LIST|");
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                resources.append(rs.getString("resource_name"))
                        .append(",").append(rs.getInt("quantity"))
                        .append(",").append(rs.getString("unit"))
                        .append(";");
            }

            if (!hasData) {
                sendMessage("RESOURCES_LIST|");
            } else {
                sendMessage(resources.toString());
            }
        } catch (SQLException e) {
            sendMessage("ERROR|Failed to fetch resources: " + e.getMessage());
        }
    }

    private void sendRequest(String resourceName, int amount, int deadlineDays) {
        try {
            Connection conn = Server.getDbConnection();

            // Insert request
            String sql = "INSERT INTO requests (camp_id, resource_name, amount, deadline_days) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, campNumber);
            stmt.setString(2, resourceName);
            stmt.setInt(3, amount);
            stmt.setInt(4, deadlineDays);
            stmt.executeUpdate();

            sendMessage("REQUEST_SENT|Success");
            logAction("Request sent: " + resourceName + " x" + amount);

            // Notify HQ
            Server.sendToHQ("NEW_REQUEST|Camp " + campNumber + " requested " + amount + " " + resourceName);
        } catch (SQLException e) {
            sendMessage("ERROR|Failed to send request: " + e.getMessage());
        }
    }

    private void sendRequests() {
        try {
            Connection conn = Server.getDbConnection();
            String sql = "SELECT r.request_id, c.camp_number, c.camp_name, r.resource_name, r.amount, r.deadline_days, r.status, r.requested_at "
                    + "FROM requests r "
                    + "JOIN camps c ON r.camp_id = c.camp_id "
                    + "ORDER BY r.requested_at DESC";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            StringBuilder requests = new StringBuilder("REQUESTS_LIST|");
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                requests.append(rs.getInt("request_id")).append(",")
                        .append(rs.getInt("camp_number")).append(" - ").append(rs.getString("camp_name")).append(",")
                        .append(rs.getString("resource_name")).append(",")
                        .append(rs.getInt("amount")).append(",")
                        .append(rs.getInt("deadline_days")).append(",")
                        .append(rs.getString("status")).append(",")
                        .append(rs.getTimestamp("requested_at"))
                        .append(";");
            }

            if (!hasData) {
                sendMessage("REQUESTS_LIST|");
            } else {
                sendMessage(requests.toString());
            }
        } catch (SQLException e) {
            sendMessage("REQUESTS_LIST|");
        }
    }

    private void sendLogs(int targetCampNumber) {
        try {
            Connection conn = Server.getDbConnection();
            String sql;
            PreparedStatement stmt;

            if (targetCampNumber == 0) {
                // All logs
                sql = "SELECT l.action_type, l.description, l.log_time, c.camp_name "
                        + "FROM logs l JOIN camps c ON l.camp_id = c.camp_id "
                        + "ORDER BY l.log_time DESC";
                stmt = conn.prepareStatement(sql);
            } else {
                // Specific camp logs
                sql = "SELECT action_type, description, log_time FROM logs WHERE camp_id = ? ORDER BY log_time DESC";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, targetCampNumber);
            }

            ResultSet rs = stmt.executeQuery();

            StringBuilder logs = new StringBuilder("LOGS_LIST|");
            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                logs.append(rs.getString("action_type")).append(",")
                        .append(rs.getString("description")).append(",")
                        .append(rs.getTimestamp("log_time"))
                        .append(";");
            }

            if (!hasData) {
                sendMessage("LOGS_LIST|");
            } else {
                sendMessage(logs.toString());
            }
        } catch (SQLException e) {
            sendMessage("LOGS_LIST|");
        }
    }

    private void sendActiveCamps() {
        try {
            Set<Integer> activeCamps = Server.getActiveCamps();
            if (activeCamps.isEmpty()) {
                sendMessage("ACTIVE_CAMPS|");
                return;
            }

            Connection conn = Server.getDbConnection();
            StringBuilder campIds = new StringBuilder();
            for (int campId : activeCamps) {
                if (campIds.length() > 0) {
                    campIds.append(",");
                }
                campIds.append(campId);
            }

            String sql = "SELECT camp_number, camp_name FROM camps WHERE camp_id IN (" + campIds + ")";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            StringBuilder camps = new StringBuilder("ACTIVE_CAMPS|");
            while (rs.next()) {
                camps.append(rs.getInt("camp_number"))
                        .append(" - ")
                        .append(rs.getString("camp_name"))
                        .append(";");
            }
            sendMessage(camps.toString());
        } catch (SQLException e) {
            sendMessage("ACTIVE_CAMPS|");
        }
    }

    private void approveRequest(int requestId) {
        try {
            Connection conn = Server.getDbConnection();

            // Get request details
            String sql = "SELECT r.camp_id, r.resource_name, r.amount "
                    + "FROM requests r "
                    + "WHERE r.request_id = ? AND r.status = 'PENDING'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int campId = rs.getInt("camp_id");
                String resourceName = rs.getString("resource_name");
                int amount = rs.getInt("amount");

                // HEADQUARTERS HAS UNLIMITED RESOURCES - Add to camp's inventory
                sql = "INSERT INTO resources (camp_id, resource_name, quantity, unit) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE quantity = quantity + ?";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, campId);
                stmt.setString(2, resourceName);
                stmt.setInt(3, amount);
                stmt.setString(4, getUnitForResource(resourceName));
                stmt.setInt(5, amount);
                stmt.executeUpdate();

                // Update request status
                sql = "UPDATE requests SET status = 'APPROVED', acknowledged_at = CURRENT_TIMESTAMP WHERE request_id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, requestId);
                stmt.executeUpdate();

                sendMessage("REQUEST_APPROVED|Request " + requestId + " approved successfully. " + amount + " " + resourceName + " sent to camp.");
                Server.sendToCamp(campId, "ACK_RECEIVED|Your request for " + amount + " " + resourceName + " has been APPROVED and added to your inventory");
                logAction(campId, "Request approved and resources delivered: " + resourceName + " x" + amount);

                System.out.println("Request " + requestId + " approved. Resources delivered to camp " + campId);
            } else {
                sendMessage("ERROR|Request not found or already processed");
            }
        } catch (SQLException e) {
            sendMessage("ERROR|Failed to approve request: " + e.getMessage());
        }
    }

    private void rejectRequest(int requestId) {
        try {
            Connection conn = Server.getDbConnection();

            // Get request details
            String sql = "SELECT camp_id FROM requests WHERE request_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int campId = rs.getInt("camp_id");

                // Update request status
                sql = "UPDATE requests SET status = 'REJECTED', acknowledged_at = CURRENT_TIMESTAMP WHERE request_id = ?";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, requestId);
                stmt.executeUpdate();

                sendMessage("REQUEST_REJECTED|Request " + requestId + " rejected");
                Server.sendToCamp(campId, "ACK_RECEIVED|Your request has been REJECTED");
                logAction(campId, "Request rejected");
            }
        } catch (SQLException e) {
            sendMessage("ERROR|Failed to reject request: " + e.getMessage());
        }
    }

    private String getUnitForResource(String resourceName) {
        // Define units for different resources
        switch (resourceName.toLowerCase()) {
            case "water":
                return "liters";
            case "food":
                return "kgs";
            case "medicines":
                return "boxes";
            case "blankets":
                return "pieces";
            case "tents":
                return "units";
            case "first aid kits":
                return "kits";
            default:
                return "units";
        }
    }

    private void logAction(String description) {
        logAction(campNumber, description);
    }

    private void logAction(int targetCampNumber, String description) {
        try {
            Connection conn = Server.getDbConnection();
            String sql = "INSERT INTO logs (camp_id, action_type, description) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, targetCampNumber);
            stmt.setString(2, "SYSTEM_ACTION");
            stmt.setString(3, description);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Failed to log action: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void cleanup() {
    	try {
        	// Proper cleanup order
        	if (clientType != null && clientType.equals("CAMP") && campNumber != -1) {
            		Server.unregisterCamp(campNumber);
            		System.out.println("Camp " + campNumber + " disconnected.");
        	} else if (clientType != null && clientType.equals("HQ")) {
            		Server.unregisterHQ();
            		System.out.println("Headquarters disconnected.");
        	}

        	// Close resources in reverse order
        	if (in != null) in.close();
        	if (out != null) out.close();
        	if (clientSocket != null && !clientSocket.isClosed()) {
            		clientSocket.close();
        	}
    	} catch (IOException e) {
        	System.out.println("Cleanup warning: " + e.getMessage());
    	}
    }
}
