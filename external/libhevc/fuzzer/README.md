# Fuzzer for libhevc decoder and encoder

This describes steps to build hevc_dec_fuzzer and hevc_enc_fuzzer binary.

## Linux x86/x64

###  Requirements
- cmake (3.9.1 or above)
- make
- clang (6.0 or above)
  needs to support -fsanitize=fuzzer, -fsanitize=fuzzer-no-link

### Steps to build
Clone libhevc repository
```
$ git clone https://android.googlesource.com/platform/external/libhevc
```
Create a directory inside libhevc and change directory
```
 $ cd libhevc
 $ mkdir build
 $ cd build
```
Build fuzzer with required sanitizers (-DSANITIZE=fuzzer-no-link is mandatory
  to enable fuzzers)
```
 $ cmake .. -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
   -DCMAKE_BUILD_TYPE=Debug -DSANITIZE=fuzzer-no-link,address
 $ make
 ```

### Steps to run
Create a directory CORPUS_DIR and copy some elementary hevc files
(for hevc_dec_fuzzer) or yuv files (for hevc_enc_fuzzer) to that directory

To run the fuzzers
```
$ ./hevc_dec_fuzzer CORPUS_DIR
$ ./hevc_enc_fuzzer CORPUS_DIR
```

## Android

### Steps to build
Build the fuzzers
```
  $ mm -j$(nproc) hevc_dec_fuzzer
  $ mm -j$(nproc) hevc_enc_fuzzer
```

### Steps to run
Create a directory CORPUS_DIR and copy some elementary hevc files
(for hevc_dec_fuzzer) or yuv files (for hevc_enc_fuzzer) to that folder
Push this directory to device

To run hevc_dec_fuzzer on device
```
  $ adb sync data
  $ adb shell /data/fuzz/hevc_dec_fuzzer CORPUS_DIR
```
To run hevc_enc_fuzzer on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/hevc_enc_fuzzer/hevc_enc_fuzzer CORPUS_DIR
```

To run hevc_dec_fuzzer on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/hevc_dec_fuzzer/hevc_dec_fuzzer CORPUS_DIR
```

To run hevc_enc_fuzzer on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/hevc_enc_fuzzer/hevc_enc_fuzzer CORPUS_DIR
```


# Appendix
## libhevc encoder fuzzer

## Plugin Design Considerations
The fuzzer plugin for HEVC is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hardcoded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzer.

HEVC supports the following parameters:
1. Frame Width (parameter name: `i4_width`)
2. Frame Height (parameter name: `i4_height`)
3. Intra max transform tree depth (parameter name: `i4_max_tr_tree_depth_I`)
4. Inter max transform tree depth (parameter name: `i4_max_tr_tree_depth_nI`)
5. CU level Qp modulation (parameter name: `i4_cu_level_rc`)
6. Rate control mode (parameter name: `i4_rate_control_mode`)
7. Frame level Qp (parameter name: `ai4_frame_qp`)
8. Encoder quality preset (parameter name: `i4_quality_preset`)
9. Target Bitrate (parameter name: `ai4_tgt_bitrate`)
10. Enable entropy sync (parameter name: `i4_enable_entropy_sync`)
11. Deblocking type (parameter name: `i4_deblocking_type`)
12. Default scaling matrices (parameter name: `i4_use_default_sc_mtx`)
13. Max temporal layers (parameter name: `i4_max_temporal_layers`)
14. Max difference b/w IDR frames (parameter name: `i4_max_closed_gop_period`)
15. Min difference b/w IDR frames (parameter name: `i4_min_closed_gop_period`)
16. Max difference b/w I frames (parameter name: `i4_max_i_open_gop_period`)
17. Max difference b/w CRA frames (parameter name: `i4_max_cra_open_gop_period`)
18. Automatic insertion of SPS at each CDR (parameter name: `i4_sps_at_cdr_enable`)
19. Enable VUI output (parameter name: `i4_vui_enable`)
20. Enable SEI messages (parameter name: `i4_sei_enable_flag`)
21. Architecture type (parameter name: `e_arch_type`)
22. Enable force IDR frame test(parameter name: `mIsForceIdrEnabled`)
23. Enable dynamic bitrate test (parameter name: `mIsDynamicBitrateChangeEnabled`)
24. Force IDR frame number (parameter name: `mForceIdrInterval`)
25. Frame number for dynamic bitrate  (parameter name: `mDynamicBitrateInterval`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `i4_width` | In the range `0 to 10239` | All the bits of 1st and 2nd byte of data |
| `i4_height` | In the range `0 to 10239` | All the bits of 3rd and 4th byte of data |
| `i4_max_tr_tree_depth_I` | 0. `1` 1. `2` 2. `3` | All the bits of 5th byte of data |
| `i4_max_tr_tree_depth_nI` | 0. `1` 1. `2` 2. `3` 3. `4` | bit 0 and 1 of 6th byte of data |
| `i4_cu_level_rc` | 0. `0` 1. `1` | bit 0 of 7th byte of data |
| `i4_rate_control_mode` | 0. `VBR` 1. `CQP` 2. `CBR` | All the bits of 8th byte of data modulus 3 |
| `ai4_frame_qp` | In the range `1 to 51` | All the bits of 9th byte of data |
| `i4_quality_preset` | 0. `IHEVCE_QUALITY_P0` 1. `IHEVCE_QUALITY_P2` 2. `IHEVCE_QUALITY_P3` 3. `IHEVCE_QUALITY_P4 4. `IHEVCE_QUALITY_P5` 5. `IHEVCE_QUALITY_P6` 6. `IHEVCE_QUALITY_P7` | All the bits of 10th byte of data modulus 7 |
| `ai4_tgt_bitrate` | In the range `0 to 500000000` | All the bits of 11th and 12th byte of data |
| `i4_enable_entropy_sync` | 0. `0` 1. `1` | bit 0 of 13th byte of data |
| `i4_deblocking_type` | 0. `0` 1. `1` | bit 0 of 14th byte of data |
| `i4_use_default_sc_mtx` | 0. `0` 1. `1` | bit 0 of 15th byte of data |
| `i4_max_temporal_layers` | 0. `0` 1. `1` 2. `2` 3. `3` | bit 0 and 1 of 16th byte of data |
| `i4_max_closed_gop_period` | In the range `0 to 255` | All the bits of 17th byte of data |
| `i4_min_closed_gop_period` | In the range `0 to 255` | All the bits of 18th byte of data |
| `i4_max_i_open_gop_period` | In the range `0 to 255` | All the bits of 19th byte of data |
| `i4_max_cra_open_gop_period` | In the range `0 to 255` | All the bits of 20th byte of data |
| `i4_sps_at_cdr_enable` | 0. `0` 1. `1` | bit 0 of 21st byte of data |
| `i4_vui_enable` | 0. `0` 1. `1` | bit 0 of 22nd byte of data |
| `i4_sei_enable_flag` | 0. `0` 1. `1` | bit 0 of 23th byte of data |
| `e_arch_type` | 0. `ARCH_ARM_NONEON` 1. `ARCH_NA` | bit 0 and 1 of 24th byte of data |
| `mIsForceIdrEnabled` | 0. `0` 1. `1` | bit 0 of 25th byte of data |
| `mIsDynamicBitrateChangeEnabled` | 0. `0` 1. `1` | bit 0 of 	 byte of data |
| `mForceIdrInterval` | In the range `0 to 7` | bit 0, 1 and 2 of 27th byte of data |
| `mDynamicBitrateInterval` | In the range `0 to 7` | bit 0, 1 and 2 of 28th byte of data |

This also ensures that the plugin is always deterministic for any given input.

##### Maximize utilization of input data
The plugin feeds the entire input data to the codec using a loop.
If the encode operation was successful, the input is advanced by the frame size.
If the encode operation was un-successful, the input is still advanced by frame size so
that the fuzzer can proceed to feed the next frame.

This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.


## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
