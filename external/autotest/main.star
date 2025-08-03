#!/usr/bin/env lucicfg generate
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This is the main entry point for the generation of configuration.
# Invoking (lucicfg is included with depot_tools):
# lucicfg generate ./main.star
# will update the configuration.


load("//metadata/generate.star", "generate")
generate()
