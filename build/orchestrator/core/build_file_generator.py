#!/usr/bin/python3
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
"""A module for generating Android.bp/BUILD files for stub libraries"""

from enum import Enum
import json
import os
import textwrap
from typing import List
from lunch import walk_paths

TAB = "    "  # 4 spaces


class ConfigAxis(Enum):
    """A helper class to manage the configuration axes of stub libraries."""
    NoConfig = ""
    ArmConfig = "arm"
    Arm64Config = "arm64"
    X86Config = "x86"
    X86_64Config = "x86_64"

    def is_arch_axis(self):
        return self != ConfigAxis.NoConfig

    @classmethod
    def get_arch_axes(cls) -> list:
        return [a for a in cls if a.is_arch_axis()]

    @classmethod
    def get_axis(cls, value):
        for axis in cls:
            if axis.value == value:
                return axis
        return None


class AndroidBpPropertyKey:
    """Unique key identifying an Android.bp property."""

    def __init__(self, name: str, axis: str):
        self.name = name
        self.axis = axis

    def __hash__(self):
        return hash((self.name, self.axis))

    def __eq__(self, other):
        return (isinstance(other, AndroidBpPropertyKey)
                and self.name == other.name and self.axis == other.axis)


class AndroidBpProperty:
    """Properties of Android.bp modules."""

    def __init__(self, name, value, axis):
        self.name = name
        self.value = value
        self.axis = axis

    def extend(self, value):
        """Extend the existing value of the property.

        Parameters:
            value: object to add to the value of
            existing property.

        Returns:
            None

        `extend` adds the value to the end of `self.value` (list).
        If `value` is an iterator, it adds each element piecemeal.

        `self.value` is a Python list, and therefore can contain
        elements of different types.

        e.g.
        p = AndroidBpProperty("myprop", 1, ConfigAxis.NoConfig)
        p.extend("a")
        p.extend(["b", "c"])
        p.extend(("d", "2"))

        print(p.value)
        [1, 'a', 'b', 'c', 'd', '2']
        """

        self.value = self._as_list(self.value)
        self.value.extend(self._as_list(value))

    def _as_list(self, value):
        """Converts an object into a list."""
        if not value:
            return []
        if isinstance(value, (int, bool, str)):
            return [value]
        if isinstance(value, list):
            return value
        if isinstance(value, (set, tuple)):
            return list(value)
        raise TypeError(f"bad type {type(value)}")


class AndroidBpModule:
    """Soong module of an Android.bp file."""

    def __init__(self, name: str, module_type: str):
        if not name:
            raise ValueError("name cannot be empty in SoongModule")
        if not module_type:
            raise ValueError("module_type cannot be empty in SoongModule")
        self.name = name
        self.module_type = module_type
        self.properties = {}  # indexed using AndroidBpPropertyKey

    def add_property(self, prop: str, val: object, axis=ConfigAxis.NoConfig):
        """Add a property to the Android.bp module.

        Raises:
            ValueError if (`prop`, `axis`) is already registered.
        """
        key = AndroidBpPropertyKey(name=prop, axis=axis)
        if key in self.properties:
            raise ValueError(f"Property: {prop} in axis: {axis} has been"
                             "registered. Use extend_property method instead.")

        p = AndroidBpProperty(prop, val, axis)
        self.properties[key] = p

    def extend_property(self,
                        prop: str,
                        val: object,
                        axis=ConfigAxis.NoConfig):
        """Extend the value of a property."""
        key = AndroidBpPropertyKey(name=prop, axis=axis)
        if key in self.properties:
            self.properties[key].extend(val)
        else:
            self.add_property(prop, val, axis)

    def get_properties(self, axis) -> List[AndroidBpProperty]:
        return [prop for prop in self.properties.values() if prop.axis == axis]

    def string(self, formatter=None) -> None:
        """Return the string representation of the module using the provided
        formatter."""
        if formatter is None:
            formatter = module_formatter
        return formatter(self)

    def __str__(self):
        return self.string()


class AndroidBpComment:
    """Comment in an Android.bp file."""

    def __init__(self, comment: str):
        self.comment = comment

    def _add_comment_token(self, raw: str) -> str:
        return raw if raw.startswith("//") else "// " + raw

    def split(self) -> List[str]:
        """Split along \n tokens."""
        raw_comments = self.comment.split("\n")
        return [self._add_comment_token(r) for r in raw_comments]

    def string(self, formatter=None) -> str:
        """Return the string representation of the comment using the provided
        formatter."""
        if formatter is None:
            formatter = comment_formatter
        return formatter(self)

    def __str__(self):
        return self.string()


class AndroidBpFile:
    """Android.bp file."""

    def __init__(self, directory: str):
        self._path = os.path.join(directory, "Android.bp")
        self.components = []

    def add_module(self, soong_module: AndroidBpModule):
        """Add a Soong module to the file."""
        self.components.append(soong_module)

    def add_comment(self, comment: AndroidBpComment):
        """Add a comment to the file."""
        self.components.append(comment)

    def add_comment_string(self, comment: str):
        """Add a comment (str) to the file."""
        self.components.append(AndroidBpComment(comment=comment))

    def add_license(self, lic: str) -> None:
        raise NotImplementedError("Addition of License in generated Android.bp"
                                  "files is not supported")

    def string(self, formatter=None) -> None:
        """Return the string representation of the file using the provided
        formatter."""
        fragments = [
            component.string(formatter) for component in self.components
        ]
        return "\n".join(fragments)

    def __str__(self):
        return self.string()

    def is_stale(self, formatter=None) -> bool:
        """Return true if the object is newer than the file on disk."""
        exists = os.path.exists(self._path)
        if not exists:
            return True
        with open(self._path, encoding='iso-8859-1') as f:
            # TODO: Avoid wasteful computation using cache
            return f.read() != self.string(formatter)

    def write(self, formatter=None) -> None:
        """Write the AndroidBpFile object to disk."""
        if not self.is_stale(formatter):
            return
        os.makedirs(os.path.dirname(self._path), exist_ok=True)
        with open(self._path, "w+", encoding='iso-8859-1') as f:
            f.write(self.string(formatter))

    def fullpath(self) -> str:
        return self._path


# Default formatters
def comment_formatter(comment: AndroidBpComment) -> str:
    return "\n".join(comment.split())


def module_properties_formatter(props: List[AndroidBpProperty],
                                indent=1) -> str:
    formatted = ""
    for prop in props:
        name, val = prop.name, prop.value
        if isinstance(val, str):
            val_f = f'"{val}"'
        # bool is a subclass of int, check it first
        elif isinstance(val, bool):
            val_f = "true" if val else "false"
        elif isinstance(val, int):
            val_f = val
        elif isinstance(val, list):
            # TODO: Align with bpfmt.
            # This implementation splits non-singular lists into multiple lines.
            if len(val) < 2:
                val_f = json.dumps(val)
            else:
                nested_indent = indent + 1
                val_f = json.dumps(val, indent=f"{nested_indent*TAB}")
                # Add TAB before the closing `]`
                val_f = val_f[:
                                              -1] + indent * TAB + val_f[
                                                  -1]
        else:
            raise NotImplementedError(
                f"Formatter for {val} of type: {type(val)}"
                "has not been implemented")

        formatted += f"""{indent*TAB}{name}: {val_f},\n"""
    return formatted


def module_formatter(module: AndroidBpModule) -> str:
    formatted = textwrap.dedent(f"""\
            {module.module_type} {{
            {TAB}name: "{module.name}",
            """)
    # Print the no-arch props first.
    no_arch_props = module.get_properties(ConfigAxis.NoConfig)
    formatted += module_properties_formatter(no_arch_props)

    # Print the arch props if they exist.
    contains_arch_props = any(
        [prop.axis.is_arch_axis() for prop in module.properties])
    if contains_arch_props:
        formatted += f"{TAB}arch: {{\n"
        arch_axes = ConfigAxis.get_arch_axes()
        for arch_axis in arch_axes:
            arch_axis_props = module.get_properties(arch_axis)
            if not arch_axis_props:
                continue
            formatted += f"{2*TAB}{arch_axis.value}: {{\n"
            formatted += module_properties_formatter(arch_axis_props, indent=3)
            formatted += f"{2*TAB}}},\n"

        formatted += f"{TAB}}},\n"

    formatted += "}"
    return formatted


class BazelTarget:
    pass  # TODO


class BazelBuildFile:
    pass  # TODO


class BuildFileGenerator:
    """Class to maintain state of generated Android.bp/BUILD files."""

    def __init__(self):
        self.android_bp_files = []
        self.bazel_build_files = []

    def add_android_bp_file(self, file: AndroidBpFile):
        self.android_bp_files.append(file)

    def add_bazel_build_file(self, file: BazelBuildFile):
        raise NotImplementedError("Bazel BUILD file generation"
                                  "is not supported currently")

    def write(self):
        for android_bp_file in self.android_bp_files:
            android_bp_file.write()

    def clean(self, staging_dir: str):
        """Delete discarded Android.bp files.

        This is necessary when a library is dropped from an API surface."""
        valid_bp_files = set([x.fullpath() for x in self.android_bp_files])
        for bp_file_on_disk in walk_paths(staging_dir,
                                          lambda x: x.endswith("Android.bp")):
            if bp_file_on_disk not in valid_bp_files:
                # This library has been dropped since the last run
                os.remove(bp_file_on_disk)
