# Fuzzer for libFLAC decoder

## Plugin Design Considerations
The fuzzer plugin for FLAC decoder is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hardcoded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzer.

FLAC supports the following two decoder API's for native mode:
1. FLAC__stream_decoder_process_single (frame by frame decoding)
2. FLAC__stream_decoder_process_until_end_of_stream (decoding entire stream)

One of these two decoder API's will be called based on LSB of 5th byte of data.

This also ensures that the plugin is always deterministic for any given input.

##### Maximize utilization of input data
The plugin feeds the input data to the codec using a read_callback as and when
requested by the decoder. The read_callback feeds the input data to the decoder
until the end of stream.

This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build flac_dec_fuzzer binary.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) flac_dec_fuzzer
```

#### Steps to run
Create a directory CORPUS_DIR and copy some flac files to that folder
Push this directory to device.

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/flac_dec_fuzzer/flac_dec_fuzzer CORPUS_DIR
```
To run on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/flac_dec_fuzzer/flac_dec_fuzzer CORPUS_DIR
```

# Fuzzer for libFLAC encoder

## Plugin Design Considerations
The fuzzer plugin for flac encoder is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage

The configuration parameters are not hardcoded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzer.

Follwing functions were called in initEncoder to configure the encoder:

| Function name | Parameter| Valid Values| Configured Value|
|------------- |------------- |-------------| ----- |
| `FLAC__stream_encoder_set_sample_rate`   | sampleRate |`1 ` to `655350 ` | All the bits of 1st, 2nd and 3rd byte of data |
| `FLAC__stream_encoder_set_channels`   | mChannels |`1 ` `2 ` | bit 0 of 4th byte of data |
| `FLAC__stream_encoder_set_compression_level`   | compression |`1 ` `2 ` | bit 0 of 5th byte of data |
| `FLAC__stream_encoder_set_bits_per_sample`   | bitsPerSample |`16 ` `24 ` | bit 0 of 6th byte of data |
| `FLAC__stream_encoder_set_verify`   | - |`false ` `true ` |  bit 0 of 7th byte of data |
| `FLAC__stream_encoder_set_streamable_subset`   | - |`false ` `true ` |  bit 0 of 8th byte of data |
| `FLAC__stream_encoder_set_do_mid_side_stereo`   | - |`false ` `true ` |  bit 0 of 9th byte of data |
| `FLAC__stream_encoder_set_loose_mid_side_stereo`   | - |`false ` `true ` |  bit 0 of 10th byte of data |
| `FLAC__stream_encoder_set_max_lpc_order`   | - |`false ` `true ` |  bit 0 of 11th byte of data|
| `FLAC__stream_encoder_set_qlp_coeff_precision`   | - |`0 ` `1 ` |  bit 0 of 12th byte of data |
| `FLAC__stream_encoder_set_do_qlp_coeff_prec_search`   | - |`false ` `true ` |  bit 0 of 13th byte of data |
| `FLAC__stream_encoder_set_do_escape_coding`   | - |`false ` `true ` |  bit 0 of 14th byte of data|
| `FLAC__stream_encoder_set_do_exhaustive_model_search`   | - |`false ` `true ` |  bit 0 of 15th byte of data |
| `FLAC__stream_encoder_set_min_residual_partition_order`   | - |`0 ` `1 ` |  bit 0 of 16th byte of data |
| `FLAC__stream_encoder_set_max_residual_partition_order`   | - |`0 ` `1 ` |  bit 0 of 17th byte of data |
| `FLAC__stream_encoder_set_rice_parameter_search_dist`   | - |`0 ` `1 ` |  bit 0 of 18th byte of data|
| `FLAC__stream_encoder_set_total_samples_estimate`   | - |`0 ` `1 ` |  bit 0 of 19th byte of data|


##### Maximize utilization of input data
The plugin feeds the entire input data to the codec and continues with the encoding even on a failure. This ensures that the plugin tolerates any kind of input (empty, huge, malformed, etc) and doesn't `exit()` on any input and thereby increasing the chance of identifying vulnerabilities.

## Build

This describes steps to build flac_enc_fuzzer binary.

## Android

### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) flac_enc_fuzzer
```

### Steps to run
Create a directory CORPUS_DIR and copy some wav files to that folder.
Push this directory to device.

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/flac_enc_fuzzer/flac_enc_fuzzer CORPUS_DIR
```
To run on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/flac_enc_fuzzer/flac_enc_fuzzer CORPUS_DIR
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
