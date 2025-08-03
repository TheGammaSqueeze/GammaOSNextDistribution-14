# This filegroup is necessary because Bazel requires that every file in the .d
# file of a compilation action is in a subdirectory of one of the transitive
# dependencies of the rule being compiled.
#
# (this is not an intentional feature but accidentally results from the fact
# that lines in the .d files must be turned into Artifact instances and thus
# need a source root. See ArtifactFactory.findSourceRoot() for the pertinent
# logic)
#
# The easiest way to ensure this is to add this filegroup to one of the
# dependencies of the cc_toolchain. Then the root directory of the repository
# becomes part of said transitive dependencies and thus every file is in a
# subdirectory of it.
filegroup(
    name = "empty",
    visibility = ["//visibility:public"],
)
