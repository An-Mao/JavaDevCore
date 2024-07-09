package anmao.dev.core.math;

import java.util.Stack;

public class _Math extends _MathCDT{

    public static int maxToZero(int value , int max){
        value++;
        if (value > max){
            return 0;
        }
        return value;
    }
    public static float maxToZero(float value , float max){
        value++;
        if (value > max){
            return 0;
        }
        return value;
    }


    public static int log(Float number,int base) {
        int customBaseLog = 0;
        double powerOfBase = 1.0;

        while (powerOfBase < number) {
            powerOfBase *= base;
            customBaseLog++;
        }

        if (powerOfBase > number) {
            customBaseLog--;
        }
        return customBaseLog;
    }
    public static int log(Double number,int base) {
        int customBaseLog = 0;
        double powerOfBase = 1.0;

        while (powerOfBase < number) {
            powerOfBase *= base;
            customBaseLog++;
        }

        if (powerOfBase > number) {
            customBaseLog--;
        }
        return customBaseLog;
    }
    public static int log2Floor(int n){
        assert n >= 1;
        int log = 0;
        if(n > 0xffff){
            n >>>= 16;
            log = 16;
        }
        if(n > 0xff){
            n >>>= 8;
            log |= 8;
        }
        if(n > 0xf){
            n >>>= 4;
            log |= 4;
        }
        if(n > 0b11){
            n >>>= 2;
            log |= 2;
        }
        return log + (n >>> 1);
    }
    public static double evaluate(String expression) {
        char[] tokens = expression.toCharArray();

        // Stack for numbers
        Stack<Double> values = new Stack<>();

        // Stack for operators
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < tokens.length; i++) {
            // Skip whitespace
            if (tokens[i] == ' ')
                continue;

            // If current token is a number, push it to stack for numbers
            if (Character.isDigit(tokens[i]) || tokens[i] == '.') {
                StringBuilder sb = new StringBuilder();
                // There may be more than one digits in the number
                while (i < tokens.length && (Character.isDigit(tokens[i]) || tokens[i] == '.')) {
                    sb.append(tokens[i++]);
                }
                values.push(Double.parseDouble(sb.toString()));
                // Since the index is incremented one extra in loop
                // for(i++)
                i--;
            }
            // If current token is an opening brace, push it to operators stack
            else if (tokens[i] == '(')
                operators.push(tokens[i]);

                // If current token is a closing brace, solve entire brace
            else if (tokens[i] == ')') {
                while (operators.peek() != '(')
                    values.push(applyOp(operators.pop(), values.pop(), values.pop()));
                operators.pop();
            }

            // If current token is an operator
            else if (tokens[i] == '+' || tokens[i] == '-' ||
                    tokens[i] == '*' || tokens[i] == '/') {
                // While top of 'operators' has same or greater precedence to current
                // token, which is an operator. Apply operator on top of 'operators'
                // to top two elements in values stack
                while (!operators.empty() && hasPrecedence(tokens[i], operators.peek()))
                    values.push(applyOp(operators.pop(), values.pop(), values.pop()));

                // Push current token to 'operators'.
                operators.push(tokens[i]);
            }
        }

        // Entire expression has been parsed at this point, apply remaining
        // operators to remaining values
        while (!operators.empty())
            values.push(applyOp(operators.pop(), values.pop(), values.pop()));

        // Top of 'values' contains result, return it
        return values.pop();
    }

    // Returns true if 'op2' has higher or same precedence as 'op1',
    // otherwise returns false.
    public static boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')')
            return false;
        return (op1 != '*' && op1 != '/') || (op2 != '+' && op2 != '-');
    }

    // A utility method to apply an operator 'op' on operands 'a'
    // and 'b'. Return the result.
    public static double applyOp(char op, double b, double a) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> {
                if (b == 0)
                    throw new
                            UnsupportedOperationException("Cannot divide by zero");
                yield a / b;
            }
            default -> 0;
        };
    }
}
