package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface SessionFactory extends Remote {

    List<String> listFiles() throws RemoteException;
    boolean createFolder(String folderName) throws RemoteException;
    boolean changeDirectory(String folderName) throws RemoteException;

    boolean rename(String oldName, String newName) throws RemoteException;

    boolean move(String itemName, String targetFolder) throws RemoteException;

    boolean upload(String filename, byte[] data) throws RemoteException;

    byte[] download(String filename) throws RemoteException;

    void delete(String filename) throws RemoteException;

    void shareWithUser(String filename, String withUsername) throws RemoteException;

    String getPath() throws RemoteException;
}
