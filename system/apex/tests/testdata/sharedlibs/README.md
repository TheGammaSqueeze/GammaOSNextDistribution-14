### Test artifacts for shared libraries APEX support

This directory contains APEX packages used for testing the platform support for
moving shared libraries used by binaries within an APEX package into another
APEX package.

Due to the peculiarity of the build needs, this directory contains prebuilt
artifacts used by tests. In order to regenerate these artifacts, run from the
root of the tree:

```shell script
./system/apex/tests/testdata/sharedlibs/build/build_artifacts.sh
```