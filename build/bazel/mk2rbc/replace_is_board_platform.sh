#! /bin/bash
##CL Replace is-board-platform[-in-list] with is-board-platform[-in-list]2
##CL
##CL The regular is-board-platform[-in-list] functions are defined in
##CL some product/board configuration makefiles, and sometimes also
##CL used in Android.mk files. When the product/board configuration
##CL is converted to starlark, the functions will no longer be defined
##CL for the Android.mk files to use. Switch to using a version of
##CL these functions that is defined inside the core build system
##CL makefiles, so it will still be defined when the configuration
##CL is in Starlark.
##CL
##CL The new function returns either an empty string or the matching
##CL platform, while the old one returned either an empty string or true.
##CL So now if statements are compared against an empty string instead of
##CL true.
##CL
##CL Bug: 201477826
##CL Test: treehugger
declare -r files="$(grep -rlP '^[^#]*call +is-board-platform' --include '*.mk' --exclude 'utils_test.mk' --exclude 'utils_sample_usage.mk')"
[[ -z "$files" ]] || sed -i -r -f <(cat <<"EOF"
s/ifeq +\(\$\(call is-board-platform,(.*)\), *true\)/ifneq (,$(call is-board-platform2,\1))/
s/ifeq +\(\$\(call is-board-platform,(.*)\), *\)/ifeq (,$(call is-board-platform2,\1))/
s/ifneq +\(\$\(call is-board-platform,(.*)\), *true\)/ifeq (,$(call is-board-platform2,\1))/
s/ifeq +\(\$\(call is-board-platform-in-list,(.*)\), *true\)/ifneq (,$(call is-board-platform-in-list2,\1))/
s/ifeq +\(\$\(call is-board-platform-in-list,(.*)\), *\)/ifeq (,$(call is-board-platform-in-list2,\1))/
s/ifeq +\(\$\(call is-board-platform-in-list,(.*)\), *false\)/ifeq (,T)  # TODO: remove useless check/
s/ifneq +\(\$\(call is-board-platform-in-list,(.*)\), *true\)/ifeq (,$(call is-board-platform-in-list2,\1))/
s/\$\(call is-board-platform,(.*)\)/$(call is-board-platform2,\1)/
EOF
) $files
