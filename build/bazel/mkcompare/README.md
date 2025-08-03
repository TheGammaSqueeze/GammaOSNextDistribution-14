# mkcompare: Compare generated Android-TARGET.mk makefiles

## Summary

This tool shows the differences between two `Android-`_target_`.mk` makefile.
This makefile contains information about the Soong build graph that is exposed
to Make (Android.mk) and packaging rules.

## Usage

```shell
# run product config
$ lunch ${target}

# run soong for reference build
$ m nothing && cp out/soong/Android-${target}.mk Android-${target}.mk.ref

# apply your local changes..
$ m nothing && cp out/soong/Android-${target}.mk Android-${target}.mk.new

# compare!
$ GOWORK=$PWD/build/bazel/mkcompare/go.work go run android/bazel/mkcompare/cmd \
    -json \
    Android-${target}.mk.ref \
    Android-${target}.mk.new > ${target}.mk.json
```

## Options ##

The comparator optionally:

* Generates a JSON file with all the differences (`-json`). This option turns off all out output.
* Stops after finding given _N_ different modules `-m N`)
* Ignores variables with given names (`--ignore_variables=VAR,...`)
* Shows per-variable value difference (`--show_module_diffs`)
* For each module type, shows the names of the modules with this difference (`--show_type_modules`)

## How it works

We assume that both makefiles were generated for the same configuration (i.e.,
the same  _target_ value, and our goal is thus to find out the difference that
a change contributes to the Makefile interface between Soong and Make.

Currently, the comparator inspects only the module sections of a file.

A _module section_ looks something like this:
```makefile
include $(CLEAR_VARS)   # <module type>
LOCAL_MODULE := mymod
LOCAL_MODULE_CLASS := ETC
include $(BUILD_PREBUILT)
```

i.e., it always starts with `include $(CLEAR_VARS)` ('module header') line
and spans until the blank line. Before a blank line there is an
`include <mkfile>` line ('module footer'), which may be followed by a few extra
variable assignments. Between those two `include ` lines are the assignment lines.

The name of the module is synthesized from the value of the `LOCAL_MODULE` variable
and target configuration, e.g, `apex_tzdata.com.android.tzdata|cls:ETC|target_arch:arm64`
or `aac_dec_fuzzer|cls:EXECUTABLES|host_arch:x86_64`

The module header includes the module type as a comment (the plan was to use the
_mkfile_ on the footer line, but it proved to be common to most of the modules,
so Soong was modified to provide a module detailed module type as a comment
on the header line).

A module section in the reference file is compared with the
identically named module section of our file. The following items are compared:

*   module types
*   the number of extra lines following the section footer
*   the variables and their values

## Summary Output

The default outputs look as follows:
```
159 missing modules, by type:
  apex.apexBundle.files (159 modules)

Missing variables (14):
  ...
  LOCAL_REQUIRED_MODULES, by type:
    art_cc_library (2 modules)
    art_cc_library_static (4 modules)
    cc_library (28 modules)
    cc_library_shared (2 modules)
  LOCAL_SHARED_LIBRARIES, by type:
    art_cc_library (60 modules)
    ....
Extra variables (7):
  LOCAL_EXPORT_CFLAGS, by type:
    cc_library (4 modules)
  LOCAL_EXPORT_C_INCLUDE_DEPS, by type:
    art_cc_library (28 modules)
    ...
Diff variables: (18)
  LOCAL_EXPORT_C_INCLUDE_DEPS, by type:
    aidl_interface.go_android/soong/aidl.wrapLibraryFactory.func1__topDownMutatorModule (1721 modules)
    art_cc_library (12 modules)
  LOCAL_PREBUILT_MODULE_FILE, by type:
    apex.apexBundle (7 modules)
    apex.apexBundle.files (625 modules)
   ...
```

## JSON Output ##

It looks like this:
```JSON
{
  "RefPath": "<...>/out/soong/Android-aosp_arm64.mk",
  "OurPath": "<...>/out.mixed/soong/Android-aosp_arm64.mk",
  "MissingModules": [
    "adbd.com.android.adbd|cls:EXECUTABLES|target_arch:arm64",
    "android.hardware.common-V2-ndk.com.android.media.swcodec|cls:SHARED_LIBRARIES|target_arch:arm64",
    "android.hardware.graphics.allocator-V1-ndk.com.android.media.swcodec|cls:SHARED_LIBRARIES|target_arch:arm64",
    "android.hardware.graphics.allocator@2.0.com.android.media.swcodec|cls:SHARED_LIBRARIES|target_arch:arm64",
    ...
  ],
  "DiffModules": [
    {
      "Name": "_makenames|cls:EXECUTABLES|target_arch:arm64",
      "RefLocation": 137674,
      "OurLocation": 137673,
      "MissingVars": [ "LOCAL_SHARED_LIBRARIES", "LOCAL_STATIC_LIBRARIES" ],
      "DiffVars": [
        {
          "Name": "LOCAL_PREBUILT_MODULE_FILE",
          "MissingItems": [ "out/soong/.intermediates/external/libcap/_makenames/android_arm64_armv8-a/_makenames" ],
          "ExtraItems": [ "out/bazel-bin/external/libcap/_makenames" ]
        },
        {
          "Name": "LOCAL_SOONG_UNSTRIPPED_BINARY",
          "MissingItems": [ "out/soong/.intermediates/external/libcap/_makenames/android_arm64_armv8-a/unstripped/_makenames" ],
          "ExtraItems": [ "out/bazel-bin/external/libcap/_makenames_unstripped" ]
        }
      ]
    },
    ...
  ]
}
```
Use JSON query tool like [`jq`](https://github.com/stedolan/jq) to slice and dice it.
