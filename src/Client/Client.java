package Client;

import Interface.AuthFactory;

import java.rmi.Naming;

public class Client {
    public static void main(String[] args) {
        try{
            AuthFactory authService = (AuthFactory) Naming.lookup("rmi://localhost:1099/AuthService");
            System.out.println("Worked!");
            ClientMenu menu = new ClientMenu(authService);
            menu.run();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
