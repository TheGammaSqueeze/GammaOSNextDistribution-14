# Difftool

This directory contains tools to compare build artifacts from two separate
build invocations as a way of gauging build correctness and debugging
potential problems with build systems under development.

# Usage

Use of these tools requires a multistep process:

1. Build using legacy build system:
   ```
   $ m libc
   ```
2. Collect results to a tmp directory.
   ```
   $ ./collect.py out/combined-aosp_flame.ninja \
         out/target/product/flame/obj/libc.so \
         /tmp/legacyFiles
   ```
3. Build using the new build system:
   ```
   $ m --bazel-mode-dev libc
   ```
4. Collect results to a tmp directory.
   ```
   $ ./collect.py out/combined-aosp_flame.ninja \
         out/target/product/flame/obj/libc.so \
         /tmp/newFiles
   ```
5. Run comparative analysis on the two tmp directories. (See
   documentation of difftool.py for exact usage.)
   ```
   $ ./difftool.py /tmp/legacyFiles \
         out/target/product/flame/obj/libc.so \
         /tmp/newFiles \
         out/target/product/flame/obj/libc.so
   ```

Use `./collect.py -h` or `./difftool.py -h` for full usage information of
these subtools.

