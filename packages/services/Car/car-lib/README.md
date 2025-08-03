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

# Android Automotive OS API

NOTE: car-lib directory will be replaced with car-lib-module.

This directory contains Car services API. All the vendor or app code should use the API defined
here. The APIs also released to the final Android Automotive OS SDK as part of the Android SDK. The
actual services implementation are located under `packages/services/Car/service`.

Some vendor services use AIDL/HIDL interfaces located in `hardware/interfaces/automotive/` and
`frameworks/hardware/interfaces/automotive/`, see
https://source.android.com/devices/architecture/hidl/interfaces to learn more.

Car API documentation is available at https://developer.android.com/reference/android/car/packages.
See https://source.android.com/devices/automotive to learn how to use AAOS SDK.

## Structure

```
api/                       - Generated API signature
src/
  android/car/             - All the available Car API
    Car.java               - Top level Car API
    *.aidl                 - Internal AIDL declarations
  com/android/car/internal - Internal helper classes used in Car API
```

## Adding a New API

1. Declare an AIDL under `src/android/car/...`
2. Create a manager for the new API
   * Handle binder exceptions
   * If adding callbacks, create a callback interface for users and define Executor argument
     to allow users provide their own executors to run callbacks in
   * The new manager class should be thread-safe, use locks when necessary
3. Define the manager in Car.java
4. Add service implementation under `p/s/Car/service/`
   * Add permission checks to make sure only the apps with permissions can use it
   * Implement `dump()` method for access the state of the service using
     `adb shell dumpsys car_service --services <CLASS_NAME>`
5. Optionally create a fake manager for testing under src/android/car/testapi/ and add it to
   FakeCar.java
6. Optionally create an item in `EmbeddedKitchenSinkApp` for exercising the API manually
7. Optionally create an item in `p/s/Car/service/.../CarShellCommand.java` for
   `adb shell cmd car_service`, it's useful for debugging
8. Add API tests and unit tests under `p/s/Car/tests/`, see the `tests/README.md` to learn
   where to add the tests
9. Generate new API signature:
    * `m android.car-stubs-docs-update-current-api`
    * `m android.car-system-stubs-docs-update-current-api`
    * `m android.car-test-stubs-docs-update-current-api`
