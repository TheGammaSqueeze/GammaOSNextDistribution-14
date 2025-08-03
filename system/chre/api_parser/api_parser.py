#!/usr/bin/python3
#
# Copyright (C) 2023 The Android Open Source Project
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

import os

from collections import defaultdict
from pyclibrary import CParser

from utils import system_chre_abs_path


class ApiParser:
    """Given a file-specific set of annotations (extracted from JSON annotations file), parses a
    single API header file into data structures suitable for use with code generation. This class
    will contain the parsed representation of the headers when instantiated.
    """

    def __init__(self, json_obj):
        """Initialize and parse the API file described in the provided JSON-derived object.

        :param json_obj: Extracted file-specific annotations from JSON
        """

        self.json = json_obj
        self.structs_and_unions = {}
        self._parse_annotations()
        self._parse_api()

    def _parse_annotations(self):
        # Converts annotations list to a more usable data structure: dict keyed by structure name,
        # containing a dict keyed by field name, containing a list of annotations (as they
        # appear in the JSON). In other words, we can easily get all of the annotations for the
        # "version" field in "chreWwanCellInfoResult" via
        # annotations['chreWwanCellInfoResult']['version']. This is also a defaultdict, so it's safe
        # to access if there are no annotations for this structure + field; it'll just give you
        # an empty list in that case.

        self.annotations = defaultdict(lambda: defaultdict(list))
        for struct_info in self.json['struct_info']:
            for annotation in struct_info['annotations']:
                self.annotations[struct_info['name']
                                 ][annotation['field']].append(annotation)

    def _files_to_parse(self):
        """Returns a list of files to supply as input to CParser"""

        # Input paths for CParser are stored in JSON relative to <android_root>/system/chre
        # Reformulate these to absolute paths, and add in some default includes that we always
        # supply
        chre_project_base_dir = system_chre_abs_path()
        default_includes = ['api_parser/parser_defines.h',
                            'chre_api/include/chre_api/chre/version.h']
        files = default_includes + \
            self.json['includes'] + [self.json['filename']]
        return [os.path.join(chre_project_base_dir, file) for file in files]

    def _parse_structs_and_unions(self):
        # Starts with the root structures (i.e. those that will appear at the top-level in one
        # or more CHPP messages), build a data structure containing all of the information we'll
        # need to emit the CHPP structure definition and conversion code.

        structs_and_unions_to_parse = self.json['root_structs'].copy()
        while len(structs_and_unions_to_parse) > 0:
            type_name = structs_and_unions_to_parse.pop()
            if type_name in self.structs_and_unions:
                continue

            entry = {
                'appears_in': set(),  # Other types this type is nested within
                'dependencies': set(),  # Types that are nested in this type
                'has_vla_member': False,  # True if this type or any dependency has a VLA member
                'members': [],  # Info about each member of this type
            }
            if type_name in self.parser.defs['structs']:
                defs = self.parser.defs['structs'][type_name]
                entry['is_union'] = False
            elif type_name in self.parser.defs['unions']:
                defs = self.parser.defs['unions'][type_name]
                entry['is_union'] = True
            else:
                raise RuntimeError(
                    "Couldn't find {} in parsed structs/unions".format(type_name))

            for member_name, member_type, _ in defs['members']:
                member_info = {
                    'name': member_name,
                    'type': member_type,
                    'annotations': self.annotations[type_name][member_name],
                    'is_nested_type': False,
                }

                if member_type.type_spec.startswith('struct ') or \
                        member_type.type_spec.startswith('union '):
                    member_info['is_nested_type'] = True
                    member_type_name = member_type.type_spec.split(' ')[1]
                    member_info['nested_type_name'] = member_type_name
                    entry['dependencies'].add(member_type_name)
                    structs_and_unions_to_parse.append(member_type_name)

                entry['members'].append(member_info)

                # Flip a flag if this structure has at least one variable-length array member, which
                # means that the encoded size can only be computed at runtime
                if not entry['has_vla_member']:
                    for annotation in self.annotations[type_name][member_name]:
                        if annotation['annotation'] == 'var_len_array':
                            entry['has_vla_member'] = True

            self.structs_and_unions[type_name] = entry

        # Build reverse linkage of dependency chain (i.e. lookup between a type and the other types
        # it appears in)
        for type_name, type_info in self.structs_and_unions.items():
            for dependency in type_info['dependencies']:
                self.structs_and_unions[dependency]['appears_in'].add(
                    type_name)

        # Bubble up "has_vla_member" to types each type it appears in, i.e. if this flag is set to
        # True on a leaf node, then all its ancestors should also have the flag set to True
        for type_name, type_info in self.structs_and_unions.items():
            if type_info['has_vla_member']:
                types_to_mark = list(type_info['appears_in'])
                while len(types_to_mark) > 0:
                    type_to_mark = types_to_mark.pop()
                    self.structs_and_unions[type_to_mark]['has_vla_member'] = True
                    types_to_mark.extend(
                        list(self.structs_and_unions[type_to_mark]['appears_in']))

    def _parse_api(self):
        """
        Parses the API and stores the structs and unions.
        """

        file_to_parse = self._files_to_parse()
        self.parser = CParser(file_to_parse, cache='parser_cache')
        self._parse_structs_and_unions()
