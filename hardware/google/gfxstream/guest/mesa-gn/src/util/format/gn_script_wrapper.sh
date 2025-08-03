#!/usr/bin/env bash

# Copyright 2020 The Fuchsia Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Executes the given python executable with PYTHONPATH set and captures
# its output in a file. The first PYTHON_ARG should probably be the path
# to a python script that writes something to stdout.
# This is to let GN use the mesa python scripts that write headers to stdout,
# and to support a custom PYTHONPATH.

USAGE="Usage: $0 PYTHON_EXE PYTHONPATH OUT_FILE [PYTHON_ARGS...]"

if [[ "$#" -lt "3" ]]; then
    echo "${USAGE}" 1>&2;
    exit 1
fi

PYTHON_EXE=${1}
shift
PYTHONPATH=${1}
shift
OUT=${1}
shift

# -S to decrease start-up time by not looking for site packages.
env PYTHONPATH="${PYTHONPATH}" "${PYTHON_EXE}" -S "$@" > ${OUT}
exit $?
