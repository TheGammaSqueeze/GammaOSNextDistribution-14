fn main() {
    let target_dir = std::env::var_os("CARGO_TARGET_DIR").unwrap();

    // The main linking point with c++ code is the libbluetooth-static.a
    // These includes all the symbols built via C++ but doesn't include other
    // links (i.e. pkg-config)
    println!("cargo:rustc-link-lib=static=bluetooth-static");
    println!("cargo:rustc-link-search=native={}", target_dir.clone().into_string().unwrap());

    // A few dynamic links
    println!("cargo:rustc-link-lib=dylib=flatbuffers");
    println!("cargo:rustc-link-lib=dylib=protobuf");
    println!("cargo:rustc-link-lib=dylib=resolv");
    println!("cargo:rustc-link-lib=dylib=lc3");
    println!("cargo:rustc-link-lib=dylib=fmt");

    println!("cargo:rerun-if-changed=build.rs");
}
