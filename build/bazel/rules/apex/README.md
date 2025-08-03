# The Bazel APEX ruleset

**Example**

```
$ b build //path/to/module:com.android.module.apex --config=android
```

**Code locations**

The main entry point is the `apex` macro in [//build/bazel/rules/apex/apex.bzl](https://cs.android.com/android/platform/superproject/+/master:build/bazel/rules/apex/apex.bzl?q=f:apex.bzl), which expands to the `_apex` rule implementation.

Related files in this directory include:

*   `cc.bzl` for the C/C++ specific aspect that traverses into native dependencies
*   `toolchain.bzl` for the host toolchain
*   `transition.bzl` for the configuration transition to add APEX-specific configuration to dependencies, like the APEX name and min sdk version.
*   `apex_aab.bzl` to repackage APEXes into multi-architecture Android app bundles and APK sets.
*   `apex_info.bzl` contains ApexInfo and ApexMkInfo providers. These form the main interface of an APEX target.
*   `apex_key.bzl` for the `apex_key()` rule
*   `apex_test.bzl` for Starlark analysis tests

The bp2build converter (`ConvertWithBp2build`) is located [here](https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/apex.go;l=3469;drc=4d247e6f21004d3998bf32d46c22111a380b81af).

The mixed build handler (`ProcessBazelQueryResponse`) is located [here](https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/apex.go;l=1888;drc=4d247e6f21004d3998bf32d46c22111a380b81af).

**Major features**

*   Build, compress, and sign APEX `ext4` images and containers for all architectures/bitness
*   Supports outputs: `.apex`, `.capex` (compressed apex), `.aab` (Android app bundle), `.apks` (APK set)
*   Supports packaging prebuilts (e.g. tzdata), native shared libs, native binaries, `sh_binary`
*   Works with `apex`, `override_apex` and `apex_test` Soong module types
*   Supports AOSP and Google/Go APEX variants
*   Supports standalone/unbundled APEX builds (fast for development) with `b` and full platform build with `m` (preloaded on system)
*   Supports generating Mainline quality signals metadata files: required/provided libs, `installed_files.txt`
*   Internal mainline build scripts is capable of building multi-arch AABs/APKs in a single Bazel invocation

**Detailed features**

*   Bazel build settings/flags in `//build/bazel/rules/apex/BUILD`, like `unsafe_disable_apex_allowed_deps_check`
*   ABI stability for native deps
    *   ABI stable stubs are marked as required, and not included within the APEX
    *   non-ABI stable transitive deps are copied into the APEX
*   Supports testonly APEXes (converted from `apex_test`)
*   Supports default certificates with product config
*   Supports default `file_contexts`
*   Supports `allowed_deps.txt` validation
*   Supports `apex_available` validation
*   Supports `min_sdk_version` validation
*   Supports `logging_parent`, `package_name`, `android_manifest`, `key`.
*   Supports `apex_manifest.pb` conversion
*   Supports `canned_fs_config` generation
*   Supports `file_contexts` generation
*   Licensing: `NOTICE.html.gz` embedded in the APEX
*   All host tools are built from source by Bazel, or vendored prebuilts
*   All actions are fully sandboxed
*   Ability to build metadata files on the command line with `--output_groups=&lt;coverage_files,backing_libs,...>`

**Guardrails / others**

*   `--config=android` is needed to build for the device. All APEX targets set `target_compatible_with` to android only - no host APEXes.
*   Comprehensive set of rule analysis tests for in `apex_test.bzl`
*   Example APEX in `//build/bazel/examples/apex/...`
*   Unit and integration tests in `//build/bazel/{examples,tests,rules}/apex/...` and `//build/soong/tests/...`

**Known issues / gap analysis (non-exhaustive)**

Upcoming features are based on Roboleaf module conversion priorities, like Java, Rust, DCLA and API fingerprinting support.

*  `override_apex` modules are converted to a regular apex with duplicated attributes. These are hidden by bp2build currently and will be cleaned up with macros in the future.
*   Correct product platform transitions for `apex_aab` to `mainline_modules_*` products
*   Java support
*   Rust support
