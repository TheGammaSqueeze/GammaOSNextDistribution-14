# Doing RAM dump of a Microdroid VM and analyzing it

A debuggable Microdroid VM creates a RAM dump of itself when the kernel panics. This
document explains how the dump can be obtained and analyzed.

## Force triggering a RAM dump

RAM dump is created automatically when there's a kernel panic. However, for
debugging purpose, you can forcibly trigger it via magic SysRq key.

```shell
$ adb shell /apex/com.android.virt/bin/vm run-app ...     // run a Microdroid VM
$ m vm_shell; vm_shell                                    // connect to the VM
# echo c > /proc/sysrq-trigger                            // force trigger a crash
```

Then you will see following message showing that crash is detected and the
crashdump kernel is executed.

```
[   14.949892][  T148] sysrq: Trigger a crash
[   14.952133][  T148] Kernel panic - not syncing: sysrq triggered crash
[   14.955309][  T148] CPU: 0 PID: 148 Comm: sh Kdump: loaded Not tainted 5.15.60-android14-5-04357-gbac79d727aea-ab9013362 #1
[   14.957803][  T148] Hardware name: linux,dummy-virt (DT)
[   14.959053][  T148] Call trace:
[   14.959809][  T148]  dump_backtrace.cfi_jt+0x0/0x8
[   14.961019][  T148]  dump_stack_lvl+0x68/0x98
[   14.962137][  T148]  panic+0x160/0x3f4

----------snip----------

[   14.998693][  T148] Starting crashdump kernel...
[   14.999411][  T148] Bye!
Booting Linux on physical CPU 0x0000000000 [0x412fd050]
Linux version 5.15.44+ (build-user@build-host) (Android (8508608, based on r450784e) clang version 14.0.7 (https://android.googlesource.com/toolchain/llvm-project 4c603efb0cca074e9238af8b4106c30add4418f6), LLD 14.0.7) #1 SMP PREEMPT Thu Jul 7 02:57:03 UTC 2022
achine model: linux,dummy-virt
earlycon: uart8250 at MMIO 0x00000000000003f8 (options '')
printk: bootconsole [uart8250] enabled

----------snip----------

Run /bin/crashdump as init process
Crashdump started
Size is 98836480 bytes
.....................................................................random: crng init done
...............................done
reboot: Restarting system with command 'kernel panic'
```

## Obtaining the RAM dump

RAM dumps are sent to tombstone. To see which tombstone file is for
the RAM dump, look into the log.

```shell
$ adb logcat | grep SYSTEM_TOMBSTONE
09-22 17:24:28.798  1335  1504 I BootReceiver: Copying /data/tombstones/tombstone_47 to DropBox (SYSTEM_TOMBSTONE)
```

In the above example, the RAM dump is saved as `/data/tombstones/tombstone_47`.
You can download this using `adb pull`.

```shell
$ adb root && adb pull /data/tombstones/tombstone_47 ramdump && adb unroot
```

## Analyzing the RAM dump

### Building the crash(8) tool

You first need to build the crash(8) tool for the target architecture, which in most case is aarch64.

Download the source code and build it as follows. This needs to be done only once.

```shell
$ wget https://github.com/crash-utility/crash/archive/refs/tags/8.0.2.tar.gz -O - | tar xzv
$ make -j -C crash-8.0.2 target=ARM64
```

### Obtaining vmlinux

You also need the image of the kernel binary with debuggin enabled. The kernel
binary should be the same as the actual kernel that you used in the Microdroid
VM that crashed. To identify which kernel it was, look for the kernel version
number in the logcat log.

```
[   14.955309][  T148] CPU: 0 PID: 148 Comm: sh Kdump: loaded Not tainted 5.15.60-android14-5-04357-gbac79d727aea-ab9013362 #1
```

Here, the version number is
`5.15.60-android14-5-04357-gbac79d727aea-ab9013362`. What is important here is
the last component: `ab9013362`. The numbers after `ab` is the Android Build ID
of the kernel.

With the build ID, you can find the image from `ci.android.com` and download
it. The direct link to the image is `https://ci.android.com/builds/submitted/9013362/kernel_microdroid_aarch64/latest/vmlinux`.

DON'T forget to replace `9013362` with the actual build ID of the kernel you used.

### Running crash(8) with the RAM dump and the kernel image

```shell
$ crash-8.0.2/crash ramdump vmlinux
```

You can now analyze the RAM dump using the various commands that crash(8) provides. For example, `bt <pid>` command shows the stack trace of a process.

```
crash> bt
PID: 148    TASK: ffffff8001a2d880  CPU: 0   COMMAND: "sh"
 #0 [ffffffc00926b9f0] machine_kexec at ffffffd48a852004
 #1 [ffffffc00926bb90] __crash_kexec at ffffffd48a948008
 #2 [ffffffc00926bc40] panic at ffffffd48a86e2a8
 #3 [ffffffc00926bc90] sysrq_handle_crash.35db4764f472dc1c4a43f39b71f858ea at ffffffd48ad985c8
 #4 [ffffffc00926bca0] __handle_sysrq at ffffffd48ad980e4
 #5 [ffffffc00926bcf0] write_sysrq_trigger.35db4764f472dc1c4a43f39b71f858ea at ffffffd48ad994f0
 #6 [ffffffc00926bd10] proc_reg_write.bc7c2a3e70d8726163739fbd131db16e at ffffffd48ab4d280
 #7 [ffffffc00926bda0] vfs_write at ffffffd48aaaa1a4
 #8 [ffffffc00926bdf0] ksys_write at ffffffd48aaaa5b0
 #9 [ffffffc00926be30] __arm64_sys_write at ffffffd48aaaa644
#10 [ffffffc00926be40] invoke_syscall at ffffffd48a84b55c
#11 [ffffffc00926be60] do_el0_svc at ffffffd48a84b424
#12 [ffffffc00926be80] el0_svc at ffffffd48b0a29e4
#13 [ffffffc00926bea0] el0t_64_sync_handler at ffffffd48b0a2950
#14 [ffffffc00926bfe0] el0t_64_sync at ffffffd48a811644
     PC: 00000079d880b798   LR: 00000064b4afec8c   SP: 0000007ff6ddb2e0
    X29: 0000007ff6ddb360  X28: 0000007ff6ddb320  X27: 00000064b4b238e8
    X26: 00000079d9c49000  X25: 0000000000000000  X24: b40000784870fda9
    X23: 00000064b4b236f8  X22: 0000007ff6ddb340  X21: 0000007ff6ddb338
    X20: b40000784870f618  X19: 0000000000000002  X18: 00000079daea4000
    X17: 00000079d880b790  X16: 00000079d882dee0  X15: 0000000000000080
    X14: 0000000000000000  X13: 0000008f00000160  X12: 000000004870f6ac
    X11: 0000000000000008  X10: 000000000009c000   X9: b40000784870f618
     X8: 0000000000000040   X7: 000000e70000000b   X6: 0000020500000210
     X5: 00000079d883a984   X4: ffffffffffffffff   X3: ffffffffffffffff
     X2: 0000000000000002   X1: b40000784870f618   X0: 0000000000000001
    ORIG_X0: 0000000000000001  SYSCALLNO: 40  PSTATE: 00001000
```

Above shows that the shell process that executed `echo c > /proc/sysrq-trigger`
actually triggered a crash in the kernel.

For more commands of crash(8), refer to the man page, or embedded `help` command.
