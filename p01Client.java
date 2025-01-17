import java.net.*;
import java.util.Scanner;
import java.io.*;

public class p01Client {
    private static final int PORT = 3092;
    private static final String address = "localhost";

    public static void main(String[] args) throws UnknownHostException, IOException {
        Socket clientSocket;
        Scanner userInput = new Scanner(System.in);
        // userName is meant to store the name of the user, while userEquation stores
        // the equations that the user gives to the client
        // serverResponse is the message given back from the server
        String userName, serverResponse, userEquation;
        BufferedReader fromServer;
        PrintWriter toServer;

        System.out.println("Attempting to connect to the UTD Math Server");
        clientSocket = new Socket(address, PORT);

        fromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        toServer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);

        /*
         * Repeatedly poll the user for a name and act accordingly to the server
         * responses.
         */
        while (true) {
            System.out.print("What is your name? ");
            userName = userInput.nextLine().trim();
            toServer.println("0," + userName);
            serverResponse = fromServer.readLine();
            // empty string != closed connection. The client could send only "\n" which
            // would be read in by readLine as ""
            if (serverResponse == null) {
                System.out.println("Lost connection to server. Closing client");
                closeResources(userInput, clientSocket);
                return;
            } else if (serverResponse.isEmpty()) {
                System.out.println("The server did not send back a response");
            } else if (serverResponse.equals("0," + userName + " has joined the session.")) {
                System.out.println("Connected to the UTD Math Server as " + userName);
                break;
            } else if (serverResponse.startsWith("2,")) {
                System.out.println("Server Error: Name already taken");
            } else {
                System.out.println("Server sent back an unexpected message: " + serverResponse);
                closeResources(userInput, clientSocket);
                return;
            }
        }
        /*
         * This while loop should continue until the client enters "quit" (any case)
         * Otherwise, the client program will send the expression to the server for the
         * server to calculate.
         */
        while (true) {
            System.out.print("Equation: ");
            userEquation = userInput.nextLine().trim();
            if (userEquation.equalsIgnoreCase("quit")) {
                toServer.println("1,");
                System.out.println("Closing the server connection and quitting the client");
                break;
            }
            toServer.println("3," + userEquation);
            serverResponse = fromServer.readLine();
            if (serverResponse == null) {
                System.out.println("Lost connection to server. Closing client");
                break;
            } else if (serverResponse.isEmpty()) {
                System.out.println("Server did not return any response");
            } else if (serverResponse.startsWith("5,")) {
                System.out.println("Answer: " + serverResponse.substring(2));
            } else if (serverResponse.startsWith("4,")) {
                System.out.println(formatErrMsg(serverResponse.substring(2), userEquation));
            } else {
                System.out.println("Server sent back an unexpected message " + serverResponse);
            }
        }
        closeResources(userInput, clientSocket);
    }

    // Close every connection before ending the program
    private static void closeResources(Scanner scanner, Socket socket) {
        scanner.close();
        try {
            if (socket.isConnected()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    /*
     * Error Messages sent by the server are one line messages containing what
     * caused the error and where.
     * - <err_msg> at char <num>
     * The server has the exact same copy as the one stored in the client program,
     * and so we can use the same indexing
     * that the server uses to pretty print an error message that is somewhat useful
     * to see
     * 
     * An example formatted error message:
     * Error: Unfinished ( at char 3
     * 
     * 2+(3
     * ^
     * The characters are 1-indexed to make it easier for non-CS people to read
     */
    private static String formatErrMsg(String errMsg, String userEquation) {
        String formattedErrMsg = errMsg + "\n\n\t" + userEquation + "\n";
        int loc = Integer.parseInt(errMsg.substring(errMsg.lastIndexOf("char") + 5));
        String padding = "\t";
        for (int i = 0; i < loc - 1; i++) {
            padding += " ";
        }
        formattedErrMsg += padding + "^";
        return formattedErrMsg;
    }
}
