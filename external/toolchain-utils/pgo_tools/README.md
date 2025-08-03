# pgo_tools

This directory contains scripts used to generate and vet PGO profiles for LLVM.

If you're a Mage who wants to generate a new PGO profile for the llvm-next
release, `./generate_llvm_next_pgo.py` is what you should run **outside of a
chroot**. It will create a new chroot, and generate a profile that's
backwards-compatible with our bootstrap chroot toolchains. This script takes a
few dozen minutes, and prints an "upload PGO profile" command at the end.

If you're a user who wants to generate a bespoke PGO profile for LLVM,
`./generate_pgo_profile.py` is what you want. Run it **inside of a chroot**, and
it will generate a profile for you with a pretty comprehensive, predefined
workload (building absl's tests for arm32, arm64, and a few x86_64 configs).

If you want to compare the rough performance of PGO profiles,
`./benchmark_pgo_profiles.py` may be useful.
