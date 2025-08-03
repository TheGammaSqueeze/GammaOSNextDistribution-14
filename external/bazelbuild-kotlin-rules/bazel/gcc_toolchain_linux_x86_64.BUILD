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

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

filegroup(
    name = "sysroot_crt_files",
    srcs = [
        "x86_64-unknown-linux-gnu/sysroot/usr/lib/crt1.o",
        "x86_64-unknown-linux-gnu/sysroot/usr/lib/crti.o",
        "x86_64-unknown-linux-gnu/sysroot/usr/lib/crtn.o",
    ],
)

filegroup(
    name = "libgcc_crt_files",
    srcs = [
        "lib/gcc/x86_64-unknown-linux-gnu/4.8.5/crtbegin.o",
        "lib/gcc/x86_64-unknown-linux-gnu/4.8.5/crtend.o",
    ],
)

filegroup(
    name = "libs",
    srcs = glob([
        "lib/gcc/x86_64-unknown-linux-gnu/4.8.5/*.a",
        "x86_64-unknown-linux-gnu/sysroot/lib/*.a",
        "x86_64-unknown-linux-gnu/sysroot/usr/lib/*.a",
        "x86_64-unknown-linux-gnu/sysroot/lib/*.so*",
        "x86_64-unknown-linux-gnu/sysroot/usr/lib/*.so*",
    ]),
)
