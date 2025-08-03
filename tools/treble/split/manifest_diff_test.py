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
"""Test manifest diff."""
import unittest
import xml.etree.ElementTree as ET

import manifest_diff


class ManifestDiffTest(unittest.TestCase):

  def _assertEqualCanonical(self, change1, change2):
    def _canonicalize(change):
      return {
          identifier : ' '.join(sorted(value.split(' ')))
          for identifier, value in change.items()
      }
    return self.assertEqual(_canonicalize(change1), _canonicalize(change2))

  def test_project_changes(self):
    p1 = ET.fromstring("""<project attr1="hello">
      <linkfile src="newfile2" dest="notneeded" />
      <linkfile src="oldfile1" dest="dest1" />
      <copyfile src="oldfile2" dest="dest2" />
    </project>
    """)
    p2 = ET.fromstring("""<project>
      <linkfile src="newfile1" dest="dest1" />
      <copyfile src="newfile2" dest="dest2" />
      <copyfile src="somefile" dest="addedfile" />
    </project>
    """)
    changes = manifest_diff.project_changes(p1, p2, set())
    self.assertEqual(changes.linkfiles.added, {})
    self._assertEqualCanonical(
        changes.linkfiles.removed,
        {'notneeded': '<linkfile src="newfile2" dest="notneeded" />'})
    self.assertEqual(
        changes.linkfiles.modified, {
            'dest1':
                manifest_diff.ChangeMap(modified={
                    'src': manifest_diff.Change('oldfile1', 'newfile1')
                })
        })
    self._assertEqualCanonical(
        changes.copyfiles.added,
        {'addedfile': '<copyfile src="somefile" dest="addedfile" />'})
    self.assertEqual(changes.copyfiles.removed, {})
    self.assertEqual(
        changes.copyfiles.modified, {
            'dest2':
                manifest_diff.ChangeMap(modified={
                    'src': manifest_diff.Change('oldfile2', 'newfile2')
                })
        })
    self.assertEqual(
        changes.attributes,
        manifest_diff.ChangeMap(
            added={}, removed={'attr1': 'hello'}, modified={}))

  def test_project_changes_same(self):
    p1 = ET.fromstring("""<project attr1="hello">
      <linkfile src="newfile2" dest="notneeded" />
      <linkfile src="oldfile1" dest="dest1" />
      <copyfile src="oldfile2" dest="dest2" />
    </project>
    """)
    changes = manifest_diff.project_changes(p1, p1, set())
    self.assertFalse(changes)

  def test_compare_single_node_elements(self):
    m1 = ET.fromstring("""<manifest>
      <default revision='dev' remote='aosp' />
      <repo-hooks />
    </manifest>
    """)
    m2 = ET.fromstring("""<manifest>
      <default revision='release' />
    </manifest>
    """)
    changes = manifest_diff.compare_single_node_elements(m1, m2, set())
    self.assertEqual(changes.added, {})
    self._assertEqualCanonical(changes.removed, {'repo-hooks': '<repo-hooks />'})
    self.assertEqual(
        changes.modified, {
            'default':
                manifest_diff.ChangeMap(
                    added={},
                    removed={'remote': 'aosp'},
                    modified={
                        'revision': manifest_diff.Change('dev', 'release')
                    })
        })

  def test_compare_remote_elements(self):
    m1 = ET.fromstring("""<manifest>
      <remote revision="dev" name="aosp" fetch="https://aosp-source.com" />
      <remote name="android" fetch="https://android-source.com" attr="test" />
      <repo-hooks />
    </manifest>
    """)
    m2 = ET.fromstring("""<manifest>
      <remote revision="dev" name="android" fetch="https://android-source.com"
        attr="test2"/>
    </manifest>
    """)
    changes = manifest_diff.compare_remote_elements(m1, m2, set())
    self.assertEqual(changes.added, {})
    self._assertEqualCanonical(
        changes.removed, {
            'aosp':
                '<remote revision="dev" name="aosp" fetch="https://aosp-source.com" />'
        })
    self.assertEqual(
        changes.modified, {
            'android':
                manifest_diff.ChangeMap(
                    added={'revision': 'dev'},
                    removed={},
                    modified={'attr': manifest_diff.Change('test', 'test2')})
        })

  def test_compare_project_elements(self):
    m1 = ET.fromstring("""<manifest>
      <project name="platform/project1" path="system/project1" />
      <project name="platform/project2" path="system/project2" />
      <project name="platform/project3" path="system/project3" />
    </manifest>""")
    m2 = ET.fromstring("""<manifest>
      <project name="platform/project1" path="system/project1" />
      <project name="system/project2" />
      <project name="platform/project4" path="system/project4" />
    </manifest>""")
    changes = manifest_diff.compare_project_elements(m1, m2, set())
    self._assertEqualCanonical(
        changes.added, {
            'system/project4':
                '<project name="platform/project4" path="system/project4" />'
        })
    self._assertEqualCanonical(
        changes.removed, {
            'system/project3':
                '<project name="platform/project3" path="system/project3" />'
        })
    self.assertEqual(
        changes.modified, {
            'system/project2':
                manifest_diff.ProjectChanges(
                    attributes=manifest_diff.ChangeMap(
                        added={},
                        removed={},
                        modified={
                            'name':
                                manifest_diff.Change('platform/project2',
                                                     'system/project2')
                        }))
        })
