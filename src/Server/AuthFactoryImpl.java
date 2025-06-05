package Server;

import Interface.AuthFactory;
import Interface.ObserverInterface;
import Interface.SessionFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

public class AuthFactoryImpl extends UnicastRemoteObject implements AuthFactory{

    private final HashMap<String, String> users;
    private final HashMap<String, ObserverInterface> observers;
    private final HashMap<String, SessionFactory> sessions;

    public AuthFactoryImpl() throws RemoteException{
        super();
        this.users = new HashMap<>();
        this.observers = new HashMap<>();
        this.sessions = new HashMap<>();
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
        return true;
    }
}
