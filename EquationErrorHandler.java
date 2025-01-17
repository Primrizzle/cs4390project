/*
 * This class was made to make a uniform error reporting system across the lexer and the server.
 * It creates the error message using the correct protocol to send to the client.
 */
public class EquationErrorHandler {
    public static String createErrMsg(String errString, int loc) {
        return "4,Error: " + errString + " at char " + loc;
    }
}