package com.mahghuuuls.jawms.internal.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionCompilerTest {

    @Test
    void supportsVariablesOperatorsPrecedenceAndRightAssociativePower() {
        assertEquals(35.0D, evaluate("original_cost + tier * 5", 20, 3));
        assertEquals(512.0D, evaluate("2 ^ 3 ^ 2", 0, 1));
        assertEquals(-4.0D, evaluate("-2 ^ 2", 0, 1));
        assertEquals(0.25D, evaluate("2 ^ -2", 0, 1));
        assertEquals(1.0D, evaluate("10 % 3", 0, 1));
    }

    @Test
    void supportsEveryApprovedFunction() {
        assertEquals(10.0D, evaluate("min(original_cost, tier * 25)", 10, 1));
        assertEquals(25.0D, evaluate("max(original_cost, tier * 25)", 10, 1));
        assertEquals(2.0D, evaluate("abs(-2)", 0, 1));
        assertEquals(1.0D, evaluate("floor(1.9)", 0, 1));
        assertEquals(2.0D, evaluate("ceil(1.1)", 0, 1));
        assertEquals(2.0D, evaluate("round(1.5)", 0, 1));
    }

    @Test
    void rejectsUnknownNamesWrongArityIncompleteAndTrailingInput() {
        assertThrows(ExpressionCompiler.FormulaException.class,
                () -> ExpressionCompiler.compile("missing + 1"));
        assertThrows(ExpressionCompiler.FormulaException.class,
                () -> ExpressionCompiler.compile("min(1)"));
        assertThrows(ExpressionCompiler.FormulaException.class,
                () -> ExpressionCompiler.compile("1 +"));
        assertThrows(ExpressionCompiler.FormulaException.class,
                () -> ExpressionCompiler.compile("1 2"));
        assertThrows(ExpressionCompiler.FormulaException.class,
                () -> ExpressionCompiler.compile("1e"));
    }

    private static double evaluate(String source, double originalCost, double tier) {
        return ExpressionCompiler.compile(source).evaluate(originalCost, tier);
    }
}
