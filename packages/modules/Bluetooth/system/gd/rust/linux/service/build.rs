use pkg_config::Config;

fn main() {
    let target_dir = std::env::var_os("CARGO_TARGET_DIR").unwrap();
    let cxx_outdir = std::env::var_os("CXX_OUTDIR").unwrap();

    // The main linking point with c++ code is the libbluetooth-static.a
    // These includes all the symbols built via C++ but doesn't include other
    // links (i.e. pkg-config)
    println!("cargo:rustc-link-lib=static:-bundle,+whole-archive=bluetooth-static");
    println!("cargo:rustc-link-search=native={}", target_dir.clone().into_string().unwrap());
    // Also re-run the build if anything in the C++ build changes
    println!("cargo:rerun-if-changed={}", cxx_outdir.into_string().unwrap());

    // A few dynamic links
    Config::new().probe("flatbuffers").unwrap();
    Config::new().probe("protobuf").unwrap();
    println!("cargo:rustc-link-lib=dylib=resolv");

    // Clang requires -lc++ instead of -lstdc++
    println!("cargo:rustc-link-lib=c++");

    // A few more dependencies from pkg-config. These aren't included as part of
    // the libbluetooth-static.a
    Config::new().probe("libchrome").unwrap();
    Config::new().probe("libmodp_b64").unwrap();
    Config::new().probe("tinyxml2").unwrap();
    Config::new().probe("lc3").unwrap();
    Config::new().probe("fmt").unwrap();

    // Include ChromeOS-specific dependencies.
    if option_env!("TARGET_OS_VARIANT").unwrap_or("None").to_string() == "chromeos" {
        Config::new().probe("libstructuredmetrics").unwrap();
    }

    println!("cargo:rerun-if-changed=build.rs");
}
