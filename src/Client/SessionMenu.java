package Client;

import Interface.SessionFactory;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Scanner;

public class SessionMenu {

    private final String username;
    private final SessionFactory session;
    public SessionMenu(String username, SessionFactory session) {
        this.username = username;
        this.session = session;
        run();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\nWelcome, " + username + ". Type `help` to see available commands.");

        while (true) {
            try {
                String currentPath = session.getPath();
                System.out.print(username + ":" + currentPath + "$ ");
            } catch (RemoteException e) {
                System.err.println("Error fetching current directory.");
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split(" ");
            String command = parts[0];

            switch (command) {
                case "ls" -> listFiles();
                case "cd" -> {
                    if (parts.length < 2) System.out.println("Usage: cd <folder>");
                    else changeDirectory(parts[1]);
                }
                case "create" -> {
                    if (parts.length < 2) System.out.println("Usage: create <folder>");
                    else createFolder(parts[1]);
                }
                case "rename" -> {
                    if (parts.length < 3) System.out.println("Usage: rename <old_name> <new_name>");
                    else rename(parts[1], parts[2]);
                }
                case "move" -> {
                    if (parts.length < 3) System.out.println("Usage: move <item_name> <target_folder>");
                    else move(parts[1], parts[2]);
                }
                case "upload" -> {
                    if (parts.length < 2) System.out.println("Usage: upload <local_path>");
                    else uploadFile(parts[1]);
                }
                case "download" -> {
                    if (parts.length < 2) System.out.println("Usage: download <filename>");
                    else downloadFile(parts[1]);
                }
                case "delete" -> {
                    if (parts.length < 2) System.out.println("Usage: delete <filename>");
                    else deleteFile(parts[1]);
                }
                case "share" -> {
                    if (parts.length < 3) System.out.println("Usage: share <filename> <target_user>");
                    else shareFile(parts[1], parts[2]);
                }
                case "help" -> printHelp();
                case "exit" -> {
                    System.out.println("Logging out...");
                    /*try {
                        fileSystem.saveUserData(username);
                    }catch (RemoteException e){
                        System.err.println("Error in saving data: " + e.getMessage());
                    }*/
                    return;
                }
                default -> System.out.println("Unknown command: " + command + ". Type `help` for a list of commands.");
            }
        }
    }

    private void listFiles() {
        try {
            for (String s : session.listFiles())
                System.out.println(s);
        } catch (RemoteException e) {
            System.err.println("Error listing files: " + e.getMessage());
        }
    }

    private void changeDirectory(String folder) {
        try {
            session.changeDirectory(folder);
        } catch (RemoteException ignore) {
        }
    }

    private void createFolder(String folderName) {
        try {
            session.createFolder(folderName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void rename(String oldName, String newName) {
        try {
            session.rename(oldName, newName);
        } catch (RemoteException ignored) {
        }
    }
    private void move(String itemName, String targetFolder) {
        try {
            boolean success = session.move(itemName, targetFolder);
        } catch (RemoteException e) {
            System.err.println("Failed to move item: " + e.getMessage());
        }
    }
    private void uploadFile(String localPath) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(localPath));
            String filename = java.nio.file.Paths.get(localPath).getFileName().toString();
            session.upload(filename, data);
        } catch (Exception ignored) {
        }
    }

    private void downloadFile(String filename) {
        try {
            byte[] content = session.download(filename);
            System.out.println("Downloaded content (preview):\n" +
                    new String(content, 0, Math.min(content.length, 200)));
            // You may save it if needed, but the requirement is to keep client stateless
        } catch (Exception e) {
            System.err.println("Failed to download file: " + e.getMessage());
        }
    }

    private void deleteFile(String filename) {
        try {
            session.delete(filename);
        } catch (RemoteException ignored) {
        }
    }

    private void shareFile(String filename, String targetUser) {
        try {
            session.shareWithUser(filename, targetUser);
        } catch (RemoteException ignored) {
        }
    }


    private void printHelp() {
        System.out.println("""
            Available commands:
              ls                            List files
              cd <folder>                   Change directory
              create <folder>               Create new folder
              rename <name> <newname>       Rename a file or folder
              move <item> <target_folder>   Move a file or folder into a subfolder
              upload <local_path>           Upload a file to your remote area
              download <filename>           Download a file from remote (in memory)
              delete <name>                 Delete a file or folder
              share <filename> <user>       Share a file with another user
              help                          Show this help
              exit                          Exit the session
        """);
    }
}
