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
"""Compares two repo manifest xml files.

Checks to see if the manifests contain same projects. And if those projects
contain the same attributes, linkfile elements and copyfile elements.
"""

import argparse
import sys
import textwrap
from typing import Set
import xml.etree.ElementTree as ET
import dataclasses
from treble.split import xml_diff

Element = ET.Element
Change = xml_diff.Change
ChangeMap = xml_diff.ChangeMap

_SINGLE_NODE_ELEMENTS = ('default', 'manifest-server', 'repo-hooks', 'include')
_INDENT = (' ' * 2)


@dataclasses.dataclass
class ProjectChanges:
  """A collection of changes between project elements.

  Attributes:
    attributes: A ChangeMap of attributes changes. Keyed by attribute name.
    linkfiles: A ChangeMap of linkfile elements changes. Keyed by dest.
    copyfiles: A ChangeMap of copyfile elements changes. Keyed by dest.
  """
  attributes: ChangeMap = dataclasses.field(default_factory=ChangeMap)
  linkfiles: ChangeMap = dataclasses.field(default_factory=ChangeMap)
  copyfiles: ChangeMap = dataclasses.field(default_factory=ChangeMap)

  def __bool__(self):
    return bool(self.attributes) or bool(self.linkfiles) or bool(self.copyfiles)

  def __repr__(self):
    if not self:
      return 'No changes'

    ret_str = ''

    if self.attributes:
      ret_str += 'Attributes:\n'
      ret_str += textwrap.indent(str(self.attributes), _INDENT)
    if self.linkfiles:
      ret_str += 'Link Files:\n'
      ret_str += textwrap.indent(str(self.linkfiles), _INDENT)
    if self.copyfiles:
      ret_str += 'Copy Files:\n'
      ret_str += textwrap.indent(str(self.copyfiles), _INDENT)

    return ret_str


@dataclasses.dataclass
class ManifestChanges:
  """A collection of changes between manifests.

  Attributes:
    projects: A ChangeMap of changes to project elements. Keyed by project path.
    remotes: A ChangeMap of changes to remote elements. Keyed by remote name.
    other: A ChangeMap of changes to other elements. Keyed by element tag.
  """
  projects: ChangeMap = dataclasses.field(default_factory=ChangeMap)
  remotes: ChangeMap = dataclasses.field(default_factory=ChangeMap)
  other: ChangeMap = dataclasses.field(default_factory=ChangeMap)

  def has_changes(self):
    return self.projects or self.remotes or self.other

  def __repr__(self):
    ret_str = 'Project Changes:\n'
    ret_str += (textwrap.indent(str(self.projects) + '\n', _INDENT)
                if self.projects else _INDENT + 'No changes found.\n\n')
    ret_str += 'Remote Changes:\n'
    ret_str += (textwrap.indent(str(self.remotes) + '\n', _INDENT)
                if self.remotes else _INDENT + 'No changes found.\n\n')
    ret_str += 'Other Changes:\n'
    ret_str += (textwrap.indent(str(self.other) + '\n', _INDENT)
                if self.other else _INDENT + 'No changes found.\n\n')

    return ret_str


def subelement_file_changes(tag: str, p1: Element, p2: Element) -> ChangeMap:
  """Get the changes copyfile or linkfile elements between two project elements.

  Arguments:
    tag: The tag of the element.
    p1: the xml element for the base project.
    p2: the xml element for the new roject.

  Returns:
    A ChangeMap of copyfile or linkfile changes. Keyed by dest attribute.
  """
  return xml_diff.compare_subelements(
      tag=tag,
      p1=p1,
      p2=p2,
      ignored_attrs=set(),
      key_fn=lambda x: x.get('dest'),
      diff_fn=xml_diff.attribute_changes)


def project_changes(p1: Element, p2: Element,
                    ignored_attrs: Set[str]) -> ProjectChanges:
  """Get the changes between two project elements.

  Arguments:
    p1: the xml element for the base project.
    p2: the xml element for the new project.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ProjectChanges object of the changes.
  """
  return ProjectChanges(
      attributes=xml_diff.attribute_changes(p1, p2, ignored_attrs),
      linkfiles=subelement_file_changes('linkfile', p1, p2),
      copyfiles=subelement_file_changes('copyfile', p1, p2))


def compare_single_node_elements(manifest_e1: Element, manifest_e2: Element,
                                 ignored_attrs: Set[str]) -> ChangeMap:
  """Get the changes between single element nodes such as <defaults> in a manifest.

  Arguments:
    manifest_e1: the xml element for the base manifest.
    manifest_e2: the xml element for the new manifest.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ChangeMap of changes. Keyed by elements tag name.
  """
  changes = ChangeMap()
  for tag in _SINGLE_NODE_ELEMENTS:
    e1 = manifest_e1.find(tag)
    e2 = manifest_e2.find(tag)
    if e1 is None and e2 is None:
      continue
    elif e1 is None:
      changes.added[tag] = xml_diff.element_string(e2)
    elif e2 is None:
      changes.removed[tag] = xml_diff.element_string(e1)
    else:
      attr_changes = xml_diff.attribute_changes(e1, e2, ignored_attrs)
      if attr_changes:
        changes.modified[tag] = attr_changes
  return changes


def compare_remote_elements(manifest_e1: Element, manifest_e2: Element,
                            ignored_attrs: Set[str]) -> ChangeMap:
  """Get the changes to remote elements between two manifests.

  Arguments:
    manifest_e1: the xml element for the base manifest.
    manifest_e2: the xml element for the new manifest.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ChangeMap of changes to remote elements. Keyed by name attribute.
  """
  return xml_diff.compare_subelements(
      tag='remote',
      p1=manifest_e1,
      p2=manifest_e2,
      ignored_attrs=ignored_attrs,
      key_fn=lambda x: x.get('name'),
      diff_fn=xml_diff.attribute_changes)


def compare_project_elements(manifest_e1, manifest_e2,
                             ignored_attrs: Set[str]) -> ChangeMap:
  """Get the changes to project elements between two manifests.

  Arguments:
    manifest_e1: the xml element for the base manifest.
    manifest_e2: the xml element for the new manifest.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ChangeMap of changes to project elements. Keyed by path/name attribute.
  """
  # Ignore path attribute since it's already keyed on that value and avoid false
  # detection when path == name on one element and path == None on the other.
  project_ignored_attrs = ignored_attrs | set(['path'])
  return xml_diff.compare_subelements(
      tag='project',
      p1=manifest_e1,
      p2=manifest_e2,
      ignored_attrs=project_ignored_attrs,
      key_fn=lambda x: x.get('path', x.get('name')),
      diff_fn=project_changes)


def compare_manifest_elements(manifest_e1, manifest_e2,
                              ignored_attrs: Set[str]) -> ManifestChanges:
  """Get the changes between two manifests xml elements.

  Arguments:
    manifest_e1: the xml element for the base manifest.
    manifest_e2: the xml element for the new manifest.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ManifestChanges.
  """
  return ManifestChanges(
      projects=compare_project_elements(manifest_e1, manifest_e2,
                                        ignored_attrs),
      remotes=compare_remote_elements(manifest_e1, manifest_e2, ignored_attrs),
      other=compare_single_node_elements(manifest_e1, manifest_e2,
                                         ignored_attrs))


def compare_manifest_files(manifest_a: str, manifest_b: str,
                           ignored_attrs: Set[str]) -> ManifestChanges:
  """Get the changes between two manifests files.

  Arguments:
    manifest_a: Path to the base manifest xml file.
    manifest_b: Path to the manifest xml file to compare against.
    ignored_attrs: a set of attribute names to ignore changes.

  Returns:
    A ManifestChanges.
  """
  e1 = ET.parse(manifest_a).getroot()
  e2 = ET.parse(manifest_b).getroot()
  return compare_manifest_elements(
      manifest_e1=e1, manifest_e2=e2, ignored_attrs=ignored_attrs)


def main():
  parser = argparse.ArgumentParser(
      description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--ignored_attributes',
      type=str,
      help='A comma separated list of attributes to ignore when comparing ' +
      'project elements.')
  parser.add_argument('manifest_a', help='Path to the base manifest xml file.')
  parser.add_argument(
      'manifest_b', help='Path to the manifest xml file to compare against.')
  args = parser.parse_args()

  ignored_attributes = set(
      args.ignored_attributes.split(',')) if args.ignored_attributes else set()
  changes = compare_manifest_files(args.manifest_a, args.manifest_b,
                                   ignored_attributes)

  print(changes)
  if changes:
    sys.exit(1)


if __name__ == '__main__':
  main()
