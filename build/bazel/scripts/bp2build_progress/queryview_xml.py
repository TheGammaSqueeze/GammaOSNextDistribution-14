#!/usr/bin/env python3

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generate queryview xml data for testing purposes."""

import xml.etree.ElementTree as ElementTree


def make_module(
    full_name,
    name,
    kind,
    variant='',
    dep_names=[],
    soong_module_type=None,
    srcs=None,
):
  rule = ElementTree.Element('rule', attrib={'class': kind, 'name': full_name})
  ElementTree.SubElement(
      rule, 'string', attrib={'name': 'soong_module_name', 'value': name}
  )
  ElementTree.SubElement(
      rule, 'string', attrib={'name': 'soong_module_variant', 'value': variant}
  )
  if soong_module_type:
    ElementTree.SubElement(
        rule,
        'string',
        attrib={'name': 'soong_module_type', 'value': soong_module_type},
    )
  for dep in dep_names:
    ElementTree.SubElement(rule, 'rule-input', attrib={'name': dep})

  if not srcs:
    return rule

  src_element = ElementTree.SubElement(rule, 'list', attrib={'name': 'srcs'})
  for src in srcs:
    ElementTree.SubElement(src_element, 'string', attrib={'value': src})

  return rule


def make_graph(modules):
  graph = ElementTree.Element('query', attrib={'version': '2'})
  graph.extend(modules)
  return graph
