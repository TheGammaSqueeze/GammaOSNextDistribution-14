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

"""A rule for declaring and passing kotlinc opts in a restricted way.

It is a goal for rules_kotlin that Kotlin libraries use consistent default compiler
options across as much of the repo as possible. Doing so makes Kotlin easier to
maintain at scale.

If an exception needs to be made for some library, `kt_compiler_opt` can be used to
declare a set of additional options with restricted visibility. That target can then
be passed to the `custom_kotlincopts` attribute. The set of directories that allow
`kt_compiler_opt` targets is also limited, to prevent misuse.
"""

load("//bazel:stubs.bzl", "check_compiler_opt_allowlist")
load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")

# Intentionally private to prevent misuse.
_KtCompilerOptInfo = provider(
    doc = "A restricted set of kotlinc opts",
    fields = {"opts": "list[string]"},
)

_ALLOWED_VISIBILITY_NAMES = [
    "__pkg__",
    "__subpackages__",
]

def _kt_compiler_opt_impl(ctx):
    check_compiler_opt_allowlist(ctx.label)

    visibility_groups = [v for v in ctx.attr.visibility if not v.name in _ALLOWED_VISIBILITY_NAMES]
    if len(visibility_groups) > 0:
        fail("Using package groups for visibility may expose custom options too broadly: " + str(visibility_groups))

    return [_KtCompilerOptInfo(opts = ctx.attr.opts)]

kt_compiler_opt = rule(
    implementation = _kt_compiler_opt_impl,
    attrs = {
        "opts": attr.string_list(
            doc = "The opt(s) this target represents.",
            mandatory = True,
        ),
    },
)

def kotlincopts_attrs():
    return dict(
        custom_kotlincopts = attr.label_list(
            doc = "kt_compiler_opt targets to pass to Kotlin compiler. Most users should not need this attr.",
            providers = [[_KtCompilerOptInfo]],
            cfg = "exec",
        ),
    )

def merge_kotlincopts(ctx):
    """Returns the complete list of opts behind custom_kotlincopts

    Args:
      ctx: A ctx matching kotlincopts_attrs

    Returns:
      The list of opts
    """
    custom_opts = []
    for target in ctx.attr.custom_kotlincopts:
        custom_opts.extend(target[_KtCompilerOptInfo].opts)

    return custom_opts
