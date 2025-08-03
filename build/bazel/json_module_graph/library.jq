# Applies "f" to all variation maps in modules and deps

def transformModule(f):
  .Variations = (.Variations | f) |
  .DependencyVariations = (.DependencyVariations | f)
;

def transformModuleReferences(f):
  transformModule(f) |
  .Deps = [.Deps | .[] | transformModule(f)]
;



# Utility functions for transforming modules

def deleteDependencyVariations:
  if .DependencyVariations == .Variations then del(.DependencyVariations) else . end
;


# Utility functions for transforming variation maps

def emptyIfNull: if . == null then {} else . end
;

def flattenVariations:
  [. as $m | . | keys | sort | .[] | . + "=" + ($m[.] | tostring)] | join(", ")
;

def removeLinkVariation:
  del(.link)
;

def removeEmptyVariations:
  del(.[] | select(. == ""))
;

# Computes the difference of two maps, returns it as a single string
def mapDelta($outer; $inner):
  $outer | keys as $outerKeys |
  $inner | keys as $innerKeys |
  ($outerKeys - $innerKeys) as $removed |
  ($innerKeys - $outerKeys) as $added |
  [($removed | .[] | "-" + . + "=" + $outer[.]), ($added | .[] | "+" + . + "=" + $inner[.])] |
  join(", ")
;

# Transforms the variation map of dependencies (as specified by f) to a delta
# from the variation map of the module that depends on them
def depDelta(f):
  f as $outer |
  (.Deps | .[] | f) |= (. | mapDelta($outer; .))
;

# "filterMatchingDeps"  filters deps that have different variations

def differentDep($od; $ov):
  (.DependencyVariations != $od or .Variations != $ov) and
  (.DependencyVariations != {} or .Variations != {})
;

def filterMatchingDeps: .Variations as $ov |
  .DependencyVariations as $od |
  .Deps = [.Deps[] | select(differentDep($ov; $od))]
;


def groupDeps:
  group_by({Variations, DependencyVariations, Tag}) |
  map({
    DependencyVariations: .[0].DependencyVariations,
    Variations: .[0].Variations,
    Tag: .[0].Tag | sub(" {BaseDependencyTag:{}(?<g>.*)}"; "\(.g)"),
    Modules: map(.Name)
  } | del(if has("DependencyVariations") then .DependencyVariations else empty end))

;

# Utilities for filtering out interesting modules (deps remain untouched)

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

def directDeps($m):
  map($m[.] // []) + [.] | flatten | unique
;

def reverseDeps($m):
 .[] | select(.Deps[].Name == $m)
;

def transitiveDeps($m):
  {Prev: [], Next: .} |
  until (.Prev == .Next; {Prev: .Next, Next: .Next | directDeps($m)}) |
  .Next
;

def findEdge($from;$to):
  .[] | select(.Name == $from) |
  .Deps |= [.[] | select(.Name == $to)] |
  select((.Deps | length) > 0)
;

def nonNullAction: .Module.Actions != null
;

def getActionInputs: .Module.Actions | .[] |
  .Inputs | if . == null then [] else . end | .[]
;

# Gets the directory path by the given file path.
def getDirPath: sub("(?<p>.*)\\/.*"; "\(.p)")
;

# Returns the names of modules of type $arg
def modulesOfType($arg):
  [.[] | select(.Type == $arg) | .Name] | unique
;

# Returns the modules in the transitive closure of $arg.
# $arg must be an array of modules names
def fullTransitiveDeps($arg):
  [((moduleGraphNoVariants | removeSelfEdges) as $m |
  $arg |
  transitiveDeps($m)) as $names |
  .[] |
  select (IN(.Name; $names | .[]))] |
  sort_by(.Name)
;
