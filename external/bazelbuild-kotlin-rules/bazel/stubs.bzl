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

"""Stubs"""

load("//:visibility.bzl", "RULES_KOTLIN")

visibility(RULES_KOTLIN)

def _empty_fn(*_args, **_kwargs):
    pass

register_extension_info = _empty_fn

is_forbidden_dep = _empty_fn

is_exempt_dep = _empty_fn

is_android_lint_exempt = _empty_fn

DEFAULT_BUILTIN_PROCESSORS = [
    "com.google.android.apps.play.store.plugins.injectionentrypoint.InjectionEntryPointProcessor",
    "com.google.android.apps.play.store.plugins.interfaceaggregator.InterfaceAggregationProcessor",
    "com.google.auto.factory.processor.AutoFactoryProcessor",
    "dagger.android.processor.AndroidProcessor",
    "dagger.internal.codegen.ComponentProcessor",
]

BASE_JVMOPTS = []

def select_java_language_level(**_kwargs):
    return "11"

registry_checks_for_package = _empty_fn

LINT_REGISTRY = None  # Only ever passed to registry_checks_for_package

def _run_lint_on_library(ctx, output, *_args, **_kwargs):
    ctx.actions.write(output, "Android Lint Disabled")
    return output

_AndroidLintRulesetInfo = provider()

lint_actions = struct(
    AndroidLintRulesetInfo = _AndroidLintRulesetInfo,
    run_lint_on_library = _run_lint_on_library,
    get_android_lint_baseline_file = _empty_fn,
)

def check_compiler_opt_allowlist(_label):
    pass

def jspecify_flags(_ctx):
    # Trust JSpecify nullness annotations
    # (see https://kotlinlang.org/docs/whatsnew1520.html#support-for-jspecify-nullness-annotations)
    return ["-Xjspecify-annotations=strict"]

integration_test_filegroup = _empty_fn
