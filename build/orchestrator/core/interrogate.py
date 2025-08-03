#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import os

_VALID_KEYS = set(("version", "domain_data"))


class DescribeError(Exception):
    """Parsing errors."""

    def __init__(self, tree, reason):
        super().__init__(tree, reason)
        self.tree = tree
        self.reason = reason

    def __str__(self):
        return self.reason


def interrogate_tree(_tree_key, inner_tree, _cookie):
    """Interrogate the inner tree."""
    query = dict(build_domains=[inner_tree.build_domains])
    os.makedirs(os.path.dirname(inner_tree.out.tree_query()), exist_ok=True)
    with open(inner_tree.out.tree_query(), 'w', encoding="iso-8859-1") as f:
        f.write(json.dumps(query))
    inner_tree.invoke([
        "describe", "--input_json",
        inner_tree.out.tree_query(base=inner_tree.out.Base.INNER),
        "--output_json",
        inner_tree.out.tree_info_file(base=inner_tree.out.Base.INNER)
    ])

    try:
        with open(inner_tree.out.tree_info_file(), encoding='iso-8859-1') as f:
            info_json = json.load(f)
    except FileNotFoundError as e:
        raise DescribeError(inner_tree, "No return from interrogate.") from e
    except json.decoder.JSONDecodeError as e:
        raise DescribeError(
            inner_tree, f"Failed to parse interrogate response: {e}") from e

    if not isinstance(info_json, dict):
        raise DescribeError(inner_tree, "Malformed describe response")
    if set(info_json.keys()) - _VALID_KEYS:
        raise DescribeError(inner_tree,
                            "Invalid keyname in describe response.")
    # Make sure that fields are initialized, so that we can just access them.
    if info_json.setdefault("version", 0):
        raise DescribeError(inner_tree,
                            f"Invalid version: {info_json['version']}")
    info_json.setdefault("domain_data", [])

    return info_json
