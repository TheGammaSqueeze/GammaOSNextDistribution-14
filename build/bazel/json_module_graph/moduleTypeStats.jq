# CMD: Returns a summary of the module types present on the input

include "library";

def moduleTypeStats($arg):
  group_by(.Type) |
  map({
    Type: .[0].Type,
    Count: map(.Name) | unique | length,
    VariantCount: length,
  }) |
  sort_by(.Count)
  ;

moduleTypeStats($arg)