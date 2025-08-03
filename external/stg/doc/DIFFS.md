# Diffs

Consider two directed graphs, containing labelled nodes and edges. Given a
designated starting node in each graph, describe how the reachable subgraphs are
different in a textual report.

STG separates the problem of reporting graph differences into two pieces:

1. comparison - generating difference graphs
1. reporting - serialising difference graphs

The main benefits are:

* separation of responsibility allowing reporting to vary without significant
  changes to the comparison code
* a single difference graph can be used to generate multiple reports with
  guaranteed consistency and modest time savings
* the difference graph data structure may be presented as a graph, manipulated,
  subject to further analysis or stored

## Abstract Graph Diffs

There are 3 kinds of node difference and each node comparison pair can have any
number of these:

1. node label difference - a purely local change
1. outgoing edge with matching labels - a recursive difference
1. added or removed outgoing edge - modelled as a recursive difference with an
   "absent" node

STG models comparisons as pairs of nodes where either node can be absent. While
absent-absent comparisons can result from the composition of an addition and a
removal, they do not occur naturally during pairwise comparison.

## Comparison Implementation

Comparison is mostly done pair-wise recursively with a DFS, by the function
object `Compare` and with the help of the [SCC finder](SCC.md).

The algorithm divides responsibility between `operator()(Id, Id)` and various
`operator()(Node, Node)` methods. There are also trivial helpers `Removed`,
`Added` and `Mismatch`.

The `Result` type encapsulates the difference between two nodes being compared.
It contains both a list (`Diff`) of differences (`DiffDetail`) and a boolean
equality outcome. The latter is used to propagate inequality information in the
presence of cycles in the diff comparison graph.

### `operator()(Node, Node)`

For a given `Node` type, this method has the job of computing local differences,
matching edges and obtaining edge differences from recursive calls to
`operator()(Id, Id)` (or `Removed` and `Added`, if edge labels are unmatched).

Local differences can easily be rendered as text, but edge differences need
recursive calls, the results of which are merged into the local differences
`Result` with helper methods.

In general we want each comparison operator to be as small as possible,
containing no boilerplate and simply mirroring the node data. The helper
functions were therefore chosen for power, laziness and concision.

### `Added` and `Removed`

These take care of comparisons where one side is absent.

There are several reasons for not folding this functionality into `operator(Id,
Id)` itself:

* it would result in unnecessary extra work for unmatched edges as its callers
  would pack and the function would unpack `std::optional<Id>` arguments
* added and removed nodes have none of the other interesting features that it
  handles
* `Added` and `Removed` don't need to decorate their return values with any
  difference information

### `operator(Id, Id)`

This controls recursion and handles some special cases before delegating to some
`operator()(Node, Node)` in the "normal" case.

It takes care of the following:

* revisited, completed comparisons
* revisited, in-progress comparison
* qualified types
* typedefs
* incomparable and comparable nodes - handled by `Mismatch` and delegated,
  respectively

Note that the non-trivial special cases relating to typedefs and qualified types
(and their current concrete representations) require non-parallel traversals of
the graphs being compared.

#### Revisited Nodes and Recursive Comparison

STG comparison relies on `SCC` to control recursion and behaviour in the face of
graphs containing arbitrary cycles. Any difference found affecting one node in a
comparison cycle affects all nodes in that cycle.

Excluding the two special cases documented in the following sections, the
comparison steps are approximately:

1. if the comparison already has a known result then return this
1. if the comparison already is in progress then return a potential difference
1. start node visit, register the node with the SCC finder
   1. (special cases for qualified types and typedefs)
   1. incomparable nodes go to `Mismatch` which returns a difference
   1. otherwise delegate node comparison (with possible recursion)
   1. result is a tentative node comparion
1. finish node visit, informing the SCC finder
1. if an SCC was closed, we've just finished its root comparison
   1. root compared equal? discard unwanted potential differences
   1. difference found? record confirmed differences
   1. record all its comparisons as final
1. return result (whether final or tentative)

#### Typedefs

Typedefs are just named type aliases which cannot refer to themselves or later
defined types. The referred-to type is exactly identical to the typedef. So for
difference *finding*, typedefs should just be resolved and skipped over.
However, for *reporting*, it may be still useful to say where a difference came
from. This requires extra handling to collect the typedef names on each side of
the comparison, when there is something to report.

If `operator()(Id, Id)` sees a comparison involving a typedef, it resolves
typedef chains on both sides and keeps track of the names. Then it calls itself
recursively. If the result is no-diff, it returns no-diff, otherwise, it reports
the differences between the types at the end of the typedef chains.

An alternative would be to genuinely just follow the epsilons. Store the
typedefs in the diff tree but record the comparison of what they resolve to. The
presentation layer can decorate the comparison text with resolution chains.

Note that qualified typedefs present extra complications.

#### Qualified Types

STG currently represents type qualifiers as separate, individual nodes. They are
relevant for finding differences but there may be no guarantee of the order in
which they will appear. For diff reporting, STG currently reports added and
removed qualifiers but also compares the underlying types.

This implies that when faced with a comparison involving a qualifier,
`operator()(Id, Id)` should collect and compare all qualifiers on both sides and
treat the types as compound objects consisting of their qualifiers and the
underlying types, either or both of which may have differences to report.
Comparing the underlying types requires recursive calls.

Note that qualified typedefs present extra complications.

#### Qualified typedefs

Qualifiers and typedefs have subtle interactions. For example:

Before:

```c++
const int quux;
```

After 1:

```c++
typedef int foo;
const foo quux;
```

After 2:

```c++
typedef const int foo;
foo quux;
```

After 3:

```c++
typedef const int foo;
const foo quux;
```

In all cases above, the type of `quux` is unchanged. These examples strongly
suggest that a better model of C types would involve tracking qualification as a
decoration present on every type node, including typedefs.

Note that this behaviour implies C's type system is not purely constructive as
there is machinery to discard duplicate qualifiers which would be illegal
elsewhere.

For the moment, we can pretend that outer qualifications are always significant,
even though they may be absorbed by inner ones, and risk occasional false
positives.

A worse case is:

Before:

```c++
const int quux[];
```

After 1:

```c++
typedef int foo[];
const foo quux;
```

After 2:

```c++
typedef const int foo[];
foo quux;
```

After 3:

```c++
typedef const int foo[];
const foo quux;
```

All the `quux` are identically typed. There is an additional wart that what
would normally be illegal qualifiers on an array type instead decorate its
element type.

Finally, worst is:


Before:

```c++
const int quux();
```

After 1:

```c++
typedef int foo();
const foo quux;
```

After 2:

```c++
typedef const int foo();
foo quux;
```

After 3:

```c++
typedef const int foo();
const foo quux;
```

The two `const foo quux` cases invoke undefined behaviour. The consistently
crazy behaviour would have been to decorate the return type instead.

### Diff helpers

These are mainly used by the `Compare::operator()(Node, Node)` methods.

* `MarkIncomparable` - nodes are just different
* `AddNodeDiff` - add node difference, unconditionally
* `AddEdgeDiff` - add edge difference (addition or removal), unconditionally
* `MaybeAddNodeDiff` - add node difference (label change), conditionally
* `MaybeAddEdgeDiff` - add matching edge recursive difference, conditionally

Variants are possible where text is generated lazily on a recursive diff being
found, as are ones where labels are compared and serialised only if different.

## Diff Presentation

In general, there are two problems to solve:

* generating suitable text, for
   * nodes and edges
   * node and edge differences
* building a report with some meaningful structure

Node and edge description and report structure are the responsibility of the
*reporting* code. See [Names](NAMES.md) for more detailed notes on node
description, mainly C type name syntax.

Several report formats are supported and the simplest is (omitting various
complications) a rendering of a difference graph as a difference *tree* where
revisiting nodes is avoided by reporting 2 additional artificial kinds of
difference:

1. already reported - to handle diff sharing
1. being compared - to handle diff cycles

The various formats are not documented further here.

Finally, node and edge difference description is currently the responsibility of
the *comparison* code. This may change in the future, but might require a typed
difference graph.
