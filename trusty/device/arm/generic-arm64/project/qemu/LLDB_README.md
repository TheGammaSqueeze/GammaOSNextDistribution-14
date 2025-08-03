# Debugging with LLDB

Debugger support for Trusty is currently provided on a best effort basis; see caveats section.

## Prerequisites

* A modern build of LLDB (tested from Google trunk as of August 9th, 2022).

* A 64-bit build of Trusty

## How to Debug Trusty in the Emulator

After building a 64-bit debug build of Trusty, launch it in QEMU with the
`--debug` flag:

```shell
./build-root/build-qemu-generic-arm64-test-debug/run --debug
```

Then launch LLDB with the `lldbinit` file in the build directory:

```shell
lldb --source ./build-root/build-qemu-generic-arm64-test-debug/lldbinit
```

You should now be connected to QEMU and stopped just before the bootloader has
started. Add breakpoints now. You can add breakpoints to the kernel and to TAs
that were included in the build process.

### Caveats

The LLDB scripting that exists is *not* process-aware. It works purely off of
the emulated CPU's program counter. The odds of program counter collision are
low thanks to ASLR. But LLDB will lose track of processes when syscalls are
made. You'll need to add breakpoints after each syscall if you want to step
through running TAs.

Example:

```c
int main(void) {
    struct ipc_port_context ctx = {
            .ops = {.on_connect = proxy_connect},
    };

    crypt_init();
    block_cache_init(); // BREAKPOINT

    int rc = ipc_port_create(
            &ctx, STORAGE_DISK_PROXY_PORT, 1, STORAGE_MAX_BUFFER_SIZE,
            IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT);

    if (rc < 0) { // BREAKPOINT
        SS_ERR("fatal: unable to initialize proxy endpoint (%d)\n", rc);
        return rc;
    }

    ipc_loop();

    ipc_port_destroy(&ctx);
    return 0;
}
```

In the above snippet, if you have a breakpoint on the call to
`block_cache_init()` and step forward over `ipc_port_create()` the debugger will
lose track of the process. You'll need to add another breakpoint on the line
below, `if (rc < 0) {` to catch the process again.

Our debugger support depends entirely on ASLR to work. This means builds without
ASLR can not be debugged with LLDB. You may get kernel breakpoints to work. But
TAs are going to overlap in virtual address space, so our pure program counter
breakpointing is going to get messy. We only have ASLR in 64-bit builds, so
32-bit builds support LLDB even less so than 64-bit builds.

TAs loaded dynamically are not currently supported.

## How to Debug Trusty on a Phone

TODO: This should be possible with JTAG capabilities.
