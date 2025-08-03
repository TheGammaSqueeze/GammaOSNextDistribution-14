#!/bin/bash

THIS_DIR=$(realpath $(dirname ${BASH_SOURCE[0]:-$0}))

pushd ${THIS_DIR}
git fetch aosp main
git fetch aosp expected_upstream
# -t option to fetch tags used in the EXPECTED_UPSTREAM file
git fetch -t aosp upstream-openjdk7u
git fetch -t aosp upstream-openjdk8u
git fetch -t aosp upstream-openjdk9
git fetch -t aosp upstream-openjdk11u
git fetch -t aosp upstream-openjdk17u
git fetch -t aosp upstream-openjdk21u
git fetch -t aosp upstream-openjdk
popd

alias ojluni_refresh_files=${THIS_DIR}/ojluni_refresh_files.py
alias ojluni_merge_to_main=${THIS_DIR}/ojluni_merge_to_main.py
alias ojluni_modify_expectation=${THIS_DIR}/ojluni_modify_expectation.py
alias ojluni_run_tool_tests='PYTHONPATH=${PYTHONPATH}:${THIS_DIR} python3 -B -m unittest discover -v -s tests -p "*_test.py"'
alias ojluni_upgrade_identicals=${THIS_DIR}/ojluni_upgrade_identicals.py
alias ojluni_versions_report=${THIS_DIR}/ojluni_versions_report.py
alias ojluni_merge_package=${THIS_DIR}/ojluni_merge_package.sh
alias ojluni_cleanup_merged_branches=${THIS_DIR}/ojluni_cleanup_merged_branches.sh

_ojluni_modify_expectation ()
{
  COMPREPLY=( $(ojluni_modify_expectation --autocomplete $COMP_CWORD ${COMP_WORDS[@]:1}))

  return 0
}

complete -o nospace -F _ojluni_modify_expectation ojluni_modify_expectation
