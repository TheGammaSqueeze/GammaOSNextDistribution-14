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

ApexInfo = provider(
    "ApexInfo exports metadata about this apex.",
    fields = {
        "backing_libs": "File containing libraries used by the APEX.",
        "base_file": "A zip file used to create aab files.",
        "base_with_config_zip": "A zip file used to create aab files within mixed builds.",
        "bundle_key_info": "APEX bundle signing public/private key pair (the value of the key: attribute).",
        "container_key_info": "Info of the container key provided as AndroidAppCertificateInfo.",
        "installed_files": "File containing all files installed by the APEX",
        "java_symbols_used_by_apex": "Java symbol list used by this APEX.",
        "package_name": "APEX package name.",
        "provides_native_libs": "Labels of native shared libs that this apex provides.",
        "requires_native_libs": "Labels of native shared libs that this apex requires.",
        "signed_compressed_output": "Signed .capex file.",
        "signed_output": "Signed .apex file.",
        "symbols_used_by_apex": "Symbol list used by this APEX.",
        "unsigned_output": "Unsigned .apex file.",
    },
)

ApexMkInfo = provider(
    "ApexMkInfo exports metadata about this apex for Android.mk integration / bundled builds.",
    fields = {
        "make_modules_to_install": "Make module names that should be installed to the system along with this APEX.",
        "files_info": "Metadata about the files included in the APEX payload. Used for generating Make code for final packaging step (e.g. coverage zip files).",
    },
)
