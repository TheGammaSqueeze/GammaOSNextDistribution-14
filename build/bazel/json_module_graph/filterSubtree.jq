# CMD: Returns modules defined under the directory $arg

include "library";

def isBlueprint($p): .Blueprint | index($p) != null
;

def isBlueprintPrefix($p): .Blueprint | startswith($p)
;

[.[] | select(isBlueprintPrefix($arg))]