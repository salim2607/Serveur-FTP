import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class FTPServer_2 {

    private static final Map<String, String> users = new HashMap<>();

    static {
        users.put("miage", "car"); // Utilisateur : miage / Mot de passe : car
    }

    public static void main(String[] args) {
        int port = 2121; // Numéro de port du serveur FTP (peut être changé en 21 si nécessaire)
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur FTP démarré sur le port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté : " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();
            Scanner scanner = new Scanner(input)
        ) {
            // Message de bienvenue
            output.write("220 Bienvenue sur le serveur FTP\r\n".getBytes());

            // Authentification de l'utilisateur
            if (authenticateUser(scanner, output)) {
                boolean running = true;
                while (running) {
                    String command = scanner.hasNextLine() ? scanner.nextLine() : "";

                    if (command.equalsIgnoreCase("QUIT")) {
                        output.write("221 Déconnexion en cours. Au revoir!\r\n".getBytes());
                        running = false;
                    } else if (command.equalsIgnoreCase("SYST")) {
                        output.write("215 UNIX Type: L8\r\n".getBytes());
                    } else if (command.equalsIgnoreCase("PWD")) {
                        output.write("257 \"/\" est le répertoire courant.\r\n".getBytes());
                    } else if (command.toUpperCase().startsWith("CWD")) {
                        output.write("250 Répertoire changé (simulé).\r\n".getBytes());
                    } else if (command.equalsIgnoreCase("LIST")) {
                        output.write("150 Ouverture de la liste des fichiers.\r\n".getBytes());
                        // Simuler une liste de fichiers
                        output.write("-rw-r--r-- 1 user group 123 Jan 1 00:00 fichier1.txt\r\n".getBytes());
                        output.write("drwxr-xr-x 1 user group 0 Jan 1 00:00 dossier1\r\n".getBytes());
                        output.write("226 Liste envoyée.\r\n".getBytes());
                    } else {
                        output.write(("502 Commande non supportée : " + command + "\r\n").getBytes());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client déconnecté");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean authenticateUser(Scanner scanner, OutputStream output) throws IOException {
        String username = null;
        while (true) {
            String input = receiveMessage(scanner);

            if (input.toUpperCase().startsWith("USER")) {
                username = input.substring(5).trim(); // Extraire le nom d'utilisateur après "USER "
                output.write("331 Nom d'utilisateur accepté. Veuillez entrer le mot de passe.\r\n".getBytes());
            } else if (input.toUpperCase().startsWith("PASS")) {
                if (username == null) {
                    output.write("503 Mauvaise séquence de commande. Veuillez d'abord envoyer USER.\r\n".getBytes());
                } else {
                    String password = input.substring(5).trim(); // Extraire le mot de passe après "PASS "
                    if (users.containsKey(username) && users.get(username).equals(password)) {
                        output.write("230 Authentification réussie. Bienvenue !\r\n".getBytes());
                        return true;
                    } else {
                        output.write("530 Nom d'utilisateur ou mot de passe incorrect. Veuillez réessayer.\r\n".getBytes());
                    }
                }
            } else {
                output.write("530 Vous devez vous authentifier avec USER et PASS.\r\n".getBytes());
            }
        }
    }

    private static String receiveMessage(Scanner scanner) {
        return scanner.hasNextLine() ? scanner.nextLine() : "";
    }
}
