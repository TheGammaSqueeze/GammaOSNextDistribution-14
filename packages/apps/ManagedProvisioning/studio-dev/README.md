## SystemUI in Android Studio (internal documentation)

```sh
$ ./packages/apps/ManagedProvisioning/studio-dev/studiow
```

### Setup
- Run this command from the root of the initialized repo directory to start android studio
```sh
$ ./packages/apps/ManagedProvisioning/studio-dev/studiow
```
- Make changes and run `SystemUI` run configuration from Android Studio (or `SystemUITitan` when working with tablets). This configuration for system UI injects custom shell function to remount and replace the apk instead of using pm install, using a different run configuration will not work.

##### -studio branch (useful for mac)
You can alternatively checkout the sysui-studio repo branch `xxx-sysui-studio-dev` (e.g.: [master-sysui-studio-dev](https://goto.google.com/repo-init/master-sysui-studio-dev). This tracks the same git branches as the corresponding `xxx` branch but with minimal dependencies.
Command line `make` will not work on this branch as it only tracks a small number of projects to keep the checkout as small as possible (as of late 2022: 55G with [partial clones](https://goto.google.com/git-reaper) of all repositories).

### First run
- Make sure to have the rooted device connected. The script pulls the dex files from the device and decompiles them to create an SDK with hidden APIs.
- If import settings dialog shows up in Android Studio, select do-not import.
- If sdk wizard shows up, cancel and select never run in future.
- If the project do not open (happens when sdk wizard shows up), close Android Studio and run `studiow` again.
- First time you install systemUI, you might need to
  - run 'adb disable-verity' (which requires a reboot)
  - reboot your device for dexopt to kick in

### Running tests
You should be able to run instrumented tests in AndroidStudio using `SystemUI` configuration.

### Updating SDK
If after a sync, you are unable to compile, it's probably because some API in framework changed and
you need to update your SDK. Depending on how you checked out your tree, there are two ways to
update the SDK. In either case, you should pass the `--update-sdk` flag.

For a minimal studiow-dev checkout, the SDK must be pulled from the device. Flash your device with
latest image (corresponding to the tree you are working on) from [Flashstation](http://go/flash) and
update the sdk using the `--update-sdk` flag:

```sh
$ ./packages/apps/ManagedProvisioning/studio-dev/studiow --update-sdk
```

For a platform checkout, you have the option to use the built SDK from your tree's out/ directory.
This SDK exists if you've previously built with `m`. The script will prefer to use the built SDK if
it exists, otherwise it will attempt to pull the SDK from the attached device. You can pass
`--pull-sdk` to override this behavior and _always_ pull the SDK from the attached device, whether
or not the built SDK exists.

For example:
 - If you are using a sysui-studio checkout, it will always pull the SDK from the attached device.
 - If you are using a platform checkout which you've never built, it will pull the SDK from the
   attached device.
 - If you are using a platform checkout which you've built with `m`, it will use the SDK from the
   out/ directory. However, in this scenario, if you wanted to use the SDK from the attached device
   instead you can pass `--pull-sdk`.

### Adding dependencies

When you add new dependencies to SystemUI (Android.bp files), they will also need to be added
in the sysui-studio gradle files in SysUIGradleProject/ before they are available in your project.
These dependencies should stay in sync - don't add dependencies to Gradle that aren't in Soong.

## FAQ / Helpful info

This project is using android studio + gradle as a build system, and as such it can be tricky to bootstrap for a first time set up or after a sync. This section is for some common problem/solution pairs we run into and also some general things to try.

#### We have both flavors, country _and_ western

Remember that this project can be run from a full platform checkout **or** a minimal studio-dev checkout.

#### Make sure the sdk is updated after a sync

 `./studiow --update-sdk` is your friend.

 We pull the framework.jar (&friends) from the device and do some `<magic>` to compile against hidden framework APIs. Therefore, you have to build and flash the device to be current, and then pull the resulting jars from the device using the above command.

 > NOTE: if you're using the studio-dev branch (minimal checkout), then you want to ensure that the device image is as close in time to your local checkout as possible. You'll want to flash the device to the most recent possible build when you do a sync. Platform builds will always be in lock-step so long as you build after syncing.

#### Android sdk choice

Android Studio shouldn't ask you to choose which Android SDK to use and will instead select the
right SDK automatically. But, [if it does ask](https://screenshot.googleplex.com/AtA62tTRyKWiSWg),
choose the **project** SDK (likely in the `.../<branchname>/prebuilts/fullsdk-linux` directory),
**not** the Android SDK (likely in the `.../<username>/Android/Sdk` directory).

#### Javac `@IntDef`s compiler error

You will find `@IntDef` clauses all over the platform. It sometimes works in ASwB-studio but breaks the build at sysui-studio-dev. The reason is `Java 8 javac` has some issue to deal with this but not `Java 9 javac`. The workaround solution would be to avoid the static imports.

Build errors (cannot find symbol @IntDef) in Java 8 javac:
```
import static com.example.myapplication.MainActivity.LockTypes.PASSWORD;
import static com.example.myapplication.MainActivity.LockTypes.PIN;
import static com.example.myapplication.MainActivity.LockTypes.PATTERN;

@IntDef({
        PASSWORD,
        PIN,
        PATTERN
})
@interface LockTypes {
    int PASSWORD = 0;
    int PIN = 1;
    int PATTERN = 2;
}
```

Workaround to avoid the static imports:
```
@IntDef({
        LockTypes.PASSWORD,
        LockTypes.PIN,
        LockTypes.PATTERN
})
@interface LockTypes {
    int PASSWORD = 0;
    int PIN = 1;
    int PATTERN = 2;
}
```
You could find more details discussion
[here](https://buganizer.corp.google.com/issues/67418397).

#### Some other things to think about:

1. Build > clean project
2. File > Invalidate caches & restart

#### Android Studio is not launching

If Android Studio fails to start when running studio wrapper you can try to launch the binary directly to see more logs.

After running studio wrapper once you can find the binary in `~/.AndroidStudioSystemUI` directory. For example, on macOS it may look like this:

```
/Users/{{USERNAME}}/.AndroidStudioSystemUI/bin/android-studio-ide-201.6953283-mac/Android Studio.app/Contents/MacOS/studio
```

One of the issues that you may encounter is merging studio.vmoptions from different Android Studio installations that result into conflicting options.

You may see something similar to this in the logs:

```
...
2021-06-02 13:58:37.069 studio[20732:221422] Processing VMOptions file at /Users/{{USERNAME}}/.AndroidStudioSystemUI/bin/android-studio-ide-201.6953283-mac/Android Studio.app/Contents/bin/studio.vmoptions
2021-06-02 13:58:37.070 studio[20732:221422] Done
2021-06-02 13:58:37.070 studio[20732:221422] Processing VMOptions file at /Users/{{USERNAME}}/Library/Application Support/Google/AndroidStudio4.1/studio.vmoptions
2021-06-02 13:58:37.070 studio[20732:221422] Done
2021-06-02 13:58:37.070 studio[20732:221422] Processing VMOptions file at
2021-06-02 13:58:37.072 studio[20732:221422] No content found
Conflicting collector combinations in option list; please refer to the release notes for the combinations allowed
2021-06-02 13:58:37.079 studio[20732:221422] JNI_CreateJavaVM (/Users/{{USERNAME}}/.AndroidStudioSystemUI/bin/android-studio-ide-201.6953283-mac/Android Studio.app/Contents/jre/jdk) failed: 4294967295
```

To resolve this you can remove all other Android Studio installations (including `Application Support/Google/AndroidStudio*` directories) and re-launch the wrapper.

If the logs show an error similar to:

```
Error opening zip file or JAR manifest missing : ../plugins/g3plugins/bin/FileProfilingAgent.jar
Error occurred during initialization of VM
agent library failed to init: instrument
```

you can edit the file

```
/Users/{{USERNAME}}/Library/Application\ Support/Google/AndroidStudio2021.2/studio.vmoptions
```

and comment out the line

```
# -javaagent:../plugins/g3plugins/bin/FileProfilingAgent.jar
```

and try launching as normal.


#### Enable hidden projects

Some projects are hidden in Android Studio (the support app, platform code, and the one search plugin). To enable these projects for debugging and building, for example, the One Search Plugin, you need to uncomment it in `packages/apps/ManagedProvisioning/studio-dev/SysUIGradleProject/settings.gradle` for these lines:

```
// Uncomment this for OneSearch Plugin app
// include ':OneSearch'
// project(':OneSearch').projectDir = new File(rootDir,'../../../NexusLauncher/plugins/OneSearchPlugin')
```

After syncing gradle, the project should show up in Configurations in Android Studio. You might also need to update the configuration for some projects.

#### Kotlin compiler errors

Sometimes Android Studio encounters a version error when resolving the Kotlin compiler, due to version mismatches.
To fix this, add `-Xskip-prerelease-check` to the ["Additional command line arguments"](https://screenshot.googleplex.com/5h3FUEx5vjuazD9)
in the "Kotlin compiler" section of Settings.

#### KVM enabled for CRD

If you are using Chrome remote desktop and see an error like:

```
Setting up SDK from scratch
Found ADB at /path/to/adb
Updating framework.aidl file at: /path/to/framework.aidl
Updating private apis sdk
restarting adbd as root
adb: error: connect failed: closed

```

you might need to enable kvm with the following command:

```
sudo setfacl -m u:${USER}:rw /dev/kvm
```

