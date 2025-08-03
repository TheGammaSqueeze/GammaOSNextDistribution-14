# Copyright (C) 2021 The Android Open Source Project
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

def _impl(rctx):
    rctx.file("WORKSPACE", "")
    build_dir = str(rctx.path(Label("//:BUILD")).dirname.dirname)
    soong_injection_dir = build_dir + "/soong_injection"
    rctx.symlink(soong_injection_dir + "/allowlists", "allowlists")
    rctx.symlink(soong_injection_dir + "/android", "android")
    rctx.symlink(soong_injection_dir + "/mixed_builds", "mixed_builds")
    rctx.symlink(soong_injection_dir + "/cc_toolchain", "cc_toolchain")
    rctx.symlink(soong_injection_dir + "/java_toolchain", "java_toolchain")
    rctx.symlink(soong_injection_dir + "/rust_toolchain", "rust_toolchain")
    rctx.symlink(soong_injection_dir + "/apex_toolchain", "apex_toolchain")
    rctx.symlink(soong_injection_dir + "/product_config", "product_config")
    rctx.symlink(soong_injection_dir + "/product_config_platforms", "product_config_platforms")
    rctx.symlink(soong_injection_dir + "/api_levels", "api_levels")
    rctx.symlink(soong_injection_dir + "/metrics", "metrics")

soong_injection_repository = repository_rule(
    implementation = _impl,
)
