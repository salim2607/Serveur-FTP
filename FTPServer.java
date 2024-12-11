import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class FTPServer {

    private static final Map<String, String> users = new HashMap<>();

    static {
        users.put("miage", "car"); // mode de pass car / user miage
    }

    public static void main(String[] args) {
        int port = 2121; // le numero de port 2121
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
        try (InputStream input = clientSocket.getInputStream(); 
             OutputStream output = clientSocket.getOutputStream(); 
             Scanner scanner = new Scanner(input)) {

            output.write("220 Bienvenue sur le serveur FTP\r\n".getBytes());

            if (authenticateUser(scanner, output)) {
                while (true) {
                    String command = scanner.hasNextLine() ? scanner.nextLine() : "";
                    if (command.equalsIgnoreCase("QUIT")) {
                        output.write("221 Déconnexion en cours. Au revoir!\r\n".getBytes());
                        break;
                    }
                    output.write(("502 Commande non supportée : " + command + "\r\n").getBytes());
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
        String username, password;
        while (true) {
            output.write("331 Veuillez entrer votre nom d'utilisateur (LOGIN):\r\n".getBytes());
            username = receiveMessage(scanner);
            
            System.out.println("user " + username);  // Affiche le nom d'utilisateur
            output.write("331 Veuillez entrer votre mot de passe (PASSWORD):\r\n".getBytes());
            password = receiveMessage(scanner);
            System.out.println("pass " + password);  // Affiche le mot de passe

            if (users.containsKey(username) && users.get(username).equals(password)) {
                output.write(("230 Authentification réussie. Bienvenue, " + username + "!\r\n").getBytes());
                return true;
            }
            output.write("530 Nom d'utilisateur ou mot de passe incorrect. Veuillez réessayer.\r\n".getBytes());
        }
    }

    private static String receiveMessage(Scanner scanner) {
        return scanner.hasNextLine() ? scanner.nextLine() : "";
    }
}
