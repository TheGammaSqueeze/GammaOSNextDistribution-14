## Media Performance Class CTS Tests
Current folder comprises of files necessary for testing media performance class.

The test vectors used by the test suite is available at [link](https://dl.google.com/android/xts/cts/tests/mediapc/CtsMediaPerformanceClassTestCases-3.0.zip) and is downloaded automatically while running tests. Manual installation of these can be done using copy_media.sh script in this directory.

### Commands
#### To run all tests in CtsMediaPerformanceClassTestCases
```sh
$ atest CtsMediaPerformanceClassTestCases
```
#### To run a subset of tests in CtsMediaPerformanceClassTestCases
```sh
$ atest CtsMediaPerformanceClassTestCases:android.mediapc.cts.FrameDropTest
```
#### To run all tests in CtsMediaPerformanceClassTestCases by overriding Build.VERSION.MEDIA_PERFORMANCE_CLASS
In some cases it might be useful to override Build.VERSION.MEDIA_PERFORMANCE_CLASS and run the tests.
For eg: when the device doesn't advertise Build.VERSION.MEDIA_PERFORMANCE_CLASS, running the tests by overriding
this will help in determining the which performance class requirements are met by the device.
Following runs the tests by overriding Build.VERSION.MEDIA_PERFORMANCE_CLASS as S.
```sh
$ atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:media-performance-class:=31
```

### Features
All tests accepts attributes that offer selective run of tests.


#### Select codecs by media type
To select codecs by media type, *media-type-prefix* can be passed to media codec tests to select one or more codecs that start with a given prefix.

Example: To limit the tests to run for media types whose names start with video/avc

```sh
atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:media-type-prefix:=video/avc
```
#### Select codecs using regular expressions
To select codecs by applying regular expressions, *codec-filter* can be passed to media codec tests to select one or more codecs that match with a specified regular expression pattern.

Example: To limit the tests to run for c2.android.avc.decoder and c2.android.hevc.encoder

```sh
atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:codec-filter:="c2\.android\.avc\.decoder\|c2\.android\.hevc\.encoder"
```

#### Select codecs by name
To select codecs by name, *codec-prefix* can be passed to media codec tests to select one or more codecs that start with a given prefix.

Example: To limit the tests to run for codecs whose names start with c2.android.

```sh
atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:codec-prefix:=c2.android.
```

Example: To limit the tests to run for c2.android.hevc.decoder

```sh
atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:codec-prefix:=c2.android.hevc.decoder
```