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

load("//build/bazel/rules/apis:api_surface.bzl", "ALL_API_SURFACES")

def _impl(rctx):
    rctx.file("WORKSPACE", "")
    synthetic_build_dir = str(rctx.path(Label("//:BUILD")).dirname)
    api_surfaces_dir = synthetic_build_dir + "/build/bazel/api_surfaces"
    for api_surface in ALL_API_SURFACES:
        rctx.symlink(api_surfaces_dir + "/" + api_surface, api_surface)

api_surfaces_repository = repository_rule(
    implementation = _impl,
)
