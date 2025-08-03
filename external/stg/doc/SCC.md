# SCC finder core algorithm

When comparing types recursively, we explore a graph whose nodes are pairs of
types. Nodes can be visited more than once, and there can even be cycles.

The standard tool for exploring directed graphs is Depth First Search. Dealing
with cycles in such a graph requires the determination of its Strongly-Connected
Components.

DFS code for graph traversal is trivial. SCC determination is not and we would
like it to be provided by a reliable and efficient library component.

## Core algorithm

There are three commonly-studied asymptotically-optimal approaches to
determining the Strongly-Connected Components of a directed graph. Each of these
admits various optimisations and specialisations for different purposes.

* [Kosaraju's algorithm](https://en.wikipedia.org/wiki/Kosaraju%27s_algorithm)
* [Tarjan's
  algorithm](https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm)
* [The path-based
  algorithm](https://en.wikipedia.org/wiki/Path-based_strong_component_algorithm)

Kosaraju's algorithm is unsuited to DFS-generated graphs (such as type
comparison graphs) as it requires both forwards and reverse edges to be known
ahead of time.

Tarjan's algorithm can be massaged into the form where it can be separated into
a plain DFS traversal and SCC-specific pieces but the resulting code is a bit
messy and responsibility for SCC state management is rather scattered.

The path-based algorithm is the best fit and can be put in a form where the DFS
traversal and SCC state management are cleanly separated. The concept of "open"
nodes carries directly over to the implementation used here and SCC state
management occurs in two well-defined places.

*   node visit starts; repeat visits to open nodes are detected
*   node visit end; completed SCCs are detected

The remainder of this document discusses the finer details of what the finder
API should look like.

## Choice of primitives

The choice of primitives should be determined by the interface priorities:

*   simple - hard to misuse
*   efficient - does not preclude optimal implementation
*   powerful - no need to code around deficiencies

Roughly speaking, the SCC finder "opens" and "closes" nodes and the user code
will try to open and close nodes at the beginning and end of each node visit.

### Close

The logic to follow when the SCC finder reports a complete SCC (nodes closed) is
simple: the user code must ensure it considers each node as definitively visited
from this point and avoid asking the finder to consider it again. This condition
means the SCC does not have to independently track "ever visited" status that
may be duplicated elsewhere.

### Open

When reaching a node via DFS, the user of the SCC finder has to perform a 3-way
classification:

1.  never visited before - the node should immediately transition to open and
    known to the SCC finder
1.  open - the link just followed would create a cycle and the SCC finder
    algorithm needs to do some state maintenance; the user code must not
    recursively process the node
1.  closed - the link just followed reaches a node already fully processed and
    assigned to a SCC; the user code must not recursively process the node

There are at least 3 different ways of structuring program logic to distinguish
these paths.

#### Populate user visited state on open

Node lifecycle:

1. unvisited + not open
1. visited + open
1. visited + not open

If a node has never been visited, it can be unconditionally opened. If it has
been visited, we must still check if it's open. This is a bit odd in the context
of the SCC algorithm as `really_open` and `is_open` become separate operations
(-simplicity). It's possible that a user could know, for other reasons, whether
a visited node is open or not and then omitting to call `is_open` could upset
the SCC finder (which needs to update its internal state in the already-open
case). This risk could be eliminated by duplicating the state update logic in
both methods (-efficiency).

```c++
if (visited) {
  if (is_open(node)) {
    // cycle-breaking back link
    return;
  } else {
    // work-saving link to shared node
    return;
  }
}
mark_visited();
token = really_open(node);
...
// do work
...
nodes = close(token);
if (!nodes.empty()) {
  ...
}
```

#### Check SCC open / closed first

Node lifecycle:

1. not open + unvisited (never visited)
1. open (being visited)
1. not open + visited (closed)

This scheme also requires separate `is_open` and `really_open` operations as
nodes musn't be reopened (-simplicity, -efficiency). It does allow the user to
mark nodes as visited any time between open and close (-simplicity, +power).

```c++
if (is_open(node)) {
  // cycle-breaking back link
  return;
}
if (visited) {
  // work-saving link to shared node
  return;
}
token = really_open(node);
...
// do work
...
nodes = close(token);
if (!nodes.empty()) {
  // last chance to call mark_visited
}
```

#### Test user visited state before open, populate on close

NOTE: This is the currently implemented approach.

Node lifecycle:

1. unvisited + not open
1. unvisited + open
1. visited + not open

This is the purest form of the algorithm with the `open` and `close` operations
clearly bracketing "real" work. `really_open` and `is_open` operations are
folded together into `open` which will fail to open an already open node
(+simplicity, +efficiency).

The user value store cannot be something that gets initialised and updated
between open and close or it will also need to duplicate the open/closed state
and this correspondence will need to be maintained as an invariant (-power).

```c++
if (visited) {
  // work-saving link to shared node
  return;
}
token = open(node);
if (!token) {
  // cycle-breaking back link
  return;
}
...
// do work
...
nodes = close(token.value());
if (!nodes.empty()) {
  ...
  mark_visited();
}
```

Evaluating equality can be made to fit nicely into this category, as can using
equality along with the return stack to build a diff tree with stubs for
sharing- and cycle-breaking links.

However, building a graph (say a copy of the traversal, or a diff graph)
requires open node state to be squirrelled away somewhere.

##### Enhancement

The SCC finder data structure can be made to carry values associated with open
nodes and hand them to the user on failure-to-open and closure. This allows us
to retain purity and regain the ability to maintain simple state for open nodes
separately from that for closed nodes, at the expense of a slightly
heavier-touch interface (+power).

In the simplest case, we'd want nothing stored at all (beyond the node identity)
and actually supplying a second empty type would be an annoyance and an
inefficiency (-simplicity, -power, -efficiency)). So the best thing to supply is
the user's container's `value_type` and associated `value_compare` comparator.

However, in this variation, it's painful to set up the SCC structures for
efficient `open` as nodes need to exist in a map or set, independently of any
payload. The approach could be revisited if there's a solution to this.

```c++
if (visited) {
  // work-saving link to shared node
  return;
}
[&node_state, token] = open(node_state);
if (!token) {
  // cycle-breaking back link
  return;
}
...
// do work, update node_state if you like
...
node_states = close(token.value())
if (!node_states.empty()) {
  ...
  mark_visited();
}
```
