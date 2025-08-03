# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
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

load("//build/bazel/rules/cc:cc_constants.bzl", "constants")

def extension(f):
    return f.split(".")[-1]

def group_files_by_ext(files):
    cpp = []
    c = []
    asm = []

    # This for-loop iterator works because filegroups in Android don't use
    # configurable selects.
    for f in files:
        if extension(f) in constants.c_src_exts:
            c.append(f)
        elif extension(f) in constants.cpp_src_exts:
            cpp.append(f)
        elif extension(f) in constants.as_src_exts:
            asm.append(f)
        else:
            # not C based
            continue
    return cpp, c, asm

# Filegroup is a macro because it needs to expand to language specific source
# files for cc_library's srcs_as, srcs_c and srcs attributes.
def filegroup(name, srcs = [], **kwargs):
    native.filegroup(
        name = name,
        srcs = srcs,
        **kwargs
    )

    # These genrule prevent empty filegroups being used as deps to cc libraries,
    # avoiding the error:
    #
    # in srcs attribute of cc_library rule //foo/bar:baz:
    # '//foo/bar/some_other:baz2' does not produce any cc_library srcs files.
    native.genrule(
        name = name + "_null_cc",
        outs = [name + "_null.cc"],
        tags = ["manual"],
        cmd = "touch $@",
    )
    native.genrule(
        name = name + "_null_c",
        outs = [name + "_null.c"],
        tags = ["manual"],
        cmd = "touch $@",
    )
    native.genrule(
        name = name + "_null_s",
        outs = [name + "_null.S"],
        tags = ["manual"],
        cmd = "touch $@",
    )

    cpp_srcs, c_srcs, as_srcs = group_files_by_ext(srcs)
    native.filegroup(
        name = name + "_cpp_srcs",
        srcs = [name + "_null.cc"] + cpp_srcs,
        tags = ["manual"],
    )
    native.filegroup(
        name = name + "_c_srcs",
        srcs = [name + "_null.c"] + c_srcs,
        tags = ["manual"],
    )
    native.filegroup(
        name = name + "_as_srcs",
        srcs = [name + "_null.S"] + as_srcs,
        tags = ["manual"],
    )
