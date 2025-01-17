/*
 * This class was made to make it easier to report errors whenever a bad operation took place (e.g., if not enough operands for given)
 * op: The actual operation to perform (TOK_ADD, TOK_SUB, etc.)
 * location: The location of the operator within the math expression given by the client
 */
public class EquationOp {
    public int location;
    public EquationLexer.Token op;
    public EquationOp(int l, EquationLexer.Token o) {
        location = l;
        op = o;
    }
}
