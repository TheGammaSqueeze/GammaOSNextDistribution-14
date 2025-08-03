#!/bin/sh
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
#
""":" # Shell script (in docstring to appease pylint)
# Find and invoke hermetic python3 interpreter
. "`dirname $0`"/../../../../../"trusty/vendor/google/aosp/scripts/envsetup.sh"
exec "$PY3" "$0" "$@"
# Shell script end

Generate metrics stats functions for all messages defined in a .proto file

Command line (per protoc requirements):
    $ROOT_DIR/prebuilts/libprotobuf/bin/protoc \
        --proto_path=$SCRIPT_DIR \
        --plugin=metrics_atoms=metrics_atoms_protoc_plugin.py \
        --metrics_atoms_out=out \
        --metrics_atoms_opt=pkg:android/trusty/stats \
        test/atoms.proto

Important:
* For debugging purposes: set option `dump-input` as show below:
        --stats-log_opt=pkg:android/trusty/stats,dump-input:/tmp/dump.pb

* with `debug` option, Invoke the protoc command line and observe
  the creation of `/tmp/dump.pf`.

* Then invoke the debugging script
  `metrics_atoms_protoc_debug.py /tmp/dump.pb android/trusty/stats`,
  and hook the debugger at your convenience.

This is the easiest debugging approach.
It still is possible to debug when protoc is invoking the plugin,
it requires enabling remote debugging, which is slightly more tedious.

"""

import functools
import os
import re
import sys
from pathlib import Path
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List

# mypy: disable-error-code="attr-defined,valid-type"
from google.protobuf.compiler import plugin_pb2 as plugin
from google.protobuf.descriptor_pb2 import FileDescriptorProto
from google.protobuf.descriptor_pb2 import DescriptorProto
from google.protobuf.descriptor_pb2 import FieldDescriptorProto
from jinja2 import Environment, PackageLoader

DEBUG_READ_PB = False

jinja_template_loader = PackageLoader("templates_package")
jinja_template_env = Environment(
    loader=jinja_template_loader,
    trim_blocks=True,
    lstrip_blocks=True,
    keep_trailing_newline=True,
    line_comment_prefix="###",
)


def snake_case(s: str):
    """Convert impl name from camelCase to snake_case"""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", s).lower()


class StatsLogGenerationError(Exception):
    """"Error preventing the log API generation"""


@dataclass
class VendorAtomEnumValue:
    name: str
    value: int

    @functools.cached_property
    def name_len(self):
        return len(self.name)


@dataclass
class VendorAtomEnum:
    name: str
    values: List[VendorAtomEnumValue]

    @functools.cached_property
    def values_name_len(self):
        return max(len(v.name) for v in self.values)

    @functools.cached_property
    def c_name(self):
        return f"stats_{snake_case(self.name)}"


class VendorAtomValueTag(Enum):
    intValue = 0
    longValue = 1
    floatValue = 2
    stringValue = 3

    @classmethod
    def get_tag(cls, label: FieldDescriptorProto,
                type_: FieldDescriptorProto,
                type_name: str) -> 'VendorAtomValueTag':
        if label == FieldDescriptorProto.LABEL_REPEATED:
            raise StatsLogGenerationError(
                f"repeated fields are not supported in Android"
                f" please fix {type_name}({type_})")
        if type_ in [
                FieldDescriptorProto.TYPE_DOUBLE,
                FieldDescriptorProto.TYPE_FLOAT
        ]:
            return VendorAtomValueTag.floatValue
        if type_ in [
                FieldDescriptorProto.TYPE_INT32,
                FieldDescriptorProto.TYPE_SINT32,
                FieldDescriptorProto.TYPE_UINT32,
                FieldDescriptorProto.TYPE_FIXED32,
                FieldDescriptorProto.TYPE_ENUM,
        ]:
            return VendorAtomValueTag.intValue
        if type_ in [
                FieldDescriptorProto.TYPE_INT64,
                FieldDescriptorProto.TYPE_SINT64,
                FieldDescriptorProto.TYPE_UINT64,
                FieldDescriptorProto.TYPE_FIXED64
        ]:
            return VendorAtomValueTag.longValue
        if type_ in [
                FieldDescriptorProto.TYPE_BOOL,
        ]:
            raise StatsLogGenerationError(
                f"boolean fields are not supported in Android"
                f" please fix {type_name}({type_})")
        if type_ in [
                FieldDescriptorProto.TYPE_STRING,
        ]:
            return VendorAtomValueTag.stringValue
        if type_ in [
                FieldDescriptorProto.TYPE_BYTES,
        ]:
            raise StatsLogGenerationError(
                f"byte[] fields are not supported in Android"
                f" please fix {type_name}({type_})")
        raise StatsLogGenerationError(
            f"field type {type_name}({type_}) cannot be an atom field")


@dataclass
class VendorAtomValue:
    name: str
    tag: VendorAtomValueTag
    enum: VendorAtomEnum
    idx: int

    @functools.cached_property
    def c_name(self):
        return snake_case(self.name)

    @functools.cached_property
    def is_string(self):
        return self.tag in [
            VendorAtomValueTag.stringValue,
        ]

    @functools.cached_property
    def c_type(self):
        if self.enum:
            return f"enum {self.enum.c_name} "
        match self.tag:
            case VendorAtomValueTag.intValue:
                return 'int32_t '
            case VendorAtomValueTag.longValue:
                return 'int64_t '
            case VendorAtomValueTag.floatValue:
                return 'float '
            case VendorAtomValueTag.stringValue:
                return 'const char *'
            case _:
                raise StatsLogGenerationError(f"unknown tag {self.tag}")

    @functools.cached_property
    def default_value(self):
        if self.enum:
            try:
                default = [
                    v.name
                    for v in self.enum.values
                    if v.name.lower().find("invalid") > -1 or
                    v.name.lower().find("unknown") > -1
                ][0]
            except IndexError:
                default = '0'
            return default
        match self.tag:
            case VendorAtomValueTag.intValue:
                return '0'
            case VendorAtomValueTag.longValue:
                return '0L'
            case VendorAtomValueTag.floatValue:
                return '0.'
            case VendorAtomValueTag.stringValue:
                return '"", 0UL'
            case _:
                raise StatsLogGenerationError(f"unknown tag {self.tag}")

    @functools.cached_property
    def stats_setter_name(self):
        match self.tag:
            case VendorAtomValueTag.intValue:
                return 'set_int_value_at'
            case VendorAtomValueTag.longValue:
                return 'set_long_value_at'
            case VendorAtomValueTag.floatValue:
                return 'set_float_value_at'
            case VendorAtomValueTag.stringValue:
                return 'set_string_value_at'
            case _:
                raise StatsLogGenerationError(f"unknown tag {self.tag}")


@dataclass
class VendorAtom:
    name: str
    atom_id: int
    values: List[VendorAtomValue]

    @functools.cached_property
    def c_name(self):
        return snake_case(self.name)


class VendorAtomEnv:
    """Static class gathering all enums and atoms required for code generation
    """
    enums: Dict[str, VendorAtomEnum]
    atoms: List[VendorAtom]

    @classmethod
    def len(cls, ll: List):
        return len(ll)

    @classmethod
    def snake_case(cls, s: str):
        return snake_case(s)


def assert_reverse_domain_name_field(msg_dict: Dict[str, DescriptorProto],
                                     atom: DescriptorProto):
    """verify the assumption that reverse_domain_name is also an atom.field
    of type FieldDescriptorProto.TYPE_MESSAGE which we can exclude
    (see make_atom) from the VendorAtomValue list
    """
    reverse_domain_name_idx = [[
        idx
        for idx, ff in enumerate(msg_dict[f.type_name].field)
        if ff.name == "reverse_domain_name"
    ]
                               for f in atom.field
                               if f.type == FieldDescriptorProto.TYPE_MESSAGE]
    for idx_list in reverse_domain_name_idx:
        assert (len(idx_list) == 1 and idx_list[0] == 0)


def get_enum(f: FieldDescriptorProto):
    if f.type == FieldDescriptorProto.TYPE_ENUM:
        return VendorAtomEnv.enums[f.type_name]
    return None


def make_atom(msg_dict: Dict[str, DescriptorProto],
              field: FieldDescriptorProto):
    """Each field in the Atom message, pointing to
    a message, are atoms for which we need to generate the
    stats_log function.
    The `field.number` here is the atomId uniquely
    identifying the VendorAtom
    All fields except the reverse_domain_name field are
    added as VendorAtomValue.
    """
    assert field.type == FieldDescriptorProto.TYPE_MESSAGE
    return VendorAtom(msg_dict[field.type_name].name, field.number, [
        VendorAtomValue(name=ff.name,
                        tag=VendorAtomValueTag.get_tag(ff.label, ff.type,
                                                       ff.type_name),
                        enum=get_enum(ff),
                        idx=idx - 1)
        for idx, ff in enumerate(msg_dict[field.type_name].field)
        if ff.name != "reverse_domain_name"
    ])


def process_file(proto_file: FileDescriptorProto,
                 response: plugin.CodeGeneratorResponse,
                 pkg: str = '') -> None:

    def get_uri(type_name: str):
        paths = [x for x in [proto_file.package, type_name] if len(x) > 0]
        return f".{'.'.join(paths)}"

    msg_list = list(proto_file.message_type)
    msg_dict = {get_uri(msg.name): msg for msg in msg_list}

    # Get Atom message and parse its atom fields
    # recording the atomId in the process
    try:
        atom = [msg for msg in msg_list if msg.name == "Atom"][0]
    except IndexError as e:
        raise StatsLogGenerationError(
            f"the Atom message is missing from {proto_file.name}") from e

    VendorAtomEnv.enums = {
        get_uri(e.name): VendorAtomEnum(name=e.name,
                                        values=[
                                            VendorAtomEnumValue(name=ee.name,
                                                                value=ee.number)
                                            for ee in e.value
                                        ]) for e in proto_file.enum_type
    }

    assert_reverse_domain_name_field(msg_dict, atom)
    VendorAtomEnv.atoms = [
        make_atom(msg_dict, field)
        for field in atom.field
        if field.type == FieldDescriptorProto.TYPE_MESSAGE
    ]
    proto_name = Path(proto_file.name).stem
    for item in [
        {"tpl":"metrics_atoms.c.j2", "ext":'c'},
        {"tpl":"metrics_atoms.h.j2", "ext":'h'},
    ]:
        tm = jinja_template_env.get_template(item["tpl"])
        tm_env = {"env":VendorAtomEnv}
        rendered = tm.render(**tm_env)
        file = response.file.add()
        file_path = pkg.split('/')
        if item['ext'] == 'h':
            file_path.insert(0, 'include')
        file_path.append(f"{proto_name}.{item['ext']}")
        file.name = os.path.join(*file_path)
        file.content = rendered


def process_data(data: bytes, pkg: str = '') -> None:
    request = plugin.CodeGeneratorRequest()
    request.ParseFromString(data)

    dump_input_file = None
    options = request.parameter.split(',') if request.parameter else []
    for opt in options:
        match opt.split(':'):
            case ["pkg", value]:
                pkg = value
            case ["dump-input", value]:
                dump_input_file = value
            case [""]:
                pass
            case other:
                raise ValueError(f"unknown parameter {other}")

    if dump_input_file:
        # store the pb file for easy debug
        with open(dump_input_file, "wb") as f_data:
            f_data.write(data)

    # Create a response
    response = plugin.CodeGeneratorResponse()

    for proto_file in request.proto_file:
        process_file(proto_file, response, pkg)

    # Serialize response and write to stdout
    output = response.SerializeToString()

    # Write to stdout per the protoc plugin expectation
    # (protoc consumes this output)
    sys.stdout.buffer.write(output)


def main() -> None:
    data = sys.stdin.buffer.read()
    process_data(data)


if __name__ == "__main__":
    main()
