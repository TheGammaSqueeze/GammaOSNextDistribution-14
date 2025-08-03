#!/bin/bash

# A helper script to sequentially call checkstyle and clang-format which both
# requires reading git tree. Repo hooks by default executes in parallel which
# will cause race condition, so we have to use one repo hook that calls them
# in order.
set -e

${1}/prebuilts/checkstyle/checkstyle.py --sha ${2}
${1}/tools/repohooks/tools/clang-format.py --commit ${2} --style file --extensions c,h,cc,cpp
