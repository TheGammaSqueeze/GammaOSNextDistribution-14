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

# AAOS

Source code for [Android Automotive OS](https://source.android.com/devices/automotive).

## Structure

```
car_product/           - AAOS product
car-builtin-lib/       - A helper library for CarService to access hidden
                         framework APIs
car-lib/               - Car API
car-lib-module/        - Car API module
cpp/                   - Native services
experimental/          - Experimental Car API and services
packages/              - Apps and services for cars
service/               - Car service module
service-builint        - Platform builtin component that runs CarService module
tests/                 - Tests and sample apps
tools/                 - Helper scripts
```

## C++

Native (C++) code format is required to be compatible with .clang-format file. The formatter is
already integrated to `repo` tool. To run manually, use:

```
git clang-format --style=file --extension='h,cpp,cc' HEAD~
```

Note that clang-format is *not* desirable for Android java files. Therefore
the  command line above is limited to specific extensions.

## Debugging CarService

Dumpsys and car shell can be useful when debugging CarService integration issues.

### dumpsys

```
adb shell dumpsys car_service # to dump all car service information
adb shell dumpsys car_service --services [service name] # to dump a specific service information
adb shell dumpsys car_service --list # get list of available services
```

Dumpsys for CarService includes the following (more information is availble in dumpsys, below are just highlights):
- Enabled features
- Current power policy and list of registered power policies
- Power state of componens of power policy
- Silent mode status
- Garage mode status
- I/O stats
- List of available vehicle properties

### car shell

```
adb shell cmd car_service
```

CarService supports commands via car shell:\
(list is not complete, run adb shell cmd car_service -h for more details)
- Injection of vhal events
- Toggling garage mode on/off
- Toggling of suspend/hibernation/resume
- Injection of input events
- User managemnet/switching
- Power policy control/manipulation

### Helpful command for Garage mode

Start Garage mode
```
adb shell cmd car_service garage-mode on
```

Finish Garage mode
```
adb shell cmd car_service garage-mode on
```

Get Garage mode status
```
adb shell cmd car_service garage-mode query
```

Change Garage mode max duration (only eng and debug builds)
```
adb shell setprop android.car.garagemodeduration <seconds>
```