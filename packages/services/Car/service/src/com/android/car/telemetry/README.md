<!--
  Copyright (C) 2021 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License
  -->

# CarTelemetryService

Source code for AAOS OEM Telemetry solution.


## Enabling CarTelemetryService

CarTelemetryService can be enabled with

```
adb shell cmd car_service enable-feature car_telemetry_service
```

## Car Shell Command

Run the commands from `$ANDROID_BUILD_TOP`.

1. Create a MetricsConfig text proto - `sample_wifi_netstats.textproto`

```
name: "sample_wifi_netstats"
version: 1
subscribers {
  handler: "onWifiStats"
  publisher: {
    connectivity: {
        transport: TRANSPORT_WIFI
        oem_type: OEM_NONE
    }
  }
  priority: 0
}
script:
  'function onWifiStats(data, state)\n'
  '    on_script_finished(data)\n'
  'end\n'
```

2. Generate MetricsConfig binary proto

```
./out/host/linux-x86/bin/aprotoc \
  --encode=android.car.telemetry.MetricsConfig \
  packages/services/Car/car-lib/src/android/car/telemetry/telemetry.proto \
  < sample_wifi_netstats.textproto > sample_wifi_netstats.binproto
```

3. Add the config to CarTelemetryService

```
adb shell cmd car_service telemetry add sample_wifi_netstats < sample_wifi_netstats.binproto
```

4. Get results

```
adb shell cmd car_service telemetry get-result sample_wifi_netstats --result_count 10000 --timeout 20 --print-results
```
where result_count is the number of reports we expect to receive in this call,
timeout is how many seconds should we wait for the results to be returned,
and --print-results is an optional flag whether we want every result to be printed
on the screen.
