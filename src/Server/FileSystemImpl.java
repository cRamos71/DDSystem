package Server;

import Interface.FileSystemInterface;

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

    // “Espelho/Metadados” — onde temos both local e shared por usuário
    private static final Path STORAGE_ROOT       = Paths.get("storage");

    // “Ações reais de usuário” — só local por usuário
    private static final Path SERVERSTORAGE_ROOT = Paths.get("serverStorage");

    // ========== Campos de diretório ==========

    // <…>/storage/<username>
    private final Path userStorageDir;

    // <…>/storage/<username>/local
    private final Path storageLocalDir;

    // <…>/storage/<username>/shared
    private final Path storageSharedDir;

    // <…>/serverStorage/<username>
    private final Path userServerStorageDir;

    // <…>/serverStorage/<username>/local
    private final Path serverLocalDir;

    private Path currentDir;

    // ========== Construtor ==========

    public FileSystemImpl(String username) throws RemoteException {
        super();
        this.username = username;

        // 1) Pasta “storage/<username>”
        this.userStorageDir = STORAGE_ROOT.resolve(username);
        this.storageLocalDir  = userStorageDir.resolve("local");
        this.storageSharedDir = userStorageDir.resolve("shared");

        // 2) Pasta “serverStorage/<username>”
        this.userServerStorageDir = SERVERSTORAGE_ROOT.resolve(username);
        this.serverLocalDir       = userServerStorageDir.resolve("local");

        try {
            // ─── 1) Garante que storage/<username>/local e /shared existam
            if (!Files.exists(storageLocalDir)) {
                Files.createDirectories(storageLocalDir);
            }
            if (!Files.exists(storageSharedDir)) {
                Files.createDirectories(storageSharedDir);
            }

            // ─── 2) Garante que serverStorage/<username>/local exista
            if (!Files.exists(serverLocalDir)) {
                Files.createDirectories(serverLocalDir);
            }

        } catch (IOException e) {
            throw new RemoteException("Error creating initial folders", e);
        }

        this.currentDir = userStorageDir;
    }

    // ================= Helpers Privados =================

    private boolean isInsideServerLocal(Path p) {
        return p.normalize().startsWith(serverLocalDir.normalize());
    }

    private boolean isInsideStorageShared(Path p) {
        return p.normalize().startsWith(storageSharedDir.normalize());
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

    // ================= Métodos Remotos =================

    @Override
    public  List<String> listFiles() throws RemoteException {
        try {
            List<String> nomes = new ArrayList<>();

            // 1) Caso currentDir seja o próprio “storage/<username>” (root lógico “/”):
            if (currentDir.equals(userStorageDir)) {
                // devolvemos apenas as duas pastas fixas: local e shared
                nomes.add("local");
                nomes.add("shared");
                return nomes;
            }

            // 2) Caso esteja dentro de serverStorage (ex.: serverStorage/cc/local/...):
            if (isInsideServerLocal(currentDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
                    for (Path p : ds) {
                        nomes.add(p.getFileName().toString());
                    }
                }
                return nomes;
            }

            // 3) Caso esteja dentro de storage/<username>/shared/...:
            if (isInsideStorageShared(currentDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(currentDir)) {
                    for (Path p : ds) {
                        nomes.add(p.getFileName().toString());
                    }
                }
                return nomes;
            }

            // Se caiu aqui, currentDir está em um lugar inesperado (não deveria ocorrer)
            return nomes;
        } catch (IOException e) {
            throw new RemoteException("Erro em listFiles()", e);
        }
    }

    /**
     * Muda o currentDir de acordo com folderName:
     *
     *   • Se estivermos em “/” (userStorageDir), folderName só pode ser “local” ou “shared”.
     *   • Se estivermos dentro de serverStorage/cc/local/... :
     *       - “..” nos leva para “/” (userStorageDir) se estivermos em serverLocalDir exato.
     *       - caso contrário, sobe um nível dentro de serverLocalDir.
     *       - nome de subpasta → vai para serverLocalDir/subpasta.
     *   • Se estivermos dentro de storage/cc/shared/... :
     *       - “..” leva para “/” (userStorageDir) se estivermos em storageSharedDir exato
     *       - nome de subpasta → vai para storageSharedDir/subpasta
     */
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
                    currentDir = userStorageDir; // volta para “/”
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
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error creating folder " + folderName, e);
            }
        }

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
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error creating folder " + folderName, e);
            }
        }

        return false;
    }


    @Override
    public synchronized boolean rename(String oldName, String newName) throws RemoteException {

        if (isInsideServerLocal(currentDir)) {
            Path source = currentDir.resolve(oldName).normalize();
            Path target = currentDir.resolve(newName).normalize();
            if (!Files.exists(source)
                    || !source.startsWith(serverLocalDir)
                    || !target.getParent().startsWith(serverLocalDir)) {
                return false;
            }
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

                Path relOld    = serverLocalDir.relativize(source);
                Path relNew    = serverLocalDir.relativize(target);
                Path mirrorOld = storageLocalDir.resolve(relOld);
                Path mirrorNew = storageLocalDir.resolve(relNew);
                if (Files.exists(mirrorOld)) {
                    Files.createDirectories(mirrorNew.getParent());
                    Files.move(mirrorOld, mirrorNew, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error renaming: " + oldName, e);
            }
        }


        if (isInsideStorageShared(currentDir)) {
            Path source = currentDir.resolve(oldName).normalize();
            Path target = currentDir.resolve(newName).normalize();
            if (!Files.exists(source)
                    || !source.startsWith(storageSharedDir)
                    || !target.getParent().startsWith(storageSharedDir)) {
                return false;
            }
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error renaming: " + oldName, e);
            }
        }

        return false;
    }


    @Override
    public synchronized boolean move(String itemName, String targetFolder) throws RemoteException {
        // Só move se estivermos dentro de serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path source  = currentDir.resolve(itemName).normalize();
        Path destDir = currentDir.resolve(targetFolder).normalize();
        if (!Files.exists(source) || !Files.isDirectory(destDir) || !isInsideServerLocal(destDir)) {
            return false;
        }

        Path newLocation = destDir.resolve(source.getFileName());
        try {
            // 1) move no serverStorage/local
            Files.move(source, newLocation, StandardCopyOption.REPLACE_EXISTING);

            // 2) espelha em storage/local
            Path relOld    = serverLocalDir.relativize(source);
            Path relNew    = serverLocalDir.relativize(newLocation);
            Path mirrorOld = storageLocalDir.resolve(relOld);
            Path mirrorNew = storageLocalDir.resolve(relNew);
            if (Files.exists(mirrorOld)) {
                Files.createDirectories(mirrorNew.getParent());
                Files.move(mirrorOld, mirrorNew, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            throw new RemoteException(String.format("Error moving: %s → %s", source, newLocation), e);
        }
    }

    @Override
    public synchronized boolean upload(String filename, byte[] data) throws RemoteException {

        if (isInsideServerLocal(currentDir)) {
            Path dst = currentDir.resolve(filename).normalize();
            if (!dst.startsWith(serverLocalDir)) {
                return false;
            }
            try {
                Files.createDirectories(dst.getParent());
                Files.write(dst, data);

                Path relative = serverLocalDir.relativize(dst);
                Path mirror   = storageLocalDir.resolve(relative);
                Files.createDirectories(mirror.getParent());
                Files.write(mirror, data);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error uploading file: " + filename, e);
            }
        }


        if (isInsideStorageShared(currentDir)) {
            Path dst = currentDir.resolve(filename).normalize();
            if (!dst.startsWith(storageSharedDir)) {
                return false;
            }
            try {
                Files.createDirectories(dst.getParent());
                Files.write(dst, data);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error uploading file: " + filename, e);
            }
        }

        return false;
    }


    @Override
    public synchronized byte[] download(String filename) throws RemoteException {
        // Só baixa se estivermos em serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return null;
        }

        Path file = currentDir.resolve(filename).normalize();
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RemoteException("Error downloading: " + filename, e);
        }
    }

    @Override
    public synchronized boolean delete(String name) throws RemoteException {
        if (isInsideServerLocal(currentDir)) {
            Path target = currentDir.resolve(name).normalize();
            if (!Files.exists(target) || !target.startsWith(serverLocalDir)) {
                return false;
            }
            try {
                deleteRecursively(target);

                Path rel    = serverLocalDir.relativize(target);
                Path mirror = storageLocalDir.resolve(rel);
                if (Files.exists(mirror)) {
                    deleteRecursively(mirror);
                }
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error deleting file: " + name, e);
            }
        }

        if (isInsideStorageShared(currentDir)) {
            Path target = currentDir.resolve(name).normalize();
            if (!Files.exists(target) || !target.startsWith(storageSharedDir)) {
                return false;
            }
            try {
                deleteRecursively(target);
                return true;
            } catch (IOException e) {
                throw new RemoteException("Error deleting file: " + name, e);
            }
        }

        return false;
    }

    @Override
    public synchronized boolean share(String name, String withUsername) throws RemoteException {
        // 1) Só permite compartilhar se estivermos dentro de serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        // 2) Fonte: caminho físico do arquivo ou pasta dentro de serverStorage/cc/local/...
        Path source = currentDir.resolve(name).normalize();
        if (!Files.exists(source)) {
            return false;
        }

        // 3) Pasta “shared” do destinatário (apenas em storage, sem serverStorage)
        Path recipientSharedRoot = STORAGE_ROOT.resolve(withUsername).resolve("shared");
        if (!Files.exists(recipientSharedRoot)) {
            return false;
        }

        // 4) Calcula o caminho relativo de “source” em relação a serverLocalDir (= serverStorage/<cc>/local)
        Path relativeFromLocal = serverLocalDir.relativize(source);
        //    Se source = serverStorage/cc/local/a/b/text.file
        //    então relativeFromLocal = Paths.get("a", "b", "text.file")

        // 5) Em seguida, construímos a árvore dentro de ff/shared/cc/a/b/text.file
        //    Ou seja: criamos uma subpasta “cc” dentro de ff/shared, e depois “a/b/text.file”
        Path mirrorRoot = recipientSharedRoot.resolve(username);
        Path targetPath = mirrorRoot.resolve(relativeFromLocal).normalize();
        try {
            // 6) Começa certificando que todos os diretórios-pai existem:
            Files.createDirectories(targetPath.getParent());

            // 7) Copia recursivamente se for diretório, ou apenas o arquivo
            if (Files.isDirectory(source)) {
                copyRecursively(source, targetPath);
            } else {
                Files.copy(source, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
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
        // — Caso esteja em “/” (storage/<username>), devolve “/”
        if (currentDir.equals(userStorageDir)) {
            return "/";
        }

        // — Caso esteja em serverStorage/<username>/local/... → "/local/..."
        if (isInsideServerLocal(currentDir)) {
            // Monta a parte relativa a partir de serverLocalDir
            Path relative = serverLocalDir.relativize(currentDir);
            String part = relative.toString().replace(File.separator, "/");
            // Se estiver exatamente em serverLocalDir, o “relative” é vazio, então só retorna "/local"
            if (part.isEmpty()) {
                return "/local";
            } else {
                return "/local/" + part;
            }
        }

        // — Caso esteja em storage/<username>/shared/... → "/shared/..."
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