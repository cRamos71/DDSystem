package Server;

import Interface.FileSystemInterface;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public class FileSystemImpl implements FileSystemInterface {
    private final String username;
    private static final Path STORAGE_ROOT = Paths.get("storage");
    private static final Path SERVERSTORAGE_ROOT = Paths.get("serverStorage");
    private final Path userStorageDir;        // <projeto>/storage/<username>
    private final Path userServerStorageDir;  // <projeto>/serverStorage/<username>
    private final Path localDir;   // <…>/serverStorage/<username>/local
    private final Path sharedDir;  // <…>/serverStorage/<username>/shared
    private Path currentDir;       // Aponta para localDir ou sharedDir conforme o usuário mudar



    public FileSystemImpl(String username) throws RemoteException{
        super();
        this.username = username;

        // Pasta de “controle” (pode guardar metadados, logs, etc.):
        this.userStorageDir       = STORAGE_ROOT.resolve(username);

        // Pasta onde os arquivos realmente serão lidos/escritos:
        this.userServerStorageDir = SERVERSTORAGE_ROOT.resolve(username);
        this.localDir             = userServerStorageDir.resolve("local");
        this.sharedDir            = userServerStorageDir.resolve("shared");

        try {
            // 1) Garante que storage/<username> exista (opcional para metadados)
            if (!Files.exists(userStorageDir)) {
                Files.createDirectories(userStorageDir);
            }
            // 2) Garante que serverStorage/<username>/local e shared existam
            if (!Files.exists(localDir)) {
                Files.createDirectories(localDir);
            }
            if (!Files.exists(sharedDir)) {
                Files.createDirectories(sharedDir);
            }
        } catch (IOException e) {
            throw new RemoteException("Falha ao criar diretórios iniciais", e);
        }

        // Por padrão, o usuário inicia em “local”
        this.currentDir = localDir;

    }

    // Garante que o path não “escape” serverStorage/<username>
    private boolean isInsideServerStorageRoot(Path p) {
        return p.normalize().startsWith(userServerStorageDir.normalize());
    }

    /**
     * Remove recursivamente um diretório ou arquivo.
     */
    private void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
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

    /**
     * Copia recursivamente um diretório ou arquivo (subpastas e arquivos)
     * de “source” para “target”.
     */
    private void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Calcula o caminho relativo dentro da árvore source
                Path relative = source.relativize(dir);
                // Resolve esse caminho em target (criando subpastas conforme necessário)
                Path targetDir = target.resolve(relative);
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Calcula o caminho relativo do arquivo em relação a “source”
                Path relative = source.relativize(file);
                // Resolve o arquivo de destino dentro de “target”
                Path targetFile = target.resolve(relative);
                // Copia o arquivo, sobrescrevendo caso já exista
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public List<String> listFiles() throws RemoteException {
        try {
            List<String> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
                for (Path p : stream) {
                    files.add(p.getFileName().toString());
                }
            }
            return files;
        } catch (IOException e) {
            throw new RemoteException("Error in listFiles", e);
        }
    }

    @Override
    public boolean changeDirectory(String folderName) throws RemoteException {
        if ("..".equals(folderName)) {
            Path parent = currentDir.getParent();
            if (parent == null || !isInsideServerStorageRoot(parent)) {
                return false;
            }
            currentDir = parent;
            return true;
        }

        Path target = currentDir.resolve(folderName).normalize();
        if (Files.isDirectory(target) && isInsideServerStorageRoot(target)) {
            currentDir = target;
            return true;
        }

        return false;
    }

    @Override
    public boolean createFolder(String folderName) throws RemoteException {
        Path newDir = currentDir.resolve(folderName).normalize();
        if (!isInsideServerStorageRoot(newDir)) {
            return false;
        }
        try {
            if (Files.exists(newDir)) {
                return false; // já existe
            }
            Files.createDirectories(newDir);
            return true;
        } catch (IOException e) {
            throw new RemoteException("Error creating folder: " + folderName, e);
        }
    }

    @Override
    public boolean rename(String oldName, String newName) throws RemoteException {
        Path source = currentDir.resolve(oldName).normalize();
        Path target = currentDir.resolve(newName).normalize();
        if (!isInsideServerStorageRoot(source) || !isInsideServerStorageRoot(target) || !Files.exists(source)) {
            return false;
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new RemoteException("Error renaming: " + oldName, e);
        }
    }

    @Override
    public boolean move(String itemName, String targetFolder) throws RemoteException {
        Path source = currentDir.resolve(itemName).normalize();
        Path destDir = currentDir.resolve(targetFolder).normalize();
        if (!isInsideServerStorageRoot(source) || !isInsideServerStorageRoot(destDir)) {
            return false;
        }
        if (!Files.exists(source) || !Files.isDirectory(destDir)) {
            return false;
        }

        Path newLocation = destDir.resolve(source.getFileName());
        try {
            Files.move(source, newLocation, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new RemoteException(String.format("Error moving: %s → %s", source, newLocation), e);
        }
    }

    @Override
    public boolean upload(String filename, byte[] data) throws RemoteException {
        Path dst = currentDir.resolve(filename).normalize();
        if (!isInsideServerStorageRoot(dst)) {
            return false;
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.write(dst, data);
            return true;
        } catch (IOException e) {
            throw new RemoteException("Error uploading: " + filename, e);
        }
    }

    @Override
    public byte[] download(String filename) throws RemoteException {
        Path file = currentDir.resolve(filename).normalize();
        if (!isInsideServerStorageRoot(file) || !Files.exists(file) || Files.isDirectory(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RemoteException("Error downloading: " + filename, e);
        }
    }

    @Override
    public boolean delete(String name) throws RemoteException {
        Path target = currentDir.resolve(name).normalize();
        if (!isInsideServerStorageRoot(target) || !Files.exists(target)) {
            return false;
        }
        try {
            deleteRecursively(target);
            return true;
        } catch (IOException e) {
            throw new RemoteException("Delete falhou: " + name, e);
        }
    }



    @Override
    public boolean share(String name, String withUsername) throws RemoteException {
        Path source = currentDir.resolve(name).normalize();
        if (!isInsideServerStorageRoot(source) || !source.startsWith(localDir) || !Files.exists(source)) {
            return false;
        }

        Path recipientShared = SERVERSTORAGE_ROOT.resolve(withUsername).resolve("shared");
        if (!Files.exists(recipientShared)) {
            return false;
        }
        Path target = recipientShared.resolve(name).normalize();

        try {
            if (Files.isDirectory(source)) {
                copyRecursively(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            throw new RemoteException(String.format("Share error: %s → %s to user %s", name, target, withUsername), e);
        }
    }


}
