# CMD: Prints the longest distance each module has from a leaf in the module graph within the transitive closure of given module

include "library";

def onlyDeps:
  { Name: .Name, Deps: .Deps | map(.Name) }
;

def mergeDepsForSameModule:
  group_by(.Name) | map({Name: .[0].Name, Deps: map(.Deps) | flatten | unique | sort})
;

def toMergeMap:
  map({key: .Name, value: .Deps}) | from_entries
;

def moduleGraphNoVariants:
  map(onlyDeps) | mergeDepsForSameModule | toMergeMap
;

def removeSelfEdges:
  to_entries |
  map(.key as $key | {key: .key, value: .value | [.[] | select(. != $key)]}) |
  from_entries
;

def nextDepths($m):
  . as $old |
  to_entries |
  map({
      key: .key,
      value: (
          .key as $key |
          $m[.key] // [] |
          map($old[.]) |
          if any(. == -1) then -1 else (max // -1) + 1 end
      )
  }) |
  from_entries
;

def maxDepths($m):
  map({key: ., value: -1}) | from_entries |
  {Prev: [], Next: .} |
  until (.Prev == .Next; {Prev: .Next, Next: .Next | nextDepths($m)}) |
  .Next
;

def variantlessDistancesFromLeaves($root):
  (moduleGraphNoVariants | removeSelfEdges) as $m |
  [$root] |
  transitiveDeps($m) |
  maxDepths($m)
;

variantlessDistancesFromLeaves($arg)
