# Fuzzer for libvorbis decoder
## Plugin Design Considerations
The fuzzer plugin for Vorbis is designed based on the understanding of the
codec and tries to achieve the following:

##### Maximize code coverage
Dict file (dictionary file) is created for vorbis to ensure that the required start
bytes are present in every input file that goes to the fuzzer.
This ensures that decoder does not reject any input file in the first check

##### Maximize utilization of input data
The plugin feeds the entire input data to the codec in a loop till the desired headers
(`01vorbis`, `05vorbis`) are parsed.
After that, the remaining data is passed at once to the decoder.
FrameSize in Vorbis is determined only after the call to extractor, so in absence of call to extractor,
we feed the entire remaining data to the decoder.

This ensures that the plugin tolerates any kind of input (empty, huge, malformed, etc)
and doesnt `exit()` on any input and thereby increasing the chance of identifying vulnerabilities.

## Build

This describes steps to build vorbis_dec_fuzzer binary.

### Android

#### Steps to build
Build the fuzzer
```
  $ mm -j$(nproc) vorbis_dec_fuzzer
```

#### Steps to run
Create a directory CORPUS_DIR and copy some vorbis files to that folder
Push this directory to device.

To run on device
```
  $ adb sync data
  $ adb shell /data/fuzz/arm64/vorbis_dec_fuzzer/vorbis_dec_fuzzer CORPUS_DIR
```
To run on host
```
  $ $ANDROID_HOST_OUT/fuzz/x86_64/vorbis_dec_fuzzer/vorbis_dec_fuzzer CORPUS_DIR
```

## References:
 * http://llvm.org/docs/LibFuzzer.html
 * https://github.com/google/oss-fuzz
