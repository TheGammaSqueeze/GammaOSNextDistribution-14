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

"""Provides dependency information required by Tradefed test rules.

This provider encapsulates information about dependencies that is required for
setting up the execution environment. Aspects are responsible for converting the
actual dependency's provider to an instance of this structure. For example, a
dependency with a `JavaInfo` provider defines several fields for the jars
required at runtime which is different from what `SoongPrebuiltInfo` exports.
This essentially shields the test rule's implementation from the different
provider types.
"""

TradefedTestDependencyInfo = provider(
    doc = "Info required by Tradefed rules to run tests",
    fields = {
        "runtime_jars": "Jars required on the runtime classpath",
        "runtime_shared_libraries": "Shared libraries that are required at runtime",
        "transitive_test_files": "Files of test modules",
    },
)
