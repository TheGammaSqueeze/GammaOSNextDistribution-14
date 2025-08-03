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

"""A function to generate table of contents files of symbols from a shared library."""

CcTocInfo = provider(
    "Information about the table of contents of a shared library",
    fields = {
        "toc": "The single file for the table of contents",
    },
)

def generate_toc(ctx, name, input_file):
    so_name = "lib" + name + ".so"
    toc_name = so_name + ".toc"
    out_file = ctx.actions.declare_file(toc_name)
    d_file = ctx.actions.declare_file(toc_name + ".d")
    ctx.actions.run(
        env = {
            "CLANG_BIN": ctx.executable._readelf.dirname,
        },
        inputs = [input_file],
        tools = [
            ctx.executable._readelf,
        ],
        outputs = [out_file, d_file],
        executable = ctx.executable._toc_script,
        arguments = [
            # Only Linux shared libraries for now.
            "--elf",
            "-i",
            input_file.path,
            "-o",
            out_file.path,
            "-d",
            d_file.path,
        ],
        mnemonic = "GenerateToc",
    )
    return CcTocInfo(toc = out_file)
