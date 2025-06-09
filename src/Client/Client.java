package Client;

import Interface.AuthFactory;

import java.rmi.Naming;

public class Client {
    public static void main(String[] args) {
        try{
            AuthFactory authService = (AuthFactory) Naming.lookup("rmi://localhost:1099/AuthService");
            //System.out.println("Connected to RMIServer on port 1099");
            ClientMenu menu = new ClientMenu(authService);
            menu.run();
        }catch (Exception e){
            System.out.println("[WARN] Primary server offline. Trying backup...");
            try{
                AuthFactory authService = (AuthFactory) Naming.lookup("rmi://localhost:1100/AuthService");
                //System.out.println("Connected to RMIServer on port 1100");
                ClientMenu menu = new ClientMenu(authService);
                menu.run();
            }catch (Exception e2){
                System.err.println("[ERROR] Failed to connect to both primary and backup servers.");
                e2.printStackTrace();
            }
        }
    }
}
