# CMD: Finds all modules whose name is equal to the name of one of its input
# files.

include "library";

def isActionInputMatch($name; $blueprintDirPath): . as $actionInput |
getDirPath as $inputDirPath | $actionInput | split("/") |
last | . == $name and $inputDirPath == $blueprintDirPath
;

def isActionInputsMatch($name; $blueprint): getActionInputs as $actionInputs |
$blueprint | getDirPath as $blueprintDirPath | $actionInputs |
isActionInputMatch($name; $blueprintDirPath)
;

[.[] | select(nonNullAction) | select(isActionInputsMatch(.Name; .Blueprint)) | .Name] | sort_by(.) | unique
