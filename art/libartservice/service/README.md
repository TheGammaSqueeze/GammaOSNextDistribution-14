# ART Service

Warning: The contents in this doc can become stale while the code evolves.

ART Service manages dexopt artifacts of apps. With ART Service, you can dexopt
apps, query their dexopt status (the compiler filter, the compilation reason,
whether the dexopt artifacts are up-to-date, etc.), and delete dexopt artifacts.

Note: ART Service is introduced in Android U. Prior to ART Service, dexopt
artifacts were managed by Package Manager with a legacy implementation. The
legacy implementation will be removed in Android V. This doc only describes
ART Service, not the legacy implementation.

## Concepts

### Primary dex vs. secondary dex

ART Service dexopts both primary dex files and secondary dex files of an app.

A primary dex file refers to the base APK or a split APK of an app. It's
installed by Package Manager or shipped as a part of the system image, and it's
loaded by Framework on app startup.

A secondary dex file refers to an APK or JAR file that an app adds to its own
data directory and loads dynamically.

Note: Strictly speaking, an APK/JAR file is not a DEX file. It is a ZIP file
that contain one or more DEX files. However, it is called a *dex file*
conventionally.

### Compiler filters

See
[Compilation options](https://source.android.com/docs/core/runtime/configure#compilation_options).

### Priority classes

A priority class indicates the priority of an operation. The value affects the
resource usage and the process priority. A higher value may result in faster
execution but may consume more resources and compete for resources with other
processes.

Options are (from the highest to the lowest):

-   `PRIORITY_BOOT`: Indicates that the operation blocks boot.
-   `PRIORITY_INTERACTIVE_FAST`: Indicates that a human is waiting on the result
    and the operation is more latency sensitive than usual. It's typically used
    when the user is entirely blocked, such as for restoring from cloud backup.
-   `PRIORITY_INTERACTIVE`: Indicates that a human is waiting on the result.
    (E.g., for app install)
-   `PRIORITY_BACKGROUND`: Indicates that the operation runs in background.

### Compilation reasons

A compilation reason is a string that determines the default
[compiler filter](#compiler-filters) and the default
[priority class](#priority-classes) of an operation.

It's also passed to `dex2oat` and stored in the header of the OAT file, for
debugging purposes. To retrieve the compilation reason from an OAT file, run

```
pm art dump <package-name>
```

or

```
oatdump --header-only --oat-file=<odex-filename> | grep 'compilation-reason ='
```

It can be either a predefined value in
`art/libartservice/service/java/com/android/server/art/ReasonMapping.java`
or a custom string. If the value is a custom string, the priority class and the
compiler filter must be explicitly set.

Each predefined value corresponds to one of the
[dexopt scenarios](#dexopt-scenarios).

#### The `-dm` suffix

Sometimes, you may see the `-dm` suffix in an OAT file, such as `install-dm`.
However, the `-dm` suffix is **not** a part of the compilation reason. It's
appended to the compilation reason to indicate that a DM (`.dm`) file is passed
to `dex2oat` during dexopt for **app install**.

Note: ART Service also passes the DM file to `dex2oat` in other scenarios, such
as background dexopt, but for compatibility reasons, the `-dm` suffix is not
appended in those scenarios.

Note: The `-dm` suffix does **not** imply anything in the DM file being used by
`dex2oat`. The augmented compilation reason can still be `install-dm` even if
the DM file is empty or if `dex2oat` leaves all contents of the DM file unused.
That would only happen if there's a bug, like the wrong DM file being passed.

## Dexopt scenarios

At a high level, ART Service dexopts apps in the following scenarios:

-   the device is on the very first boot (Compilation reason: `first-boot`)
-   the device is on the first boot after an OTA update (Compilation reason:
    `boot-after-ota`)
-   the device is on the first boot after a mainline update (Compilation reason:
    `boot-after-mainline-update`)
-   an app is being installed (Compilation reason: `install` / `install-fast`
    / etc.)
-   the device is idle and charging (Compilation reason: `bg-dexopt` /
    `inactive`)
-   requested through commandline (Compilation reason: `cmdline`)

Warning: The sections below describe the default behavior in each scenario. Note
that the list of apps to dexopt and the compiler filter, as well as other
options, can be customized by partners through system properties, APIs, etc.

### On the very first boot / the first boot after an OTA update

On the very first boot / the first boot after an OTA update, ART Service only
dexopts primary dex files of all apps with the "verify" compiler filter.

If `pm.dexopt.downgrade_after_inactive_days` is set, during the first boot after
an OTA update, ART Service only dexopts apps used within the last given number of
days.

Note: It doesn't dexopt secondary dex files or use the "speed-profile" filter
because doing so may block the boot for too long.

In practice, ART Service does nothing for most of the apps. Because the default
compiler filter is "verify", which tolerates dependency mismatches, apps with
usable VDEX files generally don't need to be re-dexopted. This includes:

-   apps on the **system partitions** that have artifacts generated by
    dexpreopt, even if the dependencies (class loader contexts) are not properly
    configured.
-   apps on the **data partition** that have been dexopted in other scenarios
    (install, background dexopt, etc.), even though their dependencies
    (bootclasspath, boot images, and class loader contexts) have probably
    changed.

In other words, in this scenario, ART Service mostly only dexopts:

- apps in APEXes, because they are not supported by dexpreopt
- apps on the system partitions with dexpreopt disabled
- apps forced to have "speed-profile" or "speed" compiler filters (the system UI
  and the launcher) but dexpreopted with wrong dependencies

### On the first boot after a mainline update

On the first boot after a mainline update, ART Service dexopts the primary dex
files of the system UI and the launcher. It uses the compiler filter specified
by `dalvik.vm.systemuicompilerfilter` for the system UI, and uses the
"speed-profile" compiler filter for the launcher.

Note: It only dexopts those two apps because they are important to user
experience.

Note: ART Service cannot use the "speed-profile" compiler filter for the system
UI because the system UI is dexpreopted using the "speed" compiler filter and
therefore it's never JITed and as a result there is no profile collected on the
device to use, though this may change in the future. For now, we strongly
recommend to set `dalvik.vm.systemuicompilerfilter` to "speed".

### During app installation

During app installation, ART Service dexopts the primary dex files of the app.
If the app is installed along with a DM file that contains a profile (known as a
*cloud profile*), it uses the "speed-profile" compiler filter. Otherwise, it
uses the "verify" compiler filter.

Note: If the APK is uncompressed and aligned, and it is installed along with a
DM file that only contains a VDEX file (but not a profile), no dexopt will be
performed because the compiler filter will be "verify" and the VDEX file is
satisfactory.

Note: There is no secondary dex file present during installation.

### When the device is idle and charging

ART Service has a job called *background dexopt job* managed by Job Scheduler.
It is triggered when the device is idle and charging. During the job execution,
it dexopts primary dex files and secondary dex files of all apps with the
"speed-profile" compiler filter.

If `pm.dexopt.downgrade_after_inactive_days` is set, ART Service only dexopts
apps used within the last given number of days, and it downgrades other apps
(with the compilation reason `inactive`).

The job is cancellable. When the device is no longer idle or charging, Job
Scheduler cancels the job.

### When requested through commandline

ART Service can be invoked by commands (`pm compile`, `pm bg-dexopt-job`, and
`pm art dexopt-packages`). Run `pm help` to see the usages and the differences
between them.
