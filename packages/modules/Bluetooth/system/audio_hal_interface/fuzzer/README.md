# Fuzzers for libbt-audio-hal-interface

## Plugin Design Considerations
The fuzzer plugins for `libbt-audio-hal-interface` are designed based on the understanding of the
source code and tries to achieve the following:

##### Maximize code coverage
The configuration parameters are not hard-coded, but instead selected based on
incoming data. This ensures more code paths are reached by the fuzzers.

Fuzzers assigns values to the following parameters to pass on to libbt-audio-hal-interface:
1. A2DP Control Ack Status (parameter name: `status`)
2. A2DP supported codecs (parameter name: `index`)
3. Session Type  (parameter name: `sessionType`)
4. Session Type V2_1 (parameter name: `sessionType_2_1`)
5. Sample Rates (parameter name: `param.sampleRate`)
6. Sample Rates V2_1 (parameter name: `param.sampleRate`)
7. BTAV Sample Rates (parameter name: `a2dpCodecConfig.sample_rate`)
8. Bits per Sample (parameter name: `param.bitsPerSample`)
9. BTAV A2DP Codec Bits per Sample (parameter name: `a2dpCodecConfig.bits_per_sample`)
10. Channel Mode (parameter name: `param.channelMode`)
11. A2DP Codec Channel Mode (parameter name: `a2dpCodecConfig.channel_mode`)
12. Sbc Subbands (parameter name: `sbc.numSubbands`)
13. Sbc alloc methods (parameter name: `sbc.allocMethod `)
14. Sbc Channel mode (parameter name: `sbc.channelMode `)
15. Sbc Block Length (parameter name: `sbc.blockLength `)
16. Aac Object Type  (parameter name: `aac.objectType`)
17. Aac Variable Bit Rate (parameter name: `aac.variableBitRateEnabled`)
18. Ldac Quality Index (parameter name: `ldac.qualityIndex`)
19. Ldac Channel Mode (parameter name: `ldac.channelMode`)
20. Codec Type (parameter name: `codecConfig.codecType`)

| Parameter| Valid Values| Configured Value|
|------------- |-------------| ----- |
| `status` | 0.`A2DP_CTRL_ACK_SUCCESS` 1.`A2DP_CTRL_ACK_FAILURE` 2.`A2DP_CTRL_ACK_INCALL_FAILURE` 3.`A2DP_CTRL_ACK_UNSUPPORTED` 4.`A2DP_CTRL_ACK_PENDING` 5.`A2DP_CTRL_ACK_DISCONNECT_IN_PROGRESS` | Value obtained from FuzzedDataProvider |
| `index` | 0.`BTAV_A2DP_CODEC_INDEX_SOURCE_SBC` 1.`BTAV_A2DP_CODEC_INDEX_SOURCE_AAC` 2.`BTAV_A2DP_CODEC_INDEX_SOURCE_APTX` 3.`BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD` 4.`BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC` 5.`BTAV_A2DP_CODEC_INDEX_SINK_SBC` 6.`BTAV_A2DP_CODEC_INDEX_SINK_AAC` 7.`BTAV_A2DP_CODEC_INDEX_SINK_LDAC` | Value obtained from FuzzedDataProvider |
| `sessionType` | 0.`SessionType::UNKNOWN` 1.`SessionType::A2DP_SOFTWARE_ENCODING_DATAPATH` 2.`SessionType::A2DP_HARDWARE_OFFLOAD_DATAPATH` 3.`SessionType::HEARING_AID_SOFTWARE_ENCODING_DATAPATH` | Value obtained from FuzzedDataProvider |
| `sessionType_2_1` | 0.`SessionType_2_1::UNKNOWN` 1.`SessionType_2_1::A2DP_SOFTWARE_ENCODING_DATAPATH` 2.`SessionType_2_1::A2DP_HARDWARE_OFFLOAD_DATAPATH` 3.`SessionType_2_1::HEARING_AID_SOFTWARE_ENCODING_DATAPATH` 4.`SessionType_2_1::LE_AUDIO_SOFTWARE_ENCODING_DATAPATH` 5.`SessionType_2_1::LE_AUDIO_SOFTWARE_DECODED_DATAPATH` 6.`SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_ENCODING_DATAPATH` 7.`SessionType_2_1::LE_AUDIO_HARDWARE_OFFLOAD_DECODING_DATAPATH` | Value obtained from FuzzedDataProvider |
| `param.sampleRate` | 0.`SampleRate::RATE_UNKNOWN` 1.`SampleRate::RATE_8000` 2.`SampleRate::RATE_16000` 3.`SampleRate::RATE_24000` 4.`SampleRate::RATE_32000` 5.`SampleRate::RATE_44100` 6.`SampleRate::RATE_48000` | Value obtained from FuzzedDataProvider |
| `param.sampleRate (V2_1)` | 0.`SampleRate_2_1::RATE_UNKNOWN` 1.`SampleRate_2_1::RATE_8000` 2.`SampleRate_2_1::RATE_16000` 3.`SampleRate_2_1::RATE_24000` 4.`SampleRate_2_1::RATE_32000` 5.`SampleRate_2_1::RATE_44100` 6.`SampleRate_2_1::RATE_48000` | Value obtained from FuzzedDataProvider |
| `a2dpCodecConfig.sample_rate` | 0.`BTAV_A2DP_CODEC_SAMPLE_RATE_NONE` 1.`BTAV_A2DP_CODEC_SAMPLE_RATE_44100` 2.`BTAV_A2DP_CODEC_SAMPLE_RATE_48000` 3.`BTAV_A2DP_CODEC_SAMPLE_RATE_88200` 4.`BTAV_A2DP_CODEC_SAMPLE_RATE_96000` 5.`BTAV_A2DP_CODEC_SAMPLE_RATE_176400` 6.`BTAV_A2DP_CODEC_SAMPLE_RATE_192000` 7.`BTAV_A2DP_CODEC_SAMPLE_RATE_16000` 8.`BTAV_A2DP_CODEC_SAMPLE_RATE_24000` | Value obtained from FuzzedDataProvider |
| `param.bitsPerSample` | 0.`BitsPerSample::BITS_UNKNOWN` 1.`BitsPerSample::BITS_16` 2.`BitsPerSample::BITS_24` 3.`BitsPerSample::BITS_32` | Value obtained from FuzzedDataProvider |
| `a2dpCodecConfig.bits_per_sample` | 0.`BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE` 1.`BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16` 2.`BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24` 3.`BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32` | Value obtained from FuzzedDataProvider |
| `param.channelMode` | 0.`ChannelMode::UNKNOWN` 1.`ChannelMode::MONO` 2.`ChannelMode::STEREO`  | Value obtained from FuzzedDataProvider |
| `a2dpCodecConfig.channel_mode` | 0.`BTAV_A2DP_CODEC_CHANNEL_MODE_NONE` 1.`BTAV_A2DP_CODEC_CHANNEL_MODE_MONO` 2.`BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO`  | Value obtained from FuzzedDataProvider |
| `param.peerMtu` | 0.`660` 1.`663` 2.`883` 3.`1005` 4.`1500`  | Value obtained from FuzzedDataProvider |
| `sbc.numSubbands` | 0.`SbcNumSubbands::SUBBAND_4` 1.`SbcNumSubbands::SUBBAND_8` | Value obtained from FuzzedDataProvider |
| `sbc.allocMethod` | 0.`SbcAllocMethod::ALLOC_MD_S` 1.`SbcAllocMethod::ALLOC_MD_L` | Value obtained from FuzzedDataProvider |
| `sbc.channelMode ` | 0.`SbcChannelMode::UNKNOWN` 1.`SbcChannelMode::JOINT_STEREO` 2.`SbcChannelMode::STEREO` 3.`SbcChannelMode::DUAL` 4.`SbcChannelMode::MONO`  | Value obtained from FuzzedDataProvider |
| `sbc.blockLength ` | 0.`SbcBlockLength::BLOCKS_4` 1.`SbcBlockLength::BLOCKS_8` 2.`SbcBlockLength::BLOCKS_12` 3.`SbcBlockLength::BLOCKS_16` | Value obtained from FuzzedDataProvider |
| `aac.objectType` | 0.`AacObjectType::MPEG2_LC` 1.`AacObjectType::MPEG4_LC` 2.`AacObjectType::MPEG4_LTP` 3.`AacObjectType::MPEG4_SCALABLE` | Value obtained from FuzzedDataProvider |
| `aac.variableBitRateEnabled` | 0.`AacVariableBitRate::DISABLED` 1.`AacVariableBitRate::ENABLED` | Value obtained from FuzzedDataProvider |
| `ldac.qualityIndex` | 0.`LdacQualityIndex::QUALITY_HIGH` 1.`LdacQualityIndex::QUALITY_MID` 2.`LdacQualityIndex::QUALITY_LOW` 3.`LdacQualityIndex::QUALITY_ABR` | Value obtained from FuzzedDataProvider |
| `ldac.channelMode ` | 0.`LdacChannelMode::UNKNOWN` 1.`LdacChannelMode::STEREO` 2.`LdacChannelMode::DUAL` 3.`LdacChannelMode::MONO` | Value obtained from FuzzedDataProvider |
| `codecConfig.codecType` | 0.`CodecType::APTX` 1.`CodecType::APTX_HD` | Value obtained from FuzzedDataProvider |

This also ensures that the plugins are always deterministic for any given input.

##### Maximize utilization of input data
The plugins feed the entire input data to the module.
This ensures that the plugins tolerates any kind of input (empty, huge,
malformed, etc) and doesn't `exit()` on any input and thereby increasing the
chance of identifying vulnerabilities.

## Build

This describes steps to build libbt_audio_hal_a2dp_encoding_fuzzer, libbt_audio_hal_le_audio_software_fuzzer, libbt_audio_hal_hearing_aid_software_encoding_fuzzer and libbt_audio_hal_client_interface_fuzzer binaries.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) libbt_audio_hal_a2dp_encoding_fuzzer
  $ mm -j$(nproc) libbt_audio_hal_le_audio_software_fuzzer
  $ mm -j$(nproc) libbt_audio_hal_hearing_aid_software_encoding_fuzzer
  $ mm -j$(nproc) libbt_audio_hal_client_interface_fuzzer
```
### Steps to run

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/libbt_audio_hal_a2dp_encoding_fuzzer/libbt_audio_hal_a2dp_encoding_fuzzer
  $ adb shell /data/fuzz/arm64/libbt_audio_hal_le_audio_software_fuzzer/libbt_audio_hal_le_audio_software_fuzzer
  $ adb shell /data/fuzz/arm64/libbt_audio_hal_hearing_aid_software_encoding_fuzzer/libbt_audio_hal_hearing_aid_software_encoding_fuzzer
  $ adb shell /data/fuzz/arm64/libbt_audio_hal_client_interface_fuzzer/libbt_audio_hal_client_interface_fuzzer
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
