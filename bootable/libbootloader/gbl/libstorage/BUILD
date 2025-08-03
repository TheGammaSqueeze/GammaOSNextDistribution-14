# Copyright (C) 2023 The Android Open Source Project
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

load("@rules_rust//rust:defs.bzl", "rust_doc_test", "rust_library", "rust_test")

package(
    default_visibility = ["//visibility:public"],
)

rust_library(
    name = "libstorage",
    srcs = [
        "src/gpt.rs",
        "src/lib.rs",
    ],
    crate_name = "gbl_storage",
    edition = "2021",
    deps = [
        "@crc32fast",
        "@zerocopy",
    ],
)

rust_test(
    name = "libstorage_test",
    compile_data = [
        "@gbl//libstorage/test:gpt_test.bin",
        "@gbl//libstorage/test:boot_a.bin",
        "@gbl//libstorage/test:boot_b.bin",
    ],
    crate = ":libstorage",
)

rust_doc_test(
    name = "libstorage_doc_test",
    crate = ":libstorage",
)
