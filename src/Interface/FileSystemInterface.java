package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FileSystemInterface extends Remote {
    List<String> listFiles() throws RemoteException;
    boolean changeDirectory(String folderName) throws RemoteException;
    boolean createFolder(String folderName) throws RemoteException;
    boolean rename(String oldName, String newName) throws RemoteException;
    boolean move(String itemName, String targetFolder) throws RemoteException;
    boolean upload(String filename, byte[] data) throws RemoteException;
    byte[] download(String filename) throws RemoteException;
    boolean delete(String name) throws RemoteException;
    boolean share(String name, String withUsername) throws RemoteException;
    String getPath() throws RemoteException;
}
