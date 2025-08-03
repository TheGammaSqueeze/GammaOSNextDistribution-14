# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""unittests"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin:compiler_plugin.bzl", "KtCompilerPluginInfo")
load("//kotlin/common/testing:unittest_suites.bzl", "kt_unittest_suites")

visibility(RULES_KOTLIN)

unittests = kt_unittest_suites.create()

def _cannot_construct_provider(ctx):
    KtCompilerPluginInfo(
        plugin_id = "fake",
        jar = ctx.actions.declare_file("fake.jar"),
        args = [],
    )

unittests.expect_fail(_cannot_construct_provider, "Error in fail")

_test, _fail = unittests.close()  # @unused
