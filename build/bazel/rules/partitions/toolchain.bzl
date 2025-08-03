# Copyright (C) 2022 The Android Open Source Project
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

"""
The toolchain used by the partition rule.
"""

PartitionToolchainInfo = provider(
    doc = "Partitions toolchain",
    fields = [
        "avbtool",
        "build_image",
        "e2fsdroid",
        "fec",
        "mke2fs",
        "mkfs_erofs",
        "mkuserimg_mke2fs",
        "openssl",
        "simg2img",
        "toybox",
        "tune2fs",
    ],
)

def _partition_toolchain_impl(ctx):
    toolchain_info = platform_common.ToolchainInfo(
        toolchain_info = PartitionToolchainInfo(
            avbtool = ctx.attr.avbtool,
            build_image = ctx.attr.build_image,
            e2fsdroid = ctx.attr.e2fsdroid,
            fec = ctx.attr.fec,
            mke2fs = ctx.attr.mke2fs,
            mkfs_erofs = ctx.attr.mkfs_erofs,
            mkuserimg_mke2fs = ctx.attr.mkuserimg_mke2fs,
            openssl = ctx.file.openssl,
            simg2img = ctx.attr.simg2img,
            toybox = ctx.attr.toybox,
            tune2fs = ctx.attr.tune2fs,
        ),
    )
    return [toolchain_info]

partition_toolchain = rule(
    implementation = _partition_toolchain_impl,
    attrs = {
        "avbtool": attr.label(cfg = "exec", executable = True, mandatory = True),
        "build_image": attr.label(cfg = "exec", executable = True, mandatory = True),
        "e2fsdroid": attr.label(cfg = "exec", executable = True, mandatory = True),
        "fec": attr.label(cfg = "exec", executable = True, mandatory = True),
        "mke2fs": attr.label(cfg = "exec", executable = True, mandatory = True),
        "mkfs_erofs": attr.label(cfg = "exec", executable = True, mandatory = True),
        "mkuserimg_mke2fs": attr.label(cfg = "exec", executable = True, mandatory = True),
        "openssl": attr.label(allow_single_file = True, cfg = "exec", mandatory = True),
        "simg2img": attr.label(cfg = "exec", executable = True, mandatory = True),
        "toybox": attr.label(cfg = "exec", executable = True, mandatory = True),
        "tune2fs": attr.label(cfg = "exec", executable = True, mandatory = True),
    },
)
