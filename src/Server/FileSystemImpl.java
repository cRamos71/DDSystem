package Server;

import Interface.FileSystemInterface;
import Server.UpdatePublisher;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class FileSystemImpl extends UnicastRemoteObject implements FileSystemInterface {

    private final String username;
    private static final Path STORAGE_ROOT       = Paths.get("storage");
    private static final Path SERVERSTORAGE_ROOT = Paths.get("serverStorage");

    // ========== Paths ==========

    private final Path userStorageDir;
    private final Path storageLocalDir;
    private final Path storageSharedDir;
    private final Path userServerStorageDir;
    private final Path serverLocalDir;
    private Path currentDir;

    // ========== Constructor ==========

    public FileSystemImpl(String username) throws RemoteException {
        super();
        this.username = username;

        // storage
        this.userStorageDir = STORAGE_ROOT.resolve(username);
        this.storageLocalDir  = userStorageDir.resolve("local");
        this.storageSharedDir = userStorageDir.resolve("shared");

        // serverStorage
        this.userServerStorageDir = SERVERSTORAGE_ROOT.resolve(username);
        this.serverLocalDir       = userServerStorageDir.resolve("local");

        try {
            if (!Files.exists(storageLocalDir)) {
                Files.createDirectories(storageLocalDir);
            }
            if (!Files.exists(storageSharedDir)) {
                Files.createDirectories(storageSharedDir);
            }

            if (!Files.exists(serverLocalDir)) {
                Files.createDirectories(serverLocalDir);
            }

        } catch (IOException e) {
            throw new RemoteException("Error creating initial folders", e);
        }

        this.currentDir = userStorageDir;
    }

    private static class OwnerInfo {
        final String owner;
        final Path   relative;
        OwnerInfo(String owner, Path relative) {
            this.owner    = owner;
            this.relative = relative;
        }
    }

    // ================= Helpers =================

    private boolean isInsideServerLocal(Path p) {
        return p.normalize().startsWith(serverLocalDir.normalize());
    }

    private boolean isInsideStorageShared(Path p) {
        return p.normalize().startsWith(storageSharedDir.normalize());
    }
    private OwnerInfo resolveOwnerAndRelative(Path fullPath) throws RemoteException {
        Path serverLocalInvoker = SERVERSTORAGE_ROOT.resolve(username).resolve("local");
        if (fullPath.startsWith(serverLocalInvoker)) {
            return new OwnerInfo(
                    username,
                    serverLocalInvoker.relativize(fullPath)
            );
        } else {
            Path sharedBase = STORAGE_ROOT.resolve(username).resolve("shared");
            if (!fullPath.startsWith(sharedBase)) {
                throw new RemoteException("Path inválido: " + fullPath);
            }
            Path relToShared = sharedBase.relativize(fullPath);
            String owner     = relToShared.getName(0).toString();
            Path relative    = relToShared.subpath(1, relToShared.getNameCount());
            return new OwnerInfo(owner, relative);
        }
    }
    private void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path targetDir = target.resolve(relative);
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative   = source.relativize(file);
                Path targetFile = target.resolve(relative);
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void notifyUpdate(String operation, String target) {
        String message = String.format("%s:%s:%s", username, operation, target);
        try {
            UpdatePublisher.publishUpdate(message);
        } catch (Exception e) {
            System.err.println("Failed to publish update: " + e.getMessage());
        }
    }

    @Override
    public List<String> getAuthorizedUsers(String itemName) throws RemoteException {
        List<String> result = new ArrayList<>();

        Path fullPath = currentDir.resolve(itemName).normalize();

        Path serverLocalDir = Paths.get("serverStorage")
                .resolve(username)
                .resolve("local");

        if (!Files.exists(fullPath))
            return result;

        String owner;
        Path relative;

        if (fullPath.startsWith(serverLocalDir)) {
            owner    = username;
            relative = serverLocalDir.relativize(fullPath);
        } else {
            // Shared
            Path sharedBase = STORAGE_ROOT
                    .resolve(username)
                    .resolve("shared");

            if (!fullPath.startsWith(sharedBase)) {
                return result;
            }
            Path relToShared = sharedBase.relativize(fullPath);
            owner = relToShared.getName(0).toString();
            relative = relToShared.subpath(1, relToShared.getNameCount());
        }

        result.add(owner);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(STORAGE_ROOT)) {
            for (Path userDir : ds) {
                String user = userDir.getFileName().toString();
                if (user.equals(owner)) continue;
                Path sharedCopy = userDir
                        .resolve("shared")
                        .resolve(owner)
                        .resolve(relative);
                if (Files.exists(sharedCopy)) {
                    result.add(user);
                }
            }
        } catch (IOException e) {
            throw new RemoteException("Error", e);
        }

        return result;
    }

    // ================= Remote Methods =================

    @Override
    public  List<String> listFiles() throws RemoteException {
        try {
            List<String> nomes = new ArrayList<>();

            if (currentDir.equals(userStorageDir)) {
                nomes.add("local");
                nomes.add("shared");
                return nomes;
            }

            // Local
            if (isInsideServerLocal(currentDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
                    for (Path p : ds) {
                        nomes.add(p.getFileName().toString());
                    }
                }
                return nomes;
            }

            // Shared
            if (isInsideStorageShared(currentDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
                    for (Path p : ds) {
                        nomes.add(p.getFileName().toString());
                    }
                }
                return nomes;
            }

            // Error
            return nomes;
        } catch (IOException e) {
            throw new RemoteException("Error listFiles()", e);
        }
    }

    @Override
    public boolean changeDirectory(String folderName) throws RemoteException {

        if (currentDir.equals(userStorageDir)) {
            if ("local".equals(folderName)) {
                currentDir = serverLocalDir;
                return true;
            }
            if ("shared".equals(folderName)) {
                currentDir = storageSharedDir;
                return true;
            }
            return false;
        }

        if (isInsideServerLocal(currentDir)) {
            if ("..".equals(folderName)) {
                if (currentDir.equals(serverLocalDir)) {
                    currentDir = userStorageDir;
                    return true;
                } else {
                    Path parent = currentDir.getParent();
                    if (parent != null && isInsideServerLocal(parent)) {
                        currentDir = parent;
                        return true;
                    }
                    return false;
                }
            }


            Path target = currentDir.resolve(folderName).normalize();
            if (Files.isDirectory(target) && isInsideServerLocal(target)) {
                currentDir = target;
                return true;
            }
            return false;
        }


        if (isInsideStorageShared(currentDir)) {
            if ("..".equals(folderName)) {
                if (currentDir.equals(storageSharedDir)) {
                    currentDir = userStorageDir; // volta para "/"
                    return true;
                } else {
                    Path parent = currentDir.getParent();
                    if (parent != null && isInsideStorageShared(parent)) {
                        currentDir = parent;
                        return true;
                    }
                    return false;
                }
            }

            Path target = currentDir.resolve(folderName).normalize();
            if (Files.isDirectory(target) && isInsideStorageShared(target)) {
                currentDir = target;
                return true;
            }
            return false;
        }

        return false;
    }

    @Override
    public synchronized boolean createFolder(String folderName) throws RemoteException {
        // Local
        if (isInsideServerLocal(currentDir)) {
            Path newDir = currentDir.resolve(folderName).normalize();
            if (!newDir.startsWith(serverLocalDir)) {
                return false;
            }
            try {
                if (Files.exists(newDir)) {
                    return false;
                }
                Files.createDirectories(newDir);

                Path relative = serverLocalDir.relativize(newDir);
                Path mirror   = storageLocalDir.resolve(relative);
                if (!Files.exists(mirror.getParent())) {
                    Files.createDirectories(mirror.getParent());
                }
                Files.createDirectories(mirror);
                notifyUpdate("createFolder", folderName);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error creating folder " + folderName, e);
            }
        }

        // Server
        if (isInsideStorageShared(currentDir)) {
            Path newDir = currentDir.resolve(folderName).normalize();
            if (!newDir.startsWith(storageSharedDir)) {
                return false;
            }
            try {
                if (Files.exists(newDir)) {
                    return false;
                }
                Files.createDirectories(newDir);
                notifyUpdate("createFolder", folderName);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error creating folder " + folderName, e);
            }
        }

        return false;
    }


    @Override
    public synchronized boolean rename(String oldName, String newName) throws RemoteException {
        Path fullOld = currentDir.resolve(oldName).normalize();
        if (!Files.exists(fullOld)) {
            return false;
        }

        OwnerInfo info = resolveOwnerAndRelative(fullOld);
        String owner       = info.owner;
        Path relativeOld   = info.relative;
        Path relativeParent= relativeOld.getNameCount()>1
                ? relativeOld.getParent()
                : Paths.get("");

        List<String> authorized = getAuthorizedUsers(oldName);

        Path ownerServerOld  = SERVERSTORAGE_ROOT.resolve(owner).resolve("local").resolve(relativeOld);
        Path ownerServerNew  = SERVERSTORAGE_ROOT.resolve(owner).resolve("local").resolve(relativeParent).resolve(newName);
        Path ownerMirrorOld  = STORAGE_ROOT       .resolve(owner).resolve("local").resolve(relativeOld);
        Path ownerMirrorNew  = STORAGE_ROOT       .resolve(owner).resolve("local").resolve(relativeParent).resolve(newName);

        try {
            // rename in serverStorage
            Files.createDirectories(ownerServerNew.getParent());
            Files.move(ownerServerOld, ownerServerNew, StandardCopyOption.REPLACE_EXISTING);

            // rename in storageMirror
            if (Files.exists(ownerMirrorOld)) {
                Files.createDirectories(ownerMirrorNew.getParent());
                Files.move(ownerMirrorOld, ownerMirrorNew, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RemoteException("Error renaming: " + oldName, e);
        }

        for (String u : authorized) {
            if (u.equals(owner)) continue;
            Path sharedOld = STORAGE_ROOT.resolve(u)
                    .resolve("shared")
                    .resolve(owner)
                    .resolve(relativeOld);
            Path sharedNew = STORAGE_ROOT.resolve(u)
                    .resolve("shared")
                    .resolve(owner)
                    .resolve(relativeParent)
                    .resolve(newName);
            if (Files.exists(sharedOld)) {
                try {
                    Files.createDirectories(sharedNew.getParent());
                    Files.move(sharedOld, sharedNew, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
        }

        notifyUpdate("rename", oldName + " → " + newName);
        return true;
    }


    @Override
    public synchronized boolean move(String itemName, String targetFolder) throws RemoteException {
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path source = currentDir.resolve(itemName).normalize();
        Path destDir = currentDir.resolve(targetFolder).normalize();
        if (!Files.exists(source) || !Files.isDirectory(destDir) || !isInsideServerLocal(destDir)) {
            return false;
        }

        List<String> authorized = getAuthorizedUsers(itemName);
        Path serverLocalInvoker = SERVERSTORAGE_ROOT.resolve(username).resolve("local");

        String owner = username;
        Path relativeOld = serverLocalInvoker.relativize(source);

        Path newLocation     = destDir.resolve(source.getFileName());
        Path relativeNew     = serverLocalDir.relativize(newLocation);

        try {
            Files.createDirectories(newLocation.getParent());
            Files.move(source, newLocation, StandardCopyOption.REPLACE_EXISTING);

            Path mirrorOld = storageLocalDir.resolve(relativeOld);
            Path mirrorNew = storageLocalDir.resolve(relativeNew);
            if (Files.exists(mirrorOld)) {
                Files.createDirectories(mirrorNew.getParent());
                Files.move(mirrorOld, mirrorNew, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RemoteException(
                    String.format("Error moving %s → %s", source, newLocation), e
            );
        }

        for (String u : authorized) {
            if (u.equals(owner)) {
                continue;
            }

            Path sharedOld = STORAGE_ROOT
                    .resolve(u)
                    .resolve("shared")
                    .resolve(owner)
                    .resolve(relativeOld);

            Path sharedNew = STORAGE_ROOT
                    .resolve(u)
                    .resolve("shared")
                    .resolve(owner)
                    .resolve(relativeNew);

            if (Files.exists(sharedOld)) {
                try {
                    Files.createDirectories(sharedNew.getParent());
                    Files.move(sharedOld, sharedNew, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }

        notifyUpdate("move", source + " → " + newLocation);
        return true;
    }

    @Override
    public synchronized boolean upload(String filename, byte[] data) throws RemoteException {
        Path dst = currentDir.resolve(filename).normalize();
        boolean ok;
        try {
            Files.createDirectories(dst.getParent());
            Files.write(dst, data);
            if (isInsideServerLocal(currentDir)) {
                Path relative = serverLocalDir.relativize(dst);
                Path mirror   = storageLocalDir.resolve(relative);
                Files.createDirectories(mirror.getParent());
                Files.write(mirror, data);
            }
            ok = true;
        } catch (IOException e) {
            throw new RemoteException("Error uploading file: " + filename, e);
        }

        if (!ok) {
            return false;
        }

        OwnerInfo info = resolveOwnerAndRelative(dst);
        String owner   = info.owner;
        Path relative   = info.relative;

        List<String> authorized = getAuthorizedUsers(filename);

        for (String u : authorized) {
            if (u.equals(username)) {
                continue;
            }

            if (u.equals(owner)) {
                Path ownerServer = SERVERSTORAGE_ROOT.resolve(owner)
                        .resolve("local")
                        .resolve(relative);
                Path ownerMirror = STORAGE_ROOT.resolve(owner)
                        .resolve("local")
                        .resolve(relative);
                try {
                    Files.createDirectories(ownerServer.getParent());
                    Files.copy(dst, ownerServer, StandardCopyOption.REPLACE_EXISTING);
                    Files.createDirectories(ownerMirror.getParent());
                    Files.copy(dst, ownerMirror, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            } else {
                Path sharedCopy = STORAGE_ROOT.resolve(u)
                        .resolve("shared")
                        .resolve(owner)
                        .resolve(relative);
                try {
                    Files.createDirectories(sharedCopy.getParent());
                    Files.copy(dst, sharedCopy, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }

        notifyUpdate("upload", filename);
        return true;
    }


    @Override
    public synchronized boolean download(String filename) throws RemoteException {
        if (!isInsideStorageShared(currentDir)) {
            return false;
        }

        Path sharedFile = currentDir.resolve(filename).normalize();
        if (!Files.exists(sharedFile) || Files.isDirectory(sharedFile)) {
            return false;
        }
        try {
            byte[] data = Files.readAllBytes(sharedFile);

            OwnerInfo info = resolveOwnerAndRelative(sharedFile);
            String owner   = info.owner;
            Path relative  = info.relative;

            Path serverPath = SERVERSTORAGE_ROOT.resolve(username)
                                                .resolve("local")
                                                .resolve(relative);
            Path mirrorDst = STORAGE_ROOT.resolve(username)
                    .resolve("local")
                    .resolve(relative);

            Files.createDirectories(serverPath.getParent());
            Files.write(serverPath, data);

            Files.createDirectories(mirrorDst.getParent());
            Files.write(mirrorDst, data);


        } catch (IOException e) {
            throw new RemoteException("Error downloading: " + filename, e);
        }
        notifyUpdate("download", filename);
        return true;
    }

        @Override
        public boolean delete(String name) throws RemoteException {
            Path fullPath = currentDir.resolve(name).normalize();
            if (!Files.exists(fullPath)) {
                return false;
            }

            OwnerInfo info = resolveOwnerAndRelative(fullPath);
            String owner   = info.owner;
            Path relative  = info.relative;

            Path ownerServerPath = SERVERSTORAGE_ROOT.resolve(owner)
                    .resolve("local")
                    .resolve(relative);
            Path ownerMirrorPath = STORAGE_ROOT.resolve(owner)
                    .resolve("local")
                    .resolve(relative);

            List<String> authorized = getAuthorizedUsers(name);

            try {
                deleteRecursively(ownerServerPath);
                if (Files.exists(ownerMirrorPath)) {
                    deleteRecursively(ownerMirrorPath);
                }
            } catch (IOException e) {
                throw new RemoteException("Error deleting file: " + name, e);
            }

        for (String u : authorized) {
            if (u.equals(owner)) {
                continue;
            }
            Path sharedPath = STORAGE_ROOT.resolve(u)
                    .resolve("shared")
                    .resolve(owner)
                    .resolve(relative);
            if (Files.exists(sharedPath)) {
                try {
                    deleteRecursively(sharedPath);
                } catch (IOException ignored) {
                }
            }
        }

        notifyUpdate("delete", name);
        return true;
    }

    @Override
    public synchronized boolean share(String name, String withUsername) throws RemoteException {
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path source = currentDir.resolve(name).normalize();
        if (!Files.exists(source)) {
            return false;
        }

        Path recipientSharedRoot = STORAGE_ROOT.resolve(withUsername).resolve("shared");
        if (!Files.exists(recipientSharedRoot)) {
            return false;
        }

        Path relativeFromLocal = serverLocalDir.relativize(source);


        Path mirrorRoot = recipientSharedRoot.resolve(username);
        Path targetPath = mirrorRoot.resolve(relativeFromLocal).normalize();
        try {

            Files.createDirectories(targetPath.getParent());

            if (Files.isDirectory(source)) {
                copyRecursively(source, targetPath);
            } else {
                Files.copy(source, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            notifyUpdate("share", name + " → " + targetPath);
            return true;
        } catch (IOException e) {
            throw new RemoteException(
                    String.format(
                            "Error sharing: %s → %s with user %s",
                            source, targetPath, withUsername
                    ), e
            );
        }
    }

    @Override
    public String getPath() throws RemoteException {
        if (currentDir.equals(userStorageDir)) {
            return "/";
        }

        if (isInsideServerLocal(currentDir)) {
            Path relative = serverLocalDir.relativize(currentDir);
            String part = relative.toString().replace(File.separator, "/");
            if (part.isEmpty()) {
                return "/local";
            } else {
                return "/local/" + part;
            }
        }

        if (isInsideStorageShared(currentDir)) {
            Path relative = storageSharedDir.relativize(currentDir);
            String part = relative.toString().replace(File.separator, "/");
            if (part.isEmpty()) {
                return "/shared";
            } else {
                return "/shared/" + part;
            }
        }

        return "/";
    }
}