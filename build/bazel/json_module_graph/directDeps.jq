# CMD: Returns the names of the direct dependencies of the module named $arg

include "library";

[.[] | select(.Name == $arg) | .Deps | map(.Name)] | flatten | unique | sort