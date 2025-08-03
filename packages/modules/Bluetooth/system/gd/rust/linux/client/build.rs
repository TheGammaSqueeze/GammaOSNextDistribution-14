use pkg_config::Config;

fn main() {
    let target_dir = std::env::var_os("CARGO_TARGET_DIR").unwrap();
    println!("cargo:rustc-link-search=native={}", target_dir.into_string().unwrap());

    // When cross-compiling, pkg-config is looking for dbus at the host libdir instead of the
    // sysroot. Adding this dependency here forces the linker to include the current sysroot's
    // libdir and fixes the build issues.
    Config::new().probe("dbus-1").unwrap();
    println!("cargo:rerun-if-changed=build.rs");
}
