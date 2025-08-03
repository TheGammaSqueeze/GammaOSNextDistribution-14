#! /bin/bash
##CL Replace is-vendor-board-platform with is-vendor-board-qcom.
##CL
##CL Bug: 201477826
##CL Test: treehugger
declare -r files="$(grep -rlP '^[^#]*call +is-vendor-board-platform' --include '*.mk')"
[[ -z "$files" ]] || sed -i -r -f <(cat <<"EOF"
s/ifeq \(\$\(call is-vendor-board-platform,QCOM\),true\)/ifneq (,$(call is-vendor-board-qcom))/
s/ifneq \(\$\(call is-vendor-board-platform,QCOM\),true\)/ifeq (,$(call is-vendor-board-qcom))/
EOF
) $files
