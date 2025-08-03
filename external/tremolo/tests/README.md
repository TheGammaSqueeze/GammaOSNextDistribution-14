## Media Testing ##
---
#### Vorbis Decoder
The VorbisDecoder Test Suite validates the libvorbisidec library available in external/tremelo.

Run the following steps to build the test suite:
```
m VorbisDecoderTest
```

The 32-bit binaries will be created in the following path : ${OUT}/data/nativetest/

The 64-bit binaries will be created in the following path : ${OUT}/data/nativetest64/

To test 64-bit binary push binaries from nativetest64.
```
adb push ${OUT}/data/nativetest64/VorbisDecoderTest/VorbisDecoderTest /data/local/tmp/
```

To test 32-bit binary push binaries from nativetest.
```
adb push ${OUT}/data/nativetest/VorbisDecoderTest/VorbisDecoderTest /data/local/tmp/
```

The resource file for the tests is taken from [here](https://dl.google.com/android-unittest/media/external/tremolo/tests/VorbisDecoderRes-1.0.zip). Download, unzip and push these files into device for testing.

```
adb push VorbisDecoderRes-1.0 /sdcard/test/
```

usage: VorbisDecoderTest -P \<path_to_folder\> -C <remove_output_file>
```
adb shell /data/local/tmp/VorbisDecoderTest -P /sdcard/test/VorbisDecoderRes-1.0/ -C true
```
Alternatively, the test can also be run using atest command.

```
atest VorbisDecoderTest -- --enable-module-dynamic-download=true
```
