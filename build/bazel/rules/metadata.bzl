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

MetadataFileInfo = provider(
    fields = {
        "metadata_file": "METADATA file of a module",
    },
)

# Define metadata file of packages, usually the file is METADATA in the root directory of a package.
# Attribute applicable_licenses is needed on the filegroup, so when the filegroup is used in
# package(default_package_metadata=) Bazel will not regard it as cyclic reference.
def metadata(name, metadata = "METADATA"):
    native.filegroup(
        name = name,
        srcs = [metadata],
        applicable_licenses = [],
    )
