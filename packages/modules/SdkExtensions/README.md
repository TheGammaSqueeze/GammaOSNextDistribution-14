# SdkExtensions module

SdkExtensions module is responsible for:
- deciding the extension SDK level of the device;
- providing APIs for applications to query the extension SDK level;
- determining the values for the BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, and
  SYSTEMSERVERCLASSPATH environment variables.

## General information

### Structure

The module is packaged in an apex, `com.android.sdkext`, and has several
components:
- `bin/derive_classpath`: a native binary that runs early in the device boot
  process. It reads individual classpath configs files from the system and
  other modules, merges them, and defines the definition of *CLASSPATH environ
  variables.
- `bin/derive_sdk`: native binary that runs early in the device boot process and
  reads metadata of other modules, to set system properties relating to the
  extension SDK (for instance `build.version.extensions.r`).
- `javalib/framework-sdkextension.jar`: this is a jar on the bootclasspath that
  exposes APIs to applications to query the extension SDK level.

### Deriving extension SDK level
`derive_sdk` is a program that reads metadata stored in other apex modules, in
the form of binary protobuf files in subpath `etc/sdkinfo.pb` inside each
apex. The structure of this protobuf can be seen [here][sdkinfo-proto]. The
exact steps for converting a set of metadata files to actual extension versions
is likely to change over time, and should not be depended upon.

### Reading extension SDK level
The module exposes a java class [`SdkExtensions`][sdkextensions-java] in the
package `android.os.ext`. The method `getExtensionVersion(int)` can be used to
read the version of a particular sdk extension, e.g.
`getExtensionVersion(Build.VERSION_CODES.R)`.

### Deriving classpaths
`derive_classpath` service reads and merges individual config files in the
`/system/etc/classpaths/` and `/apex/*/etc/classpaths`. Each config stores
protobuf message from [`classpaths.proto`] in a proto binary format. Exact
merging algorithm that determines the order of the classpath entries is
described in [`derive_classpath.cpp`] and may change over time.

[`classpaths.proto`]: https://android.googlesource.com/platform/packages/modules/common/+/refs/heads/master/proto/classpaths.proto
[`derive_classpath.cpp`]: derive_classpath/derive_classpath.cpp
[sdkinfo-proto]: https://android.googlesource.com/platform/packages/modules/common/+/refs/heads/master/proto/sdk.proto
[sdkextensions-java]: java/android/os/ext/SdkExtensions.java

## Developer information

### Defining a new extension version

In order to bump the extension version, the following steps must be taken.

#### Gather information

1) Identify the set of modules that are part of this extension version release.
These are the set of modules that are releasing new APIs in this train.

2) Decide the integer value of this extension version. Usually this is the
`previous_version + 1`.

#### Code changes

3) **build/make:** Update the extension version of the module development
branch. This is defined by the `PLATFORM_SDK_EXTENSION_VERSION` variable in
`core/version_defaults.mk`. Subsequent module builds in the branch will embed
the new version code into the proto in the modules.

   [Example CL][bump]

4) **packages/modules/SdkExtensions:** Define the new SDK extension version.
We have a utility script that automates this. Run:
   ```sh
   $ packages/modules/SdkExtensions/gen_sdk/bump_sdk.sh <NEW_VERSION> <MODULES> <BUG>
   ```

   ...where `<MODULES>` is a comma-separated list of modules included in the
   bump, with identifiers listed in the [sdkinfo proto][sdkinfo-proto]). To
   include all modules, this argument can be omitted.

   [Example CL][def]

5)  Upload these two CLs in a topic and submit them. It is imperative that

   * the cl generated in step #3 is included in the builds of all the relevant
    modules in the train
   * the cl generated in step #4 is included in the SdkExtensions build of the
    train

#### Update continuous test configuration

6) The continuous test configuration has a list of module builds to include when
running the SdkExtensions tests. They need to be updated to use module builds
that contain the CLs generated above. See http://shortn/_aKhLxsQLZd

#### Finalize SDK artifacts

7) **prebuilts/sdk & module sdk repos:** Once the train is finalized, the API
artifacts need to be recorded for doc generation to work correctly. Do this by
running the finalize_sdk script:

    ```
    $ packages/modules/common/tools/finalize_sdk.py \
        -f <VERSION> \
        -b <BUG> \
        -r <README_ENTRY> \
        -m <MODULE1> \
        -m <MODULE2> [..]
    ```

   [Example CL][finalize]

[bump]: https://android.googlesource.com/platform//build/+/f5dfe3ff7b59b44556510ba89d15161c87312069
[def]: https://android.googlesource.com/platform/packages/modules/SdkExtensions/+/5663ebb842412b0235a140656db17025280f9f08
[derive_sdk_test]: derive_sdk/derive_sdk_test.cpp
[current_version]: java/com/android/os/ext/testing/CurrentVersion.java
[finalize]: https://android.googlesource.com/platform/prebuilts/sdk/+/d77e77b6746acba806c263344711eb0c4df2b108

### Adding a new extension

An extension is a way to group a set of modules so that they are versioned
together. We currently define a new extension for every Android SDK level that
introduces new modules. Every module shipped in previous versions are also part
of the new extension. For example, all the R modules are part of both the R
extensions and the S extensions.

The steps to define a new extension are:

-   Add any new modules to the SdkModule enum in sdk.proto. e.g.
    [for new required, updatable modules in U](http://ag/21148706)

-   Add the binary "current sdk version" proto to the apexes of the new modules.
    e.g. [for health fitness](http://ag/21158651) and
    [config infrastructure](http://ag/21158650).

-   Update `derive_sdk.cpp` by:

    *   mapping the modules' package names to the new enum values

    *   creating a new set with the new enum values of the modules relevant for
        this extension.

    *   set a new sysprop to the value of `GetSdkLevel` with the new enum set

    *   add a unit test to `derive_sdk_test.cpp` verifying the new extensions
        work

    *   update the hard-coded list of extensions in `ReadSystemProperties`

    *   e.g. [for U extension](http://ag/21481214)

-   Make the `SdkExtensions.getExtensionVersion` API support the new extensions.

    *   Extend `CtsSdkExtentensionsTestCase` to verify the above two behaviors.

    *   e.g. [for U extensions](http://ag/21507939)

-   Add a new sdk tag in sdk-extensions-info.xml
