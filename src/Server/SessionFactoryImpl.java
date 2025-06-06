package Server;

import Interface.FileSystemInterface;
import Interface.SessionFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;


public class SessionFactoryImpl extends UnicastRemoteObject implements SessionFactory {
    private final FileSystemInterface fileSystem;

    public SessionFactoryImpl(String username) throws RemoteException {
        super();
        this.fileSystem = (FileSystemInterface) new FileSystemImpl(username);
    }

    @Override
    public List<String> listFiles() throws RemoteException {
        return fileSystem.listFiles();
    }
    @Override
    public boolean createFolder(String folderName) throws RemoteException{
        return fileSystem.createFolder(folderName);
    }

    @Override
    public boolean changeDirectory(String folderName) throws RemoteException{
        return fileSystem.changeDirectory(folderName);
    }

    @Override
    public boolean rename(String oldName, String newName) throws RemoteException {
        return fileSystem.rename(oldName, newName);
    }

    @Override
    public boolean move(String itemName, String targetFolder) throws RemoteException {
        return fileSystem.move(itemName, targetFolder);
    }

    @Override
    public boolean upload(String filename, byte[] data) throws RemoteException {
        return fileSystem.upload(filename, data);
    }

    @Override
    public byte[] download(String filename) throws RemoteException {
        return fileSystem.download(filename);
    }

    @Override
    public void delete(String filename) throws RemoteException {
        fileSystem.delete(filename);
    }

    @Override
    public void shareWithUser(String filename, String withUsername) throws RemoteException {
        fileSystem.share(filename, withUsername);
    }
    @Override
    public String getPath() throws RemoteException{
        return fileSystem.getPath();
    }
}
