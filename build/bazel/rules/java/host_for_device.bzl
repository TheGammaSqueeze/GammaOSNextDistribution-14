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

visibility([
    "//external/guava/...",
    "//external/kotlinx.coroutines/...",
    "//external/robolectric-shadows/...",
    "//external/robolectric/...",
])

def _host_for_device_impl(ctx):
    return [java_common.merge([d[JavaInfo] for d in ctx.attr.exports])]

java_host_for_device = rule(
    doc = """Rule to provide java libraries built with a host classpath in a device configuration.
This is rarely necessary and restricted to a few allowed projects.
""",
    implementation = _host_for_device_impl,
    attrs = {
        # This attribute must have a specific name to let the DexArchiveAspect propagate
        # through it.
        "exports": attr.label_list(
            cfg = "exec",
            providers = [JavaInfo],
            doc = "List of targets whose contents will be visible to targets that depend on this target.",
        ),
    },
    provides = [JavaInfo],
)
