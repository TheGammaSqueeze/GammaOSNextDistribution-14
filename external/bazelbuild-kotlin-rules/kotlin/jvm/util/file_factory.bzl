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

"""FileFactory"""

load("//:visibility.bzl", "RULES_KOTLIN")

visibility(RULES_KOTLIN)

def FileFactory(ctx, base):
    """Creates files with names derived from some base file or prefix

    Including the name of a rule is not always enough to guarantee unique filenames. For example,
    helper functions that declare their own output files may be called multiple times in the same
    rule impl.

    Args:
        ctx: ctx
        base: [File|string] The file to derive other filenames from, or an exact base prefix

    Returns:
        FileFactory
    """

    if type(base) != "string":
        base = _scrub_base_file(ctx, base)

    def declare_directory(suffix):
        return ctx.actions.declare_directory(base + suffix)

    def declare_file(suffix):
        return ctx.actions.declare_file(base + suffix)

    def derive(suffix):
        return FileFactory(ctx, base + suffix)

    return struct(
        base_as_path = ctx.bin_dir.path + "/" + ctx.label.package + "/" + base,
        declare_directory = declare_directory,
        declare_file = declare_file,
        derive = derive,
    )

def _scrub_base_file(ctx, file):
    if not file.extension:
        fail("Base file must have an extension: was %s" % (file.path))
    if file.owner.package != ctx.label.package:
        fail("Base file must be from ctx package: was %s expected %s" % (file.owner.package, ctx.label.package))

    return file.short_path.removeprefix(ctx.label.package + "/").rsplit(".", 1)[0]
