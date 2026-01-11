import java.io.*;
import java.net.*;

public class Camp {

    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static BufferedReader consoleReader;
    private static int campNumber;
    private static String campName;
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

            // Register camp
            registerCamp();

            // Show menu
            showMenu();

        } catch (IOException e) {
            System.out.println("Failed to connect to server: " + e.getMessage());
        }
    }

    private static void registerCamp() throws IOException {
        System.out.print("Enter camp name: ");
        campName = consoleReader.readLine();
        waitingForResponse = true;
        responseReceived = false;
        out.println("REGISTER_CAMP|" + campName);
        
        System.out.print("Registering camp");
        waitForResponse();
    }

    private static void handleServerMessage(String message) {
        String[] parts = message.split("\\|");
        String command = parts[0];

        switch (command) {
            case "REGISTERED_CAMP":
                campNumber = Integer.parseInt(parts[1]);
                responseReceived = true;
                System.out.println("\nSuccessfully registered as Camp " + campNumber + " - " + campName);
                break;
            case "RESOURCES_LIST":
                responseReceived = true;
                if (parts.length > 1) {
                    displayResources(parts[1]);
                } else {
                    System.out.println("No resources data received");
                }
                break;
            case "REQUEST_SENT":
                responseReceived = true;
                System.out.println("✓ Request sent successfully!");
                break;
            case "LOGS_LIST":
                responseReceived = true;
                if (parts.length > 1) {
                    displayLogs(parts[1]);
                } else {
                    System.out.println("No logs data received");
                }
                break;
            case "ACK_RECEIVED":
                responseReceived = true;
                System.out.println("✓ Acknowledgement from HQ: " + parts[1]);
                break;
            case "ERROR":
                responseReceived = true;
                System.out.println("✗ Error: " + parts[1]);
                break;
        }
    }

    private static void showMenu() throws IOException {
        // Wait for registration to complete
        while (campNumber == 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        
        while (true) {
            System.out.println("\n=== CAMP SITE MENU (Camp " + campNumber + " - " + campName + ") ===");
            System.out.println("1. Send Resource Request to HQ");
            System.out.println("2. View Available Resources");
            System.out.println("3. View Request Logs");
            System.out.println("4. Exit");
            System.out.print("Choose option: ");

            String choice = consoleReader.readLine();

            boolean success = false;
            switch (choice) {
                case "1":
                    success = sendResourceRequest();
                    break;
                case "2":
                    success = viewAvailableResources();
                    break;
                case "3":
                    success = viewLogs();
                    break;
                case "4":
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

    private static boolean sendResourceRequest() throws IOException {
        System.out.print("Enter resource name: ");
        String resource = consoleReader.readLine();

        System.out.print("Enter amount: ");
        int amount = Integer.parseInt(consoleReader.readLine());

        System.out.print("Enter deadline (days): ");
        int deadline = Integer.parseInt(consoleReader.readLine());

        waitingForResponse = true;
        responseReceived = false;
        out.println("SEND_REQUEST|" + resource + "|" + amount + "|" + deadline);
        
        System.out.print("Sending request to HQ");
        return waitForResponse();
    }

    private static boolean viewAvailableResources() {
        waitingForResponse = true;
        responseReceived = false;
        out.println("GET_RESOURCES|");
        
        System.out.print("Fetching available resources");
        return waitForResponse();
    }

    private static boolean viewLogs() {
        waitingForResponse = true;
        responseReceived = false;
        out.println("GET_LOGS|" + campNumber);
        
        System.out.print("Fetching logs");
        return waitForResponse();
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

    private static void displayResources(String resourcesData) {
        System.out.println("\n=== AVAILABLE RESOURCES ===");

        if (resourcesData == null || resourcesData.isEmpty() || resourcesData.trim().isEmpty()) {
            System.out.println("No resources available.");
            return;
        }

        String[] resources = resourcesData.split(";");
        System.out.printf("%-15s %-10s %-10s\n", "Resource", "Quantity", "Unit");
        System.out.println("-----------------------------------");

        for (String resource : resources) {
            if (!resource.isEmpty()) {
                String[] details = resource.split(",");
                System.out.printf("%-15s %-10s %-10s\n", details[0], details[1], details[2]);
            }
        }
    }

    private static void displayLogs(String logsData) {
        System.out.println("\n=== REQUEST LOGS ===");

        if (logsData == null || logsData.isEmpty() || logsData.trim().isEmpty()) {
            System.out.println("No logs found.");
            return;
        }

        String[] logs = logsData.split(";");

        for (String log : logs) {
            if (!log.isEmpty()) {
                String[] details = log.split(",");
                System.out.println("Time: " + details[2]);
                System.out.println("Action: " + details[0]);
                System.out.println("Description: " + details[1]);
                System.out.println("------------------------");
            }
        }
    }
}