#!/usr/bin/env python3
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
"""Module that searches the tree for files named api_packages.json and returns
the Fully Qualified Bazel label of API domain contributions to API surfaces.
"""

import json
import os

from finder import FileFinder

# GLOBALS
# Filename to search for
API_PACKAGES_FILENAME = "api_packages.json"
# Default name of the api contribution Bazel target. Can be overridden by module
# authors in api_packages.json.
DEFAULT_API_TARGET = "contributions"
# Directories inside inner_tree that will be searched for api_packages.json
# This pruning improves the speed of the API export process
INNER_TREE_SEARCH_DIRS = [
    ("build", "orchestrator"
     ),  # TODO: Remove once build/orchestrator stops contributing to system.
    ("frameworks", "base"),
    ("packages", "modules")
]


# TODO: Fix line lengths and re-enable the pylint check..
# pylint: disable=line-too-long
class BazelLabel:
    """Class to represent a Fully qualified API contribution Bazel target
    https://docs.bazel.build/versions/main/skylark/lib/Label.html"""

    def __init__(self, package: str, target: str):
        self.package = package.rstrip(":")
        self.target = target.lstrip(":")

    def to_string(self):
        return self.package + ":" + self.target


class ApiPackageDecodeException(Exception):

    def __init__(self, filepath: str, msg: str):
        self.filepath = filepath
        msg = f"Found malformed api_packages.json file at {filepath}: " + msg
        super().__init__(msg)


class ContributionData:
    """Class to represent metadata of API contributions in api_packages.json."""

    def __init__(self, api_domain, api_bazel_label, is_apex=False):
        self.api_domain = api_domain
        self.api_contribution_bazel_label = api_bazel_label
        self.is_apex = is_apex

    def __repr__(self):
        props = [f"api_domain={self.api_domain}"]
        props.append(f"api_contribution_bazel_label={self.api_contribution_bazel_label}")
        props.append(f"is_apex={self.is_apex}")
        props_joined = ", ".join(props)
        return f"ContributionData({props_joined})"

def read(filepath: str) -> ContributionData:
    """Deserialize the contents of the json file at <filepath>
    Arguments:
        filepath
    Returns:
        ContributionData object
    """

    def _deserialize(filepath, json_contents) -> ContributionData:
        domain = json_contents.get("api_domain")
        package = json_contents.get("api_package")
        target = json_contents.get("api_target", "") or DEFAULT_API_TARGET
        is_apex = json_contents.get("is_apex", False)
        if not domain:
            raise ApiPackageDecodeException(
                filepath,
                "api_domain is a required field in api_packages.json")
        if not package:
            raise ApiPackageDecodeException(
                filepath,
                "api_package is a required field in api_packages.json")
        return ContributionData(domain,
                                BazelLabel(package=package, target=target),
                                is_apex=is_apex)

    with open(filepath, encoding='iso-8859-1') as f:
        try:
            return json.load(f,
                             object_hook=lambda json_contents: _deserialize(
                                 filepath, json_contents))
        except json.decoder.JSONDecodeError as ex:
            raise ApiPackageDecodeException(filepath, "") from ex


class ApiPackageFinder:
    """A class that searches the tree for files named api_packages.json and returns the fully qualified Bazel label of the API contributions of API domains

    Example api_packages.json
    ```
    [
        {
            "api_domain": "system",
            "api_package": "//build/orchestrator/apis",
            "api_target": "system",
            "is_apex": false
        }
    ]
    ```

    The search is restricted to $INNER_TREE_SEARCH_DIRS
    """

    def __init__(self, inner_tree_root: str, search_depth=6):
        self.inner_tree_root = inner_tree_root
        self.search_depth = search_depth
        self.finder = FileFinder(
            filename=API_PACKAGES_FILENAME,
            ignore_paths=[],
        )
        self._cache = None

    def _create_cache(self) -> None:
        self._cache = []
        search_paths = [
            os.path.join(self.inner_tree_root, *search_dir)
            for search_dir in INNER_TREE_SEARCH_DIRS
        ]
        for search_path in search_paths:
            for packages_file in self.finder.find(
                    path=search_path, search_depth=self.search_depth):
                api_contributions = read(packages_file)
                self._cache.extend(api_contributions)

    def _find_api_label(self, api_domain_filter) -> list[BazelLabel]:
        # Compare to None and not []. The latter value is possible if a tree has
        # no API contributoins.
        if self._cache is None:
            self._create_cache()
        return [
            c.api_contribution_bazel_label for c in self._cache
            if api_domain_filter(c)
        ]

    def find_api_label_string(self, api_domain: str) -> str:
        """ Return the fully qualified bazel label of the contribution target

        Parameters:
            api_domain: Name of the API domain, e.g. system

        Raises:
            ApiPackageDecodeException: If a malformed api_packages.json is found during search

        Returns:
            Bazel label, e.g. //frameworks/base:contribution
            None if a contribution could not be found
        """
        labels = self._find_api_label(lambda x: x.api_domain == api_domain)
        assert len(
            labels
        ) < 2, f"Duplicate contributions found for API domain: {api_domain}"
        return labels[0].to_string() if labels else None

    def find_api_package(self, api_domain: str) -> str:
        """ Return the Bazel package of the contribution target

        Parameters:
            api_domain: Name of the API domain, e.g. system

        Raises:
            ApiPackageDecodeException: If a malformed api_packages.json is found during search

        Returns:
            Bazel label, e.g. //frameworks/base
            None if a contribution could not be found
        """
        labels = self._find_api_label(lambda x: x.api_domain == api_domain)
        assert len(
            labels
        ) < 2, f"Duplicate contributions found for API domain: {api_domain}"
        return labels[0].package if label else None

    def find_api_label_string_using_filter(self,
                                           api_domain_filter: callable) -> str:
        """ Return the Bazel label of the contributing targets
        that match a search filter.

        Parameters:
            api_domain_filter: A callback function. The first arg to the function
            is ContributionData

        Raises:
            ApiPackageDecodeException: If a malformed api_packages.json is found during search

        Returns:
            List of Bazel labels, e.g. [//frameworks/base:contribution, //packages/myapex:contribution]
            None if a contribution could not be found
        """
        labels = self._find_api_label(api_domain_filter)
        return [label.to_string() for label in labels]
