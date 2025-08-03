#!/bin/bash
# SPDX-License-Identifier: MIT
# Copyright 2020 Google LLC
#
# Use of this source code is governed by an MIT-style
# license that can be found in the LICENSE file or at
# https://opensource.org/licenses/MIT.

set -e -u -o pipefail

find programs lib -name '*.c' | while read -r file; do
	sparse "$file" -gcc-base-dir "$(gcc --print-file-name=)"	\
		-Iinclude -D_FILE_OFFSET_BITS=64 -Wbitwise -D_GNU_SOURCE
done
