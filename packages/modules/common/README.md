# packages/modules/common

This project includes mainline build and other utility code. Any library code
intended for use by modules should go in `frameworks/libs/modules-utils`
instead.

## java code

This project uses a single source path for java code. All java code should go
in the `java` directory with subdirectories corresponding to the java package.
`Android.bp` files should go alongside the java source files, and should only
include java source for a single java package to encourage good code hygiene.

Tests for java code should go in the `javatests` directory and follow the same
structure.