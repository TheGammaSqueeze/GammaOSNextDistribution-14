# Copyright (C) 2020 The Android Open Source Project
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
"""A library containing functions for diffing XML elements."""
import textwrap
from typing import Any, Callable, Dict, Set
import xml.etree.ElementTree as ET
import dataclasses

Element = ET.Element

_INDENT = (' ' * 2)


@dataclasses.dataclass
class Change:
  value_from: str
  value_to: str

  def __repr__(self):
    return f'{self.value_from} -> {self.value_to}'


@dataclasses.dataclass
class ChangeMap:
  """A collection of changes broken down by added, removed and modified.

  Attributes:
    added: A dictionary of string identifiers to the added string.
    removed: A dictionary of string identifiers to the removed string.
    modified: A dictionary of string identifiers to the changed object.
  """
  added: Dict[str, str] = dataclasses.field(default_factory=dict)
  removed: Dict[str, str] = dataclasses.field(default_factory=dict)
  modified: Dict[str, Any] = dataclasses.field(default_factory=dict)

  def __repr__(self):
    ret_str = ''
    if self.added:
      ret_str += 'Added:\n'
      for value in self.added.values():
        ret_str += textwrap.indent(str(value) + '\n', _INDENT)
    if self.removed:
      ret_str += 'Removed:\n'
      for value in self.removed.values():
        ret_str += textwrap.indent(str(value) + '\n', _INDENT)
    if self.modified:
      ret_str += 'Modified:\n'
      for name, value in self.modified.items():
        ret_str += textwrap.indent(name + ':\n', _INDENT)
        ret_str += textwrap.indent(str(value) + '\n', _INDENT * 2)
    return ret_str

  def __bool__(self):
    return bool(self.added) or bool(self.removed) or bool(self.modified)


def element_string(e: Element) -> str:
  return ET.tostring(e).decode(encoding='UTF-8').strip()


def attribute_changes(e1: Element, e2: Element,
                      ignored_attrs: Set[str]) -> ChangeMap:
  """Get the changes in attributes between two XML elements.

  Arguments:
    e1: the first xml element.
    e2: the second xml element.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ChangeMap of attribute changes. Keyed by attribute name.
  """
  changes = ChangeMap()
  attributes = set(e1.keys()) | set(e2.keys())
  for attr in attributes:
    if attr in ignored_attrs:
      continue
    a1 = e1.get(attr)
    a2 = e2.get(attr)
    if a1 == a2:
      continue
    elif not a1:
      changes.added[attr] = a2 or ''
    elif not a2:
      changes.removed[attr] = a1
    else:
      changes.modified[attr] = Change(value_from=a1, value_to=a2)
  return changes


def compare_subelements(
    tag: str,
    p1: Element,
    p2: Element,
    ignored_attrs: Set[str],
    key_fn: Callable[[Element], str],
    diff_fn: Callable[[Element, Element, Set[str]], Any]) -> ChangeMap:
  """Get the changes between subelements of two parent elements.

  Arguments:
    tag: tag name for children element.
    p1: the base parent xml element.
    p2: the parent xml element to compare
    ignored_attrs: a set of attribute names to ignore changes.
    key_fn: Function that takes a subelement and returns a key
    diff_fn: Function that take two subelements and a set of ignored
      attributes, returns the differences

  Returns:
    A ChangeMap object of the changes.
  """
  changes = ChangeMap()
  group1 = {}
  for e1 in p1.findall(tag):
    group1[key_fn(e1)] = e1

  for e2 in p2.findall(tag):
    key = key_fn(e2)
    e1 = group1.pop(key, None)
    if e1 is None:
      changes.added[key] = element_string(e2)
    else:
      echange = diff_fn(e1, e2, ignored_attrs)
      if echange:
        changes.modified[key] = echange

  for name, e1 in group1.items():
    changes.removed[name] = element_string(e1)

  return changes
