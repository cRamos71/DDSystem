package Client;

import Interface.ObserverRI;
import Interface.SubjectRI;
import Server.State;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ObserverImpl extends UnicastRemoteObject implements ObserverRI {

    private String username;
    private SubjectRI subjectRI;
    private State lastObservedState;


    public ObserverImpl(String username, SubjectRI subjectRI) throws RemoteException {
        super();
        this.username = username;
        this.subjectRI = subjectRI;
        this.subjectRI.attach(this);
    }

    @Override
    public void update() throws RemoteException {
        lastObservedState = subjectRI.getState();
        System.out.printf("[NOTIFICATION][%s] %s", lastObservedState.getId(), lastObservedState.getInfo());
    }
}
