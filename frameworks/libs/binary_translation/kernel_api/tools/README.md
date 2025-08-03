# System calls translation

The pipeline uses kernel as a source of truth. Unfortunately, all other options proved to be either
incomplete or imprecise.

System call names, numbers and entry points are extracted from kernel sources.

*TODO: consider extracting names, numbers and entry points from vmlinux!*

System call parameters are extracted from vmlinux DWARF.

Automatic translation happens between system calls with the same entry point if entry point
parameters are compatible.

### Checkout kernel

Assume the kernel checkout is at `$KERNEL_SRC`.

### Extract system calls names, numbers and entry points

This is specific for architectures. The script does extraction for `arm`, `arm64`, `x86`,
`x86_64` and `riscv64`.

In addition, the script checks entry point prototypes and marks those without parameters. System
calls without parameters do not need compatibility checking (and they don't have corresponding
`__do_sys_*` functions, see "Extract system calls parameters").

Unfortunately, extracting full info about parameters from prototypes is a bad idea. Most
architectures have specific calling conventions for system calls, so even the number of parameters
may vary. But, hopefully, "no parameters" means the same thing everywhere.

```./extract_syscalls_from_kernel_src.py $KERNEL_SRC/common > ./kernel_syscalls.json```

### Extract system calls parameters

Entry point `sys_foo` is a function that gets system call parameters in a form of `struct ptregs*`.
`SYSCALL_DEFINEx` macro unpacks parameters and calls implementation function `__do_sys_foo` which
receives parameters normally.

`__do_sys_foo` functions are usually inlined and do not exist in symbols table. However, DWARF
should still have these functions.

To get parameters info in our common .json API form, do:

Build kernel for arch with debug info. Assume this is `VMLINUX_RISCV64`.

Extract the list of `__do_sys_*` functions for arch:

```./gen_kernel_syscalls_symbols.py riscv64 ./kernel_syscalls.json > ./symbols_riscv64.txt```

Use `dwarf_reader` to get .json API info for these functions:

```dwarf_reader --filter=symbols_riscv64.txt VMLINUX_RISCV64 > ./kernel_api_riscv64.json```

### Generate system calls translation

Now there should be files like:

```
kernel_syscalls.json (common system calls info)
kernel_api_riscv64.json (src arch system calls API)
kernel_api_x86_64.json (dst arch system calls API)
custom_syscalls.json (translation exceptions)
```

Run the script to generate code for translation function:

```
gen_kernel_syscalls_translation.py riscv64 x86_64 \
> kernel_api/gen_syscall_emulation_riscv64_to_x86_64-inl.h
```
