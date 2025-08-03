"""
Copyright 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("@bazel_skylib//lib:paths.bzl", "paths")

def _remove_extension(p):
    """Removes the extension from the path `p`.

    Leading periods on the basename are ignored, so
    `_strip_extension(".bashrc")` returns `".bashrc"`.

    Args:
      p: The path to modify.

    Returns:
      The path with the extension removed.
    """

    # paths.split_extension() does all of the work.
    return paths.split_extension(p)[0]

# Expands an output path template for the given context and input file.
def _expand_out_path_template(ctx, src_file):
    # Each src_file has a short_path that looks like:
    #
    #   <source-package-path>/<source-package-rel-path>/<base.ext>
    #
    # For some expansions, we want to strip of the source package path, and
    # only use the rest for output file path in the expansion.
    #
    # There is also an option during expansion to just use the <base> or
    # <base.ext> portion of the input path.
    #
    # This means there can be collisions if input files are taken from
    # `filegroups` defined in different packages, if they happen to use
    # the same relative path for that package.
    #
    # These conflcits are left to the user of this `gensrcs` rule to resolve for
    # their use case, as at least Bazel will raise an error when they occur.

    # Try to obtain the path to the package that defines `src_file`. It may or
    # may not be defined by the same package this `gensrcs` rule is in.
    # The `owner` label `package` attribute value is the closest we can get
    # to that path, but it may not be correct in all cases, such as if the
    # source path is itself for a generated file, where the generated file is
    # under a build artifact path, and not in the source tree.
    pkg_dirname = paths.dirname(src_file.short_path)
    rel_dirname = pkg_dirname
    if (src_file.is_source and src_file.owner and
        src_file.short_path.startswith(src_file.owner.package + "/")):
        rel_dirname = paths.dirname(paths.relativize(
            src_file.short_path,
            src_file.owner.package,
        ))

    base_inc_ext = src_file.basename
    base_exc_ext = _remove_extension(base_inc_ext)
    rel_path_base_inc_ext = paths.join(rel_dirname, base_inc_ext)
    rel_path_base_exc_ext = paths.join(rel_dirname, base_exc_ext)
    pkg_path_base_inc_ext = paths.join(pkg_dirname, base_inc_ext)
    pkg_path_base_exc_ext = paths.join(pkg_dirname, base_exc_ext)

    # Expand the output template
    return ctx.attr.output \
        .replace("$(SRC:PKG/PATH/BASE.EXT)", pkg_path_base_inc_ext) \
        .replace("$(SRC:PKG/PATH/BASE)", pkg_path_base_exc_ext) \
        .replace("$(SRC:PATH/BASE.EXT)", rel_path_base_inc_ext) \
        .replace("$(SRC:PATH/BASE)", rel_path_base_exc_ext) \
        .replace("$(SRC:BASE.EXT)", base_inc_ext) \
        .replace("$(SRC:BASE)", base_exc_ext) \
        .replace("$(SRC)", rel_path_base_inc_ext)

# A rule to generate files based on provided srcs and tools.
def _gensrcs_impl(ctx):
    # The next two assignments can be created by using ctx.resolve_command.
    # TODO: Switch to using ctx.resolve_command when it is out of
    # experimental.
    command = ctx.expand_location(ctx.attr.cmd)
    tools = [
        tool[DefaultInfo].files_to_run
        for tool in ctx.attr.tools
    ]

    # Expand the shell command by substituting $(RULEDIR), which will be
    # the same for any source file.
    command = command.replace(
        "$(RULEDIR)",
        paths.join(
            ctx.var["GENDIR"],
            ctx.label.package,
        ),
    )

    src_files = ctx.files.srcs
    out_files = []
    for src_file in src_files:
        # Expand the output path template for this source file.
        out_file_path = _expand_out_path_template(ctx, src_file)

        # out_file is at output_file_path that is relative to
        # <GENDIR>/<gensrc-package-dir>, hence, the fullpath to out_file is
        # <GENDIR>/<gensrc-package-dir>/<out_file_path>
        out_file = ctx.actions.declare_file(out_file_path)

        # Expand the command template for this source file by performing
        # substitution for $(SRC) and $(OUT).
        shell_command = command \
            .replace("$(SRC)", src_file.path) \
            .replace("$(OUT)", out_file.path)

        # Run the shell comand to generate the output from the input.
        ctx.actions.run_shell(
            tools = tools,
            outputs = [out_file],
            inputs = [src_file],
            command = shell_command,
            progress_message = "Generating %s from %s" % (
                out_file.path,
                src_file.path,
            ),
        )
        out_files.append(out_file)

    return [DefaultInfo(
        files = depset(out_files),
    )]

gensrcs = rule(
    implementation = _gensrcs_impl,
    doc = "This rule generates files, where each of the `srcs` files is " +
          "passed to `cmd` to generate an `output`.",
    attrs = {
        "srcs": attr.label_list(
            # We allow srcs to directly reference files, instead of only
            # allowing references to other rules such as filegroups.
            allow_files = True,
            # An empty srcs is likely an mistake.
            allow_empty = False,
            # srcs must be explicitly specified.
            mandatory = True,
            doc = "A list of source files to process",
        ),
        "output": attr.string(
            # By default we generate an output filename based on the input
            # filename (no extension).
            default = "$(SRC)",
            doc = "An output path template which is expanded to generate " +
                  "the output path given an source file. Portions " +
                  "of the source filename can be included in the expansion " +
                  "with one of: $(SRC:BASE), $(SRC:BASE.EXT), " +
                  "$(SRC:PATH/BASE), $(SRC:PATH/BASE), " +
                  "$(SRC:PKG/PATH/BASE), or $(SRC:PKG/PATH/BASE.ext). For " +
                  "example, specifying `output = " +
                  "\"includes/lib/$(SRC:BASE).h\"` would mean the input " +
                  "file `some_path/to/a.txt` generates `includes/lib/a.h`, " +
                  "while instead specifying `output = " +
                  "\"includes/lib/$(SRC:PATH/BASE.EXT).h\"` would expand " +
                  "to `includes/lib/some_path/to/a.txt.h`.",
        ),
        "cmd": attr.string(
            # cmd must be explicitly specified.
            mandatory = True,
            doc = "The command to run. Subject to $(location) expansion. " +
                  "$(SRC) represents each input file provided in `srcs` " +
                  "while $(OUT) reprensents corresponding output file " +
                  "generated by the rule. $(RULEDIR) is intepreted the same " +
                  "as it is in genrule.",
        ),
        "tools": attr.label_list(
            # We allow tools to directly reference files, as there could be a local script
            # used as a tool.
            allow_files = True,
            doc = "A list of tool dependencies for this rule. " +
                  "The path of an individual `tools` target //x:y can be " +
                  "obtained using `$(location //x:y)`",
            cfg = "exec",
        ),
    },
)
