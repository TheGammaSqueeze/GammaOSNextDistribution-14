#!/bin/bash
set -e
set -u

rename() {
  sed -r 's@(^|[^A-Za-z0-9_])'"$1"'([^A-Za-z0-9_]|$)@\1'"$2"'\2@g;'
}

apf_internal_function() {
  rename "$1" "apf_internal_$1"
}

do_assemble() {
  local -r RE_INCLUDE='^#include "([a-z_]+[.]h)"$'
  local -r RE_UNDEF='^#undef ([_A-Za-z0-9]+)$'
  local -r RE_DEFINE='^#define ([_A-Za-z0-9]+) (.*)$'

  local line
  while IFS='' read -r line; do
    if [[ "${line}" =~ ${RE_INCLUDE} ]]; then
      local include_name="${BASH_REMATCH[1]}"
      case "${include_name}" in
        apf_interpreter.h)
          echo "#include \"${BASH_REMATCH[1]}\""
          ;;
        *)
          echo "/* Begin include of ${include_name} */"
          cat "${include_name}"
          echo "/* End include of ${include_name} */"
          ;;
      esac
    elif [[ "${line}" =~ ${RE_UNDEF} ]]; then
      case "${BASH_REMATCH[1]}" in
        bool|true|false) : ;;
        *) echo "${line}" ;;
      esac
    elif [[ "${line}" =~ ${RE_DEFINE} ]]; then
      case "${BASH_REMATCH[1]}" in
        bool|true|false) : ;;
        *) echo "${line}" ;;
      esac
    else
      echo "${line}"
    fi
  done < apf_interpreter_source.c \
  | sed -r 's@(^|[^:])//(.*)$@\1/*\2 */@;'\
  | rename bool Boolean \
  | rename true True \
  | rename false False \
  | apf_internal_function match_single_name \
  | apf_internal_function match_names \
  | apf_internal_function calc_csum \
  | apf_internal_function csum_and_return_dscp \
  | apf_internal_function do_transmit_buffer
  # The above sed converts // comments into /* */ comments for c89,
  # and converts bool/true/false into Boolean/True/False
  # and converts non-static functions to have an apf_internal_ prefix
}

do_test() {
  diff -q <(do_assemble) apf_interpreter.c
}

main() {
  cd "${0%/*}"

  local -r me="${0##*/}"
  case "${me}" in
    apf_interpreter_assemble.sh)
      do_assemble > apf_interpreter.c
      ;;
    apf_assemble_test.sh)
      do_test
      ;;
    *)
      echo "Unknown $0" 1>&2
      return 1
      ;;
  esac
}

main "$@"; exit
