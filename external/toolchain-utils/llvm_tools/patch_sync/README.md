# Overview

See go/llvm-patch-sync for motivation, diagrams, and design docs. The
`patch_sync` tool also has a detailed `--help`, which can be quickly
accessed via `cargo run -- --help`, `cargo run -- transpose --help`,
and `cargo run -- show --help`.

## Building

```rs
# Release version
cargo build --release

# Debug version
cargo build
```

The executable will then exist at `./target/(debug|release)/patch_sync`.

## Running Unittests

```rs
cargo test
```

Because `patch_sync` requires a specific file system layout to work correctly,
the unittests are unfortunately fairly sparse. Full testing will likely require
running `patch_sync transpose ...` with the necessary arguments.

## Example Transpose Command

This command will:

1. Sync the Android toolchain and ChromiumOS overlay repositories.
2. Find any new patches between the current version and the base ref.
3. Copy any new and applicable patches into each repository.

```
./patch_sync transpose \
  --sync \
  --aosp-checkout "${HOME}/android" \
  --aosp-base-ref "${base_aosp_git_hash}" \
  --cros-checkout "${HOME}/chromiumos" \
  --overlay-base-ref "${base_cros_git_hash}"
```
