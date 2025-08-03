# Chrome OS Update Process

[TOC]

System updates in more modern operating systems like Chrome OS and Android are
called A/B updates, over-the-air ([OTA]) updates, seamless updates, or simply
auto updates. In contrast to more primitive system updates (like Windows or
macOS) where the system is booted into a special mode to override the system
partitions with newer updates and may take several minutes or hours, A/B updates
have several advantages including but not limited to:

*   Updates maintain a workable system that remains on the disk during and after
    an update. Hence, reducing the likelihood of corrupting a device into a
    non-usable state. And reducing the need for flashing devices manually or at
    repair and warranty centers, etc.
*   Updates can happen while the system is running (normally with minimum
    overhead) without interrupting the user. The only downside for users is a
    required reboot (or, in Chrome OS, a sign out which automatically causes a
    reboot if an update was performed where the reboot duration is about 10
    seconds and is no different than a normal reboot).
*   The user does not need (although they can) to request for an update. The
    update checks happen periodically in the background.
*   If the update fails to apply, the user is not affected. The user will
    continue on the old version of the system and the system will attempt to
    apply the update again at a later time.
*   If the update applies correctly but fails to boot, the system will rollback
    to the old partition and the user can still use the system as usual.
*   The user does not need to reserve enough space for the update. The system
    has already reserved enough space in terms of two copies (A and B) of a
    partition. The system doesn’t even need any cache space on the disk,
    everything happens seamlessly from network to memory to the inactive
    partitions.

## Life of an A/B Update

In A/B update capable systems, each partition, such as the kernel or root (or
other artifacts like [DLC]), has two copies. We call these two copies active (A)
and inactive (B). The system is booted into the active partition (depending on
which copy has the higher priority at boot time) and when a new update is
available, it is written into the inactive partition. After a successful reboot,
the previously inactive partition becomes active and the old active partition
becomes inactive.

### Generation

But everything starts with generating OTA packages on (Google) servers for
each new system image. This is done by calling
[ota_from_target_files](https://cs.android.com/android/platform/superproject/+/master:build/make/tools/releasetools/ota_from_target_files.py)
with source and destination builds. This script requires target_file.zip to work,
image files are not sufficient.

### Distribution/Configuration
Once the OTA packages are generated, they are signed with specific keys
and stored in a location known to an update server (GOTA).
GOTA will then make this OTA package accessible via a public URL. Optionally,
operators an choose to make this OTA update available only to a specific
subset of devices.

### Installation
When the device's updater client initiates an update (either periodically or user
initiated), it first consults different device policies to see if the update
check is allowed. For example, device policies can prevent an update check
during certain times of a day or they require the update check time to be
scattered throughout the day randomly, etc.

Once policies allow for the update check, the updater client sends a request to
the update server (all this communication happens over HTTPS) and identifies its
parameters like its Application ID, hardware ID, version, board, etc.

Some policities on the server might prevent the device from getting specific
OTA updates, these server side policities are often set by operators. For
example, the operator might want to deliver a beta version of software to only
a subset of devices.

But if the update server decides to serve an update payload, it will respond
with all the parameters needed to perform an update like the URLs to download the
payloads, the metadata signatures, the payload size and hash, etc. The updater
client continues communicating with the update server after different state
changes, like reporting that it started to download the payload or it finished
the update, or reports that the update failed with specific error codes, etc.

The device will then proceed to actually installing the OTA update. This consists
of roughly 3 steps.
#### Download & Install
Each payload consists of two main sections: metadata and extra data. The
metadata is basically a list of operations that should be performed for an
update. The extra data contains the data blobs needed by some or all of these
operations. The updater client first downloads the metadata and
cryptographically verifies it using the provided signatures from the update
server’s response. Once the metadata is verified as valid, the rest of the
payload can easily be verified cryptographically (mostly through SHA256 hashes).

Next, the updater client marks the inactive partition as unbootable (because it
needs to write the new updates into it). At this point the system cannot
rollback to the inactive partition anymore.

Then, the updater client performs the operations defined in the metadata (in the
order they appear in the metadata) and the rest of the payload is gradually
downloaded when these operations require their data. Once an operation is
finished its data is discarded. This eliminates the need for caching the entire
payload before applying it. During this process the updater client periodically
checkpoints the last operation performed so in the event of failure or system
shutdown, etc. it can continue from the point it missed without redoing all
operations from the beginning.

During the download, the updater client hashes the downloaded bytes and when the
download finishes, it checks the payload signature (located at the end of the
payload). If the signature cannot be verified, the update is rejected.

#### Hash Verification & Verity Computation

After the inactive partition is updated, the updater client will compute
Forward-Error-Correction(also known as FEC, Verity) code for each partition,
and wriee the computed verity data to inactive partitions. In some updates,
verity data is included in the extra data, so this step will be skipped.

Then, the entire partition is re-read, hashed and compared to a hash value
passed in the metadata to make sure the update was successfully written into
the partition. Hash computed in this step includes the verity code written in
last step.

#### Postintall

In the next step, the [Postinstall] scripts (if any) is called. From OTA's perspective,
these postinstall scripts are just blackboxes. Usually postinstall scripts will optimize
existings apps on the phone and run file system garbage collection, so that device can boot
fast after OTA. But these are managed by other teams.

#### Finishing Touches

Then the updater client goes into a state that identifies the update has
completed and the user needs to reboot the system. At this point, until the user
reboots (or signs out), the updater client will not do any more system updates
even if newer updates are available. However, it does continue to perform
periodic update checks so we can have statistics on the number of active devices
in the field.

After the update proved successful, the inactive partition is marked to have a
higher priority (on a boot, a partition with higher priority is booted
first). Once the user reboots the system, it will boot into the updated
partition and it is marked as active. At this point, after the reboot, the
[update_verifier](https://cs.android.com/android/platform/superproject/+/master:bootable/recovery/update_verifier/)
program runs, read all dm-verity devices to make sure the partitions aren't corrupted,
then mark the update as successful.

A/B updates are considered completed at this point. Virtual A/B updates will have an
additional step after this, called "merging". Merging usually takes few minutes, after that
Virtual A/B updates are considered complete.

## Update Engine Daemon

The `update_engine` is a single-threaded daemon process that runs all the
times. This process is the heart of the auto updates. It runs with lower
priorities in the background and is one of the last processes to start after a
system boot. Different clients (like GMS Core or other services) can send requests
for update checks to the update engine. The details of how requests are passed
to the update engine is system dependent, but in Chrome OS it is D-Bus.  Look at
the [D-Bus interface] for a list of all available methods. On Android it is binder.

There are many resiliency features embedded in the update engine that makes auto
updates robust including but not limited to:

*   If the update engine crashes, it will restart automatically.
*   During an active update it periodically checkpoints the state of the update
    and if it fails to continue the update or crashes in the middle, it will
    continue from the last checkpoint.
*   It retries failed network communication.
*   If it fails to apply a delta payload (due to bit changes on the active
    partition) for a few times, it switches to full payload.

The updater clients writes its active preferences in
`/data/misc/update_engine/prefs`. These preferences help with tracking changes
during the lifetime of the updater client and allows properly continuing the
update process after failed attempts or crashes.



### Interactive vs Non-Interactive vs. Forced Updates

Non-interactive updates are updates that are scheduled periodically by the
update engine and happen in the background. Interactive updates, on the other
hand, happen when a user specifically requests an update check (e.g. by clicking
on “Check For Update” button in Chrome OS’s About page). Depending on the update
server's policies, interactive updates have higher priority than non-interactive
updates (by carrying marker hints). They may decide to not provide an update if
they have busy server load, etc. There are other internal differences between
these two types of updates too. For example, interactive updates try to install
the update faster.

Forced updates are similar to interactive updates (initiated by some kind of
user action), but they can also be configured to act as non-interactive. Since
non-interactive updates happen periodically, a forced-non-interactive update
causes a non-interactive update at the moment of the request, not at a later
time. We can call a forced non-interactive update with:

```bash
update_engine_client --interactive=false --check_for_update
```

### Network

The updater client has the capability to download the payloads using Ethernet,
WiFi, or Cellular networks depending on which one the device is connected
to. Downloading over Cellular networks will prompt permission from the user as
it can consume a considerable amount of data.

### Logs

In Chrome OS the `update_engine` logs are located in `/var/log/update_engine`
directory. Whenever `update_engine` starts, it starts a new log file with the
current data-time format in the log file’s name
(`update_engine.log-DATE-TIME`). Many log files can be seen in
`/var/log/update_engine` after a few restarts of the update engine or after the
system reboots. The latest active log is symlinked to
`/var/log/update_engine.log`.

In Android the `update_engine` logs are located in `/data/misc/update_engine_log`.

## Update Payload Generation

The update payload generation is the process of converting a set of
partitions/files into a format that is both understandable by the updater client
(especially if it's a much older version) and is securely verifiable. This
process involves breaking the input partitions into smaller components and
compressing them in order to help with network bandwidth when downloading the
payloads.

`delta_generator` is a tool with a wide range of options for generating
different types of update payloads. Its code is located in
`update_engine/payload_generator`. This directory contains all the source code
related to mechanics of generating an update payload. None of the files in this
directory should be included or used in any other library/executable other than
the `delta_generator` which means this directory does not get compiled into the
rest of the update engine tools.

However, it is not recommended to use `delta_generator` directly, as it has way
too many flags. Wrappers like [ota_from_target_files](https://cs.android.com/android/platform/superproject/+/master:build/make/tools/releasetools/ota_from_target_files.py)
or [OTA Generator](https://github.com/google/ota-generator) should be used.

### Update Payload File Specification

Each update payload file has a specific structure defined in the table below:

| Field                   | Size (bytes) | Type                                 | Description                                                                                                                   |
| ----------------------- | ------------ | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| Magic Number            | 4            | char[4]                              | Magic string "CrAU" identifying this is an update payload.                                                                    |
| Major Version           | 8            | uint64                               | Payload major version number.                                                                                                 |
| Manifest Size           | 8            | uint64                               | Manifest size in bytes.                                                                                                       |
| Manifest Signature Size | 4            | uint32                               | Manifest signature blob size in bytes (only in major version 2).                                                              |
| Manifest                | Varies       | [DeltaArchiveManifest]               | The list of operations to be performed.                                                                                       |
| Manifest Signature      | Varies       | [Signatures]                         | The signature of the first five fields. There could be multiple signatures if the key has changed.                            |
| Payload Data            | Varies       | List of raw or compressed data blobs | The list of binary blobs used by operations in the metadata.                                                                  |
| Payload Signature Size  | Varies       | uint64                               | The size of the payload signature.                                                                                            |
| Payload Signature       | Varies       | [Signatures]                         | The signature of the entire payload except the metadata signature. There could be multiple signatures if the key has changed. |

### Delta vs. Full Update Payloads

There are two types of payload: Full and Delta. A full payload is generated
solely from the target image (the image we want to update to) and has all the
data necessary to update the inactive partition. Hence, full payloads can be
quite large in size. A delta payload, on the other hand, is a differential
update generated by comparing the source image (the active partitions) and the
target image and producing the diffs between these two images. It is basically a
differential update similar to applications like `diff` or `bsdiff`. Hence,
updating the system using the delta payloads requires the system to read parts
of the active partition in order to update the inactive partition (or
reconstruct the target partition). The delta payloads are significantly smaller
than the full payloads. The structure of the payload is equal for both types.

Payload generation is quite resource intensive and its tools are implemented
with high parallelism.

#### Generating Full Payloads

A full payload is generated by breaking the partition into 2MiB (configurable)
chunks and either compressing them using bzip2 or XZ algorithms or keeping it as
raw data depending on which produces smaller data. Full payloads are much larger
in comparison to delta payloads hence require longer download time if the
network bandwidth is limited. On the other hand, full payloads are a bit faster
to apply because the system doesn’t need to read data from the source partition.

#### Generating Delta Payloads

Delta payloads are generated by looking at both the source and target images
data on a file and metadata basis (more precisely, the file system level on each
appropriate partition). The reason we can generate delta payloads is that Chrome
OS partitions are read only. So with high certainty we can assume the active
partitions on the client’s device is bit-by-bit equal to the original partitions
generated in the image generation/signing phase. The process for generating a
delta payload is roughly as follows:

1.  Find all the zero-filled blocks on the target partition and produce `ZERO`
    operation for them. `ZERO` operation basically discards the associated
    blocks (depending on the implementation).
2.  Find all the blocks that have not changed between the source and target
    partitions by directly comparing one-to-one source and target blocks and
    produce `SOURCE_COPY` operation.
3.  List all the files (and their associated blocks) in the source and target
    partitions and remove blocks (and files) which we have already generated
    operations for in the last two steps. Assign the remaining metadata (inodes,
    etc) of each partition as a file.
4.  If a file is new, generate a `REPLACE`, `REPLACE_XZ`, or `REPLACE_BZ`
    operation for its data blocks depending on which one generates a smaller
    data blob.
5.  For each other file, compare the source and target blocks and produce a
    `SOURCE_BSDIFF` or `PUFFDIFF` operation depending on which one generates a
    smaller data blob. These two operations produce binary diffs between a
    source and target data blob. (Look at [bsdiff] and [puffin] for details of
    such binary differential programs!)
6.  Sort the operations based on their target partitions’ block offset.
7.  Optionally merge same or similar operations next to each other into larger
    operations for better efficiency and potentially smaller payloads.

Full payloads can only contain `REPLACE`, `REPLACE_BZ`, and `REPLACE_XZ`
operations. Delta payloads can contain any operations.

### Major and Minor versions

The major and minor versions specify the update payload file format and the
capability of the updater client to accept certain types of update payloads
respectively. These numbers are [hard coded] in the updater client.

Major version is basically the update payload file version specified in the
[update payload file specification] above (second field). Each updater client
supports a range of major versions. Currently, there are only two major
versions: 1, and 2. And both Chrome OS and Android are on major version 2 (major
version 1 is being deprecated). Whenever there are new additions that cannot be
fitted in the [Manifest protobuf], we need to uprev the major version. Upreving
major version should be done with utmost care because older clients do not know
how to handle the newer versions. Any major version uprev in Chrome OS should be
associated with a GoldenEye stepping stone.

Minor version defines the capability of the updater client to accept certain
operations or perform certain actions. Each updater client supports a range of
minor versions. For example, the updater client with minor version 4 (or less)
does not know how to handle a `PUFFDIFF` operation. So when generating a delta
payload for an image which has an updater client with minor version 4 (or less)
we cannot produce PUFFDIFF operation for it. The payload generation process
looks at the source image’s minor version to decide the type of operations it
supports and only a payload that confirms to those restrictions. Similarly, if
there is a bug in a client with a specific minor version, an uprev in the minor
version helps with avoiding to generate payloads that cause that bug to
manifest. However, upreving minor versions is quite expensive too in terms of
maintainability and it can be error prone. So one should practice caution when
making such a change.

Minor versions are irrelevant in full payloads. Full payloads should always be
able to be applied for very old clients. The reason is that the updater clients
may not send their current version, so if we had different types of full
payloads, we would not have known which version to serve to the client.

### Signed vs Unsigned Payloads

Update payloads can be signed (with private/public key pairs) for use in
production or be kept unsigned for use in testing. Tools like `delta_generator`
help with generating metadata and payload hashes or signing the payloads given
private keys.

## update_payload Scripts

[update_payload] contains a set of python scripts used mostly to validate
payload generation and application. We normally test the update payloads using
an actual device (live tests). [`brillo_update_payload`] script can be used to
generate and test applying of a payload on a host device machine. These tests
can be viewed as dynamic tests without the need for an actual device. Other
`update_payload` scripts (like [`check_update_payload`]) can be used to
statically check that a payload is in the correct state and its application
works correctly. These scripts actually apply the payload statically without
running the code in payload_consumer.

## Postinstall

[Postinstall] is a process called after the updater client writes the new image
artifacts to the inactive partitions. One of postinstall's main responsibilities
is to recreate the dm-verity tree hash at the end of the root partition. Among
other things, it installs new firmware updates or any board specific
processes. Postinstall runs in separate chroot inside the newly installed
partition. So it is quite separated from the rest of the active running
system. Anything that needs to be done after an update and before the device is
rebooted, should be implemented inside the postinstall.

## Building Update Engine

You can build `update_engine` the same as other platform applications:

### Setup

Run these commands at top of Android repository before building anything.
You only need to do this once per shell.

* `source build/envsetup.sh`
* `lunch aosp_cf_x86_64_only_phone-userdebug` (Or replace aosp_cf_x86_64_only_phone-userdebug with your own target)


### Building

`m update_engine update_engine_client delta_generator`

## Running Unit Tests

[Running unit tests similar to other platforms]:

* `atest update_engine_unittests` You will need a device connected to
  your laptop and accessible via ADB to do this. Cuttlefish works as well.
* `atest update_engine_host_unittests` Run a subset of tests on host, no device
required.

## Initiating a Configured Update

There are different methods to initiate an update:

*   Click on the “Check For Update” button in setting’s About page. There is no
    way to configure this way of update check.
*   Use the [`scripts/update_device.py`] program and pass a path to your OTA zip file.



## Note to Developers and Maintainers

When changing the update engine source code be extra careful about these things:

### Do NOT Break Backward Compatibility

At each release cycle we should be able to generate full and delta payloads that
can correctly be applied to older devices that run older versions of the update
engine client. So for example, removing or not passing arguments in the metadata
proto file might break older clients. Or passing operations that are not
understood in older clients will break them. Whenever changing anything in the
payload generation process, ask yourself this question: Would it work on older
clients? If not, do I need to control it with minor versions or any other means.

Especially regarding enterprise rollback, a newer updater client should be able
to accept an older update payload. Normally this happens using a full payload,
but care should be taken in order to not break this compatibility.

### Think About The Future

When creating a change in the update engine, think about 5 years from now:

*   How can the change be implemented that five years from now older clients
    don’t break?
*   How is it going to be maintained five years from now?
*   How can it make it easier for future changes without breaking older clients
    or incurring heavy maintenance costs?

### Prefer Not To Implement Your Feature In The Updater Client
If a feature can be implemented from server side, Do NOT implement it in the
client updater. Because the client updater can be fragile at points and small
mistakes can have catastrophic consequences. For example, if a bug is introduced
in the updater client that causes it to crash right before checking for update
and we can't quite catch this bug early in the release process, then the
production devices which have already moved to the new buggy system, may no
longer receive automatic updates anymore. So, always think if the feature is
being implemented can be done form the server side (with potentially minimal
changes to the client updater)? Or can the feature be moved to another service
with minimal interface to the updater client. Answering these questions will pay
off greatly in the future.

### Be Respectful Of Other Code Bases

~~The current update engine code base is used in many projects like Android.~~~

The Android and ChromeOS codebase have officially diverged.

We sync the code base among these two projects frequently. Try to not break Android
or other systems that share the update engine code. Whenever landing a change,
always think about whether Android needs that change:

*   How will it affect Android?
*   Can the change be moved to an interface and stubs implementations be
    implemented so as not to affect Android?
*   Can Chrome OS or Android specific code be guarded by macros?

As a basic measure, if adding/removing/renaming code, make sure to change both
`build.gn` and `Android.bp`. Do not bring Chrome OS specific code (for example
other libraries that live in `system_api` or `dlcservice`) into the common code
of update_engine. Try to separate these concerns using best software engineering
practices.

### Merging from Android (or other code bases)

Chrome OS tracks the Android code as an [upstream branch]. To merge the Android
code to Chrome OS (or vice versa) just do a `git merge` of that branch into
Chrome OS, test it using whatever means and upload a merge commit.

```bash
repo start merge-aosp
git merge --no-ff --strategy=recursive -X patience cros/upstream
repo upload --cbr --no-verify .
```

[Postinstall]: #postinstall
[update payload file specification]: #update-payload-file-specification
[OTA]: https://source.android.com/devices/tech/ota
[DLC]: https://chromium.googlesource.com/chromiumos/platform2/+/master/dlcservice
[`chromeos-setgoodkernel`]: https://chromium.googlesource.com/chromiumos/platform2/+/master/installer/chromeos-setgoodkernel
[D-Bus interface]: /dbus_bindings/org.chromium.UpdateEngineInterface.dbus-xml
[this repository]: /
[UpdateManager]: /update_manager/update_manager.cc
[update_manager]: /update_manager/
[P2P update related code]: https://chromium.googlesource.com/chromiumos/platform2/+/master/p2p/
[`cros_generate_update_payloads`]: https://chromium.googlesource.com/chromiumos/chromite/+/master/scripts/cros_generate_update_payload.py
[`chromite/lib/paygen`]: https://chromium.googlesource.com/chromiumos/chromite/+/master/lib/paygen/
[DeltaArchiveManifest]: /update_metadata.proto#302
[Signatures]: /update_metadata.proto#122
[hard coded]: /update_engine.conf
[Manifest protobuf]: /update_metadata.proto
[update_payload]: /scripts/
[Postinstall]: https://chromium.googlesource.com/chromiumos/platform2/+/master/installer/chromeos-postinst
[`update_engine` protobufs]: https://chromium.googlesource.com/chromiumos/platform2/+/master/system_api/dbus/update_engine/
[Running unit tests similar to other platforms]: https://chromium.googlesource.com/chromiumos/docs/+/master/testing/running_unit_tests.md
[Nebraska]: https://chromium.googlesource.com/chromiumos/platform/dev-util/+/master/nebraska/
[upstream branch]: https://chromium.googlesource.com/aosp/platform/system/update_engine/+/upstream
[`cros flash`]: https://chromium.googlesource.com/chromiumos/docs/+/master/cros_flash.md
[bsdiff]: https://android.googlesource.com/platform/external/bsdiff/+/master
[puffin]: https://android.googlesource.com/platform/external/puffin/+/master
[`update_engine_client`]: /update_engine_client.cc
[`brillo_update_payload`]: /scripts/brillo_update_payload
[`check_update_payload`]: /scripts/paycheck.py
[Dev Server]: https://chromium.googlesource.com/chromiumos/chromite/+/master/docs/devserver.md
