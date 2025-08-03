# CMD: Returns the modules that have test_suites property with $arg as one of its value. Use $arg2 as the top level field key to be collected, e.g. Name, Blueprint.

def hasTestSuitesWithValue($a):
  .[] | select(.Name == "Test_suites") | .Values | .[] | . == $a
;

[.[] |
select(.Module.Android.SetProperties |
    if . == null then [] else . end |
    hasTestSuitesWithValue($arg)) |
.[$arg2] ] | unique | sort | .[]
