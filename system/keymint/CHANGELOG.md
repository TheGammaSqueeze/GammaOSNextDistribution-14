# Changelog

This file attempts to list significant changes to the Rust reference implementation of KeyMint,
where "significant" means things that are likely to affect vendors whose KeyMint implementations are
based on this codebase.

- The `BootInfo` structure passed to `kmr_ta::KeyMintTa::set_boot_info()` method did not make clear
  what the contents of the `verified_boot_key` field should be: the key itself, or a SHA-256 hash of
  the key.  The KeyMint implementation has been modified to cope with either, using a SHA-256 hash
  in places where the value is externally visible (key attestations and root-of-trust transfer) when
  it appears that the full key has been provided.  However, this requires that **vendor
  implementations provide an implementation of the new `Sha256`** trait (from
  <https://r.android.com/2786540>).  A sample implementation based on BoringSSL is available in
  `boringssl/src/sha256.rs`.
- Addition of features to indicate support for different HAL versions.  Vendors targetting the
  current version of the KeyMint HAL **should ensure that all `hal_v2`, `hal_v3` etc. features are
  enabled** in their build system (from <https://r.android.com/2777607>).  Vendors using the Soong
  build system are unaffected (because the Soong targets have been updated).
