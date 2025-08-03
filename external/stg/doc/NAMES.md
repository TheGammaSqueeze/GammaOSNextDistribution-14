# C Type Names

STG does not contain full type names for every type node in the graph. If full
type names are needed then we need to generate them ourselves.

## Implementation

The STG type `Name` is the basic entity representing the human-friendly name of
a graph node. Values of this type are computed recursively by `Describe` and are
memoised in a `NameCache`.

`Name` copes with the inside-out C type syntax in a fairly uniform fashion.
There is some slightly special code for `Qualifier` decoration.

Infinite recursion prevention in `Describe` is done in the most straightforward
fashion possible. One consequence of this is that if there is a naming cycle,
the memoised names for nodes in the cycle will depend on where it was entered.

The rest of this document covers the concepts and design principles.

## Background

In sensible operator grammars, composition can be done using precedence levels.

Example with binary operators (there are minor adjustments needed if operators
have left or right associativity):

| op  | precedence |
| --- | ---------- |
| +   | 0          |
| *   | 1          |
| num | 2          |

```haskell
show x = show_prec 0 x

show_paren p x = if p then "(" ++ x ++ ")" else x

show_prec _ (Number n) = to_string n
show_prec prec (Mult e1 e2) = show_paren (prec > 1) (show_prec 2 e1 ++ "*" ++ show_prec 2 e2)
show_prec prec (Add e1 e2) = show_paren (prec > 2) (show_prec 3 e1 ++ "+" ++ show_prec 3 e2)
```

The central idea is that expressions are rendered in the context of a precedence
level. Parentheses are needed if the context precedence is higher than the
expression's own precedence. Atomic values can be viewed as having maximal
precedence. The default precedence context for printing an expression is the
minimal one; no parentheses will be emitted.

## The more-than-slightly-bonkers C declaration syntax

C's type syntax is closely related to the inside-out declaration syntax it uses
and has the same precedence rules. A simplified, partial precedence table for
types might look like this.

thing      | precedence
---------- | ----------
int        | 0
refer *    | 1
elt[N]     | 2
ret(args)  | 2
identifier | 3

The basic (lowest precedence) elements are:

*   primitive types, possibly CV-qualified
*   typedefs, possibly CVR-qualified
*   struct, union and enum types, possibly CV-qualified

The "operators" in increasing precedence level order are:

* pointer-to, possibly CVR-qualified
* function (return type) and array (element type)

The atomic (highest precedence) elements are:

* variable names
* function names

### CVR-qualifiers

The qualifiers `const`, `volatile` and `restrict` appear to the right of the
pointer-to operator `*` and are idiomatically placed to the left of the basic
elements. They can be considered as transparent to precedence.

### User-defined types

Struct, union and enum types can be named or anonymous. The normal case is a
named type. Anonymous types are given structural descriptions.

### Pointer, array and function types

To declare `x` as a pointer to type `t`, we declare the dereferencing of `x` as
`t`.

```c++
t * x;
```

To declare `x` as an array of type `t` and size `n`, we declare the `n`th
element of `x` as `t`.

```c++
t x[n];
```

To declare `x` as a function returning type `t` and taking args `n`, we declare
the result of applying `x` to `n` as `t`.

```c++
t x(n);
```

The context precedence level for rendering `x`, which may be a complex thing in
its own right, is 2 in the case of arrays and functions and 1 in the case of
pointers.

We need to do things inside-out now, because the outer type is a leaf of the
type expression tree. Instead we say `x` has a precedence and the leaf type
wraps around it, using parentheses if `x`'s precedence is less than the leaf
type (which typically happens if `x` is a pointer type).

In each of these cases `x` has been replaced by another type that mentions `y`.

```c++
t * * y;     // y is a pointer to pointer to t
t * y[m];    // y is an array of pointer to t
t * y(m);    // y is a function returning pointer to t
t (* y)[n];  // y is a pointer to an array of t
t y[m][n];   // y is an array of arrays of t
t y(m)[n];   // if a function could return an array, this is what it would look like
t (* y)(n);  // y is a pointer to a function returning t
t y[m](n);   // if an array could contain functions, this is what it would look like
t y(m)(n);   // if a function could return a function, this is what it would look like
```

## Concise and Efficient Pretty Printer (sketch)

This builds a type recursively, so that outside bits will be rendered first. The
recursion needs to keep track of a left piece, a right piece and the precedence
level of the hole in the middle.

```haskell
render (Basic type) = (type, 0, "")
render (Ptr ref) = add Left 1 "*" (render ref)
render (Function ret args) = add Right 2 ("(" ++ render_args args ++ ")") (render ret)
render (Array elt size) = add Right 2 ("[" ++ render_size size ++ "]") (render elt)
render (Decl name type) = add Left 3 name (render type)

add side prec text (l, p, r) =
  case side of
    Left => (ll ++ text, prec, rr)
    Right => (ll, prec, text ++ rr)
  where
    paren = prec < p
    ll = if paren then l ++ "(" else if side == LEFT then l ++ " " else l
    rr = if paren then ")" ++ r else r
```

To finally print something, just print the left and right pieces in sequence.

The cunning bit about structuring the pretty printer this way is that `render`
can be memoised. With the expectation that any given type will appear many times
in typical output, this is a big win.

NOTE: C type qualifiers are a significant extra complication and are omitted
from this sketch. They must appear to the left or right of (the left part of) a
type name. Which side can be determined by the current precendence.

NOTE: Whitespace can be emitted sparingly as specific side / precedence contexts
can imply the impossibility of inadvertently joining two words.
