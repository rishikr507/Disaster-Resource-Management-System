import java.io.*;
import java.net.*;

public class Headquarter {

    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static BufferedReader consoleReader;
    private static boolean isRegistered = false;
    private static boolean waitingForResponse = false;
    private static boolean responseReceived = false;

    public static void main(String[] args) {
        consoleReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.println("Connecting to Disaster Management Server...");
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start listener thread for incoming messages
            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        handleServerMessage(response);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            }).start();

            // Register HQ
            out.println("REGISTER_HQ|");

            // Show menu
            showMenu();

        } catch (IOException e) {
            System.out.println("Failed to connect to server: " + e.getMessage());
        }
    }

    private static void handleServerMessage(String message) {
        String[] parts = message.split("\\|");
        String command = parts[0];

        try {
            switch (command) {
                case "REGISTERED_HQ":
                    System.out.println("✓ Headquarters registered successfully!");
                    isRegistered = true;
                    break;
                case "ACTIVE_CAMPS":
                    responseReceived = true;
                    if (parts.length > 1) {
                        displayActiveCamps(parts[1]);
                    } else {
                        System.out.println("No active camps data received");
                    }
                    break;
                case "REQUESTS_LIST":
                    responseReceived = true;
                    if (parts.length > 1) {
                        displayRequests(parts[1]);
                    } else {
                        System.out.println("No requests data received");
                    }
                    break;
                case "LOGS_LIST":
                    responseReceived = true;
                    if (parts.length > 1) {
                        displayLogs(parts[1]);
                    } else {
                        System.out.println("No logs data received");
                    }
                    break;
                case "NEW_REQUEST":
                    System.out.println("🔔 New Request: " + parts[1]);
                    break;
                case "REQUEST_APPROVED":
                    responseReceived = true;
                    System.out.println("✓ " + parts[1]);
                    break;
                case "REQUEST_REJECTED":
                    responseReceived = true;
                    System.out.println("✓ " + parts[1]);
                    break;
                case "ERROR":
                    responseReceived = true;
                    System.out.println("✗ Error: " + parts[1]);
                    break;
                default:
                    responseReceived = true;
                    System.out.println("Unknown message: " + message);
            }
        } catch (Exception e) {
            responseReceived = true;
            System.out.println("Error processing message: " + e.getMessage());
        }
    }

    private static void showMenu() throws IOException {
        System.out.print("Waiting for server registration");

        while (!isRegistered) { 
            try {
                Thread.sleep(100);
                System.out.print(".");
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println(); // New line after waiting dots
        
        while (true) {
            System.out.println("\n=== HEADQUARTERS MENU ===");
            System.out.println("1. View Active Camp Sites");
            System.out.println("2. View All Resource Requests");
            System.out.println("3. View All Logs");
            System.out.println("4. Approve/Reject Request");
            System.out.println("5. Exit");
            System.out.print("Choose option: ");

            String choice = consoleReader.readLine();

            boolean success = false;
            switch (choice) {
                case "1":
                    success = viewActiveCamps();
                    break;
                case "2":
                    success = viewAllRequests();
                    break;
                case "3":
                    success = viewAllLogs();
                    break;
                case "4":
                    success = manageRequest();
                    break;
                case "5":
                    System.out.println("Disconnecting from server...");
                    if (socket != null && !socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Ignore during close
                        }
                    }
                    System.out.println("Disconnected successfully!");
                    System.exit(0); // Force exit the application
                    return;
                default:
                    System.out.println("Invalid option!");
            }
            
            if (success) {
                System.out.println("\nPress Enter to continue...");
                consoleReader.readLine();
            }
        }
    }

    private static boolean viewActiveCamps() {
        waitingForResponse = true;
        responseReceived = false;
        out.println("GET_ACTIVE_CAMPS|");
        
        System.out.print("Fetching active camps");
        return waitForResponse();
    }

    private static boolean viewAllRequests() {
        waitingForResponse = true;
        responseReceived = false;
        out.println("GET_REQUESTS|");
        
        System.out.print("Fetching resource requests");
        return waitForResponse();
    }

    private static boolean viewAllLogs() throws IOException {
        System.out.print("Enter camp number to view logs (0 for all): ");
        String input = consoleReader.readLine();
        try {
            int campNumber = Integer.parseInt(input);
            waitingForResponse = true;
            responseReceived = false;
            out.println("GET_LOGS|" + campNumber);
            
            System.out.print("Fetching logs");
            return waitForResponse();
        } catch (NumberFormatException e) {
            System.out.println("Invalid camp number! Please enter a valid number.");
            return false;
        }
    }

    private static boolean manageRequest() throws IOException {
        try {
            System.out.print("Enter request ID to manage: ");
            int requestId = Integer.parseInt(consoleReader.readLine());

            System.out.print("Approve (A) or Reject (R): ");
            String action = consoleReader.readLine().toUpperCase();

            if (action.equals("A") || action.equals("R")) {
                waitingForResponse = true;
                responseReceived = false;
                
                if (action.equals("A")) {
                    out.println("APPROVE_REQUEST|" + requestId);
                    System.out.print("Approving request");
                } else if (action.equals("R")) {
                    out.println("REJECT_REQUEST|" + requestId);
                    System.out.print("Rejecting request");
                }
                return waitForResponse();
            } else {
                System.out.println("Invalid action! Please enter 'A' for Approve or 'R' for Reject.");
                return false;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid request ID! Please enter a valid number.");
            return false;
        }
    }

    private static boolean waitForResponse() {
        int waitCount = 0;
        int maxWaitTime = 50; // 5 seconds maximum wait
        
        while (waitingForResponse && !responseReceived && waitCount < maxWaitTime) {
            try {
                Thread.sleep(100);
                System.out.print(".");
                waitCount++;
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println(); // New line after waiting dots
        
        if (waitCount >= maxWaitTime) {
            System.out.println("⚠ Request timeout - no response from server");
            waitingForResponse = false;
            return false;
        }
        
        waitingForResponse = false;
        return true;
    }

    private static void displayActiveCamps(String campsData) {
        System.out.println("\n=== ACTIVE CAMPS ===");

        if (campsData == null || campsData.isEmpty() || campsData.trim().isEmpty()) {
            System.out.println("No active camps currently connected.");
            return;
        }

        String[] camps = campsData.split(";");
        int activeCount = 0;

        System.out.printf("%-25s %-15s\n", "Camp", "Status");
        System.out.println("--------------------------------------");

        for (String camp : camps) {
            if (camp != null && !camp.trim().isEmpty()) {
                activeCount++;
                String campInfo = camp.trim();
                System.out.printf("%-25s %-15s\n", campInfo, "Online");
            }
        }

        if (activeCount == 0) {
            System.out.println("No active camps currently connected.");
        } else {
            System.out.println("--------------------------------------");
            System.out.println("Total active camps: " + activeCount);
        }
    }

    private static void displayRequests(String requestsData) {
        System.out.println("\n=== RESOURCE REQUESTS ===");

        if (requestsData == null || requestsData.isEmpty() || requestsData.trim().isEmpty()) {
            System.out.println("No resource requests found.");
            return;
        }

        String[] requests = requestsData.split(";");
        int requestCount = 0;

        System.out.printf("%-12s %-15s %-15s %-10s %-10s %-12s %-20s\n",
                "Req ID", "Camp", "Resource", "Amount", "Deadline", "Status", "Requested At");
        System.out.println("------------------------------------------------------------------------------------------------------");

        for (String request : requests) {
            if (request != null && !request.trim().isEmpty()) {
                String[] details = request.split(",");
                if (details.length >= 7) {
                    requestCount++;
                    System.out.printf("%-12s %-15s %-15s %-10s %-10s %-12s %-20s\n",
                            details[0], details[1], details[2], details[3],
                            details[4], details[5], details[6]);
                }
            }
        }

        if (requestCount == 0) {
            System.out.println("No resource requests found.");
        } else {
            System.out.println("------------------------------------------------------------------------------------------------------");
            System.out.println("Total requests: " + requestCount);
        }
    }

    private static void displayLogs(String logsData) {
        System.out.println("\n=== SYSTEM LOGS ===");

        if (logsData == null || logsData.isEmpty() || logsData.trim().isEmpty()) {
            System.out.println("No logs found.");
            return;
        }

        String[] logs = logsData.split(";");
        int logCount = 0;

        for (String log : logs) {
            if (log != null && !log.trim().isEmpty()) {
                String[] details = log.split(",");
                if (details.length >= 3) {
                    logCount++;
                    System.out.println("Time: " + details[2]);
                    System.out.println("Type: " + details[0]);
                    System.out.println("Description: " + details[1]);
                    System.out.println("------------------------");
                }
            }
        }

        if (logCount == 0) {
            System.out.println("No logs found.");
        } else {
            System.out.println("Total logs displayed: " + logCount);
        }
    }
}