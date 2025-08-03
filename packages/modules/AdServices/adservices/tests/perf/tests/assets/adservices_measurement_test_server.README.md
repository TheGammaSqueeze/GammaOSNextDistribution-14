Generate CRT
```shell
openssl req -new -x509 -nodes -sha1 -days 3650 \
    -subj "/CN=AdServicesScenarioTests" \
    -addext "subjectAltName=DNS:rb-measurement.com,IP:127.0.0.1" \
    -out /tmp/adservices_rollback_test_server.crt \
    -keyout /tmp/adservices_rollback_test_server.key
```

Generate P12 with passcode: `adservices`
```shell
openssl pkcs12 -export -keypbe pbeWithSHA1And3-KeyTripleDES-CBC \
-certpbe pbeWithSHA1And3-KeyTripleDES-CBC  -macalg sha1 \
-in /tmp/adservices_measurement_test_server.crt -inkey /tmp/adservices_measurement_test_server.key \
-out adservices_measurement_test_server.p12
```

Generate android certificate file:
```
cp /tmp/adservices_measurement_test_server.crt \
$(echo $(openssl x509 -in /tmp/adservices_measurement_test_server.crt --issuer_hash_old -noout)".0")
```

Make, install, and run test locally:
```
lunch <target>
make AdServicesScenarioTests
adb install -r -g out/target/product/<target>/testcases/AdServicesScenarioTests/arm64/AdServicesScenarioTests.apk
adb shell am instrument -w -e class android.adservices.test.scenario.adservices.MeasurementRegisterCalls android.platform.test.scenario/androidx.test.runner.AndroidJUnitRunner
```