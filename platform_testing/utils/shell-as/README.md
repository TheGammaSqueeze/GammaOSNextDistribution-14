# shell-as

shell-as is a utility that can be used to execute a binary in a less privileged
security context. This can be useful for verifying the capabilities of a process
on a running device or testing PoCs with different privilege levels.

## Usage

The security context can either be supplied explicitly, inferred from a process
running on the device, or set to a predefined profile.

For example, the following are equivalent and execute `/system/bin/id` in the
context of the init process.

```shell
shell-as \
    --uid 0 \
    --gid 0 \
    --selinux u:r:init:s0 \
    --seccomp system \
    /system/bin/id
```

```shell
shell-as --pid 1 /system/bin/id
```

The "untrusted-app" profile can be used to execute a binary with all the
possible privileges attainable by an untrusted app:

```shell
shell-as --profile untrusted-app /system/bin/id
```
