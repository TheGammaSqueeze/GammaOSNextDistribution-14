# About `external/wayland`

## What this is.

This Android repository contains minimally modified snapshots of official
release of the core Wayland system code, and its core protocol. It is
configured to easily build for use in Android projects.

Official home page: <https://wayland.freedesktop.org>

Official source code home: <https://gitlab.freedesktop.org/wayland/wayland.git>

## Changes from the official version

### Android Open Source Metadata

These files should always be present.

- `./LICENSE` (symlinks to COPYING)
- `./METADATA`
- `./MODULE_LICENSE_MIT`

Note that `./METADATA` should be updated with the latest version and time
imported. If using `import_official_snapshot.py`, this will be updated by the
script automatically.

For further details about these files, refer to
<https://opensource.google/docs/thirdparty/android#how-to-add-new-third-party-code-to-android>

### Android Build Files

These files are NOT in the official source code, and have been added to make it
usable in Android. These should always be present, according to the current
open source rules, and are not really functional changes.

- `./Android.bp`

  The Android Soong blueprint file which builds the libraries.

- `./config.h`

  A fixed `config.h` to use in all Android builds, defining C preprocessor
  macros for features generally available on the Android platform.

- `./import_official_snapshot.py`

  A helper Python script useful to import updated official versions of the
  source code, while still preserving all needed Android changes. See below
  for usage.

- `./wayland-version.h`

  A static expansion of `src/wayland-version.h.in` for the current Wayland
  version here. If using `./import_official_snapshot.py`, this will be updated
  by the script automatically.

- `./OWNERS`

  The list of OWNERS for the code here.

- `./PREUPLOAD.cfg`

  A `repo` preupload hook. Mostly just exists to strongly recommend making
  changes in the AOSP master branch.

- `./README.android.md`

  This document, describing the differences from the official sources, and
  giving instructions for upgrading.

### Additional patches for Android

These are any additional changes to the Wayland core sources that might be
helpful for use with the Android platform.

These differences are stored in `./patches/`, and are expected to cleanly apply
to the current version of Wayland used here.

## Importing a new snapshot

`./import_official_snapshot.py` can be used to automate the work of pulling in
an updated snapshot. Its only dependencies are a standard Python3 installation,
along with command-line git.

Usage:

```
./import_official_snapshot.py <version-tag>
```

For example, to import the "1.18.0" version tag from the official sources:

```
./import_official_snapshot.py 1.18.0
```

The script will create a new branch based on your current checkout, and commit
a series of changes to it:

- An inital empty commit with a simple import message.

- A commit removing all existing files, and adding in a clean import of the
  **CURRENT** version from the official sources.

- A commit adding (back) the various required Android files

- Additional commits for each patch in `./patches/`

The script will then validate that there are no differences between the branch
start (committed code) and a cleanly-patched import of the current version. If
there is a difference, it should mean that there is some patch to the code not
in `./patches`.

Assuming success, the script will continue commiting to the branch:

- A commit removing all existing files, and adding in a clean import of
  the **NEW** version from the official sources.

- A commit adding (back) the various required Android files, including
  updating `./METADATA` and `./wayland-version.h`.

- A commit for each patch in `./patches/`, if there are any.

If there were no problems needing manual intervention, the script will then
execute a final `git rebase -i --autosquash` to squash the CL into a single
commit for upload review. Otherwise, you should do that squash it to a single
commit yourself.

There may be failures in applying the patches if there are conflicts, in
which case they need to be resolved before squashing. Please update the files
in `./patches/` with the new patches.

Additionally, there may be additional changes needed to `./Android.bp` for
the client and server libraries to build.

## Adding new patches

Please maintain the list of patch files in `./patches` against the current
version of Wayland if you make any changes to the official sources.

With no arguments, `./import_official_snapshot.py` will do a clean import of the
current version, and apply the current list of patches WITHOUT squashing the
commits involved.

You can then make the changes to the sources, and do a clean reexport of the
patch files for the last N commits.

```
# Remove the current patch files
git rm patches/*

# Export the last five commits as patches. You should adjust this number.
git format-patch HEAD~5 --no-stat --numbered --zero-commit --no-signature \
    --suffix=.diff --output-directory patches/
```

Create a new commit that adds those same patches, and squash all the commits
together. You should end up with a commit that makes the changes as well as adds
the patch file.

If you want, you can then run `./import_official_snapshot.py` again to validate
that your patch will be applied. If there is a difference, it will be reported.

