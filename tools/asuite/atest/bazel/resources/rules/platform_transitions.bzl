# Copyright (C) 2021 The Android Open Source Project
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

"""Configuration transitions to change the platform flavor.

These transitions are used to specify the build setting configuration of
test targets required by test runner rule, because in different use cases,
test runner requires the test target in different platform flavor and in
the test target provider rules, the test target will be built based on the
build setting specified by these transitions.

More documentation on how to use transitions at
https://docs.bazel.build/versions/main/skylark/config.html#user-defined-transitions
"""

def _host_transition_impl(settings, attr):
    _ignore = (settings, attr)
    return {
        "//bazel/rules:platform_flavor": "host",
    }

host_transition = transition(
    inputs = [],
    outputs = ["//bazel/rules:platform_flavor"],
    implementation = _host_transition_impl,
)

def _device_transition_impl(settings, attr):
    _ignore = (settings, attr)
    return {
        "//bazel/rules:platform_flavor": "device",
    }

device_transition = transition(
    inputs = [],
    outputs = ["//bazel/rules:platform_flavor"],
    implementation = _device_transition_impl,
)
