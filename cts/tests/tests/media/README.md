## Media CTS Tests
The tests are organized into following testcases

| TestCase                         | Description                                                               |
|----------------------------------|---------------------------------------------------------------------------|
| CtsMediaAudioTestCases           | Audio related tests                                                       |
| CtsMediaBetterTogetherTestCases  | BetterTogether related test cases (MediaSession/MediaRouter/MediaBrowser) |
| CtsMediaCodecTestCases           | MediaCodec related tests, for combinations decode/encode                  |
| CtsMediaDecoderTestCases         | MediaCodec related tests, for decoding                                    |
| CtsMediaEncoderTestCases         | MediaCodec related tests, for encoding                                    |
| CtsMediaDrmFrameworkTestCases    | Media DRM related tests                                                   |
| CtsMediaExtractorTestCases       | MediaExtractor related tests                                              |
| CtsMediaMuxerTestCases           | MediaMuxer related tests                                                  |
| CtsMediaPlayerTestCases          | MediaPlayer related tests                                                 |
| CtsMediaProjectionTestCases      | MediaProjection related tests                                             |
| CtsMediaProjectionSDK33TestCases | MediaProjection related tests, detecting consent re-use on SDK 33         |
| CtsMediaProjectionSDK34TestCases | MediaProjection related tests, detecting consent re-use on SDK 34         |
| CtsMediaRecorderTestCases        | MediaRecorder related tests                                               |
| CtsMediaMiscTestCases            | All other media tests                                                     |


## Test files used in the tests
The test files used by the test suite are available on Google cloud
and these are downloaded automatically while running tests.

Link to the zip files can be found in DynamicConfig.xml in each of the subdirectories
listed as "media_files_url"

Manual installation of these can be done using copy_all_media.sh script in this directory.

Each of the sub-folders has a copy_media.sh that will download and install the assets
relevant to that test.

The copy_all_media.sh in this folder will invoke all of those subsidiary copy_media.sh scripts
so that all assets for all of the media tests are on the device.

## Troubleshooting

#### Too slow / no progress in the first run
Zip containing the media files are quite large and first execution of the test
(after each time the test is updated to download a different zip file) takes
considerable amount of time (30 minutes or more) to download and push the media files.

#### File not found in /sdcard/test/CtsMedia* failures
If the device contains an incomplete directory (from previous incomplete execution of the tests,
Ctrl-C during earlier tests etc),
the test framework doesn't push the remaining files to device.
This leads to tests failing with file not found errors.

Solution in such cases is to remove the /sdcard/test/CtsMedia* folder on
the device and executing the atest command again, or running ./tests/tests/media/copy_media.sh to
manually download and copy the test files to the device before running the tests.
