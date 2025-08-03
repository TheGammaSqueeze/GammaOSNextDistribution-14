JSON module graph queries
=========================

This directory contains `jq` scripts that query Soong's module graph.
`jq` may be installed through your distribution's repository.

It's best to filter the full module graph to the part you are interested in
because `jq` isn't too fast on the full graph.

Usage
-----

```
m json-module-graph
query.sh [-C] <command> <base-of-your-tree>/out/soong/module-graph.json [argument]
```

Run `./query.sh` with no arguments for additional usage information.
