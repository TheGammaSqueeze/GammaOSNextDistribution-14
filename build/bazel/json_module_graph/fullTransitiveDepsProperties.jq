# CMD: Returns the properties (including defaults) grouped by module type of modules in the transitive closure of module(s) $arg, splits on ","

include "library";

[((moduleGraphNoVariants | removeSelfEdges) as $m |
  $arg | split(",") |
  transitiveDeps($m)) as $names |
  .[] |
  select (IN(.Name; $names | .[]))] |
  group_by(.Type) |
  map({Type: .[0].Type,
    Props: map(.Module.Android.SetProperties) | flatten | map(.Name) | unique | sort }) |
  sort_by(.Type)



