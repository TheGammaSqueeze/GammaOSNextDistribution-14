# CMD: Returns the modules in the transitive closure of module(s) $arg, splits on ","

include "library";

fullTransitiveDeps($arg | split(","))


