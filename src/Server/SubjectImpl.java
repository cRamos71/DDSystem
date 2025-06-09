package Server;

import Interface.ObserverRI;
import Interface.SubjectRI;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class SubjectImpl extends UnicastRemoteObject implements SubjectRI{

    State subjectState;
    ObserverRI observer;

    public SubjectImpl() throws RemoteException {
        super();
    }
    @Override
    public void attach(ObserverRI obsRI) throws RemoteException {
        this.observer = (obsRI);
    }

    @Override
    public void detach(ObserverRI obsRI) throws RemoteException {
        this.observer = null;
    }

    @Override
    public State getState() throws RemoteException {
        return subjectState;
    }

    @Override
    public void setState(State state) throws RemoteException {
        this.subjectState = state;
        observer.update();
    }
}
