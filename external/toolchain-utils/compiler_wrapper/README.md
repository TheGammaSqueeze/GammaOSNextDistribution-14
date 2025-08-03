Copyright 2023 The ChromiumOS Authors
Use of this source code is governed by a BSD-style license that can be
found in the LICENSE file.

Toolchain utils compiler wrapper sources.

Build the wrapper:
./build.py --config=<config name> --use_ccache=<bool> \
  --use_llvm_next=<bool> --output_file=<file>

Please note that there's a regular syncing operation between
`chromiumos-overlay/sys-devel/llvm/files/compiler_wrapper` and
`toolchain-utils/compiler_wrapper`. This sync is one way (from
chromiumos-overlay to `toolchain-utils`). Syncing in this way helps the Android
toolchain keep up-to-date with our wrapper easily, as they're a downstream
consumer of it. For this reason, **please be sure to land all actual changes in
chromeos-overlay**.
