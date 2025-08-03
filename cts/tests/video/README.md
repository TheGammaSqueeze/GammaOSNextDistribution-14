## Video CTS Tests
Current folder comprises of files necessary for:
1. Testing video encoder/decoder performance by running encoding/decoding without displaying the raw data.
2. Testing key operating rate for Hardware video encoder/decoder.

The test vectors used by the test suite is available at [link](https://dl.google.com/android/xts/cts/tests/video/CtsVideoTestCases-1.3.zip) and is downloaded automatically while running tests. Manual installation of these can be done using copy_media.sh script in this directory.

### Commands
```sh
$ atest android.video.cts
$ atest android.video.cts.CodecEncoderPerformanceTest
$ atest android.video.cts.CodecDecoderPerformanceTest
```
