/*
 * This class was made to make it easier to break down the math expression into smaller parts without putting that logic within the calculation function itself.
 * 
 * Explaination of Variables:
 * 
 * equation: Represents the math expression to break down into smaller chunks. This string will not change and will be traversed instead using eqLoc
 * eqLoc: The current location of the next character to read (and to determine if it is a character, a part of a character, whitespace, or an invalid character)
 * curNum: The string that stores a valid number. This will be updated whenever curTok is assigned to TOK_NUMBER
 * errMsg: The string that stores the error message if one occurred. This will be updated whenever curTok is assigned to TOK_ERR
 * possibleUnary: This is a boolean flag to help the lexer determine if a (+/-) it encounters is a positive/negative sign or an addition/subtraction sign
 * There are currently 3 possible cases this is true:
 *  1. If the (+/-) is the first character in the expression
 *  2. If an operator preceeds (+/-)
 *  3. If a left parentheses preceeds (+/-)
 */
public class EquationLexer {
    private String equation;
    private String curNum;
    private Token curTok;
    private String errMsg;
    private int eqLoc;
    private boolean possibleUnary;

    /*
     * These tokens represent the types of characters that can be encountered within
     * a math expression.
     * 
     * We did not include a root operator as that can be done with the exponent
     * operator
     */
    public enum Token {
        /*
         * We do not make a distinction between a int string and a double string as both
         * are stored in as a Double value. This is done because all int values can be
         * stored within a Double (a double can store 32-bit signed integer values).
         * 
         * The string of the number is stored within curNum.
         */
        TOK_NUMBER,

        /*
         * Binary Operators
         */
        TOK_ADD, // Addition
        TOK_SUB, // Subtraction
        TOK_MUL, // Multiplication
        TOK_DIV, // Division
        TOK_MOD, // Modulo
        TOK_EXP, // Exponent

        /*
         * These are unary operators and they were made distinct to allow expressions
         * like -(2-3) to be calculated.
         * In other words if we tokenize "-4", we would get the tokens TOK_NEGATIVE and
         * TOK_NUMBER
         */
        TOK_POSITIVE,
        TOK_NEGATIVE,

        TOK_LPAREN, // Left Parentheses
        TOK_RPAREN, // Right Parentheses
        TOK_EOF, // To signify that there are no new tokens to read
        /*
         * If this token is encountered, then errMsg will be set to the error that
         * occurred.
         */
        TOK_ERR,
    }

    public EquationLexer(String e) {
        ChangeEquation(e);
    }

    public void ChangeEquation(String e) {
        equation = e;
        curTok = Token.TOK_EOF;
        eqLoc = 0;
        possibleUnary = true;
        GetNextTok();
    }

    public String PeekNum() {
        return curNum;
    }

    public String GetError() {
        return errMsg;
    }

    public EquationLexer.Token PeekTok() {
        return curTok;
    }

    public int GetEqLoc() {
        return eqLoc;
    }

    public void GetNextTok() {
        char current_char = '\0';
        // Go through the entire expression and break whenever a non-whitespace
        // character is encountered
        for (; eqLoc < equation.length(); eqLoc++) {
            current_char = equation.charAt(eqLoc);
            if (current_char != ' ' && current_char != '\t' && current_char != '\r') {
                eqLoc++;
                break;
            }
        }
        // Used to determine if eqLoc >= equation.length() and no characters were read
        if (current_char == '\0') {
            curTok = Token.TOK_EOF;
            return;
        }
        switch (current_char) {
            case '+':
                if (possibleUnary) {
                    curTok = Token.TOK_POSITIVE;
                    possibleUnary = false;
                } else {
                    curTok = Token.TOK_ADD;
                    possibleUnary = true;
                }
                break;
            case '-':
                if (possibleUnary) {
                    curTok = Token.TOK_NEGATIVE;
                    possibleUnary = false;
                } else {
                    curTok = Token.TOK_SUB;
                    possibleUnary = true;
                }
                break;
            case '/':
                curTok = Token.TOK_DIV;
                possibleUnary = true;
                break;
            case '*':
                curTok = Token.TOK_MUL;
                possibleUnary = true;
                break;
            case '%':
                curTok = Token.TOK_MOD;
                possibleUnary = true;
                break;
            case '^':
                curTok = Token.TOK_EXP;
                possibleUnary = true;
                break;
            case '(':
                curTok = Token.TOK_LPAREN;
                possibleUnary = true;
                break;
            case ')':
                curTok = Token.TOK_RPAREN;
                possibleUnary = false;
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                HandleDigit(current_char, false);
                break;
            case '.':
                HandleDigit(current_char, true);
                break;
            default:
                curTok = Token.TOK_ERR;
                errMsg = EquationErrorHandler.createErrMsg("Unexpected token " + current_char, eqLoc);
                break;
        }
    }

    /*
     * This function handles the processing of digits to ensure that the given
     * number is a valid number
     * starting_char: The first char in the number string. Mainly used to not
     * process the same char again in the for loop
     * is_decimal: Since this function can be called whenever the first_char is a
     * decimal point, it was also put as a function parameter to help speed things
     * up
     */
    private void HandleDigit(char starting_char, boolean is_decimal) {
        curNum = String.valueOf(starting_char);
        boolean process_num = true;
        char current_char = '\0';
        for (; eqLoc < equation.length() && process_num; eqLoc++) {
            current_char = equation.charAt(eqLoc);
            switch (current_char) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    curNum += current_char;
                    break;
                case '.':
                    if (is_decimal) {
                        eqLoc++;
                        errMsg = EquationErrorHandler.createErrMsg("Decimal point appeared more than once in number",
                                eqLoc);
                        curTok = Token.TOK_ERR;
                        return;
                    } else {
                        is_decimal = true;
                        curNum += current_char;
                    }
                case '_':
                    break;
                default:
                    process_num = false;
                    eqLoc--;
                    break;
            }
        }
        if (curNum.equals(".")) {
            errMsg = EquationErrorHandler.createErrMsg("Unfinished decimal number", eqLoc);
            curTok = Token.TOK_ERR;
        } else {
            curTok = Token.TOK_NUMBER;
        }
        possibleUnary = false;
    }
}
