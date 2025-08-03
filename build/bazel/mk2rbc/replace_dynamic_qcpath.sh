#! /bin/bash
##CL Provide location hint for the dynamically calculated paths.
##CL
##CL For the paths using QC_PROP_PATH or QC_PROP_ROOT
##CL Bug: 203582721
##CL Test: treehugger
declare -r files="$(grep -rlP '^ *(\$\(call inherit-product|-?include).*\$\(QC_PROP_(PATH|ROOT)\)' --include 'BoardConfig*.mk')"
[[ -z "$files" ]] || sed -i -r -f <(cat <<"EOF"
/^ *(\$\(call inherit-product|-?include).*\$\(QC_PROP_(PATH|ROOT)\)/i#RBC# include_top vendor/qcom
EOF
) $files
