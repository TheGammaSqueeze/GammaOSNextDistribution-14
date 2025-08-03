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

"""Test on *_deploy.jar freshness"""

load("//:visibility.bzl", "RULES_KOTLIN")

visibility(RULES_KOTLIN)

def _deploy_jar_freshness_golden_test_impl(ctx):
    test_command = """
      if ! cmp $1 $2 ; then
          echo "$1 needs to be rebuilt"
          echo "exit 1" > $3
          exit 0
      fi

      # Always passes
      echo "#!/bin/bash" > $3
    """

    dummy_test_script = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.run_shell(
        inputs = [ctx.file.current_jar, ctx.file.newly_built_jar],
        outputs = [dummy_test_script],
        arguments = [
            ctx.file.current_jar.path,
            ctx.file.newly_built_jar.path,
            dummy_test_script.path,
        ],
        command = test_command,
    )

    return [DefaultInfo(executable = dummy_test_script)]

deploy_jar_freshness_golden_test = rule(
    implementation = _deploy_jar_freshness_golden_test_impl,
    attrs = dict(
        newly_built_jar = attr.label(
            doc = "Newly built target deploy.jar",
            mandatory = True,
            allow_single_file = [".jar"],
        ),
        current_jar = attr.label(
            doc = "Prebuilt jar to verify",
            allow_single_file = [".jar"],
            mandatory = True,
        ),
    ),
    test = True,
)
