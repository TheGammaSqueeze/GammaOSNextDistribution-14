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

# Bazel still resolves toolchains even if targets are marked incompatible with
# target_compatible_with, which can cause failures due to the toolchain not being found.
# To work around this issue, some rules make the toolchain optional, but then in their
# impl functions assert that it exists. This helper function can do that assertion.
def verify_toolchain_exists(ctx, toolchain):
    if not ctx.toolchains[toolchain]:
        # Mimic the bazel failure if this toolchain was mandatory
        fail("While resolving toolchains for target %s: No matching toolchains found for types %s.\nTo debug, rerun with --toolchain_resolution_debug='%s'" %
             (str(ctx.label), toolchain, toolchain))
