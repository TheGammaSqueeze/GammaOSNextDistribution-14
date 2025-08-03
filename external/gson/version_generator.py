#!/usr/bin/python3

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

# Script that generates the GsonBuildConfig.java file based on the version in
# pom.xml. It expects paths to pom.xml and the GsonBuildConfig.java template as
# arguments in any order. It emits the finished file to the standard output.

import sys
import xml.etree.ElementTree as ET


def main():
  pom_path = next(path for path in sys.argv[1:] if path.endswith(".xml"))
  java_path = next(path for path in sys.argv[1:] if path.endswith(".java"))
  pom_file = open(pom_path, "r")
  pom_xml = ET.parse(pom_file)
  version = pom_xml.getroot().find("{http://maven.apache.org/POM/4.0.0}version").text
  java_file = open(java_path, "r")
  print(java_file.read().replace("${project.version}", version))

main()
