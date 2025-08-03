The "emu64xa16k" product defines a non-hardware-specific IA target
without a kernel or bootloader.

This only supports 64bit 16KB pagesize abi: x86_64_16k
and ndk-translated arm64_16k abi

It can be used to build the entire user-level system, and
will work with the IA version of the emulator,

It is not a product "base class"; no other products inherit
from it or use it in any way.
