# bp2build progress graphs

This directory contains tools to generate reports and .png graphs of the
bp2build conversion progress, for any module.

This tool relies on `json-module-graph` and `bp2build` to be buildable targets
for this branch.

## Prerequisites

* `/usr/bin/dot`: turning dot graphviz files into .pngs

Tip: `--use_queryview=true` runs `bp2build_progress.py` with queryview.

## Instructions

### Syntax

```sh
b run //build/bazel/scripts/bp2build_progress -- <mode> <flags> ...
```

Flags:

* --module, -m : Name(s) of Soong module(s). Multiple modules only supported for report.
* --type, -t : Type(s) of Soong module(s). Multiple modules only supported in report mode.
* --package-dir, -p: Package directory for Soong modules. Single package directory only supported for report.
* --recursive, -r: Whether to perform recursive search when --package-dir or -p flag is passed.
* --use-queryview: Whether to use queryview or module_info.
* --ignore-by-name : Comma-separated list. When building the tree of transitive dependencies, will not follow dependency edges pointing to module names listed by this flag.
* --ignore-java-auto-deps : Whether to ignore automatically added java deps.
* --banchan : Whether to run Soong in a banchan configuration rather than lunch.
* --show-converted, -s : Show bp2build-converted modules in addition to the unconverted dependencies to see full dependencies post-migration. By default converted dependencies are not shown.
* --hide-unconverted-modules-reasons: Hide unconverted modules reasons of heuristics and bp2build_metrics.pb. By default unconverted modules reasons are shown.

### Examples

#### Generate the report for a module, e.g. adbd

```sh
b run //build/bazel/scripts/bp2build_progress:bp2build_progress \
  -- report -m <module-name>
```

or:

```sh
b run //build/bazel/scripts/bp2build_progress:bp2build_progress \
  -- report -m <module-name> --use-queryview
```

When running in report mode, you can also write results to a proto with the flag
`--proto-file`

#### Generate the graph for a module, e.g. adbd

```sh
b run //build/bazel/scripts/bp2build_progress:bp2build_progress \
  -- graph -m adbd -o /tmp/graph.in && \
  dot -Tpng -o /tmp/graph.png /tmp/graph.in
```

or:

```sh
b run //build/bazel/scripts/bp2build_progress:bp2build_progress \
  -- graph -m adbd --use-queryview -o /tmp/graph.in && \
  dot -Tpng -o /tmp/graph.png /tmp/graph.in
```
Note: Currently, file output paths cannot be relative (b/283512659).
