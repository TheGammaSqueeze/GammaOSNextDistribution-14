fn main() {
    // Link the keystore static lib and make sure to use -lc++
    #[cfg(feature = "keystore")]
    {
        let target_dir = std::env::var_os("CARGO_TARGET_DIR").unwrap();
        println!("cargo:rustc-link-lib=static=bt_keystore_cc");
        println!("cargo:rustc-link-search=native={}", target_dir.clone().into_string().unwrap());
        println!("cargo:rustc-link-lib=c++");
        // Re-run if this file changed.
        println!("cargo:rerun-if-changed={}", target_dir.into_string().unwrap());
    }

    println!("cargo:rerun-if-changed=build.rs");
}
