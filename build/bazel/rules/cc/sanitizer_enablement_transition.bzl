# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load(":cc_constants.bzl", "transition_constants")

# This logic checks for the enablement of sanitizers to update the relevant
# config_setting for the purpose of controlling the addition of sanitizer
# blocklists.
# TODO: b/294868620 - This whole file can be removed when completing the bug
def apply_sanitizer_enablement_transition(features):
    if "android_cfi" in features and "-android_cfi" not in features:
        return True
    for feature in features:
        if feature.startswith("ubsan_"):
            return True
    return False

def _drop_sanitizer_enablement_transition_impl(_, __):
    return {
        transition_constants.sanitizers_enabled_key: False,
    }

drop_sanitizer_enablement_transition = transition(
    implementation = _drop_sanitizer_enablement_transition_impl,
    inputs = [],
    outputs = [transition_constants.sanitizers_enabled_key],
)
