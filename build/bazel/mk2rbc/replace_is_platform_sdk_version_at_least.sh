#! /bin/bash
##CL Replace is-platform-sdk-version-at-least calls with checking IS_AT_LEAST_xxx.
##CL
##CL Bug: 201477826
##CL Test: treehugger
declare -r files="$(grep -rlP '^[^#]*call +is-platform-sdk-version-at-least' --include '*.mk')"
[[ -z "$files" ]] || sed -i -r -f <(cat <<"EOF"
s/^([^#]*ifn?eq) +\(\$\(call is-platform-sdk-version-at-least, *(16|17|18|19|20|21|22|23|24|25)\), *true\)/\1 \(T,T\)  \# TODO: Obsolete, please remove/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *26\), *true\)/\1if\2def IS_AT_LEAST_OPR1/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *27\), *true\)/\1if\2def IS_AT_LEAST_OPM1/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *28\), *true\)/\1if\2def IS_AT_LEAST_PPR1/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *29\), *true\)/\1if\2def IS_AT_LEAST_QP1A/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *30\), *true\)/\1if\2def IS_AT_LEAST_RP1A/
s/^([^#]*)if(n?)eq +\(\$\(call is-platform-sdk-version-at-least, *31\), *true\)/\1if\2def IS_AT_LEAST_SP1A/
EOF
) $files
