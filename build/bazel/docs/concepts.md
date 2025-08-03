# Android Build System Concepts

This document provides high level explanations and mapping of the internal
build system components and concepts of the Android build system and Bazel,
and how components communicate with each other.

For implementation concepts, see:
https://android.googlesource.com/platform/build/bazel/+/refs/heads/master/docs/internal_concepts.md.

## High level components

This table provides a high level overview of the components in the current
Android Platform build system, and how each component maps to a concept in
Bazel.

|Android build system component|Description|Mapping to Bazel concepts|
|---|---|---|
|Kati|Make-compatible front-end. Encodes build logic in `.mk` scripts. Declares buildable units in `Android.mk`. Generates Ninja file directly.|Loading and analysis phase. Conceptually similar to `bazel build --nobuild`.|
|Blueprint|Build definition syntax. Build syntax parser. Internal data structures like Modules/Variations/Context/Scope. Ninja file generator.|Starlark.|
|Soong|Bazel-like front-end. Encodes build logic in Go. Declares build units in `Android.bp`, parsed by Blueprint. Uses Blueprint to generate Ninja file.  Generates a `.mk` file with prebuilt module stubs to Kati.|Loading and analysis phase. Conceptually similar to `bazel build --nobuild command`.|
|Ninja|Serialized command line action graph executor. Executes Ninja graph generated from Kati and Soong.|Bazel's execution phase.|
|atest|Test executor and orchestrator.|Conceptually similar to `bazel test` command.|
|Blueprint + Kati + Soong + Ninja + atest|The entire build pipeline for Android.|Conceptually similar to `bazel build` or `bazel test` commands.|
|`<script>.sh`|Running arbitrary scripts in AOSP.|Conceptually similar to `bazel run` command.|
|Make (replaced in-place by Kati)|No longer in use. Entire build system, replaced by the tools above.|Loading, analysis, execution phases. Conceptually similar to `bazel build` command.|
|`Android.bp`|Build definition file for Soong.|`BUILD.bazel` or `BUILD`.|
|`Android.mk`|Build definition file for Kati.|`BUILD.bazel` or `BUILD`.|

## Communication between components

* Kati product configuration component to generate config variables (config.mk, AndroidProducts.mk)
  * **“Kati-config” for the purpose of this document**
* Kati component to generate build actions in Ninja files (main.mk, Android.mk files)
  * **“Kati-build” for the purpose of this document**
* Kati component to generate packaging actions in Ninja files (packaging.mk file)
  * **“Kati-package” for the purpose of this document**
* Kati component to generate cleaning actions in Ninja files (cleanbuild.mk, CleanSpec.mk files)
  * **"Kati-cleanspec" for the purpose of this document**
* **soong\_build** (and **Blueprint**) component to generate build actions (Android.bp, Blueprints files)
* **Ninja** component to execute actions from Kati-build, Kati-package and soong\_build
* **Bazel** as the next generation of the entire build system, starting as a Ninja executor drop-in replacement
* **soong\_ui** as the tool to orchestrate all of the above, and with auxiliary tools like finder, path\_interposer, soong\_env, minibp and bpglob.

The current build system architecture primarily uses **files** as the medium
for inter-process communication (IPC), with one known case of unix socket
communication (e.g.
[path\_interposer](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/paths/logs.go;l=112-133;drc=184901135cda8bdcc51cab4f16c401a28a510593)),
and a fifo between Ninja and soong_ui for the Protobuf stream for build
status reporting.

## Component order

The build system components run in the following order, orchestrated by soong\_ui:

1. soong\_ui bootstraps itself with microfactory (`go build` replacement) and launches.
1. soong\_ui runs auxiliary tools to aggregate files into filelists, for
Android.mk, Android.bp, AndroidProducts.mk and several others.
1. soong\_ui runs Kati-config with
   [the config.mk entry point](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/dumpvars.go;l=89;drc=9f43597ff7349c4facd9e338e5b4b277e625e518).
1. soong\_ui orchestrates 3 Blueprint/Soong phases to generate the main out/soong/build.ninja file:
   minibootstrap, bootstrap, and primary.
    1. Minibootstrap phase uses Blueprint/Microfactory to build itself
       (minibp) so that Android.bp and Blueprint files can be used to define
       Soong.
    1. Bootstrap phase runs Ninja on a build.ninja file that runs minibp to
       read all Android.bp files across the source tree that describes Soong and
       plugins, and builds soong\_build.
    1. Primary phase runs Ninja on a build.ninja file that runs soong_build
       to generate the final out/soong/build.ninja file.
    1. soong\_build also runs its own tests alongside generating
    out/soong/build.ninja, which can be skipped with the `--skip-soong-tests`
    argument.
1. soong\_ui runs Kati-cleanspec with
   [the cleanbuild.mk entry point](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/kati.go;l=362;drc=b1d30d63c5d1b818ea38e77cd155da2016fe8b6c).
1. soong\_ui runs Kati-build to generate a Ninja file, with
   [the main.mk entry point.](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/kati.go;l=202;drc=b1d30d63c5d1b818ea38e77cd155da2016fe8b6c)
1. soong\_ui runs Kati-package to generate a Ninja file, with
   [the packaging/main.mk](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/kati.go;l=314;drc=b1d30d63c5d1b818ea38e77cd155da2016fe8b6c)
   entry point.
1. soong\_ui generates a Ninja file to combine above Ninja files.
1. soong\_ui runs either Ninja or Bazel to execute the build, with the
combined Ninja file as entry point.

soong\_ui has a --skip-make flag that will skip Kati-config, Kati cleanspec,
Kati-build and Kati-package, used for Soong-only builds in NDK and some
Mainline projects.

### soong\_ui

soong\_ui is primarily responsible for orchestrating the build, cleaning the
build environment, and running auxiliary tools. These tools (minibp,
microfactory) can bootstrap other tools (soong\_build), aggregate file lists
(finder.go), improve hermeticity (path\_interposer, nsjail) or perform checks
against the environment (soong\_env).

soong\_ui uses finder.go to generate &lt;filename>.list files for other
tools. For example, it generates Android.mk.list for Kati-build,
AndroidProducts.mk.list for Kati-config, and Android.bp.list for
soong\_build.

soong\_ui uses path\_interposer to prepare an hermetic $PATH with runtime
checks against allowlisted system tools. The $PATH contains these system
tools with checked-in prebuilts, and uses path\_interposer to intercept calls
and error out whenever non-allowlisted tools are used (see out/.path for
directory of intercepted tool symlinks).

soong\_ui generates a Kati suffix to ensure that Kati-generated files are
regenerated if inputs to Kati have changed between builds.

soong\_ui calls Soong and Kati to generate Ninja files, and eventually
creates another Ninja file (out/combined-&lt;product>.ninja) to combine the
others, and executes either Ninja or Bazel to complete the build.

soong\_ui sets up the sandbox and environment for the Ninja/Bazel process.

## Kati-config

As a product configuration tool, soong\_ui runs Kati-config in
**[--dumpvars-mode](https://cs.android.com/android/platform/superproject/+/master:build/soong/cmd/soong_ui/main.go;l=298-305;drc=master)**
to dump the values of specified Make variables at the end of an evaluation,
with build/make/core/config.mk as the entry point. During this phase,
Kati-config eventually evaluates[
soong\_config.mk](https://cs.android.com/android/platform/superproject/+/master:build/make/core/soong_config.mk;l=2?q=soong.variables)
to generate the **[soong.variables JSON
file](https://cs.android.com/android/platform/superproject/+/master:build/make/core/soong_config.mk;l=16-222;drc=341928ecc5da205401bcfd86f098662b0cee7857)**.
This way, Kati-config can communicate product configuration to soong\_build,
as soong\_build parses the dumped variables from the JSON on startup, and
stores them into an in-memory Config object.

To communicate
[dexpreopt](https://cs.android.com/android/platform/superproject/+/master:build/soong/java/dexpreopt.go;l=115;drc=8cbc5d269b20b5743679bfb8684ed174dcf58a30)
variables to soong\_build, [dexpreopt.config is also
generated](https://cs.android.com/android/platform/superproject/+/master:build/make/core/dex_preopt_config.mk;l=71-142;drc=f26015449f0747b9fdeceb5ce70e30ecd76e20e8)
as a $(shell) action and [read by
soong\_build](https://cs.android.com/android/platform/superproject/+/master:build/soong/dexpreopt/config.go;l=175-196;drc=1af783fae74715bcf1a94733bd75b2e6cc688e8c)
in a similar way as Kati-config evaluates dex\_preopt\_config.mk included in
soong\_config.mk.

soong\_ui sets up a **KatiReader** to monitor Kati-config’s stdout/err for UI
reporting and error handling purposes.

## soong\_build

soong\_build’s primary role is to evaluate all Android.bp files, run a series
of mutators, and generate out/soong/build.ninja file.

soong\_build communicates with Kati-build by generating Make Vars and running
the AndroidMk singleton to generate .mk files in the output directory
(out/soong/{Android, late, make\_vars}-&lt;product>.mk).

* Android-&lt;product>.mk contains Soong modules as Make modules so Make
  modules can depend on Soong modules.
* make\_vars-&lt;product>.mk contains Make variables for Kati-build, exported
  from Soong modules. There are also checks built into this .mk file to ensure that
  if a duplicate Make variable of the same name comes from another source, the Soong
  and Make variable values are identical.
* late-&lt;product>.mk contains Make variables that are not read while Kati-build
  parses the Android.mk file. (Late variables)
  * soong\_ui invokes Kati to parse make\_vars .mk file earlier than the Android.mk
  files,and late.mk after parsing the Android.mk files.
  * late.mk is used to define phony rules to take advantage of Make’s ability to
  add extra dependencies to an existing rule. late.mk is not strictly necessary to
  make this happen at this moment, since late.mk rules don’t currently depend on any
  variables defined during Android.mk processing (e.g. ALL\_MODULES$(module).INSTALLED).

## Kati-build / Kati-package

Kati-build’s primary role is to evaluate all Android.mk files with
build/make/core/main.mk as entry point, and generate
out/build-&lt;product>.ninja. It also generates cleanspec.ninja for the
product, containing statements on how to remove stale output files.

Kati-build’s primary role is to evaluate all packaging .mk files with
build/make/packaging/main.mk as entry point, including
build/make/packaging/distdir.mk for dist-for-goals calls, and generate
out/package-&lt;product>.ninja.

Kati-build/Kati-package’s stdout/stderr is monitored by soong\_ui’s
KatiReader to UI and error handling.

As Kati-build/Kati-package generates Ninja files, they also generate
out/ninja-&lt;product>.sh and out/env-&lt;product>.sh. These scripts are
wrappers for soong\_ui to execute Ninja with the correct Ninja files, in a
controlled environment.

## Ninja

As Ninja executes files from Kati-build, Kati-package, soong\_build and other
bootstrapping tools like Blueprint, it writes to a fifo in a proto front end
that soong\_ui monitors with NinjaReader. NinjaReader ensures that the user
interface for Ninja progress is consistent with the rest of the build.

## Bazel

As more Soong modules are converted to BUILD files, soong\_build serializes
information about converted modules to BUILD/bzl files on disk. soong\_build
then consumes information about these targets from Bazel by directly calling
the Bazel client to issue `cquery` calls about these targets.
