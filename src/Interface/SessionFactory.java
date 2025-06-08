package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface SessionFactory extends Remote {

    void setSubjectRI(SubjectRI subjectRI) throws RemoteException;
    SubjectRI getSubjectRI() throws RemoteException;
    List<String> listFiles() throws RemoteException;
    void createFolder(String folderName) throws RemoteException;
    boolean changeDirectory(String folderName) throws RemoteException;

    void rename(String oldName, String newName) throws RemoteException;

    void move(String itemName, String targetFolder) throws RemoteException;

    void upload(String filename, byte[] data) throws RemoteException;

    void download(String filename) throws RemoteException;

    void delete(String filename) throws RemoteException;

    void shareWithUser(String filename, String withUsername) throws RemoteException;

    String getPath() throws RemoteException;
}
