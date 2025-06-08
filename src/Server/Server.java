package Server;

import Interface.AuthFactory;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class Server {
    public static void main (String[] args){
        try {
            LocateRegistry.createRegistry(1099);
            AuthFactory authService = (AuthFactory) new AuthFactoryImpl();
            Naming.rebind("rmi://localhost/AuthService", authService);

            System.out.println("[SERVER] RMI server is now running on port 1099\n[SERVER][NOTIFICATION] AuthService Registered");
        }catch (Exception e){
            System.err.println("Error starting RMI server:" + e.getMessage());
            e.printStackTrace();
        }
    }
}
