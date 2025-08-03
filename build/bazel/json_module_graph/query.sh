#!/bin/bash -eu

LIBDIR="$(dirname "$(readlink -f "$0")")"

function print_usage() {
  echo "Usage: query.sh [-C] <command> <graph JSON> [argument]" 1>&2
  echo "  -C: colorized output" 1>&2
  echo
  echo "Commands":
  for jq in "$LIBDIR"/*.jq; do
    if ! grep -q "^# CMD:" "$jq"; then
      continue
    fi

    local CMD="$(echo $(basename "$jq") | sed 's/\..*$//')"
    echo "  $CMD": $(cat "$jq" | grep "^# CMD:" | head -n 1 | sed 's/^# CMD://')
  done
  exit 1
}

JQARGS=""

while getopts "C" arg; do
  case "$arg" in
    C)
      JQARGS="$JQARGS -C"
      shift
      ;;
    *)
      print_usage
      ;;
  esac
done

if [[ "$#" -lt 2 ]]; then
  print_usage
fi

COMMAND="$1"
GRAPH="$2"

if [[ "$#" -gt 2 ]]; then
  ARG="$3"
else
  ARG=""
fi

if [[ "$#" -gt 3 ]]; then
  ARG2="$4"
else
  ARG2=""
fi

jq $JQARGS -L "$LIBDIR" -f "$LIBDIR/$COMMAND".jq "$GRAPH" --arg arg "$ARG" --arg arg2 "$ARG2"
