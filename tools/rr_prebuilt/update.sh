#!/bin/bash
set -e
set -x

cd "$(dirname "$0")"

if [[ ! -e .venv ]]; then
    python -m venv .venv
fi
source .venv/bin/activate
python -m pip install --upgrade -r requirements.txt
python update.py "$@"
