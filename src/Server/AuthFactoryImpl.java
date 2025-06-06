package Server;

import Interface.AuthFactory;
import Interface.ObserverInterface;
import Interface.SessionFactory;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

public class AuthFactoryImpl extends UnicastRemoteObject implements AuthFactory{
    private static final String USERS_FILE = "users.dat";
    private final HashMap<String, String> users;
    private final HashMap<String, ObserverInterface> observers;
    private final HashMap<String, SessionFactory> sessions;

    public AuthFactoryImpl() throws RemoteException{
        super();
        this.users = new HashMap<>();
        this.observers = new HashMap<>();
        this.sessions = new HashMap<>();
        loadUsers();
    }

    @Override
    public SessionFactory login(String username, String password) throws RemoteException{
        if (users.containsKey(username) && users.get(username).equals(password)){
            SessionFactory session = (SessionFactory) new SessionFactoryImpl(username);
            sessions.put(username, session);
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

    /**
     * Serializa o HashMap<String,String> "users" para o arquivo users.dat.
     */
    private void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(users);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lê o HashMap<String,String> de volta do arquivo users.dat (se existir),
     * e injeta seu conteúdo no campo "users".
     */
    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            // Se o arquivo não existe, não há nada para carregar
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof HashMap) {
                // Limpa o HashMap atual (que inicialmente está vazio) e injeta os dados lidos do disco
                this.users.clear();
                this.users.putAll((HashMap<String, String>) obj);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
