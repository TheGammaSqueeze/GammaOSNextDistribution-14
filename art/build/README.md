# Building the ART Module

ART is built as a module in the form of an APEX package, `com.android.art.apex`.
That package can be installed with `adb install` on a device running Android S
or later. It is also included in the system partition (in the `system/apex`
directory) of platform releases, to ensure it is always available.

The recommended way to build the ART Module is to use the `master-art` manifest,
which only has the sources and dependencies required for the module.

The ART Module is available as a debug variant, `com.android.art.debug.apex`,
which has extra internal consistency checks enabled, and some debug tools. A
device cannot have both the non-debug and debug variants installed at once - it
may not boot then.

`com.google.android.art.apex` (note `.google.`) is the Google signed variant of
the module. It is also mutually exclusive with the other ones.


## Building as a module on `master-art`

1.  Check out the `master-art` tree:

    ```
    repo init -b master-art -u <repository url>
    ```

    See the [Android source access
    instructions](https://source.android.com/setup/build/downloading) for
    further details. Google internal users please see [go/sync](http://go/sync).

2.  Set up the development environment:

    See
    [art/test/README.chroot.md](https://android.googlesource.com/platform/art/+/refs/heads/main/test/README.chroot.md)
    for details on how to set up the environment on `master-art`, but the
    `lunch` step can be skipped. Instead use `banchan` to initialize an
    unbundled module build:

    ```
    source build/envsetup.sh
    banchan com.android.art <arch>
    ```

    `<arch>` is the device architecture - use `hmm` to see the options.
    Regardless of the device architecture, the build also includes the usual
    host architectures, and 64/32-bit multilib for the 64-bit products.

    To build the debug variant of the module, specify `com.android.art.debug`
    instead of `com.android.art`. It is also possible to list both.

3.  Build the module:

    ```
    m apps_only dist
    ```

4.  Install the module and reboot:

    ```
    adb install out/dist/com.android.art.apex
    adb reboot
    ```

    The name of the APEX file depends on what you passed to `banchan`.


## Building as part of the base system image

NOTE: This method of building is slated to be obsoleted in favor of the
module build on `master-art` above (b/172480617).

1.  Check out a full Android platform tree and lunch the appropriate product the
    normal way.

2.  Ensure the ART Module is built from source:

    ```
    export ART_MODULE_BUILD_FROM_SOURCE=true
    ```

    If this isn't set then the build may use prebuilts of the ART Module that
    may be older than the sources.

3.  Build the system image the normal way, for example:

    ```
    m droid
    ```


## Prebuilts

Prebuilts are used for the ART Module dependencies that have sources outside the
`master-art` manifest. Conversely the ART Module may be a prebuilt when used in
platform builds of the base system image.

The locations of the prebuilts are:

*  `prebuilts/runtime/mainline` for prebuilts and SDKs required to build the ART
   Module.

   See
   [prebuilts/runtime/mainline/README.md](https://android.googlesource.com/platform/prebuilts/runtime/+/master/mainline/README.md)
   for instructions on how to update them.

*  `packages/modules/ArtPrebuilt` for the ART Module APEX packages, if present.

*  `prebuilts/module_sdk/art` for the ART Module SDK and other tools, needed to
   build platform images and other modules that depend on the ART Module.
