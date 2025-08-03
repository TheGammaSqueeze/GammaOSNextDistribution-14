# Manifest Split

## Overview

Split manifests are Android repo manifests that contain the minimum set of
projects necessary to build a given target. If a project isn't used for building
the target, it shouldn't be in the split manifest. This smaller manifest can be
used to sync the Android source tree and build the specific target. This sync
should be faster and smaller than a sync of a full manifest because it is
syncing less projects.

The `treble_manifest_split` tool is used to automatically create a split
manifest from a full manifest using dependency information from the source tree
and the build outputs. The tool attempts to infer as many dependencies as it
can, but some will be missed due to implicit dependencies in the build system
and source tree. This is solved by manually fine-tuning a tool configuration XML
specific to your target.

## Example for aosp_arm64

### 1. Run a full build using a full manifest

The `treble_manifest_split` tool needs the ninja build graph and deps log from a
completed build in order to have a full view of the dependency graph. While the
build graph is created at the beginning of a ninja build, the deps log is not
complete until the build finishes.

Use standard Android build commands to build your target.

### 2. Use the treble_manifest_split tool

```shell
# Change to the directory where you ran the full build.
cd /path/to/android

# Set command line variables for the Android target you are using and the build
# target that should be buildable from your split manifest.
ANDROID_TARGET=aosp_arm64-userdebug
BUILD_TARGET=droid

# Build treble_manifest_split as a python binary.
lunch $ANDROID_TARGET
m treble_manifest_split

# Create the split manifest using a sample config XML specific to aosp_arm64.
out/host/linux-x86/bin/treble_manifest_split \
  --manifest .repo/manifests/default.xml \
  --split-manifest split_default.xml \
  --debug-file debug.json \
  --config tools/treble/split/sample_config.xml \
  $BUILD_TARGET
```

### 3. Build using the split manifest

You should test that the split manifest created by the tool can be used to build
the partial target files package.

1.  Initialize a new repo directory using the steps in
    https://source.android.com/setup/build/downloading#initializing-a-repo-client.
1.  Replace the `.repo/manifests/default.xml` full manifest with the
    newly-generated split manifest.
1.  Use standard `repo sync` commands to sync your repo.
1.  Attempt a build of your target.

### 4. Fix build errors

Build errors may arise due to missing dependencies that were previously provided
by now-removed projects. These dependencies may be implicit in the source code,
or an explicit dependency type that is not yet able to be automatically detected
by the tool.

1.  Find the dependency source project in your full-manifest repo directory.
1.  Update your config XML to manually add projects to your split manifest.

    -   For example, the following line in `sample_config.xml` in this tool
        directory specifies a project that should be included in the split
        manifest even if the tool doesn't automatically detect that it is
        necessary.

    ```
        <add_project name="platform/external/python/cpython3" />
    ```

1.  Regenerate the split manifest using `treble_manifest_split` in your
    full-manifest directory. Remember to pass the path of your config XML to the
    script's `--config` flag.

### 5. Compare built artifacts

A successful build alone is not sufficient to have full confidence in the split
manifest. You should diff the output artifacts of the split-manifest build
against the output artifacts of the full-manifest build.

Suggestions for viewing diffs:

-   Use an external directory diffing tool on the output directories for each
    partition, such as `out/target/product/<device>/system`.
-   Use `development/vndk/tools/image-diff-tool/diff.py` on output directories,
    or on a zipped target-files archive if you are creating `dist` builds.

The following may cause differences between output artifacts:

-   Non-hermetic inputs used in the module build rule, such as timestamps. Can
    be fixed by removing the timestamp from the build rule.
-   An implicit and optional source dependency. Can be fixed by manually adding
    the project that defines the missing source.
