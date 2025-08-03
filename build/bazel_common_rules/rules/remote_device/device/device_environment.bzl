# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""A provider to create a Cuttlefish device environment."""

DeviceEnvironment = provider(
    "Represents the environment a test will run under. Concretely this is an " +
    "executable and any runfiles required to trigger execution in the " +
    "environment.",
    fields = {
        "runner": "depset of executable to to setup test environment and execute test.",
        "data": "runfiles of all needed artifacts in the executable.",
    },
)
