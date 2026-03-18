# Pratt Parser Exercise (Java)

This project is a small arithmetic parser/evaluator in `Code.java`.

The main learning goal is **operator precedence and associativity**:
- precedence decides *which operators bind tighter*
- associativity decides *how same-precedence chains group*

---

## Accepted Grammar

Current input grammar (no parentheses/unary yet):

```ebnf
Expr   := Number (Op Number)*
Number := [0-9]+
Op     := "+" | "-" | "*" | "/"
```

Equivalent precedence-aware view:

```ebnf
Expr     := AddExpr
AddExpr  := MulExpr (("+" | "-") MulExpr)*
MulExpr  := Primary (("*" | "/") Primary)*
Primary  := Number
```

---

## Deep Dive: Precedence Ordering

### 1) Precedence values in this codebase

```java
interface Precedence {
    int ADD = 0;
    int MULT = 1;
    int MAX = 2;
}
```

and:

```java
PLUS("+", ADD), MINUS("-", ADD), DIVIDE("/", MULT), MULTIPLY("*", MULT)
```

So:
- `+`, `-` are level `0` (weaker)
- `*`, `/` are level `1` (stronger)

### 2) Why lower number means weaker here

The parser starts at `parseExpr(0)` and parses RHS using tighter levels (`+1`).

So level `0` is visited first (broad expression), and level `1` is used when a tighter binding is needed for operands. This is why `*` / `/` naturally bind before `+` / `-`.

### 3) Role of `MAX`

`MAX` is a boundary: once precedence reaches `MAX`, parser stops looking for binary operators and falls back to `parsePrimary()`.

That means `MAX` is *not* an operator precedence itself in this design; it is the "atom/base" threshold.

---

## Parser Evolution (Initial -> Fixed)

This section summarizes our debugging journey.

### Initial recursive version (problematic for associativity)

```java
Expr parseExpr(int precedence) {
    if (precedence >= BinaryOperator.MAX_PRECEDENCE) {
        return parsePrimary();
    }

    Expr lhs = parseExpr(precedence + 1);
    if (peek().equals(Token.eof())) return lhs;

    BinaryOperator operator = expect(Token.BINARY_OPERATOR.class).value();
    if (operator.precedence == precedence) {
        Expr rhs = parseExpr(precedence); // <- key issue
        return new Expr.Binary(lhs, rhs, operator);
    } else {
        back();
        return lhs;
    }
}
```

### Why this caused issues

`rhs = parseExpr(precedence)` lets the RHS parse at the same precedence level, so same-precedence chains tend to be grouped on the right.

For `10 - 2 - 3`, the tree becomes:

```text
10 - (2 - 3)
```

instead of expected left-associative:

```text
(10 - 2) - 3
```

### Failed fix idea we discussed

Changing precedence numbers alone (for example making `-` higher) does **not** solve associativity, because associativity is caused by parser control flow (who consumes the next same-precedence operator), not by numeric precedence labels.

In addition, making `-` higher precedence than `+` changes the language rules (non-standard arithmetic precedence), so expressions mixing `+` and `-` may group unexpectedly.

### Current fixed parser (precedence climbing)

```java
Expr parseExpr(int minPrec) {
    Expr lhs = parsePrimary();

    while (peek() instanceof Token.BINARY_OPERATOR(var operator)
            && operator.precedence >= minPrec) {
        advance();

        // +1 is the associativity fix for left-associative operators
        Expr rhs = parseExpr(operator.precedence + 1);

        lhs = new Expr.Binary(lhs, rhs, operator);
    }

    return lhs;
}
```

Key point:
- For left-associative operators, parse RHS with tighter minimum precedence: `op.precedence + 1`
- This prevents RHS from consuming the next same-precedence operator.

---

## Core Intuition (the part that usually "clicks")

Associativity is decided by **who is allowed to consume the next same-precedence operator**.

Use expression:

```text
10 - 3 - 2
```

After parsing `10 - 3`, there is still `- 2`.

Now ask:
- Does the **current parse frame** consume the next `-`?  
  -> left-associative: `(10 - 3) - 2`
- Or does the **RHS recursive call** consume it?  
  -> right-associative: `10 - (3 - 2)`

### Why `+1` works

In the fixed parser:

```java
Expr rhs = parseExpr(op.precedence + 1);
```

If `op` is `-` (precedence `0`), RHS is parsed with `minPrec = 1`.
So RHS is **not allowed** to consume another `-` (`0 < 1`).
Control returns to the current frame, which then consumes the next `-`.

That gives left-associative grouping.

### Why no `+1` becomes right-associative

If you do:

```java
Expr rhs = parseExpr(op.precedence);
```

then RHS is allowed to consume another same-precedence `-`, so it can parse `3 - 2` first.
That produces `10 - (3 - 2)`.

---

## Worked Examples (with grouping intuition)

### Example A: `2 + 3 * 4`

Expected grouping:

```text
2 + (3 * 4)
```

because `*` has higher precedence than `+`.

### Example B: `10 - 2 - 3`

Expected grouping (left-associative):

```text
(10 - 2) - 3
```

With the old parser, it could group as:

```text
10 - (2 - 3)
```

With the fixed parser (`+1` on RHS min precedence), grouping is correctly left-associative.

---

## How to Run

### Test
```bash
javac Code.java
java -ea Code
```

Without assertions:

```bash
javac Code.java
java Code
```

### REPL mode:

```bash
java Code repl
```

Use `quit` to exit REPL.
