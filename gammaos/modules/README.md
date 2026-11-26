# GammaOS modules

A module is a newer build of one GammaOS program (the nano home, one of the nano emulators)
that a console installs without a system update. No partition is written: the files are kept
outside the system image and bind mounted over the image's files at boot. A full system
update stays what it was (gammaos-ota, partition flashing) and is still what brings a new
platform.

## On the console

`/system/bin/gammaos-modules` does all of it.

| Store | Path | Holds |
| --- | --- | --- |
| early | `/metadata/gammaos/modules` | files marked `early`: the home's program, which init starts before `/data` is mounted |
| data | `/data/gammaos_modules` | everything else; not under file based encryption, so it reads as soon as `/data` is mounted |

init runs these steps (system/core/rootdir/init.rc):

1. `gammaos-modules early` at early-init, before any service is started. Mounts what the early
   store holds. Never touches `/data`.
2. `gammaos-modules late` first thing at post-fs-data. Mounts what the data store holds, before
   anything that could run it is started.
3. `gammaos-modules confirm` when the boot has completed. Marks the modules it ran with as
   good and removes what can no longer be used.
4. `gammaos-modules settle` at shutdown, only when a newer home waits for its place (below).

No step makes the boot wait for anything. With no module installed nothing is even started:
init looks for the state file itself (`if_file`, a GammaOS addition to init) and that is all.
With modules installed the early step runs as a service beside the device coldboot, and init
only checks that it is through before it starts the home; the late step is a few
milliseconds of work at post-fs-data. Each step logs how long it took
(`dmesg | grep gammaos-modules`).

### When a module is used

A module is for the system image it was made after. It is mounted only while

- its version is higher than what the image ships (`/system/etc/gammaos/modules/<name>.prop`),
- it was built later than the image (`built=` in module.prop against `ro.build.date.utc`),
- it was built for the image's module platform (`ro.gammaos.modules.platform`),
- every one of its files is in place with the listed size, and the image has the file it
  stands in for (a module cannot add files or change an init script),
- it has not been switched off (below).

So a system update supersedes every module made before it, whatever the version numbers say:
the stale module is not mounted on the first boot of the new image and is deleted when that
boot completes. The same rule keeps such a package from being installed, and the home does
not offer it. A module published after the image, with a higher version, is offered again.

A newly installed module that keeps the console from completing its boot three times in a row
is switched off, and the image's program runs again. A module can also name a property its
program sets once it runs (`alive=` in module.prop; the home uses `sys.gammaos.nano.alive`).
Such a module is only confirmed with the property set. If init sees the program stop without
it, which is a build that crashes at start, `gammaos-modules reject` switches the module off
at once and init starts the image's program in the same boot.

The mounted file takes the owner, mode and SELinux label of the image file, so the program
runs in the same domain as before.

### SELinux

The consoles run permissive, but nothing here depends on that. The tool has a domain of its
own, `gammaosmodules` (system/sepolicy/private/gammaosmodules.te), and the store in `/data`
a type of its own; init creates `/data/gammaos_modules`, since no other domain may make a
folder in the root of `/data`. The domain is permissive: giving a file outside the image a
program's label and mounting over a program are not something the platform policy lets any
domain be allowed.

The stores' file systems are mounted nosuid, and under enforcing SELinux init cannot start a
program in its own domain from a nosuid mount. So every module mount is remounted without
nosuid (and read-only), in both of init's mount namespaces: the first one, where the home
lives, and the default one, where init starts the emulators and the home's overlay.

Any change to this policy changes the platform policy hash. A vendor image that carries a
precompiled policy has to have it regenerated against the new system image, or init compiles
the policy on every boot.

### The home and `/metadata`

The home starts long before `/data` is mounted and is never made to wait for it, so its file
has to be in `/metadata` before the boot that uses it. `/metadata` is small, and the running
home holds its own file there:

- When the new file fits beside the one in use (4 MB are always left free for vold's keys),
  the install puts it into the early store at once.
- When it only fits once the old one is gone, it waits in the data store and init runs
  `gammaos-modules settle` at the next shutdown or restart: the home is stopped, the old file
  removed, the new one copied in. If a shutdown never gets that far (the battery ran out),
  the version before keeps running and the next shutdown tries again.
- When it would not fit even then, the install is refused.

A module with an early file changes hands only at boot, all of its files together.

### Commands

```
gammaos-modules list                 name, image version, module version, status, placement, in use
gammaos-modules inspect <package>    what a package holds and whether it can be installed here
gammaos-modules install <package>    --force also takes a version that is not newer
gammaos-modules remove <name>        back to the program of the system image
gammaos-modules verify               checks the installed files against their checksums
```

`install` and `remove` answer on standard output with `OK <name> <version> live|reboot` or
`ERROR <reason>` (tab separated). `live` means the change is in effect; `reboot` means it
needs the next start of the console.

The home offers all of this under Settings > System Update > Update Components, and lists
`.gmod` files found on storage under Update via Storage Media.

## Packages

A package (`<name>-<version>.gmod`) is a zip:

```
module.prop      name, version, platform, build time, one "file=" line per file with size and SHA-256
module.sig       Ed25519 signature of module.prop
files/system/... the files, at the paths they have in the image
```

An installed module keeps its module.prop and module.sig in the store beside its files. The
image carries the same pair for what it ships, as `/system/etc/gammaos/modules/<name>.record`
and `<name>.record.sig` (`module-pack.py record` writes them next to a repository's built
files). The home reads them to tell a program GammaOS built from one put in its place: a
console that is not registered only starts the former.

The signature covers module.prop and module.prop names every file by checksum. The keys a
console accepts are in `ModuleKeys.h`; the private halves are kept out of every repository.
A console refuses an unsigned package unless `persist.gammaos.modules.allow_unsigned` is 1.

Each program's repository describes its module in its own `module.prop` (name, version, which
built file goes where). `common/tools/module-pack.py pack` turns that into a package and
`module-pack.py index` adds it to the index the consoles download:

```
https://ota.gammaos.sh/api/v1/modules/<platform>
{"modules":[{"name":"...","version":"1.0.1","platform":1,"built":0,"size":0,"sha256":"...","url":"...","changes":"..."}]}
```

The same module.prop is installed in the image as the record of what the image ships. Raise
its version with every released build: a console compares versions, nothing else.

## The module platform

`ro.gammaos.modules.platform` (device/phh/treble/gammaos-nano.mk) and nano-common's
`platform/MODULE_PLATFORM` are the same number. It stands for the platform interface the
programs are built against. Raise both when a system image changes that interface in a way
programs built before it cannot run on; consoles then ignore older modules and the server
keeps one index per platform.
