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

# Car Services Tests and Test Apps

This directory contains unit tests, instrumentation tests and sample apps.

## Structure

```
android_car_api_test/        - Car API instrumentation tests, they use the real services
CarSecurityPermissionTest/   - Car API permission tests
carservice_test/             - Car API instrumentation tests, mocks VHAL
carservice_unit_test/        - Car services instrumented unit tests
common_utils/                - Shared utility library

# The following test directories are located relative to $ANDROID_BUILD_TOP
cts/hostsidetests/car/                      - Host-driven CTS tests
cts/tests/tests/car/                        - CTS tests (prefer this over hostsidetests)
frameworks/hardware/interfaces/automotive/  - Contains `vts/` folders for tests
hardware/interfaces/automotive/             - Contains `vts/` folders for tests
test/vts-testcase/hal/automotive/           - Host-side VTS tests
```

## Where to add tests

Add necessary tests to all the test suits, and also don't forget to add ATS/CTS/VTS. See
https://source.android.com/compatibility/tests to learn more about CTS/VTS.

Try not to repeat the same test in multiple suits, as it creates unnecessary test maintenance.

Add tests using these priorities:

1. CTS/VTS
2. `CarSecurityPermissionTest`
3. `android_car_api_test` - if CTS doesn't cover
4. `carservice_test` - if CTS doesn't cover
5. `carservice_unit_test`
