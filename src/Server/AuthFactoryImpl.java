package Server;

import Interface.AuthFactory;
import Interface.SessionFactory;
import Interface.SubjectRI;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

public class AuthFactoryImpl extends UnicastRemoteObject implements AuthFactory{
    private static final String USERS_FILE = "users.dat";
    private final HashMap<String, String> users;
    private final HashMap<String, SessionFactory> sessions;

    public AuthFactoryImpl() throws RemoteException{
        super();
        this.users = new HashMap<>();
        this.sessions = new HashMap<>();
        loadUsers();
    }

    @Override
    public SessionFactory login(String username, String password) throws RemoteException{
        if (users.containsKey(username) && users.get(username).equals(password)){
            SessionFactory session = new SessionFactoryImpl(username);
            SubjectRI subjectRI = new SubjectImpl();
            session.setSubjectRI(subjectRI);
            sessions.put(username, session);
            SubjectRegistry.register(username, subjectRI);

            return session;
        }
        return null;
    }
    @Override
    public boolean register(String username, String password) throws RemoteException{
        if(users.containsKey(username)) return false;
        users.put(username,password);
        saveUsers();
        return true;
    }

    private void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(users);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof HashMap) {
                this.users.clear();
                this.users.putAll((HashMap<String, String>) obj);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
