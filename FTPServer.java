import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class FTPServer {

    private static final Map<String, String> users = new HashMap<>();
    private static ServerSocket dataSocket; // Serveur pour la connexion de données
    private static int dataPort; // Port pour la connexion de données

    static {
        users.put("miage", "car"); // Mode de pass car / user miage
    }

    public static void main(String[] args) {
        int port = 2121; // Port pour la connexion de contrôle (fixe)

        try {
            // Initialisation de la socket pour la connexion de données avec un port dynamique
            dataSocket = new ServerSocket(0);
            dataPort = dataSocket.getLocalPort(); // Récupération du port attribué
            System.out.println("Connexion de données démarrée sur le port : " + dataPort);

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Serveur FTP démarré sur le port : " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // Client accepté, afficher son adresse
                    System.out.println("Client connecté : " + clientSocket.getInetAddress());
                    new Thread(() -> handleClient(clientSocket)).start();
                }
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

                    // **Afficher la commande reçue dans la console**
                    System.out.println("Commande reçue : " + command);

                    if (command.equalsIgnoreCase("QUIT")) {
                        output.write("221 Déconnexion en cours. Au revoir!\r\n".getBytes());
                        running = false;
                    } else if (command.equalsIgnoreCase("EPSV")) {
                        // Réponse à la commande EPSV
                        sendResponse(output, "229 Entering Extended Passive Mode (|||" + dataPort + "|)\r\n");

                        // Lancez un thread pour gérer la connexion de données
                        new Thread(() -> {
                            try (Socket dataConnection = dataSocket.accept()) {
                                System.out.println("Data connection established on port: " + dataPort);
                            } catch (IOException e) {
                                System.err.println("Error handling data connection: " + e.getMessage());
                            } finally {
                                try {
                                    dataSocket.close();
                                } catch (IOException e) {
                                    System.err.println("Error closing data socket: " + e.getMessage());
                                }
                            }
                        }).start();
                    } else if (command.startsWith("RETR")) {
                        // Réponse à la commande RETR
                        String[] parts = command.split(" ");
                        if (parts.length == 2) {
                            String fileName = parts[1];
                            File file = new File(fileName);

                            if (file.exists() && file.isFile()) {
                                // Envoi de la réponse 150
                                sendResponse(output, "150 Opening data connection for " + fileName + " (" + file.length() + " bytes).\r\n");

                                // Lancez un thread pour gérer la connexion de données et le transfert du fichier
                                new Thread(() -> {
                                    try (Socket dataConnection = dataSocket.accept(); // Accepter la connexion de données
                                         FileInputStream fis = new FileInputStream(file);
                                         OutputStream dataOut = dataConnection.getOutputStream()) {

                                        System.out.println("Sending file: " + fileName);

                                        byte[] buffer = new byte[4096];
                                        int bytesRead;

                                        // Lire et envoyer le fichier par la connexion de données
                                        while ((bytesRead = fis.read(buffer)) != -1) {
                                            dataOut.write(buffer, 0, bytesRead);
                                        }

                                        // Envoi de la réponse 226 après le transfert
                                        sendResponse(output, "226 Transfer complete.\r\n");
                                        System.out.println("File " + fileName + " sent successfully.");

                                    } catch (IOException e) {
                                        // En cas d'erreur pendant le transfert
                                        try {
                                            sendResponse(output, "451 Requested action aborted: local error in processing.\r\n");
                                        } catch (IOException e1) {
                                            e1.printStackTrace();
                                        }
                                        System.err.println("Error sending file: " + e.getMessage());
                                    }
                                }).start();
                            } else {
                                // Fichier non trouvé ou inaccessible
                                sendResponse(output, "550 File not found or not accessible.\r\n");
                                System.err.println("File not found: " + fileName);
                            }
                        } else {
                            // Mauvaise syntaxe de la commande
                            sendResponse(output, "501 Syntax error in parameters or arguments.\r\n");
                            System.err.println("Invalid RETR command syntax.");
                        }
                    } else if (command.equalsIgnoreCase("SYST")) {
                        sendResponse(output, "215 UNIX Type: L8\r\n");

                    } else if (command.equalsIgnoreCase("PWD")) {
                        sendResponse(output, "257 \"/\" est le répertoire courant.\r\n");

                    } else if (command.equalsIgnoreCase("LIST")) {
                        sendResponse(output, "150 Ouverture de la connexion de données pour la liste des fichiers.\r\n");
                        try (
                            Socket dataConnection = dataSocket.accept(); // Accepter la connexion de données
                            OutputStream dataOutput = dataConnection.getOutputStream()
                        ) {
                            // Simuler une liste de fichiers
                            dataOutput.write("-rw-r--r-- 1 user group 123 Jan 1 00:00 fichier1.txt\r\n".getBytes());
                            dataOutput.write("drwxr-xr-x 1 user group 0 Jan 1 00:00 dossier1\r\n".getBytes());
                        } catch (IOException e) {
                            sendResponse(output, "425 Impossible d'ouvrir la connexion de données.\r\n");
                            return;
                        }
                        sendResponse(output, "226 Liste des fichiers envoyée avec succès.\r\n");

                    } else if (command.toUpperCase().startsWith("CWD")) {
                        sendResponse(output, "250 Répertoire changé (simulé).\r\n");

                    } else if (command.equalsIgnoreCase("TYPE I")) {
                        // Réponse à la commande TYPE I (mode binaire)
                        sendResponse(output, "200 Type set to I (Binary mode).\r\n");
                        System.out.println("Commande TYPE I reçue : Mode binaire activé");

                    } else {
                        sendResponse(output, ("502 Commande non supportée : " + command + "\r\n"));
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

    // Authentification
    private static boolean authenticateUser(Scanner scanner, OutputStream output) throws IOException {
        String username = null;
        while (true) {
            String input = receiveMessage(scanner);

            if (input.toUpperCase().startsWith("USER")) {
                username = input.substring(5).trim(); // Extraire le nom d'utilisateur après "USER "
                sendResponse(output, "331 Nom d'utilisateur accepté. Veuillez entrer le mot de passe.\r\n");
            } else if (input.toUpperCase().startsWith("PASS")) {
                if (username == null) {
                    sendResponse(output, "503 Mauvaise séquence de commande. Veuillez d'abord envoyer USER.\r\n");
                } else {
                    String password = input.substring(5).trim(); // Extraire le mot de passe après "PASS "
                    if (users.containsKey(username) && users.get(username).equals(password)) {
                        sendResponse(output, "230 Authentification réussie. Bienvenue !\r\n");
                        return true;
                    } else {
                        sendResponse(output, "530 Nom d'utilisateur ou mot de passe incorrect. Veuillez réessayer.\r\n");
                    }
                }
            } else {
                sendResponse(output, "530 Vous devez vous authentifier avec USER et PASS.\r\n");
            }
        }
    }

    private static String receiveMessage(Scanner scanner) {
        return scanner.hasNextLine() ? scanner.nextLine() : "";
    }

    // Méthode d'envoi de réponse
    private static void sendResponse(OutputStream output, String message) throws IOException {
        output.write(message.getBytes());
    }
}
