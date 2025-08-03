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

def _kind_api(kind, api_level):
    return "config_setting_android_%s_%s" % (kind, api_level)

def _kind_api_pre_java_9(kind, api_level):
    return _kind_api(kind, api_level) + "_pre_java_9"

def _kind_api_post_java_9(kind, api_level):
    return _kind_api(kind, api_level) + "_post_java_9"

_CONFIG_SETTING_SDK_NONE = "config_setting_sdk_none"
_CONFIG_SETTING_PRE_JAVA_9 = "config_setting_pre_java_9"
_CONFIG_SETTING_POST_JAVA_9 = "config_setting_post_java_9"

config_setting_names = struct(
    SDK_NONE = _CONFIG_SETTING_SDK_NONE,
    PRE_JAVA_9 = _CONFIG_SETTING_PRE_JAVA_9,
    POST_JAVA_9 = _CONFIG_SETTING_POST_JAVA_9,
    kind_api = _kind_api,
    kind_api_pre_java_9 = _kind_api_pre_java_9,
    kind_api_post_java_9 = _kind_api_post_java_9,
)
