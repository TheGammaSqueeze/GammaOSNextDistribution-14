# CMD: Prints the module named $arg in a slightly more concise way

include "library";

def printModule($mod):
  .[] | select(.Name == $mod) |
  transformModuleReferences(emptyIfNull | removeLinkVariation | removeEmptyVariations) |
  depDelta(.Variations) | depDelta(.DependencyVariations) |
  transformModule(flattenVariations) |
  deleteDependencyVariations |
  .Deps |= map(deleteDependencyVariations) |
  .Deps |= groupDeps
;

printModule($arg)