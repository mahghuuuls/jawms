package com.mahghuuuls.jawms.internal.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Small project-owned compiler for the configured spell-cost expression language.
 */
public final class ExpressionCompiler {

    private ExpressionCompiler() {
    }

    public static CompiledExpression compile(String source) {
        if (source == null) {
            throw new FormulaException("Formula must not be null.");
        }
        Parser parser = new Parser(source);
        Node root = parser.parseExpression();
        parser.require(TokenType.END, "Unexpected trailing input.");
        return new CompiledExpression(root);
    }

    public static final class CompiledExpression {

        private final Node root;

        private CompiledExpression(Node root) {
            this.root = root;
        }

        public double evaluate(double originalCost, double tier) {
            return root.evaluate(originalCost, tier);
        }
    }

    public static final class FormulaException extends IllegalArgumentException {

        FormulaException(String message) {
            super(message);
        }
    }

    private interface Node {

        double evaluate(double originalCost, double tier);
    }

    private enum TokenType {
        NUMBER,
        NAME,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        CARET,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        END
    }

    private static final class Token {

        private final TokenType type;
        private final String text;
        private final double number;
        private final int position;

        private Token(TokenType type, String text, double number, int position) {
            this.type = type;
            this.text = text;
            this.number = number;
            this.position = position;
        }
    }

    private static final class Parser {

        private final String source;
        private final List<Token> tokens;
        private int current;

        private Parser(String source) {
            this.source = source;
            this.tokens = tokenize(source);
        }

        private Node parseExpression() {
            return parseAddition();
        }

        private Node parseAddition() {
            Node node = parseMultiplication();
            while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
                Token operator = previous();
                Node left = node;
                Node right = parseMultiplication();
                node = operator.type == TokenType.PLUS
                        ? (original, tier) -> left.evaluate(original, tier) + right.evaluate(original, tier)
                        : (original, tier) -> left.evaluate(original, tier) - right.evaluate(original, tier);
            }
            return node;
        }

        private Node parseMultiplication() {
            Node node = parseUnary();
            while (match(TokenType.STAR) || match(TokenType.SLASH) || match(TokenType.PERCENT)) {
                Token operator = previous();
                Node left = node;
                Node right = parseUnary();
                if (operator.type == TokenType.STAR) {
                    node = (original, tier) -> left.evaluate(original, tier) * right.evaluate(original, tier);
                } else if (operator.type == TokenType.SLASH) {
                    node = (original, tier) -> left.evaluate(original, tier) / right.evaluate(original, tier);
                } else {
                    node = (original, tier) -> left.evaluate(original, tier) % right.evaluate(original, tier);
                }
            }
            return node;
        }

        private Node parseUnary() {
            if (match(TokenType.PLUS)) {
                return parseUnary();
            }
            if (match(TokenType.MINUS)) {
                Node operand = parseUnary();
                return (original, tier) -> -operand.evaluate(original, tier);
            }
            return parsePower();
        }

        private Node parsePower() {
            Node node = parsePrimary();
            if (match(TokenType.CARET)) {
                Node left = node;
                Node right = parseUnary();
                node = (original, tier) -> Math.pow(
                        left.evaluate(original, tier),
                        right.evaluate(original, tier)
                );
            }
            return node;
        }

        private Node parsePrimary() {
            if (match(TokenType.NUMBER)) {
                double value = previous().number;
                return (original, tier) -> value;
            }
            if (match(TokenType.NAME)) {
                Token name = previous();
                if (match(TokenType.LEFT_PAREN)) {
                    return parseFunction(name);
                }
                if ("original_cost".equals(name.text)) {
                    return (original, tier) -> original;
                }
                if ("tier".equals(name.text)) {
                    return (original, tier) -> tier;
                }
                throw error(name, "Unknown variable '" + name.text + "'.");
            }
            if (match(TokenType.LEFT_PAREN)) {
                Node node = parseExpression();
                require(TokenType.RIGHT_PAREN, "Expected ')' after expression.");
                return node;
            }
            throw error(peek(), "Expected a number, variable, function, or parenthesized expression.");
        }

        private Node parseFunction(Token name) {
            List<Node> arguments = new ArrayList<>();
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    arguments.add(parseExpression());
                } while (match(TokenType.COMMA));
            }
            require(TokenType.RIGHT_PAREN, "Expected ')' after function arguments.");

            if ("min".equals(name.text) || "max".equals(name.text)) {
                requireArity(name, arguments, 2);
                Node first = arguments.get(0);
                Node second = arguments.get(1);
                return "min".equals(name.text)
                        ? (original, tier) -> Math.min(
                                first.evaluate(original, tier),
                                second.evaluate(original, tier)
                        )
                        : (original, tier) -> Math.max(
                                first.evaluate(original, tier),
                                second.evaluate(original, tier)
                        );
            }
            if ("abs".equals(name.text)
                    || "floor".equals(name.text)
                    || "ceil".equals(name.text)
                    || "round".equals(name.text)) {
                requireArity(name, arguments, 1);
                Node argument = arguments.get(0);
                if ("abs".equals(name.text)) {
                    return (original, tier) -> Math.abs(argument.evaluate(original, tier));
                }
                if ("floor".equals(name.text)) {
                    return (original, tier) -> Math.floor(argument.evaluate(original, tier));
                }
                if ("ceil".equals(name.text)) {
                    return (original, tier) -> Math.ceil(argument.evaluate(original, tier));
                }
                return (original, tier) -> Math.floor(argument.evaluate(original, tier) + 0.5D);
            }
            throw error(name, "Unknown function '" + name.text + "'.");
        }

        private void requireArity(Token name, List<Node> arguments, int expected) {
            if (arguments.size() != expected) {
                throw error(
                        name,
                        "Function '" + name.text + "' requires " + expected
                                + " argument" + (expected == 1 ? "" : "s") + "."
                );
            }
        }

        private boolean match(TokenType type) {
            if (!check(type)) {
                return false;
            }
            current++;
            return true;
        }

        private boolean check(TokenType type) {
            return peek().type == type;
        }

        private Token previous() {
            return tokens.get(current - 1);
        }

        private Token peek() {
            return tokens.get(current);
        }

        private void require(TokenType type, String message) {
            if (!match(type)) {
                throw error(peek(), message);
            }
        }

        private FormulaException error(Token token, String message) {
            return new FormulaException(message + " At position " + token.position + " in '" + source + "'.");
        }
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (Character.isWhitespace(character)) {
                index++;
                continue;
            }
            int position = index;
            switch (character) {
                case '+':
                    tokens.add(simple(TokenType.PLUS, character, position));
                    index++;
                    continue;
                case '-':
                    tokens.add(simple(TokenType.MINUS, character, position));
                    index++;
                    continue;
                case '*':
                    tokens.add(simple(TokenType.STAR, character, position));
                    index++;
                    continue;
                case '/':
                    tokens.add(simple(TokenType.SLASH, character, position));
                    index++;
                    continue;
                case '%':
                    tokens.add(simple(TokenType.PERCENT, character, position));
                    index++;
                    continue;
                case '^':
                    tokens.add(simple(TokenType.CARET, character, position));
                    index++;
                    continue;
                case '(':
                    tokens.add(simple(TokenType.LEFT_PAREN, character, position));
                    index++;
                    continue;
                case ')':
                    tokens.add(simple(TokenType.RIGHT_PAREN, character, position));
                    index++;
                    continue;
                case ',':
                    tokens.add(simple(TokenType.COMMA, character, position));
                    index++;
                    continue;
                default:
                    break;
            }

            if (Character.isDigit(character) || character == '.') {
                int start = index;
                boolean digit = false;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    digit = true;
                    index++;
                }
                if (index < source.length() && source.charAt(index) == '.') {
                    index++;
                    while (index < source.length() && Character.isDigit(source.charAt(index))) {
                        digit = true;
                        index++;
                    }
                }
                if (!digit) {
                    throw new FormulaException("Invalid numeric literal at position " + start + ".");
                }
                if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                    int exponent = index++;
                    if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                        index++;
                    }
                    int exponentDigits = index;
                    while (index < source.length() && Character.isDigit(source.charAt(index))) {
                        index++;
                    }
                    if (exponentDigits == index) {
                        throw new FormulaException("Invalid exponent at position " + exponent + ".");
                    }
                }
                String text = source.substring(start, index);
                double value;
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException exception) {
                    throw new FormulaException("Invalid numeric literal '" + text + "'.");
                }
                if (!Double.isFinite(value)) {
                    throw new FormulaException("Numeric literal must be finite: '" + text + "'.");
                }
                tokens.add(new Token(TokenType.NUMBER, text, value, start));
                continue;
            }

            if (Character.isLetter(character) || character == '_') {
                int start = index++;
                while (index < source.length()) {
                    char next = source.charAt(index);
                    if (!Character.isLetterOrDigit(next) && next != '_') {
                        break;
                    }
                    index++;
                }
                String name = source.substring(start, index);
                tokens.add(new Token(TokenType.NAME, name, 0.0D, start));
                continue;
            }

            throw new FormulaException(
                    "Unexpected character '" + character + "' at position " + position + "."
            );
        }
        tokens.add(new Token(TokenType.END, "", 0.0D, source.length()));
        return tokens;
    }

    private static Token simple(TokenType type, char character, int position) {
        return new Token(type, Character.toString(character), 0.0D, position);
    }
}
