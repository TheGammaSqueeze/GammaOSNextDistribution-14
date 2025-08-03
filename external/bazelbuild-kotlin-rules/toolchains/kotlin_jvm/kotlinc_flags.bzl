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

"""Static flags for kotlinc."""

load("//:visibility.bzl", "RULES_KOTLIN")

_KT_LANG_VERSION = "1.9"

_SHARED_FLAGS = [
    # We're supplying JDK in bootclasspath explicitly instead
    "-no-jdk",

    # stdlib included in merged_deps
    "-no-stdlib",

    # The bytecode format to emit
    "-jvm-target",
    "11",

    # Emit bytecode with parameter names
    "-java-parameters",

    # Allow default method declarations, akin to what javac emits (b/110378321).
    "-Xjvm-default=all",

    # Trust JSR305 nullness type qualifier nicknames the same as @Nonnull/@Nullable
    # (see https://kotlinlang.org/docs/reference/java-interop.html#jsr-305-support)
    "-Xjsr305=strict",

    # Trust annotations on type arguments, etc.
    # (see https://kotlinlang.org/docs/java-interop.html#annotating-type-arguments-and-type-parameters)
    "-Xtype-enhancement-improvements-strict-mode",

    # TODO: Remove this as the default setting (probably Kotlin 1.7)
    "-Xenhance-type-parameter-types-to-def-not-null=true",

    # Explicitly set language version so we can update compiler separate from language version
    "-language-version",
    _KT_LANG_VERSION,

    # Enable type annotations in the JVM bytecode (b/170647926)
    "-Xemit-jvm-type-annotations",

    # TODO: Temporarily disable 1.5's sam wrapper conversion
    "-Xsam-conversions=class",

    # We don't want people to use experimental APIs, but if they do, we want them to use @OptIn
    "-opt-in=kotlin.RequiresOptIn",

    # Don't complain when using old builds or release candidate builds
    "-Xskip-prerelease-check",

    # Allows a no source files to create an empty jar.
    "-Xallow-no-source-files",

    # TODO: Remove this flag
    "-Xuse-old-innerclasses-logic",

    # TODO: Remove this flag
    "-Xno-source-debug-extension",
]

_CLI_ADDITIONAL_FLAGS = [
    # Silence all warning-level diagnostics
    "-nowarn",
]

def _read_one_define_flags(ctx, name):
    define = ctx.var.get(name, default = None)
    return [f for f in define.split(" ") if f] if define else []

def _read_define_flags(ctx):
    return (
        _read_one_define_flags(ctx, "extra_kt_jvm_opts")
    )

kotlinc_flags = struct(
    # go/keep-sorted start
    CLI_FLAGS = _SHARED_FLAGS + _CLI_ADDITIONAL_FLAGS,
    IDE_FLAGS = _SHARED_FLAGS,
    KT_LANG_VERSION = _KT_LANG_VERSION,
    read_define_flags = _read_define_flags,
    # go/keep-sorted end
)
