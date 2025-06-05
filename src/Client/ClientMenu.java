package Client;

import Interface.AuthFactory;
import Interface.SessionFactory;

import java.rmi.Remote;
import java.util.Scanner;

public class ClientMenu {
    private final AuthFactory authService;

    public ClientMenu(AuthFactory authService) {
        this.authService = authService;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        int option;

        do {
            System.out.println("\n=== Welcome to Distributed Drive ===");
            System.out.println("[1] Register");
            System.out.println("[2] Login");
            System.out.println("[0] Exit");
            System.out.print("Choose an option: ");

            option = scanner.nextInt();
            scanner.nextLine();

            switch (option) {
                case 1 -> handleRegister(scanner);
                case 2 -> handleLogin(scanner);
                case 0 -> System.out.println("Exiting...");
                default -> System.out.println("Invalid option.");
            }

        } while (option != 0);
    }
    private void handleRegister(Scanner scanner) {
        System.out.print("Choose a username: ");
        String username = scanner.nextLine();
        System.out.print("Choose a password: ");
        String password = scanner.nextLine();

        try {
            boolean success = authService.register(username, password);
            if (success) {
                System.out.println("Registration successful!");
            } else {
                System.out.println("Error: Username already exists.");
            }
        } catch (Exception e) {
            System.err.println("Error communicating with the server: " + e.getMessage());
        }
    }

    private void handleLogin(Scanner scanner) {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        try {
            SessionFactory session = authService.login(username, password);
            if (session != null) {
                System.out.println("Login successful!");
                SessionMenu menu = new SessionMenu(username, session);
            } else {
                System.out.println("Invalid credentials.");
            }
        } catch (Exception e) {
            System.err.println("Error communicating with the server: " + e.getMessage());
        }
    }

}
