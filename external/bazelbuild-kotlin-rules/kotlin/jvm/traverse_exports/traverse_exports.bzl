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

"""Combined aspect for all rules_kotlin behaviours that need to traverse exports."""

load("//:visibility.bzl", "RULES_KOTLIN")
load(":compiler_plugin.bzl", "kt_compiler_plugin_visitor")
load(":direct_jdeps.bzl", "kt_direct_jdeps_visitor")
load(":forbidden_deps.bzl", "kt_forbidden_deps_visitor")
load(":friend_jars.bzl", "kt_friend_jars_visitor")
load(":friend_labels.bzl", "kt_friend_labels_visitor")
load(":java_plugin.bzl", "java_plugin_visitor")

visibility(RULES_KOTLIN)

# java_xxx_proto_library don't populate java_outputs but we can get them through
# required_aspect_providers from their proto_library deps.
_DEPS_AS_EXPORTS_RULES = [
    "java_proto_library",
    "java_lite_proto_library",
    "java_mutable_proto_library",
]

_NO_SRCS_DEPS_AS_EXPORTS_RULES = [
    "proto_library",
]

# visitor = struct[T](
#     name = string,
#     visit_target = function(Target, ctx.rule): list[T],
#     filter_edge = None|(function(src: ?, dest: Target): bool),
#     process_unvisited_target = None|(function(Target): list[T]),
#     finish_expansion = None|(function(depset[T]): depset[T]),
# )
_VISITORS = [
    kt_forbidden_deps_visitor,
    kt_direct_jdeps_visitor,
    kt_compiler_plugin_visitor,
    kt_friend_jars_visitor,
    kt_friend_labels_visitor,
    java_plugin_visitor,
]

_KtTraverseExportsInfo = provider(
    doc = "depsets for transitive info about exports",
    fields = {
        v.name: ("depset[%s]" % v.name)
        for v in _VISITORS
    },
)

_EMPTY_KT_TRAVERSE_EXPORTS_INFO = _KtTraverseExportsInfo(**{
    v.name: depset()
    for v in _VISITORS
})

def _aspect_impl(target, ctx):
    if not (JavaInfo in target):
        # Ignore non-JVM targets. This also chops-up the
        # traversal domain at these targets.
        # TODO: Support non-JVM targets for KMP
        return _EMPTY_KT_TRAVERSE_EXPORTS_INFO

    exports = []
    exports.extend(getattr(ctx.rule.attr, "exports", []))  # exports list is frozen
    if ctx.rule.kind in _DEPS_AS_EXPORTS_RULES:
        exports.extend(ctx.rule.attr.deps)
    elif ctx.rule.kind in _NO_SRCS_DEPS_AS_EXPORTS_RULES and not ctx.rule.attr.srcs:
        exports.extend(ctx.rule.attr.deps)

    return _KtTraverseExportsInfo(**{
        v.name: depset(
            direct = v.visit_target(target, ctx.rule),
            transitive = [
                getattr(e[_KtTraverseExportsInfo], v.name)
                for e in exports
                if (not v.filter_edge or v.filter_edge(target, e))
            ],
        )
        for v in _VISITORS
    })

_aspect = aspect(
    implementation = _aspect_impl,
    provides = [_KtTraverseExportsInfo],
    # Transitively check exports, since they are effectively directly depended on.
    # "deps" needed for rules that treat deps as exports (usually absent srcs).
    attr_aspects = ["exports", "deps"],
    required_aspect_providers = [JavaInfo],  # to get at JavaXxxProtoAspects' JavaInfos
)

def _create_visitor_expand(visitor):
    def _visitor_expand(targets, root = None):
        direct = []
        transitive = []
        for t in targets:
            if (not visitor.filter_edge or visitor.filter_edge(root, t)):
                if _KtTraverseExportsInfo in t:
                    transitive.append(getattr(t[_KtTraverseExportsInfo], visitor.name))
                elif visitor.process_unvisited_target:
                    direct.extend(visitor.process_unvisited_target(t))

        expanded_set = depset(direct = direct, transitive = transitive)
        return visitor.finish_expansion(expanded_set) if visitor.finish_expansion else expanded_set

    return _visitor_expand

kt_traverse_exports = struct(
    aspect = _aspect,
    **{
        "expand_" + v.name: _create_visitor_expand(v)
        for v in _VISITORS
    }
)
