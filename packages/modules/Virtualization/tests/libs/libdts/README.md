Device tree source (DTS) decompiler on Android device.

This is alternative to dtdiff, which only support bash.

How to use for rust_test
========================

Following dependencies are needed in addition to libdts.

```
rust_test {
  ...
  data_bins: ["dtc_static"],
  compile_multilib: "first",
}
```
