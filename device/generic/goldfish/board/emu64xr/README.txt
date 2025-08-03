The "emu64xr" product defines a non-hardware-specific IA target
without a kernel or bootloader.

This only supports 64bit abi and translated riscv64 abi

It can be used to build the entire user-level system, and
will work with the IA version of the emulator,

It is not a product "base class"; no other products inherit
from it or use it in any way.
