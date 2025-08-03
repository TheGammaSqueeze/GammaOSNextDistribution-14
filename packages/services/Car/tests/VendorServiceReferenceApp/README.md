<!--
  Copyright (C) 2023 The Android Open Source Project

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

## Reference implementation for Vendor Service Controller's service

This project has the reference implementation for a service which can be used for vendor service
controller. Vendor service controller reads different service components from RRO configuration
(config_earlyStartupServices) and these
services are started/bound by Vendor service controller. Vendor service controller is part of
CarService. The config needs to be defined in the following format:

```
 <item>com.bar.foo/.Service#bind={bind|start|startForeground},
 user={all|system|foreground|visible|backgroundVisible},
 trigger={asap|resume|userUnlocked|userPostUnlocked}</item>

 bind: bind - start service with Context#bindService
       start - start service with Context#startService
       startForeground - start service with Context#startForegroundService
       If service was bound it will be restarted unless it is constantly crashing.
       The default value is 'start'
 user: all - the service will be bound/started for system and all visible users
       system - the service will be started/bound only for system user (u0)
       foreground - the service will be bound/started only for foreground users
       visible - the service will be bound/started only for visible users (as defined by
                 `UserManager#isUserVisible()`).
       backgroundVisible - the service will be bound/started only for background users that
                           are visible.
       The default value is 'all'
 trigger: indicates when the service needs to be started/bound
       asap - the service might be bound when user is not fully loaded, be careful with
              this value, the service also needs to have directBootAware flag set to true
       resume - start service when the device resumes from suspend (suspend-to-RAM, or
                suspend-to-disk).
       userUnlocked - start service when user unlocked the device
       userPostUnlocked - start service later after user unlocked. This is used when the
                          service is not urgent and can wait to start.
       The default value is 'userUnlocked'
 maxRetries: the maximum number of attempts to rebind/restart a disconnected service.
       Retries start with 4 second initial delay, being doubled after each failed attempt.
       The default value is 6.

 If the service bound/started for foreground user it will be unbound/stopped when user
 is no longer foreground.
```

Service bound to CarService are special. As CarService is a persistent process, these services
bound with CarService would be running as
PROCESS_STATE_IMPORTANT_FOREGROUND, It means they would have higher priority and would not be killed
due to resource constraints. They can be killed based on different user scope. For example, services
running for visible user only will be killed when user is no longer visible. By default, these
services don't have access to things like location, camera, microphone or network.

## Remaining work
b/308208710

b/308208631

b/306704239


