Instructions for building/running the minimal apex

You need an android device/emulator to run it on, an easy option is:

```
lunch sdk_phone_x86_64-userdebug
m
emulator
```

To build and install with soong:
```
m build.bazel.examples.apex.minimal && adb install out/target/product/emulator_x86_64/product/apex/build.bazel.examples.apex.minimal.apex && adb reboot
```

To build and install with bazel:
```
b build --config=android_x86_64 //build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal && adb install bazel-bin/build/bazel/examples/apex/minimal/build.bazel.examples.apex.minimal.apex && adb reboot
```

The first time you try to install the apex, you will probably get this error:

```
adb: failed to install out/target/product/emulator_x86_64/product/apex/build.bazel.examples.apex.minimal.apex: Error [1] [apexd verification failed : No preinstalled apex found for package build.bazel.examples.apex.minimal]
```

There's probably better ways to resolve it, but one easy way is to take advantage of a bug (b/205632228) in soong and force it to be preinstalled by running:

```
m installclean
m build.bazel.examples.apex.minimal
m
```

and then restarting the emulator. After you've done this once you can use the regular install commands above from then on.

To run the binary that the apex installs:

```
adb shell /apex/build.bazel.examples.apex.minimal/bin/build.bazel.examples.apex.cc_binary
```
