#!/bin/bash

ANNOTATIONS=(
    org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
)

for a in ${ANNOTATIONS[@]}; do
    package=${a%.*}
    class=${a##*.}
    dir=$(dirname $0)/src/${package//.//}
    file=${class}.java

    mkdir -p ${dir}
    sed -e"s/__PACKAGE__/${package}/" -e"s/__CLASS__/${class}/" tmpl.java > ${dir}/${file}
done
