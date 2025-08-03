# Updating the Bazel prebuilts in AOSP

## Instructions

First, decide which version of Bazel you need.

*   A Bazel release (e.g. Bazel 3.7.0)
*   A Bazel nightly
*   A Bazel per-commit build

Whichever of these you use, you will need to use official **nojdk x86-64**
versions of Bazel, for Linux and macOS (Darwin).

Run the `release_bazel.py` script in the root repository to download
and verify the binaries from the trusted Bazel CI pipeline:

`python /prebuilts/bazel/common/release_bazel.py --commit  <commit>`

To get the commit hash for builds, see the [Bazel releases], [Bazel nightlies]
or [Bazel per-commit builds] sections below.

this will run `update.sh` which  will also:
  - Download the remote_java_tools prebuilts corresponding
    to the downloaded Bazel binary
  - verify that the downloaded binary has the correct SHA-256
    checksum as provided from Bazel CI metadata.

Once you have the binaries, you will need to create and send up to three CLs,
to update the Linux, macOS, and platform-agnostic prebuilts that live in separate
Git repositories, i.e.

*   https://android.googlesource.com/platform/prebuilts/bazel/linux-x86_64/
*   https://android.googlesource.com/platform/prebuilts/bazel/darwin-x86_64/
*   https://android.googlesource.com/platform/prebuilts/bazel/common/

The update script does not automatically create CLs, so you need to create them
manually. In each CL description, mention the testing you did, which should
at least include:

*   For release builds only: **Verifying the file signature**, e.g.
    *   `gpg --import bazel-release.pub.gpg`
        *   Taken from https://bazel.build/bazel-release.pub.gpg
    *   `gpg --verify bazel_nojdk-<commit>-linux-x86_64.sig`
        *   It should say "Good signature from Bazel Developer ..."
*   **Verifying that Bazel starts, on Linux and on macOS**, e.g.
    *   `source build/envsetup.sh`
    *   `bazel info`

Ensure that the CLs are set to the same Gerrit topic so they are submitted together.

## Obtaining Bazel binaries

The `update.sh` script automates downloading Bazel binaries. The next sections
describe how the different Bazel binaries (release, nightly, per-commit) can be
manually downloaded from the Bazel CI.

### Bazel releases

The commit hash for linux and darwin **nojdk x86-64** binaries are available from https://github.com/bazelbuild/bazel/releases

### Bazel nightlies

The commit hash and urls for linux and macOS **nojdk x86-64** binaries are available in https://storage.googleapis.com/bazel-builds/metadata/latest.json

### Bazel per-commit builds

You need to know the GitHub commit that contains your change, e.g.
https://github.com/bazelbuild/bazel/commit/364a867df255c57c8edc4a8aae8f78cb54900a54

And the linux and macOS **nojdk x86-64** binaries are available from:

*   `https://storage.googleapis.com/bazel-builds/metadata/<commit>.json`
    *   e.g. https://storage.googleapis.com/bazel-builds/metadata/364a867df255c57c8edc4a8aae8f78cb54900a54.json
