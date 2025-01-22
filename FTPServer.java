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
        // Utilisateurs et mots de passe par défaut
        users.put("miage", "car");
    }

    public static void main(String[] args) {
        int controlPort = 2121; // Port pour la connexion de contrôle

        try {
            // Initialisation de la connexion de données avec un port dynamique
            dataSocket = new ServerSocket(0);
            dataPort = dataSocket.getLocalPort();  // Port dynamique attribué
            System.out.println("Connexion de données démarrée sur le port : " + dataPort);

            try (ServerSocket serverSocket = new ServerSocket(controlPort)) {
                // Démarrage du serveur FTP sur le port de contrôle
                System.out.println("Serveur FTP démarré sur le port : " + controlPort);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // Acceptation d'un client et gestion de la connexion dans un thread séparé
                    System.out.println("Client connecté : " + clientSocket.getInetAddress());
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'initialisation du serveur : " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();
            Scanner scanner = new Scanner(input)
        ) {
            // Envoi du message de bienvenue
            sendResponse(output, "220 Bienvenue sur le serveur FTP\r\n");

            // Authentification de l'utilisateur
            if (authenticateUser(scanner, output)) {
                boolean running = true;
                while (running) {
                    // Attente de la commande du client
                    String command = scanner.hasNextLine() ? scanner.nextLine() : "";

                    System.out.println("Commande reçue : " + command);

                    // Quitter la session si la commande est QUIT
                    if ("QUIT".equalsIgnoreCase(command)) {
                        sendResponse(output, "221 Déconnexion en cours. Au revoir!\r\n");
                        running = false;
                    } else if ("EPSV".equalsIgnoreCase(command)) {
                        handleEPSVCommand(output);
                    } else if (command.startsWith("RETR")) {
                        handleRETRCommand(command, output);
                    } else if ("SYST".equalsIgnoreCase(command)) {
                        sendResponse(output, "215 UNIX Type: L8\r\n");
                    } else if ("PWD".equalsIgnoreCase(command)) {
                        sendResponse(output, "257 \"/\" est le répertoire courant.\r\n");
                    } else if ("LIST".equalsIgnoreCase(command)) {
                        handleLISTCommand(output);
                    } else if (command.startsWith("CWD")) {
                        handleCWDCommand(command, output);
                    } else if ("TYPE I".equalsIgnoreCase(command)) {  // Ajout de la gestion de TYPE I
                        sendResponse(output, "200 Type set to I (binary mode).\r\n");
                    } else {
                        sendResponse(output, "502 Commande non supportée : " + command + "\r\n");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du traitement du client : " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client déconnecté");
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la connexion client : " + e.getMessage());
            }
        }
    }

    private static boolean authenticateUser(Scanner scanner, OutputStream output) throws IOException {
        String username = null;
        while (true) {
            String input = receiveMessage(scanner);

            if (input.startsWith("USER")) {
                username = input.substring(5).trim();
                sendResponse(output, "331 Nom d'utilisateur accepté. Veuillez entrer le mot de passe.\r\n");
            } else if (input.startsWith("PASS")) {
                if (username == null) {
                    sendResponse(output, "503 Mauvaise séquence de commande. Veuillez d'abord envoyer USER.\r\n");
                } else {
                    String password = input.substring(5).trim();
                    if (users.containsKey(username) && users.get(username).equals(password)) {
                        sendResponse(output, "230 Authentification réussie. Bienvenue !\r\n");
                        return true;
                    } else {
                        sendResponse(output, "530 Nom d'utilisateur ou mot de passe incorrect.\r\n");
                    }
                }
            } else {
                sendResponse(output, "530 Vous devez vous authentifier avec USER et PASS.\r\n");
            }
        }
    }

    private static void handleEPSVCommand(OutputStream output) throws IOException {
        // Récupérer un port dynamique pour la connexion de données (avec la plage de ports)
        try {
            dataPort = getAvailablePort();
            dataSocket = new ServerSocket(dataPort); // Créer un serveur pour la connexion de données

            sendResponse(output, "229 Entering Extended Passive Mode (|||" + dataPort + "|)\r\n");
            new Thread(() -> {
                try (Socket dataConnection = dataSocket.accept()) {
                    System.out.println("Connexion de données établie sur le port : " + dataPort);
                } catch (IOException e) {
                    System.err.println("Erreur lors de la gestion de la connexion de données : " + e.getMessage());
                }
            }).start();
        } catch (IOException e) {
            System.err.println("Erreur lors de la gestion de la connexion de données : " + e.getMessage());
            sendResponse(output, "425 Impossible d'établir la connexion de données.\r\n");
        }
    }

    private static int getAvailablePort() throws IOException {
        int startPort = 30000;
        int endPort = 30100;
        for (int port = startPort; port <= endPort; port++) {
            try (ServerSocket testSocket = new ServerSocket(port)) {
                return port; // Si le port est disponible, on le retourne
            } catch (IOException e) {
                // Si le port est occupé, essayer le suivant
            }
        }
        throw new IOException("Aucun port disponible dans la plage définie.");
    }

    private static void handleRETRCommand(String command, OutputStream output) throws IOException {
        String[] parts = command.split(" ");
        if (parts.length == 2) {
            String fileName = parts[1];
            File file = new File(fileName);

            if (file.exists() && file.isFile()) {
                sendResponse(output, "150 Opening data connection for " + fileName + " (" + file.length() + " bytes).\r\n");
                new Thread(() -> {
                    try (Socket dataConnection = dataSocket.accept();
                         FileInputStream fis = new FileInputStream(file);
                         OutputStream dataOut = dataConnection.getOutputStream()) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dataOut.write(buffer, 0, bytesRead);
                        }

                        sendResponse(output, "226 Transfer complete.\r\n");
                        System.out.println("Fichier " + fileName + " envoyé avec succès.");

                    } catch (IOException e) {
                        System.err.println("Erreur d'envoi du fichier : " + e.getMessage());
                    }
                }).start();
            } else {
                sendResponse(output, "550 File not found or not accessible.\r\n");
            }
        } else {
            sendResponse(output, "501 Syntax error in parameters or arguments.\r\n");
        }
    }

    private static void handleLISTCommand(OutputStream output) throws IOException {
        try {
            // Utiliser le répertoire courant mis à jour
            File currentDirectory = new File(System.getProperty("user.dir"));
            File[] files = currentDirectory.listFiles();
    
            if (files == null || files.length == 0) {
                sendResponse(output, "150 Aucun fichier ou répertoire dans le répertoire courant.\r\n");
                sendResponse(output, "226 Liste terminée.\r\n");
                return;
            }
    
            // Envoyer une réponse initiale pour indiquer que le transfert va commencer
            sendResponse(output, "150 Ouverture de la connexion de données pour la liste des fichiers.\r\n");
    
            // Ouvrir un flux de sortie pour envoyer la liste via la connexion de données
            try (Socket dataConnection = dataSocket.accept(); // Accepter la connexion de données
                 OutputStream dataOutput = dataConnection.getOutputStream()) {
                
                // Envoi de la liste des fichiers
                for (File file : files) {
                    String fileInfo = (file.isDirectory() ? "Dossier " : "Fichier ") + file.getName() + "\r\n";
                    dataOutput.write(fileInfo.getBytes());
                }
    
            } catch (IOException e) {
                sendResponse(output, "425 Impossible d'ouvrir la connexion de données.\r\n");
                return;
            }
    
            // Indiquer que la liste est terminée
            sendResponse(output, "226 Liste terminée.\r\n");
    
        } catch (IOException e) {
            try {
                sendResponse(output, "451 Erreur pendant l'envoi de la liste.\r\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } finally {
            // Fermer la connexion de données à la fin du transfert
            try {
                if (dataSocket != null && !dataSocket.isClosed()) {
                    dataSocket.close();
                    System.out.println("Connexion de données fermée après LIST.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    

    private static void handleCWDCommand(String command, OutputStream output) throws IOException {
        String[] parts = command.split(" ");
        if (parts.length == 2) {
            String path = parts[1];
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                sendResponse(output, "250 Répertoire changé en " + path + ".\r\n");
            } else {
                sendResponse(output, "550 Répertoire non trouvé.\r\n");
            }
        } else {
            sendResponse(output, "501 Commande invalide.\r\n");
        }
    }

    private static void sendResponse(OutputStream output, String message) throws IOException {
        output.write(message.getBytes());
        output.flush();
    }

    private static String receiveMessage(Scanner scanner) {
        return scanner.hasNextLine() ? scanner.nextLine() : "";
    }
}
