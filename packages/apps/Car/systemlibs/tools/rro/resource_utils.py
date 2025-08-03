#!/usr/bin/env python3
#  Copyright (C) 2021 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os
import re
import sys
try:
    import lxml.etree as etree
except ImportError:
    print("Please install 'lxml' python package and retry. \n"
        + "E.g., you can use 'sudo apt-get install python3-lxml'.")
    sys.exit(1)

class ResourceLocation:
    def __init__(self, file, line=None):
        self.file = file
        self.line = line
    def __str__(self):
        if self.line is not None:
            return self.file + ':' + str(self.line)
        else:
            return self.file

class Resource:
    def __init__(self, name, type, location=None):
        self.name = name
        self.type = type
        self.locations = []
        if location is not None:
            self.locations.append(location)
    def __eq__(self, other):
        if isinstance(other, _Grab):
            return other == self
        return self.name == other.name and self.type == other.type
    def __ne__(self, other):
        if isinstance(other, _Grab):
            return other != self
        return self.name != other.name or self.type != other.type
    def __hash__(self):
        return hash((self.name, self.type))
    def __str__(self):
        result = ''
        for location in self.locations:
            result += str(location) + ': '
        result += '<'+self.type+' name="'+self.name+'"'
        return result + '>'
    def __repr__(self):
        return str(self)

def get_all_resources(resDir, excluded_resource_files=[]):
    excluded_resource_files = [os.path.abspath(file) for file in excluded_resource_files]
    allResDirs = [f for f in os.listdir(resDir) if os.path.isdir(os.path.join(resDir, f))]
    valuesDirs = [f for f in allResDirs if f.startswith('values')]
    fileDirs = [f for f in allResDirs if not f.startswith('values')]
    resources = set()
    # Get the filenames of the all the files in all the fileDirs
    for dir in fileDirs:
        type = dir.split('-')[0]
        for file in os.listdir(os.path.join(resDir, dir)):
            filePath = os.path.abspath(os.path.join(resDir, dir, file))
            if file.endswith('.xml') and filePath not in excluded_resource_files:
                add_resource_to_set(resources,
                                    Resource(file[:-4], type,
                                             ResourceLocation(os.path.join(resDir, dir, file))))
                if dir.startswith("layout"):
                    for resource in get_ids_from_layout_file(os.path.join(resDir, dir, file)):
                        add_resource_to_set(resources, resource)
    for dir in valuesDirs:
        for file in os.listdir(os.path.join(resDir, dir)):
            filePath = os.path.abspath(os.path.join(resDir, dir, file))
            if file.endswith('.xml') and filePath not in excluded_resource_files:
                for resource in get_resources_from_single_file(os.path.join(resDir, dir, file),
                                                               dir != "values"):
                    add_resource_to_set(resources, resource)
    return resources

def get_ids_from_layout_file(filename):
    result = set()
    with open(filename, 'r') as file:
        r = re.compile("@\+id/([a-zA-Z0-9_]+)")
        for i in r.findall(file.read()):
            add_resource_to_set(result, Resource(i, 'id', ResourceLocation(filename)))
    return result

def get_resources_from_single_file(filename, ignore_strings=False):
    doc = etree.parse(filename)
    root = doc.getroot()
    result = set()
    for resource in root:
        if resource.tag is etree.Comment:
            continue
        if resource.tag == 'declare-styleable':
            for attr in resource:
                resName = attr.get('name')
                # Skip resources beginning with 'android:' as they are part of the framework
                # resources. This script finds only the app's resources.
                if resName is None or resName.startswith('android:'):
                    continue
                resType = "attr"
                add_resource_to_set(result, Resource(resName, resType, ResourceLocation(filename, attr.sourceline)))
            continue
        resName = resource.get('name')
        resType = resource.tag
        if resType == "string-array" or resType == "integer-array":
            resType = "array"
        if resource.tag == 'item' or resource.tag == 'public':
            resType = resource.get('type')
        if (resType == 'string' or resType == 'plurals') and ignore_strings:
            continue
        if resType == 'overlayable':
            for policy in resource:
                for overlayable in policy:
                    resName = overlayable.get('name')
                    resType = overlayable.get('type')
                    add_resource_to_set(result, Resource(resName, resType,
                                                         ResourceLocation(filename, resource.sourceline)))
        else:
            add_resource_to_set(result, Resource(resName, resType,
                                                 ResourceLocation(filename, resource.sourceline)))
    return result

# Used to get objects out of sets
class _Grab:
    def __init__(self, value):
        self.search_value = value
    def __hash__(self):
        return hash(self.search_value)
    def __eq__(self, other):
        if self.search_value == other:
            self.actual_value = other
            return True
        return False

def add_resource_to_set(resourceset, resource):
    if (resource.name == None):
        return
    grabber = _Grab(resource)
    if grabber in resourceset:
        grabber.actual_value.locations.extend(resource.locations)
    else:
        resourceset.update([resource])

def merge_resources(set1, set2):
    for resource in set2:
        add_resource_to_set(set1, resource)

def get_androidx_resources():
    # source: https://android.googlesource.com/platform/frameworks/opt/sherpa/+/studio-3.0/constraintlayout/src/main/res/values/attrs.xml
    resources = set()
    add_resource_to_set(resources, Resource('layout_optimizationLevel', 'attr'))
    add_resource_to_set(resources, Resource('constraintSet', 'attr'))
    add_resource_to_set(resources, Resource('barrierDirection', 'attr'))
    add_resource_to_set(resources, Resource('constraint_referenced_ids', 'attr'))
    add_resource_to_set(resources, Resource('chainUseRtl', 'attr'))
    add_resource_to_set(resources, Resource('title', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintGuide_begin', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintGuide_end', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintGuide_percent', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintLeft_toLeftOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintLeft_toRightOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintRight_toLeftOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintRight_toRightOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintTop_toTopOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintTop_toBottomOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintBottom_toTopOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintBottom_toBottomOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintBaseline_toBaselineOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintStart_toEndOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintStart_toStartOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintEnd_toStartOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintEnd_toEndOf', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginLeft', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginTop', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginRight', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginBottom', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginStart', 'attr'))
    add_resource_to_set(resources, Resource('layout_goneMarginEnd', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHorizontal_bias', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintVertical_bias', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintWidth_default', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHeight_default', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintWidth_min', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintWidth_max', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintWidth_percent', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHeight_min', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHeight_max', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHeight_percent', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintLeft_creator', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintTop_creator', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintRight_creator', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintBottom_creator', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintBaseline_creator', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintDimensionRatio', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHorizontal_weight', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintVertical_weight', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintHorizontal_chainStyle', 'attr'))
    add_resource_to_set(resources, Resource('layout_constraintVertical_chainStyle', 'attr'))
    add_resource_to_set(resources, Resource('layout_editor_absoluteX', 'attr'))
    add_resource_to_set(resources, Resource('layout_editor_absoluteY', 'attr'))
    return resources
