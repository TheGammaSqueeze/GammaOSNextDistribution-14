#!/bin/bash

declare -A INNER
declare -A PARAMETER
declare -A IMPORT

ANNOTATIONS=(
    org.jspecify.nullness.Nullable
    org.jspecify.nullness.NullMarked
)

for a in ${ANNOTATIONS[@]}; do
    package=${a%.*}
    class=${a##*.}
    dir=$(dirname $0)/src/${package//.//}
    file=${class}.java
    inner=${INNER[$a]}
    parameter=${PARAMETER[$a]}
    import=

    if [ -n "${parameter}" ]; then
	parameter="${parameter} value();"
    fi

    for i in ${IMPORT[$a]}; do
	import="${import}import ${i};"
    done

    mkdir -p ${dir}
    sed -e"s/__PACKAGE__/${package}/" \
	-e"s/__CLASS__/${class}/" \
	-e"s/__INNER__/${inner}/" \
	-e"s/__PARAMETER__/${parameter}/" \
	-e"s/__IMPORT__/${import}/" \
	$(dirname $0)/tmpl.java > ${dir}/${file}
    google-java-format -i ${dir}/${file}
done
