package Server;

import Interface.FileSystemInterface;
import Interface.SessionFactory;
import Interface.SubjectRI;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;


public class SessionFactoryImpl extends UnicastRemoteObject implements SessionFactory {
    private final FileSystemInterface fileSystem;
    private final String username;
    private SubjectRI subjectRI;

    public SessionFactoryImpl(String username) throws RemoteException {
        super();
        this.fileSystem = (FileSystemInterface) new FileSystemImpl(username);
        this.username = username;
        this.subjectRI = null;
    }

    @Override
    public void setSubjectRI(SubjectRI subjectRI) throws RemoteException{
        this.subjectRI = subjectRI;
    }

    @Override
    public SubjectRI getSubjectRI() throws RemoteException{
        return this.subjectRI;
    }

    @Override
    public List<String> listFiles() throws RemoteException {
        return fileSystem.listFiles();
    }
    @Override
    public void createFolder(String folderName) throws RemoteException{
        try {
            subjectRI.setState(new State(
                    "CREATE",
                    fileSystem.createFolder(folderName) ? "'" + folderName + "' created successfully.\n"
                            : "Failed to create folder '" + folderName + "'.\n"
            ));
        } catch(RemoteException e) { e.printStackTrace(); }    }

    @Override
    public boolean changeDirectory(String folderName) throws RemoteException{
        return fileSystem.changeDirectory(folderName);
    }

    @Override
    public void rename(String oldName, String newName) throws RemoteException {
        try {
            subjectRI.setState(new State(
                    "RENAME",
                    fileSystem.rename(oldName, newName) ? "'" + oldName + "' renamed successfully to '" + newName  + "'.\n"
                            : "Failed to rename '" + oldName + "' to '" + newName + "'.\n"
            ));
        } catch(RemoteException e) { e.printStackTrace(); }
    }

    @Override
    public void move(String itemName, String targetFolder) throws RemoteException {
        try {
            subjectRI.setState(new State(
                    "MOVE",
                    fileSystem.move(itemName, targetFolder) ? "'" + itemName + "' successfully moved to '" + targetFolder + "'.\n"
                            : "Failed to move '" + itemName + "' to '" + targetFolder + "'.\n"
            ));
        } catch(RemoteException e) { e.printStackTrace(); }
    }

    @Override
    public void upload(String filename, byte[] data) throws RemoteException {
        try {
            subjectRI.setState(new State(
                    "UPLOAD",
                    fileSystem.upload(filename, data) ? "'" + filename + "' upload successful.\n"
                            : "Failed to upload '" + filename + "'."
            ));
        } catch(RemoteException e) { e.printStackTrace(); }
    }

    @Override
    public byte[] download(String filename) throws RemoteException {
        return fileSystem.download(filename);
    }

    @Override
    public void delete(String filename) throws RemoteException {
        try {
            List<String> users = fileSystem.getAuthorizedUsers(filename);

            boolean ok = fileSystem.delete(filename);
            subjectRI.setState(new State(
                            "DELETE",
                            ok ? "'" + filename + "' delete successful.\n"
                            : "Failed to delete '" + filename + "'.\n"
            ));
            if(!ok) return;

            for (String user : users) {
                SubjectRI subj = SubjectRegistry.get(user);
                if(subjectRI.equals(subj)) continue;
                if (subj != null) {
                    subj.setState((new State(
                            "DELETE",
                            "'" + filename + "' was deleted by '" + username + "'.\n"
                    )));
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shareWithUser(String filename, String withUsername) throws RemoteException {
        try {
            subjectRI.setState(new State(
                    "SHARE",
                    fileSystem.share(filename, withUsername) ? "'" + filename + "' shared with '" + withUsername + "'.\n"
                            : "Failed to share '" + filename + "' with '" + withUsername + "'.\n"
            ));
        } catch(RemoteException e) { e.printStackTrace(); }
    }
    @Override
    public String getPath() throws RemoteException{
        return fileSystem.getPath();
    }
}
