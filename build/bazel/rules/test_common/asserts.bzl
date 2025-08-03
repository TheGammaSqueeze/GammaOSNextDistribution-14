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

load("@bazel_skylib//lib:unittest.bzl", skylib_asserts = "asserts")

def _list_equals(env, l1, l2, msg = None):
    skylib_asserts.equals(
        env,
        len(l1),
        len(l2),
        msg,
    )
    for i in range(len(l1)):
        skylib_asserts.equals(
            env,
            l1[i],
            l2[i],
            msg,
        )

asserts = struct(
    list_equals = _list_equals,
)
