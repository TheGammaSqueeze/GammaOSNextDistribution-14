# Description

Adevice is a command-line tool that enables Android Platform developers to update their device with their locally built artifacts. The tool provides two major functions:

* __Synchronizing files from the build output to the device.__ This includes pushing files to the device, cleaning stale on-device files, and providing guidance about conditions that could lead to a non-functional device.
* __Running post-sync actions to put the device in a consistent working state.__ These actions are based on the files that have changed and range from doing nothing to rebooting the device.

Conceptually `adevice` tries to align the filesystem structure of the updated device to be similar to what a flash would have produced.

`Adevice` is similar to `adb sync` in that it updates the device with files from the build staging directory but has several other differences:

* `Adevice` has the notion of an _Update Set_ that limits what is pushed. This concept also enables a host of other features such as determining stale files and cleaning them.
* `Adevice` tracks content rather than timestamps to determine changed files. This limits the number of files that get pushed to the device.

Note that `adevice` does not:

* Install APKs or APEX's to the device. This ensures that the device looks as close to what flashing would have produced.  It uses `adb push`, not `adb install`.

## Prerequisites
`Adevice` is intended for incremental updates and when there aren't many differences between the local build and the device. It is recommended to build and flash after syncing source before the first run.

## Usage
``` adevice help ``` for more options.

### Updating the device
``` adevice update ```

# Displaying the build and device status
``` adevice status ```

# Adding a module to the update set
To push modules to the device that are not normally part of the image, you can add them to the update set with:

``` adevice track SomeModule ```

# Removing a module from the update set
To remove modules that you added to the update set:

``` adevice untrack SomeModule ```

# Cleaning stale files from the device
``` adevice clean ```

You can specify which connected device to use with the environment variable ANDROID_SERIAL or with the `-s` flag.
