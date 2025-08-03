# How To APEX

[go/android-apex-howto](http://go/android-apex-howto) (internal link)

This doc reflects the current implementation status, and thus is expected to
change regularly.

## Reference

To understand the design rationale, visit this
[public doc](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/docs/README.md#alternatives-considered-when-developing-apex)
and [go/android-apex](http://go/android-apex) (internal).

## Building an APEX

A cheat sheet:

```
apex {
    name: "com.android.my.apex",

    manifest: "apex_manifest.json",

    // optional. if unspecified, a default one is auto-generated
    androidManifest: "AndroidManifest.xml",

    // libc.so and libcutils.so are included in the apex
    native_shared_libs: ["libc", "libcutils"],
    binaries: ["vold"],
    java_libs: ["core-all"],
    apps: ["myapk"],
    prebuilts: ["my_prebuilt"],

    compile_multilib: "both",

    key: "com.android.my.apex.key",
    certificate: ":com.android.my.apex.certificate",
}
```

`apex_manifest.json` should look like:

```
{
  "name": "com.android.my.apex",
  "version": 0
}
```

The file contexts files should be created at
`/system/sepolicy/apex/com.android.my.apex-file_contexts`:

```
(/.*)?           u:object_r:system_file:s0
/sub(/.*)?       u:object_r:sub_file:s0
/sub/file3       u:object_r:file3_file:s0
```

The file should describe the contents of your apex. Note that the file is
amended by the build system so that the `apexd` can access the root directory of
your apex and the `apex_manifest.pb` file. (Technically, they are labeled as
`system_file`.) So if you're
[building the apex without Soong](#building-apex-without-soong), please be sure
that `apexd` can access the root directory and the `apex_manifest.pb` file. (In
the example above, the first line does that.)

#### A script to create a skeleton of APEX

For convenience, you might want to use a
[script](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/tools/create_apex_skeleton.sh)
that creates a skeleton (`Android.bp`, keys, etc.) of an APEX for you. You only
need to adjust the `APEX_NAME` variable to be your actual APEX name.

#### File types and places where they are installed in apex

file type      | place in apex
-------------- | ----------------------------------------------------------
shared libs    | `/lib` and `/lib64` (`/lib/arm` for translated arm in x86)
executables    | `/bin`
java libraries | `/javalib`
android apps   | `/app` or `/priv-app`
prebuilts      | `/etc`

#### Version overriding

The apex version should be set to 0 on development branches. This is a
placeholder version which will be overridden by Soong during build. Soong will
set it to the correct version for the current branch.

### Transitive dependencies

Transitive dependencies of a native shared lib or an executable are
automatically included in the APEX. For example, if `libFoo` depends on
`libBar`, then the two libs are included even when only `libFoo` is listed in
`native_shared_libs` property.

However, if a transitive dependency has a stable ABI, it is not included
transitively. It can be included in an APEX only by directly being referenced.
Currently (2019/08/05), the only module type that can provide stable ABI is
`cc_library`. To do so, add `stubs.*` property as shown below:

```
cc_library {
    name: "foo",
    srcs: [...],
    stubs: {
        symbol_file: "foo.map.txt",
        versions: ["29", "30"],
    },
}
```

Use this when a lib has to be accessed across the APEX boundary, e.g. between
APEXes or between an APEX and the platform.

### apex_available

Any module that is “included” (not just referenced) in an APEX either via the
direct dependency or the transitive dependency has to correctly set the
`apex_available` property in its `Android.bp` file. The property can have one or
more of the following values:

*   `<name_of_an_apex>`: Like `com.android.adbd`. By specifying the APEX names
    explicitly, the module is guaranteed to be included in those APEXes. This is
    useful when a module has to be kept as an implementation detail of an APEX
    and therefore shouldn’t be used from outside.
*   `//apex_available:anyapex`: This means that the module can be included in
    any APEX. This is useful for general-purpose utility libraries like
    `libbase`, `libcutils`, etc.
*   `//apex_available:platform`: The module can be installed to the platform,
    outside of APEXes. This is the default value. However, `if apex_available`
    is set to either of `<name_of_an_apex` or `//apex_available:anyapex`, the
    default is removed. If a module has to be included in both APEX and the
    platform, `//apex_available:platform` and`//apex_available:anyapex` should
    be specified together.

The act of adding an APEX name to the `apex_available` property of a module has
to be done or be reviewed by the author(s) of the module. Being included in an
APEX means that the module will be portable, i.e., running on multiple versions
of the current and previous platforms, whereas it usually was expected to run on
the current (the up-to-date) platform. Therefore, the module might have to be
prepared to not have version-specific dependencies to the platform, like the
existence of a dev node, a system call, etc.

### Handling multiple ABIs

`compile_multilib`: specifies the ABI(s) that this APEX will compile native
modules for. Can be either of `both`, `first`, `32`, `64`, `prefer32`. For most
of the cases, this should be `both`.

`native_shared_libs`: installed for **_both_** primary and secondary ABIs of the
device. Of course, if the APEX is built for a target having single ABI (i.e.
32-bit only or 64-bit only), only libraries with the corresponding ABI are
installed.

`binaries`: installed only for the **_primary_** ABI of the device. In other
words,

*   If the device is 32-bit only, only the 32-bit variant of the binary is
    installed.
*   If the device supports both 32/64 ABIs, but with
    `TARGET_PREFER_32_BIT_EXECUTABLES=true`, then only the 32-bit variant of the
    binary is installed.
*   If the device is 64-bit only, then only the 64-bit variant of the binary is
    installed.
*   If the device supports both 32/64 ABIs, but without
    `TARGET_PREFER_32_BIT_EXECUTABLES=true`, then only the 64-bit variant of the
    binary is installed.

In order to fine control the ABIs of the native libraries and binaries to be
installed, use
`multilib.[first|lib32|lib64|prefer32|both].[native_shared_libs|binaries]`
properties.

*   `first`: matches with the primary ABI of the device. This is the default for
    `binaries`.
*   `lib32`: matches with the 32-bit ABI of the device, if supported
*   `lib64`: matches with the 64-bit ABI of the device, it supported
*   `prefer32`: matches with the 32-bit ABI of the device, if support. If 32-bit
    ABI is not supported, it is matched with the 64-bit ABI.
*   `both`: matches with the both ABIs. This is the default for
    `native_shared_libraries`.
*   `java libraries` and `prebuilts`: ABI-agnostic

Example: (let’s assume that the device supports 32/64 and does not prefer32)

```
apex {
    // other properties are omitted
    compile_multilib: "both",
    native_shared_libs: ["libFoo"], // installed for 32 and 64
    binaries: ["exec1"], // installed for 64, but not for 32
    multilib: {
        first: {
            native_shared_libs: ["libBar"], // installed for 64, but not for 32
            binaries: ["exec2"], // same as binaries without multilib.first
        },
        both: {
            native_shared_libs: ["libBaz"], // same as native_shared_libs without multilib
            binaries: ["exec3"], // installed for 32 and 64
        },
        prefer32: {
            native_shared_libs: ["libX"], // installed for 32, but not for 64
        },
        lib64: {
            native_shared_libs: ["libY"], // installed for 64, but not for 32
        },
    },
}
```

### APEX image signing

**Note**: the APEX skeleton creation
[script](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/tools/create_apex_skeleton.sh)
automates this step.

Each APEX must be signed with different keys. There is no concept of the
platform key. `apexd` in the future might reject if multiple APEXes are signed
with the same key. When a new key is needed, create a public-private key pair
and make an `apex_key` module. Use `key` property to sign an APEX using the key.
The public key is included in the zip container of the APEX as a file entry
`apex_pubkey`.

How to generate the key pair:

```
# create an rsa key pair
$ openssl genrsa -out com.android.my.apex.pem 4096

# extract the public key from the key pair
$ avbtool extract_public_key --key com.android.my.apex.pem \
--output com.android.my.apex.avbpubkey

# in Android.bp
apex_key {
    name: "com.android.my.apex.key",
    public_key: "com.android.my.apex.avbpubkey",
    private_key: "com.android.my.apex.pem",
}
```

Important: In the above example, the name of the public key (that is
`com.android.my.apex`) becomes the ID of the key. The ID of the key used to sign
an APEX is recorded in the APEX. At runtime, a public key with the same ID in
the device is used to verify the APEX.

### APK (APEX container) signing

**Note**: the APEX skeleton creation
[script](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/tools/create_apex_skeleton.sh)
automates this step.

An APEX should also be signed just like APKs. So, an APEX is signed twice; once
for the mini file system (`apex_payload.img` file) and once for the entire file.

Just like APK, the file-level signing is done via the `certificate` property. It
can be set in three ways.

*   not set: if unset, the APEX is signed with the certificate located at
    `PRODUCT_DEFAULT_DEV_CERTIFICATE`. If the flag is also unset, it defaults to
    `build/target/product/security/testkey`
*   `<name>`: the APEX is signed with the certificate named `<name>` in the same
    directory as `PRODUCT_DEFAULT_DEV_CERTIFICATE`
*   `<name>`: the APEX signed with the certificate which is defined by a
    Soong module named `<name>`. The certificate module can be defined as
    follows.

```
android_app_certificate {
    name: "com.android.my.apex.certificate",
    // This will use com.android.my.apex.x509.pem (the cert) and
    // com.android.my.apex.pk8 (the private key)
    certificate: "com.android.my.apex",
}
```

How to generate the certificate/private key pair:

```
# Create certificate and private in PEM form
$ openssl req -x509 -newkey rsa:4096 -nodes -days 999999 -keyout key.pem -out com.android.my.apex.x509.pem

# Enter following info via the interactive prompts
# Country Name: US
# State: California
# Locality Name: Mountain View
# Organization Name: Android
# Organization Unit Name: Android
# Common Name: <your-apk-name>
# Email address: android@android.com

# Convert the private to pkcs8 format
$ openssl pkcs8 -topk8 -inform PEM -outform DER -in key.pem -out com.android.my.apex.pk8 -nocrypt
```

### Signing APEXs with release keys

The procedures described in the [APEX image signing](#apex-image-signing) and
[APK (APEX container) signing](#apk-apex-container_signing) sections require the
private keys to be present in the tree. This is not suitable for public release.
Please refer to the
[APEX signing key replacement](https://source.android.com/devices/tech/ota/sign_builds#apex-signing-key-replacement)
documentation to prepare the APEX packages for release.

For the Google-specific procedure for release keys, the documentation is
available at
[go/android-apex-howto-internal](http://go/android-apex-howto-internal)
(internal only).

### Linker namespaces for native libraries and binaries

The linker needs to be set up with separate namespaces for each APEX, for
isolation. It is done through `ld.config.txt` files, which are autogenerated by
`linkerconfig`. Normally you only need to ensure that the APEX manifest
correctly lists the native libraries it requires (from platform or other APEXes)
and provides, which by default is taken from the build system.

Refer to the [design doc](go/linker-config-apex) for more information about
linkerconfig and apex.

## Installing an APEX

Use

```
adb install --staged <path_to_apex> && adb reboot
```

The `adb install --staged` command triggers a verification for the staged APEX
which might fail when the APEX is signed incorrectly.

Note that on Q devices when the `adb install --staged` command completes you
still will have to wait until the verification for the staged APEX is finished
before issuing `adb reboot`.

On R devices we added the `--wait` option to `adb install` to wait until the
verification is completed before returning. On S devices the `--wait` option is
implicit.

## Hot swapping an APEX (development only)

Use

```
adb sync && adb shell cmd -w apexservice remountPackages
```

Note that for this command to remount your APEX, you must ensure that all
processes that have reference to your APEX are killed. E.g. if you are
developing an APEX that contributes to system\_server, you can use the
following:

```
adb root
adb remount
adb shell stop
adb sync
adb shell cmd -w apexservice remountPackages
adb shell start
```

## Using an APEX

After the reboot, the apex will be mounted at `/apex/<apex_name>@<version>`
directory. Multiple versions of the same APEX can be mounted at the same time. A
mount point that always points to the latest version of an APEX is provided:
`/apex/<apex_name>`.

Clients can use the latter path to read or execute something from APEX.

So, typical usage of APEX is as follows.

1.  an APEX is pre-loaded under `/system/apex`when the device is shipped.
2.  Files in it are accessed via the `/apex/<apex_name>/`path.
3.  When an updated version of the APEX is installed in `/data/apex/active`, the
    path will point to the new APEX after the reboot.

## Updating service with APEX

Using APEX, you can update a service. To do so, you need …

1) Mark the service in system partition as updatable. Add the new option
‘updatable’ to the service definition.

```
/system/etc/init/myservice.rc:

service myservice /system/bin/myservice
    class core
    user system
    …
    updatable
```

2) Create a new `.rc` file for the updated service. Use ‘`override`’ option to
redefine the existing service.

```
/apex/my.apex/etc/init.rc:

service myservice /apex/my.apex/bin/myservice
    class core
    user system
    …
    override
```

Note that you can only have service definitions in the rc file in APEX. You
cannot have action triggers in APEXes.

Also note that if a service marked as updatable is started before APEXes are
activated, the start is delayed until the activation of APEXes is finished.

## Configuring system to support APEX updates

Inherit `updatable_apex.mk`.

```
<device.mk>:

$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)
```

## Building APEX without Soong

An APEX can be built without relying on the build commands generated by Soong.

1) Prepare following files:

-   APEX manifest file (in JSON)

-   AndroidManifest file (in XML, optional)

-   AVB private key

-   APK certificate (`*.x509.pem`)

-   APK private key (`*.pk8`)

-   `file_contexts` file

-   files to be packaged into the APEX

2) Create `canned_fs_config` file

It is a file that specifies access bits and uid/gid of each file in the APEX.

```
/ 1000 1000 0755
/apex_manifest.json 1000 1000 0644
/apex_manifest.pb 1000 1000 0644
/file1 1000 1000 0644
/file2 1000 1000 0644
/dir 0 2000 0755
/dir/file3 1000 1000 0644
...
```

Note that ALL files AND directories must be specified. And don’t forget to have
a line for `/`and `/apex_manifest.pb`. (`/apex_manifest.json` line is for
Q-targeting modules)

3) Invoke `apexer`

```
$ apexer \
  --manifest <apex_manifest_file> \
  --file_contexts <file_contexts_file> \
  --canned_fs_config <canned_fs_config_file> \
  --key <avb_private_key_file> \
  --payload_type image \
  --android_manifest <android_manifest_file> \
  --override_apk_package_name com.google.foo \
  <input_directory> \
  <output_apex_file>
```

`--android_manifest` and -`-override_apk_package` are optional arguments and
thus can be omitted if not needed.

Note: The `<apex_manifest_file>` shouldn’t be under `<input_directory>`.

4) Sign it

`apexer` signs the `apex_payload.img` file only. The entire apex (which is a zip
file) has to be signed with `Signapk`.

```
$ java \
  -Djava.library.path=$(dirname out/host/linux-x86/lib64/libconscrypt_openjdk_jni.so)\
  -jar out/host/linux-x86/framework/signapk.jar \
  -a 4096 \
  <apk_certificate_file> \
  <apk_private_key_file> \
  <unsigned_input_file> \
  <signed_output_file>
```

This will sign the input file with the cert/privkey pairs to produce the output
file.

## Re-packaging an existing APEX

If an APEX has been build by passing `--include_build_info` to `apexer` (this is
the default when building via Soong), it will then include a file named
`apex_build_info.pb` which will store as much information as possible about how
the apex was built (see the `ApexBuildInfo` proto
[definition](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/proto/apex_build_info.proto)
for more info) with the exception of the signing keys.

We also provide a tool named `deapexer` to extract the payload content of an
APEX in a local directory.

By using these tools, you can then adapt the procedure described in the
[building the apex without Soong](#building-apex-without-soong) section and pass
the `--build_info apex_build_info.pb` file where `apex_build_info.pb` contains
all the build parameters that you would otherwise pass via flag to `apexer`.

We do this programmatically in some unit test code to generate "unusual" APEX
files, see for example
[here](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/apexer/apexer_test.py)
and
[here](https://android.googlesource.com/platform/system/apex/+/refs/heads/master/tests/testdata/sharedlibs/build/shared_libs_repack.py).
