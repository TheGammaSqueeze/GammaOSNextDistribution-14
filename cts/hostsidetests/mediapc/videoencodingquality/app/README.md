## Video Encoding CTS Tests Apk
These tests are not run directly. But these are run as part of the host side test CtsVideoEncodingQualityHostTestCases. The host side test sends an input clip and encoding configuration parameters via json file to this apk. This apk parses the information sent and performs encoding. The encoded output is stored to disk. Host side test pulls this for further analysis.

### Commands
```sh
$ atest CtsVideoEncodingTestCases -- --module-arg CtsVideoEncodingTestCases:instrumentation-arg:conf-json:=test.json
```
