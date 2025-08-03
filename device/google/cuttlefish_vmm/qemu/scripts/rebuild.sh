#!/bin/sh
SCRIPT_DIR="$(dirname "$0")"
export PYTHONPATH=
export PYTHONHOME=
exec "${SCRIPT_DIR}/../third_party/python/bin/python3" -S "${SCRIPT_DIR}"/rebuild.py "$@"
