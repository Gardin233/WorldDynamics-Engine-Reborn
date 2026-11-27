package com.thatguycy.worlddynamicsengine.manager.event;

/** Evaluates a small arithmetic formula with the town's wealth as its input. */
public final class StrengthFormulaEvaluator {
    private StrengthFormulaEvaluator() {
    }

    public static int calculatePoints(String formula, double wealth, int maximumPoints) {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("Strength formula is empty.");
        }
        int limit = Math.max(0, maximumPoints);
        if (limit == 0) {
            return 0;
        }
        double safeWealth = Double.isFinite(wealth) ? Math.max(0.0D, wealth) : 0.0D;
        double result = new Parser(formula, safeWealth).parse();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Strength formula produced a non-finite result.");
        }
        if (result <= 0.0D) {
            return 0;
        }
        if (result >= limit) {
            return limit;
        }
        return (int) Math.floor(result);
    }

    public static int scalePointsForOnlinePopulation(int strengthPoints, int onlinePopulation, int population) {
        if (strengthPoints <= 0 || onlinePopulation <= 0 || population <= 0) {
            return 0;
        }
        int countedOnline = Math.min(onlinePopulation, population);
        return (int) Math.floor((double) strengthPoints * countedOnline / population);
    }

    public static int applyMultiplier(int strengthPoints, double multiplier) {
        if (strengthPoints <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0D) {
            return 0;
        }
        double adjusted = Math.ceil(strengthPoints * multiplier);
        return adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) adjusted;
    }

    private static final class Parser {
        private final String input;
        private final double wealth;
        private int position;

        private Parser(String input, double wealth) {
            this.input = input;
            this.wealth = wealth;
        }

        private double parse() {
            double value = expression();
            skipWhitespace();
            if (position != input.length()) {
                throw error("Unexpected character.");
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    value += term();
                } else if (consume('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = unary();
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    value *= unary();
                } else if (consume('/')) {
                    double divisor = unary();
                    if (divisor == 0.0D) {
                        throw error("Division by zero.");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double unary() {
            skipWhitespace();
            if (consume('+')) {
                return unary();
            }
            if (consume('-')) {
                return -unary();
            }
            return primary();
        }

        private double primary() {
            skipWhitespace();
            if (consume('(')) {
                double value = expression();
                skipWhitespace();
                require(')');
                return value;
            }
            if (position >= input.length()) {
                throw error("Expected a number, wealth, or parenthesized expression.");
            }
            char current = input.charAt(position);
            if (Character.isDigit(current) || current == '.') {
                return number();
            }
            if (Character.isLetter(current) || current == '_') {
                return identifier();
            }
            throw error("Unexpected character.");
        }

        private double number() {
            int start = position;
            boolean hasDigit = false;
            while (position < input.length() && Character.isDigit(input.charAt(position))) {
                position++;
                hasDigit = true;
            }
            if (consume('.')) {
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                    hasDigit = true;
                }
            }
            if (!hasDigit) {
                throw error("Invalid number.");
            }
            if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position++;
                if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                int exponentStart = position;
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                }
                if (exponentStart == position) {
                    throw error("Invalid exponent.");
                }
            }
            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("Invalid number.");
            }
        }

        private double identifier() {
            int start = position;
            while (position < input.length()
                    && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
                position++;
            }
            String name = input.substring(start, position).toLowerCase(java.util.Locale.ROOT);
            if (name.equals("wealth")) {
                return wealth;
            }
            skipWhitespace();
            require('(');
            double first = expression();
            skipWhitespace();
            if (name.equals("min") || name.equals("max")) {
                require(',');
                double second = expression();
                skipWhitespace();
                require(')');
                return name.equals("min") ? Math.min(first, second) : Math.max(first, second);
            }
            require(')');
            return switch (name) {
                case "floor" -> Math.floor(first);
                case "ceil" -> Math.ceil(first);
                case "abs" -> Math.abs(first);
                default -> throw error("Unknown function: " + name);
            };
        }

        private void require(char expected) {
            skipWhitespace();
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'.");
            }
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " At position " + position + " in '" + input + "'.");
        }
    }
}
