package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthFactory extends Remote {

    SessionFactory login(String username, String password) throws RemoteException;
    boolean register(String username, String password) throws RemoteException;

}
