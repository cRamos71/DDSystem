package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface ReplicationInterface extends Remote {
    void setFullState(Map<String, Object> state) throws RemoteException;
}