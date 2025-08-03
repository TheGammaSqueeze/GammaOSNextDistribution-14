# CMD: Returns the names of the transitive dependencies of the module(s) $arg, splits on ","

include "library";

(moduleGraphNoVariants | removeSelfEdges) as $m |
  ($arg | split(",")) |
  transitiveDeps($m)
