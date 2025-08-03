RUST_OPT_LEVEL = release
# Required for linking of composite Rust + C binaries
RUST_FLAGS = RUSTFLAGS='-C relocation-model=pic'