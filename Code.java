enum BinaryOperator {
    PLUS("+", Precedence.ADD), MINUS("-" , Precedence.ADD),
    DIVIDE("/", Precedence.MULT), MULTIPLY("*", Precedence.MULT);

    int precedence;
    String symbol;
    BinaryOperator(String symbol, int precedence) {
        this.symbol = symbol;
        this.precedence = precedence;
    }

    static int MAX_PRECEDENCE = Precedence.MAX;
    static BinaryOperator fromChar(char chr) {
        return switch (chr) {
            case '+' -> PLUS;
            case '-' -> MINUS;
            case '*' -> MULTIPLY;
            case '/' -> DIVIDE;
            default -> throw new IllegalArgumentException("Unknown character: " + chr);
        };
    }

    static boolean isBinaryOperator(char chr) {
        return "+-*/".indexOf(chr) != -1;
    }

    interface Precedence {
        int ADD = 0;
        int MULT = 1;
        int MAX = 2;
    }
}

sealed interface Token {
    record NUMBER(int value) implements Token {}
    record BINARY_OPERATOR(BinaryOperator value) implements Token {}
    record EOF() implements Token {}

    static EOF EOF_INSTANCE = new EOF();
    static NUMBER number(int value) { return new NUMBER(value); }
    static BINARY_OPERATOR binaryOperator(BinaryOperator value) {
        return new BINARY_OPERATOR(value);
    }
    static EOF eof() { return EOF_INSTANCE; }
}

static class Lexer {
    int pos = 0;
    final String input;

    Lexer(String input) {
        this.input = input;
    }

    char peek() { return input.charAt(pos); }
    void advance() { pos++; }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < input.length()) {
            int start = pos;
            char chr = peek();

            if (Character.isWhitespace(chr)) {
                advance();
                continue;
            }

            if (BinaryOperator.isBinaryOperator(chr)) {
                tokens.add(Token.binaryOperator(BinaryOperator.fromChar(chr)));
                advance();
                continue;
            }


            if (Character.isDigit(chr)) {
                while (++pos < input.length()
                    && Character.isDigit(peek()));

                String token = input.substring(start, pos);
                int number = Integer.parseInt(token);
                tokens.add(Token.number(number));
                continue;
            }

            throw new IllegalStateException("[" + pos + "]" + " Unknown character");
        }

        tokens.add(Token.eof());

        return tokens;
    }
}

sealed interface Expr {
    record Number(int value) implements Expr {}

    record Binary(Expr left, Expr right, BinaryOperator operator) implements Expr {}
}

void visitor(Expr expr, int indent, BiConsumer<String, Integer> consumer) {
    switch (expr) {
		case Expr.Number(int value) -> consumer.accept(Integer.toString(value) + "\n", indent);
		case Expr.Binary(Expr left, Expr right, BinaryOperator operator) -> {
            consumer.accept(operator.symbol + "[\n", indent);
            visitor(left, indent + 1, consumer);
            visitor(right, indent + 1, consumer);
            consumer.accept("]\n", indent);
		}
    }
}

String prettyPrint(Expr expr) {
    StringBuilder result = new StringBuilder();

    visitor(expr, 0, (s, indent) -> {
        result.append("\t".repeat(indent))
        .append(s);
    });

    return result.toString();
}

static class Parser {
    int pos = 0;
    List<Token> tokens;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Token peek() { return pos >= tokens.size() ? Token.eof() : tokens.get(pos); }
    void back() { pos--; }
    Token advance() {
        Token curr = peek();
        if (pos < tokens.size()) { pos++; }
        return curr;
    }
    <T extends Token> T expect(Class<T> clazz) {
        T token = match(clazz);
        if (token == null) {
            throw new IllegalStateException("Expected token of type: " + clazz
                + " but found token: " + peek());
        }
        return token;
    }
    @SuppressWarnings("unchecked")
	<T extends Token> T match(Class<T> clazz) {
        if (!clazz.isInstance(peek())) {
            return null;
        }
        return (T) (advance());
    }

    Expr parsePrimary() {
        Token.NUMBER token = expect(Token.NUMBER.class);
        return new Expr.Number(token.value());
    }

    Expr parseExpr(int minPrec) {
        Expr lhs = parsePrimary();

        while (peek() instanceof Token.BINARY_OPERATOR(var operator)
                && operator.precedence >= minPrec) {
            advance(); // consume operator

            Expr rhs = parseExpr(operator.precedence + 1);

            lhs = new Expr.Binary(lhs, rhs, operator);
        }

        return lhs;
    }

    Expr parse() {
        Expr expr = parseExpr(0);
        expect(Token.EOF.class);
        return expr;
    }
}

Integer eval(Expr expr) {
    return switch(expr) {
        case Expr.Number(int value) -> value;
        case Expr.Binary(Expr left, Expr right, BinaryOperator operator) -> {
            Integer leftVal = eval(left);
            Integer rightVal = eval(right);

            yield switch(operator) {
                case BinaryOperator.PLUS -> leftVal + rightVal;
                case BinaryOperator.MINUS -> leftVal - rightVal;
                case BinaryOperator.MULTIPLY -> leftVal * rightVal;
                case BinaryOperator.DIVIDE -> leftVal / rightVal;
            };
        }
    };
}

Integer eval(String expr) {
    var tokens = new Lexer(expr).tokenize();
    IO.println("TOKENS: \n\t" + tokens);
    var ast = new Parser(tokens).parse();
    IO.println("AST: \n" + prettyPrint(ast));
    var value = eval(ast);
    IO.println("VALUE: \t" + value);
    return value;
}

void main(String... args) {
    List<String> exprs = List.of(
        "2 + 3 * 4",
        "2 * 3 + 4",
        "2 - 2 * 2 + 2",
        "10 - 2 - 3"
    );
    List<Number> values = List.of(14, 10, 0, 5);

    try {
    assert exprs
        .stream()
        .map(expr -> eval(expr))
        .toList()
        .equals(values);
    } catch (Exception ex) {
        IO.println(ex.getMessage());
    }

    if (args.length > 0 && args[0].equals("repl"))
        Stream.generate(() -> System.console().readLine("> "))
            .takeWhile(Predicate.not(Objects::isNull))
            .takeWhile(Predicate.not("quit"::equalsIgnoreCase))
            .forEach(expr -> eval(expr));
}
