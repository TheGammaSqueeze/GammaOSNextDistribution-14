Trusty TEE Application SDK
==========================

This SDK provides the necessary libraries, headers, and toolchain for building
Trusty applications for integration into the Trusty TEE.

The Trusty API reference is available on [our website](https://source.android.com/security/trusty/trusty-ref).

This SDK is currently in flux and no stability guarantees are currently
provided. Future versions may add, remove, or change APIs.


SDK structure
-------------

- `make`
  - `$ARCH/trusty_sdk.mk`
    Makefile suitable for including into an existing build system. Sets up the
    `CC`, `CXX`, `LD`, `CFLAGS`, `CXXFLAGS`, `ASMFLAGS`, and `LDFLAGS` variables
    with appropriate values for building Trusty apps. Includes the function
    `add-trusty-library` that adds the needed flags to compile and link against a
    particular SDK library. See header comments in this file for more details.
  - `$ARCH/lib....mk`
    Library-specific makefiles that append necessary compile and link flags to
    use that library. Should be used via `add-trusty-library` if using make.
- `sysroots`
  - `$ARCH`
    Sysroot containing the userspace libraries and headers for the corresponding
    architecture.
- `clang-version`
  Version information for the clang toolchain used to compile the SDK and
    corresponding version of the Trusty kernel. This toolchain must be used to
    build apps in order to be compatible with this SDK.
- `toolchain`
  Optionally included if the SDK was built with `--archive-toolchain`.
  - `clang`
    Symbolic link to the clang directory used to compile the SDK.
  - `clang-r...`
    The specific clang toolchain used to compile the SDK.
