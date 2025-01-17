import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import java.util.*;

public class p01Server {
    private static final int PORT = 3092; // Port number for the server to listen on
    // ThreadPool to handle multiple client connections
    private static ExecutorService pool = Executors.newFixedThreadPool(10);
    // Thread safe hashmap (Concurrent Hashmap) for handling multithreaded client
    // requests
    private static Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    // BlockingQueue for tasks awaiting processing
    private static BlockingQueue<MathTask> taskQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(PORT); // Open a server socket that listens on the specified port
        // Log that the server is up and running on specified port
        System.out.println("System is running on port " + PORT);

        new Thread(() -> { // Thread for processing math tasks
            while (true) {
                try {
                    MathTask task = taskQueue.take(); // Take a task from the queue
                    String result = processMathTask(task.expression); // Process the task
                    task.clientHandler.respond(result); // Send the result back to the client
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Handle the interruption
                }
            }
        }).start();

        while (true) { // Main server loop for accepting and handling client connections
            try {
                Socket clientSocket = serverSocket.accept(); // Wait for and accept client connection
                ClientHandler clientHandler = new ClientHandler(clientSocket); // New client handler created
                pool.execute(clientHandler); // Send to thread pool for execution
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage()); // Log the error message
            }
        }
    }

    static class MathTask { // Class for handling math tasks
        String expression;
        ClientHandler clientHandler;

        public MathTask(String expression, ClientHandler clientHandler) { // Constructor for MathTask
            this.expression = expression;
            this.clientHandler = clientHandler;
        }
    }

    static class ClientHandler implements Runnable { // Class for handling client connections
        private Socket socket;
        private String clientId;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) throws IOException { // Constructor for ClientHandler
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String initialMessage;
            // Await a response from the client for an untaken name
            while (true) {
                initialMessage = in.readLine();
                // empty string != closed connection. The client could send only "\n" which
                // would be read in by readLine as ""
                if (initialMessage == null) {
                    throw new IOException("Lost connection with the client");
                } else if (initialMessage.isEmpty()) {
                    System.out.println("Connecting client did not send a message");
                } else if (initialMessage.startsWith("0,")) {
                    this.clientId = initialMessage.substring(2).trim();
                    if (sessions.containsKey(clientId)) {
                        out.println("2,Name already taken");
                        System.out.println("An unknown client attempted to take the name " + clientId);
                    } else {
                        sessions.put(clientId, new ClientSession(clientId, System.currentTimeMillis()));
                        out.println("0," + clientId + " has joined the session.");
                        System.out.println("New Client Connected: " + clientId);
                        break;
                    }
                } else {
                    System.out.println("Client sent an invalid message: " + initialMessage);
                }
            }
        }

        public void run() { // Method for handling client connections
            try {
                String inputLine;
                while ((inputLine = in.readLine()) != null) { // Reads input from the client
                    if ("1,".equals(inputLine.trim())) { // Checks if the client wants to quit
                        closeConnection(); // Closes the connection
                        break;
                    }
                    // Handling mathematical expression prefixed with "3,"
                    if (inputLine.startsWith("3,")) {
                        // Log the received expression
                        System.out.println("Received from " + clientId + ": " + inputLine);
                        String expression = inputLine.substring(2); // Remove prefix
                        taskQueue.put(new MathTask(expression, this)); // Add the task to the queue
                    } else {
                        // Handle unexpected or malformed input
                        respond("4,Bad request format"); // Send bad equation error response for unexpected messages
                    }
                }
            } catch (IOException | InterruptedException e) { // Handles potential IO exceptions
                e.printStackTrace();
            } finally {
                closeConnection();
            }
        }

        private void closeConnection() { // Method for closing client connections
            try {
                ClientSession session = sessions.remove(clientId); // Removes the client session.
                if (session != null) {
                    // Calculate the duration of the session
                    long duration = System.currentTimeMillis() - session.connectionTime;
                    // Logs the session duration
                    System.out.println("Connection with " + clientId + " closed. Duration: " + duration + "ms");
                }
                if (socket != null && !socket.isClosed()) { // Checks if the socket is not closed.
                    socket.close(); // Closes the socket
                }
            } catch (IOException e) {
                e.printStackTrace(); // Handles potential IO exceptions.

            }
        }

        public void respond(String message) { // Method for responding to client requests
            out.println(message);
            out.flush();
        }
    }

    static class ClientSession {
        String clientId; // The client's unique identifier.
        long connectionTime; // The time at which the client connected.

    public ClientSession(String clientId, long connectionTime) {
        this.clientId = clientId; // Initializes the client ID.
        this.connectionTime = connectionTime; // Initializes the connection time.
        System.out.println("Client " + clientId + " connected at " + new Date(connectionTime).toString()); // Logs the client connection.
        }
    }

    /*
     * This function will process the math expressions provided by the clients. This
     * is thread-safe as it only uses local variables.
     * 
     * The algorithm itself is based upon the Shunting-Yard algorithm, but instead
     * of converting the expression into a post-fix expression
     * the algorithm directly calculates the value of the expression as well. This
     * is to catch some invalid expressions early (e.g., 2 +/ 2) and to make the
     * calculations faster.
     * 
     * This also features the use of a hand-written thread-safe Lexer to help get
     * the next character in the expression (e.g., the next number or operator)
     */
    private static String processMathTask(String expression) {
        /*
         * It doesn't make sense to handle empty expressions, so we'll return an error
         * because of it.
         */
        if (expression.isEmpty()) {
            return EquationErrorHandler.createErrMsg("Expression is empty", 0);
        }
        EquationLexer lexer = new EquationLexer(expression);
        /*
         * This stack contains EquationOp objects. An EquationOp object holds an
         * operator and the location of the operator within the math expression.
         * EquationOp was specifically made to help show better error messages if one
         * occurs because it stores the location of the associated operator.
         */
        Deque<EquationOp> opStack = new ArrayDeque<EquationOp>();
        /*
         * We create a stack holding Double values because a double can hold all 32-bit
         * integer values, and it would be easier to manage than having two stacks (one
         * for int values and the other for double values). BigDecimal isn't used
         * because most math expressions shouldn't exceed the 32-bit signed integer
         * range.
         */
        Deque<Double> numStack = new ArrayDeque<Double>();
        /*
         * The response variable is supposed to hold the response error message from a
         * called function.
         * If there is no response (an empty string), then the function went without
         * errors.
         * Otherwise, an error occurred and the response string holds the error message.
         * 
         * findLParen, popOpStack, and performOp follow this same format as they return
         * error responses.
         * These responses are already formatted with the expected protocol to send to
         * the client, so the calling code does not need any more logic to determine
         * which message to send.
         * In other words, all code to determine which message to send "5,<num>" or
         * "4,Error: ..." is done here
         */
        String response;

        /*
         * These Tokens and their associated uses are defined in EquationLexer.java
         */

        // Loop through the entire expression until we reach the end
        while (lexer.PeekTok() != EquationLexer.Token.TOK_EOF) {
            switch (lexer.PeekTok()) {
                case TOK_ERR:
                    return lexer.GetError();
                case TOK_NUMBER:
                    numStack.push(Double.parseDouble(lexer.PeekNum()));
                    break;
                case TOK_LPAREN:
                    opStack.push(new EquationOp(lexer.GetEqLoc(), EquationLexer.Token.TOK_LPAREN));
                    break;
                case TOK_RPAREN:
                    response = findLParen(numStack, opStack, expression, lexer.GetEqLoc());
                    if (!response.isEmpty())
                        return response;
                    break;
                case TOK_ADD:
                case TOK_SUB:
                case TOK_MUL:
                case TOK_DIV:
                case TOK_EXP:
                case TOK_MOD:
                case TOK_NEGATIVE:
                case TOK_POSITIVE:
                    response = popOpStack(numStack, opStack, lexer.PeekTok(), expression);
                    if (!response.isEmpty())
                        return response;
                    opStack.push(new EquationOp(lexer.GetEqLoc(), lexer.PeekTok()));
                case TOK_EOF:
                    break;
            }
            lexer.GetNextTok(); // Get the next token
        }
        // Creating an external EquationOp to not instantiate a new EquationOp each loop
        // through the opStack
        EquationOp operator;
        while (!opStack.isEmpty()) {
            operator = opStack.pop();
            if (operator.op == EquationLexer.Token.TOK_LPAREN) {
                return EquationErrorHandler.createErrMsg("Unfinished (", operator.location);
            }
            response = performOp(numStack, operator, expression);
            if (!response.isEmpty()) {
                return response;
            }
        }
        // If there is more than one number in the numStack, then there were not enough
        // operators to perform operations
        if (numStack.size() != 1) {
            return EquationErrorHandler.createErrMsg("Unfinished expression", 0);
        }
        Double val = numStack.pop();
        if (val % 1 == 0) { // Check if the number is an integer
            return "5," + String.valueOf(val.intValue()); // Return the integer value
        }
        return "5," + val.toString(); // Return the double value
    }

    // This method is only used when a ")" is found due to the Shunting-Yard
    // algorithm
    private static String findLParen(Deque<Double> numStack, Deque<EquationOp> opStack, String expression,
            int rparenLoc) {
        while (!opStack.isEmpty() && opStack.peek().op != EquationLexer.Token.TOK_LPAREN) {
            String response = performOp(numStack, opStack.pop(), expression);
            if (!response.isEmpty()) {
                return response;
            }
        }
        if (opStack.isEmpty()) {
            return EquationErrorHandler.createErrMsg("Unfinished )", rparenLoc);
        }
        // If the opStack isn't empty, then the loop stopped when a "(" was found.
        // Therefore, we can just pop it without checking
        opStack.pop();
        return "";
    }

    // Perform all operations with equal or greater precedence than the given
    // operator on the values within the numStack
    private static String popOpStack(Deque<Double> numStack, Deque<EquationOp> opStack, EquationLexer.Token op,
            String expression) {
        while (!opStack.isEmpty() && getOpPrecedence(opStack.peek().op) >= getOpPrecedence(op)) {
            String response = performOp(numStack, opStack.pop(), expression);
            if (!response.isEmpty()) {
                return response;
            }
        }
        return "";
    }

    // Perform a specific operation specified by the operator parameter
    private static String performOp(Deque<Double> numStack, EquationOp operator, String expression) {
        double lhs, rhs; // Variables for the left and right hand side of the equation
        /*
         * We check each operator that is given and see if it has enough operands to
         * operate on (or if the operands are valid for particular operations).
         * After both are validated, we then perform the operation and push it onto the
         * numStack
         */
        switch (operator.op) { // Switch statement for the operator
            case TOK_ADD: // Case for addition
                if (numStack.size() < 2) {
                    // Return error message if there are not enough operands
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                numStack.push(lhs + rhs); // Push the result onto the stack
                break;
            case TOK_SUB: // Case for subtraction
                if (numStack.size() < 2) { // Check if there are enough operands
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                numStack.push(lhs - rhs); // Push the result onto the stack
                break;
            case TOK_DIV: // Case for division
                if (numStack.size() < 2) { // Check if there are enough operands
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                if (rhs == 0) { // Check if the divisor is zero
                    return EquationErrorHandler.createErrMsg("Cannot divide by zero", operator.location);
                }
                numStack.push(lhs / rhs); // Push the result onto the stack
                break;
            case TOK_MUL:
                if (numStack.size() < 2) { // Check if there are enough operands
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                numStack.push(lhs * rhs); // Push the result onto the stack
                break;
            case TOK_MOD: // Case for modulo
                if (numStack.size() < 2) {
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                if (rhs == 0) { // Check if the divisor is zero
                    return EquationErrorHandler.createErrMsg("Cannot modulo by zero", operator.location);
                }
                numStack.push(lhs % rhs); // Push the result onto the stack
                break;
            case TOK_EXP: // Case for exponentiation
                if (numStack.size() < 2) {
                    return EquationErrorHandler.createErrMsg("Expected two operands", operator.location);
                }
                rhs = numStack.pop();
                lhs = numStack.pop();
                numStack.push(Math.pow(lhs, rhs)); // Push the result onto the stack
                break;
            case TOK_POSITIVE: // Case for positive
                if (numStack.isEmpty()) {
                    return EquationErrorHandler.createErrMsg("Expected an operand", operator.location);
                }
                numStack.push(Math.abs(numStack.pop())); // Push the result onto the stack
                break;
            case TOK_NEGATIVE: // Case for negative
                if (numStack.isEmpty()) {
                    return EquationErrorHandler.createErrMsg("Expected an operand", operator.location);
                }
                numStack.push(-1 * numStack.pop()); // Push the result onto the stack
                break;
            default:
                break;
        }
        return "";
    }

    // This stores the operator precedence of the operators within the math
    // expressions
    private static int getOpPrecedence(EquationLexer.Token op) {
        /*
         * The Tokens and their associated meanings are explained within
         * EquationLexer.java
         */
        switch (op) {
            case TOK_ADD:
            case TOK_SUB:
                return 1;
            case TOK_MUL:
            case TOK_DIV:
            case TOK_MOD:
                return 2;
            case TOK_EXP:
                return 3;
            case TOK_NEGATIVE:
            case TOK_POSITIVE:
                return 4;
            case TOK_LPAREN:
            default:
                return 0;
        }
    }
}
