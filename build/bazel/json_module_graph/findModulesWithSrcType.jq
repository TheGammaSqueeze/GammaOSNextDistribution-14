# CMD: Finds all modules whose input files contain the specific file type $arg.

include "library";

def isActionInputMatch($fileType): getActionInputs | split(".") | last | . == $fileType
;

[.[] | select(nonNullAction) | select(isActionInputMatch($arg)) | .Name] | sort_by(.) | unique
