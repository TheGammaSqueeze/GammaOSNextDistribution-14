# azahar-nano core

This folder holds the source of the Azahar 3DS emulator (based on the azahar-2126
release, with all of its submodules included as plain files) plus the GammaOS
changes that turn it into the azahar-nano core:

- `src/android/app/src/main/jni/nano/`: the C interface used by the azahar-nano
  frontend (`frameworks/base/cmds/azahar-nano`), built as `libazahar-nano-core.so`.
- Renderer and core changes for GammaOS Nano: DRM-direct presentation support,
  deadline frame pacing, the GammaOS renderer tweaks (render pass and folded
  clears, hardware scissor, vertex depth, right-eye skip), per-driver pipeline
  caches, a persistent audio sink, save state fixes and the JIT warm-up cache.
- `externals/dynarmic`: two additions to the A32 arm64 backend used by the JIT
  warm-up (`GetCompiledBlockLocations`, `PrecompileBlocks`).

## Building

The image build scripts (`buildtv.sh`, `buildtv_cc.sh`) run
`external/azahar-nano/gammaos/build-core.sh` before the image build. It builds the
core with the Android NDK r27c and CMake, then installs the stripped libraries
into `gammaos/emulators/azahar-nano-lib/`, which the product makefile copies into
`/system/lib64`.

- The NDK and, if the host has none new enough, CMake are downloaded once into
  `out/azahar-nano/`. Set `ANDROID_NDK` to use an NDK r27c you already have.
- Ninja comes from `prebuilts/build-tools`.
- `AZAHAR_NANO_SKIP_BUILD=1` skips the core build and keeps the libraries that are
  committed in `gammaos/emulators/azahar-nano-lib/`.
- The build directory is `out/azahar-nano/build`; delete it to reconfigure.

The `Android.bp` and `Android.mk` files of the bundled third-party libraries are
renamed to `*.disabled` so the platform build does not pick them up; the core is
built only through CMake.
