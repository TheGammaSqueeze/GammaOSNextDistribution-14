# Overview

The `prepare_bazel_test_env` script is a proof-of-concept script to create a
simulated Bazel environment within the Android source tree using targets build
by the Soong build system.

# Supported Modules

The script currently support generation of a Bazel environment to run the
following Soong test modules:

*   `//platform_testing/tests/example/native:hello_world_test`
*   `//platform_testing/tests/example/jarhosttest: HelloWorldHostTest`
*   `//platform_testing/tests/example/instrumentation: HelloWorldTests`

Additionally, the system supports running the Tradefed console directly through
the `//tools/tradefederation/core:tradefed` target.

# Usage

There are three actions that the script can perform, which are supplied as the
first argument to the script: generate, sync, and clean, discussed below. All
the commands below are written with the `-v` flag for verbose output, however
this can be safely removed if desired.

## Generate

**Command Line**: `bazel run //build/pesto/experiments/prepare_bazel_test_env --
-v generate`

The generate command builds the required targets for a Bazel environment via
Soong, then stages this environment and associated dependencies at
`out/pesto-environment/` .

The generate command performs the following actions:

1.  Builds a set of modules (defined in the packaged templates in the
    `data/templates` directory) via Soong
2.  Creates a prebuilts directory at `out/pesto-environment/prebuilts` which
    contains symlinks to the Android Build environment provided directories
    (`ANDROID_HOST_OUT`, `ANDROID_HOST_OUT_TESTCASES`, `ANDROID_PRODUCT_OUT`,
    `ANDROID_TARGET_OUT_TESTCASES`), and will later be linked to by a
    `.soong_prebuilts` symlink that is placed adjacent to generated BUILD files.
3.  Generates a Bazel environment at `out/pesto-environment/gen` using the
    packaged environment to determine the locations of files, which are placed
    in locations relative to the source tree root. For example, the
    `out/pesto-environment/gen/tools/tradefederation/core/BUILD.bazel`
    corresponds to a file that will eventually live at
    `tools/tradefederation/core/BUILD.bazel`.
4.  For each BUILD file that is staged, place a `.soong_prebuilts` symlink that
    links to the aforementioned `prebuilts` directory.

After generation, the environment create can serve as a standalone Bazel
environment, or can be synced to the source tree using the sync command,
discussed below.

## Sync

**Command Line**: `bazel run //build/pesto/experiments/prepare_bazel_test_env --
-v sync`

The sync command scans the staging directory at `out/pesto-environment/gen` for
all files and then creates symlinks in the source tree that point at these
files, additionally each synced BUILD file is provided local access to the
`prebuilts` directory through a `.soong_prebuilts` symlink.

The sync command performs the following actions:

1.  Iterates through all files in the staged `out/pesto-environment/gen`
    directory and create a symlink in the source tree to each file at the proper
    location, overwriting file in the tree if it exists. For example, the sync
    action would create a symlink at `packages/modules/adb/BUILD.bazel` that
    links to `out/pesto-environment/gen/packages/modules/adb/BUILD.bazel`.
2.  Create a `.soong_prebuilts` directory in every location in the source tree
3.  where a BUILD file is placed, providing local access to the Soong staging
4.  directories.

After synchronization, the Bazel environment has been merged with the source
tree and can be used directly from within the source tree. Additionally, after
synchronization, subsequent calls to the generate command will propogate
automatically to the source tree.

## Clean

**Command Line**: `bazel run //build/pesto/experiments/prepare_bazel_test_env --
-v clean`

The clean command removes all files that have been created in the tree, and also
cleans up the environment directory at `out/pesto-environment` .

The clean command performs the following actions:

1.  For each file packaged with the script, remove the corresponding file from
    the source tree.
2.  For each BUILD file packaged with the script, remove the corresponding
    `.soong_prebuilts` directory for the source tree.
3.  Remove the `out/pesto-environment` directory.

After clean, the environment should be removed from the tree. However, as some
files may have been overwritten, certain repositories may need to be reset. The
`build/bazel/rules/BUILD.bazel` file is a notable example that needs to be
manually reset.

# Adding New Modules.

Adding support for an additional module depends on the type of module to be
added. Each is discussed below. Of note, all files should be added in the
`templates` directory and end in the `.template` file extension unless the file
is a static, non-Bazel file.

## Test Modules (without existing Test Rules)

For targets needing a new test rule, if the test is a Tradefed run test, use the
existing test rules as a template, otherwise a custom Bazel rule can be added.

An example rule is provided at
`templates/build/bazel/rules/cc_test.bzl.template` . For a new rule that
leverages Tradefed, use the above rule as an example of how to package
dependencies and test artifacts into the runfiles for the test. For each
Tradefed rule, the required dependencies should be included as private
attributes, for use by the rule implementation.

```
cc_test = rule(
    _cc_test_impl,
    attrs = {{
        "_adb": attr.label(
            default = Label("//packages/modules/adb"),
            allow_single_file = True,
        ),
        "_tradefed_launcher": attr.label(
            default = Label("//tools/tradefederation/core:atest_tradefed"),
            allow_single_file = True,
        ),
        "_tradefed_script_help": attr.label(
            default = Label("//tools/tradefederation/core:atest_script_help"),
        ),
        "_tradefed_jars": attr.label(
            default = Label("//tools/tradefederation/core:tradefed_lib"),
        ),
        "_template": attr.label(
            default = Label(
                "//build/bazel/rules:tf_test_executable.sh.template",
            ),
            allow_single_file = True,
        ),
        "_launcher": attr.label(default = Label("//build/bazel/rules:cc_tf_test_launcher")),
        "deps": attr.label_list(allow_files = True),
    }},
    executable = True,
    test = True,
)
```

Additionally, the Soong produced artifacts should also be included as runfiles
so they can be seen during Tradefed execution. Including a target as runfiles
here, ensures that the target shows up during execution. Furthermore, it ensures
that Bazel knows when to rebuild/rerun a test when artifacts change.

```
runfiles = ctx.runfiles(
        files = ctx.files._launcher,
        transitive_files = depset(
            transitive = [
                depset(ctx.files.deps),
                depset(ctx.files._adb),
                depset(ctx.files._tradefed_launcher),
                depset(ctx.files._tradefed_script_help),
                depset(ctx.files._tradefed_jars),
            ],
        ),
    )
```

Finally, the test rule should use the tf_test_executable.sh file as its
executable and provide the proper substitutions to this file, which can be seen
in the above example rule. The tf_test_executable.sh handles setting important
variables needed by Tradefed before test execution in a Bazel environment.

```
ctx.actions.expand_template(
    template = ctx.file._template,
    output = script,
    substitutions = {{
        "{{module_name}}": ctx.label.name,
        "{{module_path}}": ctx.label.package,
        "{{tradefed_launcher_module_path}}": ctx.attr._tradefed_launcher.label.package,
        "{{tradefed_jars_module_path}}": ctx.attr._tradefed_jars.label.package,
        "{{path_additions}}": ctx.attr._adb.label.package,
        "{{launcher_path}}": "{{}}/{{}}".format(
            ctx.attr._launcher.label.package,
            ctx.attr._launcher.label.name,
        ),
    }},
    is_executable = True,
)
```

After the rule logic is added, follow the steps in the below section for how to
add a test target leveraging the newly added rule.

## Test Modules (with existing Test Rules)

For targets where the test rule is already provided (i.e. `cc_test` ), adding a
new test module requires only adding a new BUILD file (with associated import
logic).

All added BUILD templates should end in `.template` to ensure Bazel does not see
these files as part of a package, and should contain the required Soong targets,
defined like the following:

```
# SOONG_TARGET:CtsAppTestCases
# SOONG_TARGET:org.apache.http.legacy
```

Refer to the
`data/templates/platform_testing/tests/example/native/BUILD.bazel.template` as
an example of how to import files into the Bazel environment using a genrule.
The genrule logic in the example template is used to combine files from multiple
locations into a single target and strip the Soong paths from the files imported
to Bazel. The below genrule serves as a foundation and copies all files from
srcs to the files listed in outs.

```
genrule(name="hello_world_test_prebuilt",
        srcs=_LIB_SRCS + _TESTCASE_HOST_SRCS + _TESTCASE_DEVICE_SRCS,
        outs=_LIB_OUTS + _TESTCASE_HOST_OUTS + _TESTCASE_DEVICE_OUTS,
        cmd="""
          src_files=($(SRCS))
          out_files=($(OUTS))
          for i in "$${{!src_files[@]}}"
          do
            src_file=$${{src_files[$$i]}}
            out_file=$${{out_files[$$i]}}
            mkdir -p $$(dirname $$src_file)
            cp $$src_file $$out_file
          done
          """)
```

When referring to files imported from Bazel, use the `{prebuilts_dir_name}`
substitution variable instead of referring to the `.soong_prebuilts` directory
directly since this may change.

```
_LIB_SRCS = glob([
    "{prebuilts_dir_name}/host/lib/**/*",
    "{prebuilts_dir_name}/host/lib64/**/*"
])
```

Then, the newly imported module can be referenced from an existing test rule as
a dependency, as is done in the example template. Ensure that the test rule is
imported, such as in the example file:

```
load("//build/bazel/rules:cc_test.bzl", "cc_test")
.
.
.
cc_test(name="hello_world_test", deps=[":hello_world_test_prebuilt"])
```
