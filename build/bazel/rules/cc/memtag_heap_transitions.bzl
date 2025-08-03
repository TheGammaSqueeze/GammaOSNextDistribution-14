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

load(":cc_constants.bzl", "transition_constants")
load(":cc_library_common.bzl", "path_in_list")

MEMTAG_HEAP_ENABLED = "memtag_heap"
DIAG_MEMTAG_HEAP_ENABLED = "diag_memtag_heap"
MEMTAG_HEAP_DISABLED = "-" + MEMTAG_HEAP_ENABLED
DIAG_MEMTAG_HEAP_DISABLED = "-" + DIAG_MEMTAG_HEAP_ENABLED

def _remove_from_list(list, item):
    if item in list:
        list.remove(item)

def apply_memtag_heap_transition(settings, attr, cli_features):
    features = cli_features

    # Remove any memtag features that are propagated from rdeps, it should only
    # be added if the current target are part of the include paths.
    apply_drop_memtag_heap(features)

    path = attr.package_name
    exclude_paths = settings[transition_constants.memtag_heap_exclude_paths_key]
    if path_in_list(path, exclude_paths):
        return features

    target_features = getattr(attr, transition_constants.features_attr_key)
    async_include_paths = settings[transition_constants.memtag_heap_async_include_paths_key]
    sync_include_paths = settings[transition_constants.memtag_heap_sync_include_paths_key]

    if path_in_list(path, sync_include_paths):
        if not (MEMTAG_HEAP_ENABLED in target_features or MEMTAG_HEAP_DISABLED in target_features):
            features.append(MEMTAG_HEAP_ENABLED)
        if not (DIAG_MEMTAG_HEAP_ENABLED in target_features or DIAG_MEMTAG_HEAP_DISABLED in target_features):
            features.append(DIAG_MEMTAG_HEAP_ENABLED)
    elif path_in_list(path, async_include_paths):
        if not (MEMTAG_HEAP_ENABLED in target_features or MEMTAG_HEAP_DISABLED in target_features):
            features.append(MEMTAG_HEAP_ENABLED)

    return features

def apply_drop_memtag_heap(features):
    _remove_from_list(features, MEMTAG_HEAP_ENABLED)
    _remove_from_list(features, MEMTAG_HEAP_DISABLED)
    _remove_from_list(features, DIAG_MEMTAG_HEAP_ENABLED)
    _remove_from_list(features, DIAG_MEMTAG_HEAP_DISABLED)

    return features
