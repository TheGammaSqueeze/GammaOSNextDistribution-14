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

# Automotive Native Services (daemons)

WARNING: Please don't put new vendor accessible HIDL/AIDL files here. Please
         follow https://source.android.com/devices/architecture/hidl/interfaces
         to find the best directory for new interfaces.
         If your AIDL is not hardware backed, please put them under
         //frameworks/hardware/interfaces/automotive/.
         The existing vendor accessible AIDLs are legacy, and cannot be easily
         move out without changing package names.
