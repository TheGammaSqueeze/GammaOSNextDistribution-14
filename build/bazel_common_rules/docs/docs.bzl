# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Generate bare-bones docs with Stardoc"""

load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@io_bazel_stardoc//stardoc:stardoc.bzl", "stardoc")
load("//build/bazel_common_rules/dist:dist.bzl", "copy_to_dist_dir")

def _sanitize_label_as_filename(label):
    """Sanitize a Bazel label so it is safe to be used as a filename."""
    label_text = str(label)
    return _normalize(label_text)

def _normalize(s):
    """Returns a normalized string by replacing non-letters / non-numbers as underscores."""
    return "".join([c if c.isalnum() else "_" for c in s.elems()])

# TODO: Add aspect_template when necessary
def docs(
        name,
        srcs,
        default = None,
        deps = None,
        func_template = None,
        provider_template = None,
        rule_template = None,
        **kwargs):
    """Build docs.

    The following rules are also generated:
    - `{name}_dist` is created for distribution
    - `{name}_server` is created for seeing docs locally. View with
      ```
      bazel run {name}_server
      ```

    Args:
        name: name of this rule.
        srcs: sources (`*.bzl` files) to generate docs. Docs for definitions in
          these files are emitted.

          `srcs` must be a list of real files. Labels to rules are not accepted.
        default: An element in `srcs` that's shown in the renderer by default.
        deps: additional dependencies of `srcs`. Definitions in these files do
          not show up in the final output.
        func_template: Template for generating docs for functions.
        provider_template: Template for generating docs for providers.
        rule_template: Template for generating docs for rules.
        **kwargs: kwargs to internal rules
    """

    all_deps = []
    all_deps += srcs
    if deps != None:
        all_deps += deps

    if func_template == None:
        func_template = "//build/bazel_common_rules/docs:templates/func.vm"
    if provider_template == None:
        provider_template = "//build/bazel_common_rules/docs:templates/provider.vm"
    if rule_template == None:
        rule_template = "//build/bazel_common_rules/docs:templates/rule.vm"

    private_kwargs = kwargs | {
        "visibility": ["//visibility:private"],
    }

    bzl_library(
        name = name + "_deps",
        srcs = all_deps,
        **private_kwargs
    )

    # Key: label to bzl. Value: label to markdown.
    bzl_md = {}

    for src in srcs:
        stardoc_target_name = name + "-" + _sanitize_label_as_filename(src)
        stardoc(
            name = stardoc_target_name,
            out = name + "/" + _sanitize_label_as_filename(src) + ".md",
            input = src,
            deps = [":" + name + "_deps"],
            func_template = func_template,
            provider_template = provider_template,
            rule_template = rule_template,
            **private_kwargs
        )
        bzl_md[src] = stardoc_target_name

    default_file_cmd = """touch $@ && """
    for src in srcs:
        if default == src:
            default_file_cmd += """echo '<div hidden><a href="#{src}" id="default_file">{src}</a></div>' >> $@ &&""".format(
                src = src,
            )
            break
    default_file_cmd += ":"

    native.genrule(
        name = name + "_default_file.html.frag",
        srcs = [
        ],
        outs = [
            name + "/docs_resources/default_file.html.frag",
        ],
        cmd = default_file_cmd,
        **private_kwargs
    )

    native.genrule(
        name = name,
        srcs = [
            "//build/bazel_common_rules/docs:index.html",
            ":{name}_default_file.html.frag".format(name = name),
        ] + bzl_md.keys() + bzl_md.values(),
        outs = [
            name + "/root/index.html",
        ],
        cmd = """
            $(location //build/bazel_common_rules/docs:insert_resource.py) \\
              --infile $(location //build/bazel_common_rules/docs:index.html) \\
              --outfile $(location {name}/root/index.html) \\
              default_file.html.frag:$(location :{name}_default_file.html.frag) \\
              {bzl_md}
        """.format(
            name = name,
            bzl_md = " ".join(["$(location {}):$(location {})".format(bzl, md) for bzl, md in bzl_md.items()]),
        ),
        tools = [
            "//build/bazel_common_rules/docs:insert_resource.py",
        ],
        **kwargs
    )

    native.genrule(
        name = name + "_run_server.sh",
        srcs = [],
        outs = [
            name + "/run_server.sh",
        ],
        cmd = """
        cat > $(location {name}/run_server.sh) <<< '#!/usr/bin/env sh
cd $$(dirname $$0)/{name}/root &&
python3 -m http.server 8080
'
        chmod +x $(location {name}/run_server.sh)
        """.format(name = name),
        **private_kwargs
    )

    native.sh_binary(
        name = name + "_server",
        srcs = [
            ":{name}_run_server.sh".format(name = name),
        ],
        data = [":" + name],
        **kwargs
    )

    copy_to_dist_dir(
        name = name + "_dist",
        data = [":" + name],
        **kwargs
    )
