# Copyright (C) 2022 The Android Open Source Project
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

def is_env_true(value):
    """return the truthiness of the value of an environment variable

    Args:
        value (str): the environment variable value to test
    Returns:
        if the value is truthy
    """
    return value != None and value.lower() in ["1", "y", "yes", "on", "true"]
