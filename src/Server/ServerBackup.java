package Server;

import Interface.AuthFactory;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class ServerBackup {
    public static void main(String[] args) {
        try {
            LocateRegistry.createRegistry(1100);
            AuthFactory authService = new AuthFactoryImpl();
            Naming.rebind("rmi://localhost:1100/AuthService", authService);

            System.out.println("Backup AuthService started and registered on port 1100.");
        } catch (Exception e) {
            System.err.println("Error starting backup AuthService: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
