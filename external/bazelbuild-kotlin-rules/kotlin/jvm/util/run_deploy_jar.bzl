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

"""kt_run_deploy_jar"""

# go/keep-sorted start
load("//:visibility.bzl", "RULES_KOTLIN")
load("//bazel:stubs.bzl", "BASE_JVMOPTS")
# go/keep-sorted end

visibility(RULES_KOTLIN)

def kt_run_deploy_jar(
        ctx,
        java_runtime,
        deploy_jar,
        inputs,
        args = [],
        deploy_jsa = None,
        **kwargs):
    """An analogue to ctx.actions.run for _deploy.jar executables."""

    java_args = ctx.actions.args()
    java_inputs = []
    if deploy_jsa:
        java_args.add("-Xshare:auto")
        java_args.add(deploy_jsa, format = "-XX:SharedArchiveFile=%s")
        java_args.add("-XX:-VerifySharedSpaces")
        java_args.add("-XX:-ValidateSharedClassPaths")
        java_inputs.append(deploy_jsa)
    java_args.add("-jar", deploy_jar)
    java_inputs.append(deploy_jar)

    java_depset = depset(direct = java_inputs, transitive = [java_runtime[DefaultInfo].files])
    if type(inputs) == "depset":
        all_inputs = depset(transitive = [java_depset, inputs])
    else:
        all_inputs = depset(direct = inputs, transitive = [java_depset])

    ctx.actions.run(
        executable = str(java_runtime[java_common.JavaRuntimeInfo].java_executable_exec_path),
        inputs = all_inputs,
        arguments = BASE_JVMOPTS + [java_args] + args,
        **kwargs
    )
