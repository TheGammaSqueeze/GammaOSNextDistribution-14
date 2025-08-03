# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

all: bgtask

bgtask: bgtask.cc
	clang++ -O2 -std=gnu++20 -Wall -Wextra -Werror -g0 -fno-exceptions \
		bgtask.cc -o bgtask

clean:
	rm -f bgtask
