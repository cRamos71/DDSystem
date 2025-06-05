package Client;

import Interface.ObserverInterface;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Observer extends UnicastRemoteObject implements ObserverInterface {

    private final String username;

    public Observer(String username) throws RemoteException {
        super();
        this.username = username;
    }

    public static class FileOperation implements Serializable {
        public enum Type {
            CREATE_DIRECTORY,
            DELETE,
            RENAME,
            MOVE,
            UPLOAD_FILE,
            SHARE_DIRECTORY,
            UNSHARE_DIRECTORY
        }

        private final Type type;
        private final String username;
        private final String sourcePath;
        private final String targetPath;
        private final String name;
        private final long timestamp;

        public FileOperation(Type type, String username, String sourcePath, String targetPath, String name) {
            this.type = type;
            this.username = username;
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.name = name;
            this.timestamp = System.currentTimeMillis();
        }

        public Type getType() { return type; }
        public String getUsername() { return username; }
        public String getSourcePath() { return sourcePath; }
        public String getTargetPath() { return targetPath; }
        public String getName() { return name; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[EVENT] %s by '%s' | source: %s | target: %s | name: %s",
                    type, username, sourcePath, targetPath, name);
        }
    }

    @Override
    public void update(FileOperation op) throws RemoteException {
        System.out.println(op);
    }
}
