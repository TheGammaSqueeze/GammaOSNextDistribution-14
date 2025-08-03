# CMD: Returns the modules of type $arg that have property $arg2

def hasPropertyWithName($a):
  map(select(.Name == $a)) |
  length |
  . > 0
;

[.[] |
select(.Type == $arg) |
select(.Module.Android.SetProperties |
    hasPropertyWithName($arg2)) |
.Name] | unique | sort | .[]
