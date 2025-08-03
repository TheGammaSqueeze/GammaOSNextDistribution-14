# CMD: Finds all modules whose input files cross package boundaries.

include "library";

def getBlueprintDirPaths:
[.[] | .Blueprint | getDirPath] | sort_by(.) | unique | map({(.):""}) | add
;

def getNonNullActionModules:
[.[] | select(nonNullAction)]
;

def getOutputsOfModule:
[.Module.Actions | .[] | .Outputs | if . == null then [] else . end | .[]]
;

def getOutputsOfModules($nonNullActionModules):
$nonNullActionModules | map({(.Name):getOutputsOfModule}) | add
;

def getDepOutputs($outputsOfModules):
. as $depName |
if in($outputsOfModules) then ($outputsOfModules | ."\($depName)")
else [] end | .[]
;

def getDepOutputsOfModule($outputsOfModules):
[.Deps | .[] | .Name | getDepOutputs($outputsOfModules)]
| map({(.):""}) | add
;

def isDirPathMatch($blueprintDirPath; $allBlueprintDirPaths):
  def _isDirPathMatch($blueprintDirPath; $allBlueprintDirPaths):
    # True if there's a Blueprint file in the path and the path isn't
    # equal to $blueprintDirPath of the module.
    if in($allBlueprintDirPaths) and . != $blueprintDirPath then true
    # Stops checking if the current path is already the $blueprintDirPath.
    elif . == $blueprintDirPath then false
    # Usually it should not hit this logic as it stops when the path is
    # equal to $blueprintDirPath.
    elif (contains("/") | not) then false
    else (getDirPath | _isDirPathMatch($blueprintDirPath; $allBlueprintDirPaths))
    end
  ;
  _isDirPathMatch($blueprintDirPath; $allBlueprintDirPaths)
;

def isActionInputMatch($outputsOfModules; $allBlueprintDirPaths):
. as $moduleVariant | .Blueprint | getDirPath as $blueprintDirPath |
$moduleVariant | getDepOutputsOfModule($outputsOfModules) as $depOutputs |
$moduleVariant | getActionInputs | select(in($depOutputs) | not) |
select(startswith($blueprintDirPath)) | getDirPath |
isDirPathMatch($blueprintDirPath; $allBlueprintDirPaths)
;

getBlueprintDirPaths as $allBlueprintDirPaths |
getNonNullActionModules as $nonNullActionModules |
getOutputsOfModules($nonNullActionModules) as $outputsOfModules |
[$nonNullActionModules | .[] |
select(isActionInputMatch($outputsOfModules; $allBlueprintDirPaths)) |
.Name] | sort_by(.) | unique
