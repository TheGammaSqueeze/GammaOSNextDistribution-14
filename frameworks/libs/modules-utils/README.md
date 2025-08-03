# frameworks/libs/modules-utils/

Libraries and utilities intended for use by module and framework code.

Since modules use the code in this project, it must adhere to mainline
requirements, for example, by setting min_sdk_version in Soong modules.

## Java code

This project uses a single source path for Java code. All Java code should go
in the `java` directory with subdirectories corresponding to the java package.
`Android.bp` files should go alongside the java source files, and should only
include java source for a single java package to encourage good code hygiene.

Tests for java code should go in the `javatests` directory and follow the same
structure.
