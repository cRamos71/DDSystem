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

    /**
     * “currentDir” funciona como “ponteiro físico em disco” para
     * um dos três casos abaixo:
     *   1) Em serverLocalDir (e subpastas) → ação REAL do usuário
     *   2) Em userStorageDir       → root lógico “/”
     *   3) Em storageSharedDir (e subpastas) → “/shared/...”
     */
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
            throw new RemoteException("Falha ao criar diretórios iniciais", e);
        }

        // Por padrão, quem entra primeiro está em “/” (que corresponde a userStorageDir)
        this.currentDir = userStorageDir;
    }

    // ================= Helpers Privados =================

    /**
     * Verifica se “p” está dentro de serverStorage/<username>/local
     * (ou seja, p.normalize().startsWith(serverLocalDir.normalize())).
     */
    private boolean isInsideServerLocal(Path p) {
        return p.normalize().startsWith(serverLocalDir.normalize());
    }

    /**
     * Verifica se “p” está dentro de storage/<username>/shared
     */
    private boolean isInsideStorageShared(Path p) {
        return p.normalize().startsWith(storageSharedDir.normalize());
    }

    /**
     * Utilitário que remove recursivamente algo do disco (file ou diretório).
     */
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

    /**
     * Copia recursivamente um diretório/arquivo de “source” para “target”.
     */
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

    /**
     * Lista os itens do “diretório físico” apontado por currentDir:
     *
     *   • Se currentDir == userStorageDir, devolve “local” e “shared”.
     *   • Se currentDir está dentro de serverLocalDir (ou subpastas), devolve conteúdo de serverLocalDir/sub...
     *   • Se currentDir está dentro de storageSharedDir (ou subpastas), devolve conteúdo de storageSharedDir/sub...
     */
    @Override
    public List<String> listFiles() throws RemoteException {
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
        // ——————————————————————————
        // 1) Se estamos em “/” (userStorageDir)
        // ——————————————————————————
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

        // ——————————————————————————
        // 2) Se estamos dentro de serverStorage/<username>/local/...
        // ——————————————————————————
        if (isInsideServerLocal(currentDir)) {
            // subir para “/” se estivermos exatamente em serverLocalDir e invocarem “cd ..”
            if ("..".equals(folderName)) {
                if (currentDir.equals(serverLocalDir)) {
                    currentDir = userStorageDir; // volta para “/”
                    return true;
                } else {
                    Path parent = currentDir.getParent();
                    // parent continuará dentro de serverLocalDir
                    if (parent != null && isInsideServerLocal(parent)) {
                        currentDir = parent;
                        return true;
                    }
                    return false;
                }
            }

            // navegar para subpasta em serverLocalDir
            Path target = currentDir.resolve(folderName).normalize();
            if (Files.isDirectory(target) && isInsideServerLocal(target)) {
                currentDir = target;
                return true;
            }
            return false;
        }

        // ——————————————————————————
        // 3) Se estamos dentro de storage/<username>/shared/...
        // ——————————————————————————
        if (isInsideStorageShared(currentDir)) {
            // subir para “/” se estivermos em storageSharedDir e “cd ..”
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

            // navegar para subpasta em storageSharedDir
            Path target = currentDir.resolve(folderName).normalize();
            if (Files.isDirectory(target) && isInsideStorageShared(target)) {
                currentDir = target;
                return true;
            }
            return false;
        }

        // Qualquer outro caso, não processamos
        return false;
    }

    /**
     * Cria uma pasta em serverStorage/cc/local/... (apenas quando currentDir está em serverLocalDir)
     * e ESPELHA em storage/cc/local/...
     */
    @Override
    public boolean createFolder(String folderName) throws RemoteException {
        // Só criamos diretório se estivermos dentro de serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path newDir = currentDir.resolve(folderName).normalize();
        try {
            if (Files.exists(newDir)) {
                return false; // caso já exista
            }
            // 1) cria efetivamente em serverStorage
            Files.createDirectories(newDir);

            // 2) espelha em storage/local se o newDir estiver dentro de serverLocalDir
            if (isInsideServerLocal(newDir)) {
                // caminho relativo a partir de serverLocalDir
                Path relative = serverLocalDir.relativize(newDir);
                // pasta espelho em storage
                Path mirror = storageLocalDir.resolve(relative);
                if (!Files.exists(mirror.getParent())) {
                    Files.createDirectories(mirror.getParent());
                }
                Files.createDirectories(mirror);
            }
            return true;
        } catch (IOException e) {
            throw new RemoteException("Erro criando pasta: " + folderName, e);
        }
    }

    /**
     * Renomeia em serverStorage/cc/local/... e também em storage/cc/local/... se estiver em local.
     */
    @Override
    public boolean rename(String oldName, String newName) throws RemoteException {
        // Só renomeia se estivermos dentro de serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path source = currentDir.resolve(oldName).normalize();
        Path target = currentDir.resolve(newName).normalize();
        if (!Files.exists(source) || !isInsideServerLocal(source) || !isInsideServerLocal(target.getParent())) {
            return false;
        }

        try {
            // 1) renomeia no serverStorage
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

            // 2) espelha em storage/local
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
            throw new RemoteException("Erro renomeando: " + oldName, e);
        }
    }

    /**
     * Move em serverStorage/cc/local/... e espelha em storage/cc/local/...
     */
    @Override
    public boolean move(String itemName, String targetFolder) throws RemoteException {
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
            throw new RemoteException(String.format("Erro movendo: %s → %s", source, newLocation), e);
        }
    }

    /**
     * Faz upload (byte[]) em serverStorage/cc/local/... e espelha em storage/cc/local/...
     */
    @Override
    public boolean upload(String filename, byte[] data) throws RemoteException {
        // Só upload se estivermos em serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path dst = currentDir.resolve(filename).normalize();
        try {
            // 1) grava de fato em serverStorage/local
            Files.createDirectories(dst.getParent());
            Files.write(dst, data);

            // 2) espelha em storage/local
            Path rel    = serverLocalDir.relativize(dst);
            Path mirror = storageLocalDir.resolve(rel);
            Files.createDirectories(mirror.getParent());
            Files.write(mirror, data);

            return true;
        } catch (IOException e) {
            throw new RemoteException("Erro no upload: " + filename, e);
        }
    }

    /**
     * Lê bytes de serverStorage/cc/local/filename
     */
    @Override
    public byte[] download(String filename) throws RemoteException {
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
            throw new RemoteException("Erro no download: " + filename, e);
        }
    }

    /**
     * Deleta recursivamente em serverStorage/cc/local/... e espelha em storage/cc/local/...
     */
    @Override
    public boolean delete(String name) throws RemoteException {
        // Só deleta se estivermos em serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }

        Path target = currentDir.resolve(name).normalize();
        if (!Files.exists(target)) {
            return false;
        }
        try {
            // 1) deleta no serverStorage/local
            deleteRecursively(target);

            // 2) espelha o delete em storage/local
            Path rel    = serverLocalDir.relativize(target);
            Path mirror = storageLocalDir.resolve(rel);
            if (Files.exists(mirror)) {
                deleteRecursively(mirror);
            }
            return true;
        } catch (IOException e) {
            throw new RemoteException("Falha ao deletar: " + name, e);
        }
    }

    /**
     * Compartilha algo que está em serverStorage/cc/local/... copiando para storage/<withUsername>/shared/...
     */
    @Override
    public boolean share(String name, String withUsername) throws RemoteException {
        // Só compartilha se estivermos em serverLocalDir
        if (!isInsideServerLocal(currentDir)) {
            return false;
        }
        Path source = currentDir.resolve(name).normalize();
        if (!Files.exists(source)) {
            return false;
        }

        // Destino em storage do outro usuário: storage/<withUsername>/shared
        Path recipientShared = STORAGE_ROOT.resolve(withUsername).resolve("shared");
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
            throw new RemoteException(
                    String.format("Erro ao compartilhar: %s → %s para usuário %s", name, target, withUsername),
                    e
            );
        }
    }

    /**
     * Retorna o “caminho lógico”:
     *   • Se currentDir == userStorageDir         → devolve “/”
     *   • Se currentDir dentro de serverLocalDir   → devolve “/local/…”
     *   • Se currentDir dentro de storageSharedDir → devolve “/shared/…”
     */
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

        // Se chegou aqui, algo incomum aconteceu. Retorna root por segurança.
        return "/";
    }
}