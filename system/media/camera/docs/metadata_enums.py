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
#

"""
Generates aidl files for enums needed by camera metadata keys
"""

import sys
from datetime import datetime
from metadata_helpers import *
from metadata_parser_xml import *
from os.path import relpath
from os import getcwd

if __name__ == "__main__":
  if len(sys.argv) <= 4:
    print("Usage: %s <filename.xml> <template_name> <output_directory> [<copyright_year>]"
          % (sys.argv[0]), file=sys.stderr)
    sys.exit(1)

  file_name = sys.argv[1]
  template_name = sys.argv[2]
  output_dir = sys.argv[3]
  copyright_year = sys.argv[4] if len(sys.argv) > 4 else str(datetime.now().year)

  parser = MetadataParserXml.create_from_file(file_name)
  metadata = parser.metadata

  for sec in find_all_sections(metadata):
    for entry in remove_hal_non_visible(find_unique_entries(sec)):
      if entry.enum:
        enum_name = entry.name.removeprefix("android.")
        s = enum_name.split(".")
        s = [x[0].capitalize() + x[1:] for x in s]
        enum_name = ''.join(s)
        output_name = output_dir + "/" + enum_name + ".aidl"
        parser.render(template_name, output_name, entry.name, copyright_year)
        print("OK: Generated " + relpath(output_name, getcwd()))

  sys.exit(0)
