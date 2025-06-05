package Interface;

import Client.Observer;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ObserverInterface extends Remote {
    void update(Observer.FileOperation update) throws RemoteException;
}
