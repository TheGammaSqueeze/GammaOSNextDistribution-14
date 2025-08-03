# Fuzzer for libopus decoder

## Plugin Design Considerations
The fuzzer plugin for opus decoder is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage

This fuzzer provides support for both single stream and multi stream inputs,
thus enabling fuzzing for API's provided for single stream as well as multi
stream.

Following arguments are passed to OPUS_DEC_CREATE_API:

1. Sampling frequency (parameter name: `Fs`)
2. Number of channels (parameter name: `channels`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `Fs` | `8000 ` `12000 ` `16000 ` `24000 ` `48000 ` | Derived from Byte-9 of input stream|
| `channels`   | `1 ` `2 ` | Derived from Byte-9 of input stream |

##### Maximize utilization of input data
The plugin feeds the entire input data to the codec. Frame sizes are determined only
after the call to extractor, so in absence of call to extractor,
we feed the entire data to the decoder.
This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build opus_dec_fuzzer and opus_multistream_dec_fuzzer binary.

## Android

### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) opus_dec_fuzzer
  $ mm -j$(nproc) opus_multistream_dec_fuzzer
```

### Steps to run
Create a directory CORPUS_DIR and copy some opus files to that folder.
Push this directory to device.

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/opus_dec_fuzzer/opus_dec_fuzzer CORPUS_DIR
  $ adb shell /data/fuzz/arm64/opus_multistream_dec_fuzzer/opus_multistream_dec_fuzzer CORPUS_DIR
```
To run on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/opus_dec_fuzzer/opus_dec_fuzzer CORPUS_DIR
  $ $ANDROID_HOST_OUT/fuzz/x86_64/opus_multistream_dec_fuzzer/opus_multistream_dec_fuzzer CORPUS_DIR
```

# Fuzzer for libopus encoder

## Plugin Design Considerations
The fuzzer plugin for opus encoder is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage

This fuzzer provides support for both single stream and multi stream inputs,
thus enabling fuzzing for API's provided for single stream as well as multi
stream.
Following arguments are passed to OPUS_ENC_CREATE_API:

1. Sampling rate (parameter name: `sampleRate`)
2. Number of channels (parameter name: `channels`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `sampleRate` | `8000 ` `12000 ` `16000 ` `24000 ` `48000 ` | An index ranging from 0-4 is calculated using first byte of data which is used to assign value to `sampleRate` from array `kSampleRates`|
| `channels` (single stream)   | `1 ` `2 ` | Calculated using second byte of data |
| `channels` (multi stream)   | In range `1 `- `255 ` | Calculated using second byte of data |

Following arguments are passed to OPUS_ENC_CTL_API:

1. OPUS_SET_BITRATE (parameter name: `bitRate`)
2. OPUS_SET_COMPLEXITY (parameter name: `complexity`)
3. OPUS_SET_APPLICATION (parameter name: `application`)
4. OPUS_SET_DTX (parameter name: `setDTX`)
5. OPUS_SET_SIGNAL (parameter name: `signal`)
6. OPUS_SET_VBR (parameter name: `setVBR`)
7. OPUS_SET_VBR_CONSTRAINT (parameter name: `setVBRConstraint`)
8. OPUS_SET_FORCE_CHANNELS (parameter name: `forceChannel`)
9. OPUS_SET_MAX_BANDWIDTH (parameter name: `maxBandwidth`)
10. OPUS_SET_INBAND_FEC (parameter name: `setInbandFec`)
11. OPUS_SET_PACKET_LOSS_PERC (parameter name: `pktLoss`)
12. OPUS_SET_LSB_DEPTH (parameter name: `lsbDepth`)
13. OPUS_SET_PREDICTION_DISABLED (parameter name: `setPredDisable`)
14. OPUS_SET_EXPERT_FRAME_DURATION (parameter name: `frameSizeEnum`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `bitRate`   | In range `500 ` to `512000 ` | Calculated using all bits of 3rd, 4th and 5th byte of data |
| `complexity`   |0.`0 ` 1.`1 ` 2.`2 ` 3.`3 ` 4.`4 ` 5.`5 ` 6.`6 ` 7.`7 ` 8.`8 ` 9.`9 ` 10.`10 ` | Calculated using all bits of 6th byte of data |
| `application`   | 0.`OPUS_APPLICATION_VOIP ` 1.`OPUS_APPLICATION_AUDIO ` 2.`OPUS_APPLICATION_RESTRICTED_LOWDELAY ` | Calculated using all bits of 7th byte of data |
| `setDTX`   | 0.`0 ` 1.`1 ` | Calculated using bit 0 of 8th byte of data |
| `signal`   | 0.`OPUS_AUTO ` 1.`OPUS_SIGNAL_VOICE ` 2.`OPUS_SIGNAL_MUSIC ` | Calculated using bit 0 and bit 1 of 9th byte of data |
| `setVBR`   | 0.`0 ` 1.`1 `  | Calculated using bit 0 of 10th byte of data |
| `setVBRConstraint`   | 0.`0 ` 1.`1 ` | Calculated using bit 0 of 11th byte of data |
| `forceChannel`   | 0.`OPUS_AUTO ` 1.`1 ` 2.`2 ` | Calculated using all bits of 12th byte of data |
| `maxBandwidth`   | 0.`OPUS_BANDWIDTH_NARROWBAND ` 1.`OPUS_BANDWIDTH_MEDIUMBAND ` 2.`OPUS_BANDWIDTH_WIDEBAND ` 3.`OPUS_BANDWIDTH_SUPERWIDEBAND ` 4.`OPUS_BANDWIDTH_FULLBAND ` | Calculated using all bits of 13th byte of data |
| `setInbandFec`   | 0.`0 ` 1.`1 ` | Calculated using bit 0 of 14th byte of data |
| `pktLoss`   | 0.`0 ` 1.`1 ` 2.`2 ` 3.`5 `| Calculated using all bits of 15th byte of data |
| `lsbDepth`   | 0.`8 ` 1.`24 ` | Calculated using bit 0 of 16th byte of data |
| `setPredDisable`   | 0.`0 ` 1.`1 ` | Calculated using bit 0 of 17th byte of data |
| `frameSizeEnum`   | 0.`OPUS_FRAMESIZE_2_5_MS ` 1.`OPUS_FRAMESIZE_5_MS ` 2.`OPUS_FRAMESIZE_10_MS ` 3.`OPUS_FRAMESIZE_20_MS ` 4.`OPUS_FRAMESIZE_40_MS ` 5.`OPUS_FRAMESIZE_60_MS ` 6.`OPUS_FRAMESIZE_80_MS ` 7.`OPUS_FRAMESIZE_100_MS ` 8.`OPUS_FRAMESIZE_120_MS ` | Calculated using all bits of 18th byte of data |


##### Maximize utilization of input data
The plugin feeds the entire input data to the codec. For buffer size which is not a multiple of mNumPcmBytesPerInputFrame, we will accumulate the input and keep it. Once the input is filled with expected number of bytes, we will send it to encoder. This ensures that the plugin tolerates any kind of input (empty, huge,
malformed, etc) and doesnt `exit()` on any input and thereby increasing the chance of identifying vulnerabilities.

## Build

This describes steps to build opus_enc_fuzzer and opus_multistream_enc_fuzzer.

## Android

### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) opus_enc_fuzzer
  $ mm -j$(nproc) opus_multistream_enc_fuzzer
```

### Steps to run
Create a directory CORPUS_DIR and copy some raw media files to that folder.
Push this directory to device.

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/opus_enc_fuzzer/opus_enc_fuzzer CORPUS_DIR
  $ adb shell /data/fuzz/arm64/opus_multistream_enc_fuzzer/opus_multistream_enc_fuzzer CORPUS_DIR
```
To run on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/opus_enc_fuzzer/opus_enc_fuzzer CORPUS_DIR
  $ $ANDROID_HOST_OUT/fuzz/x86_64/opus_multistream_enc_fuzzer/opus_multistream_enc_fuzzer CORPUS_DIR
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
