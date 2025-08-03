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
"""Test XML diff."""
import unittest
import xml.etree.ElementTree as ET

import xml_diff


class XmlDiffTest(unittest.TestCase):

  def test_attribute_changes(self):
    e1 = ET.fromstring('<node attr1="hello" attr2="hello2" ignored="me"/>')
    e2 = ET.fromstring('<node attr3="hello3" attr2="bye2"/>')
    changes = xml_diff.attribute_changes(e1, e2, set(['ignored']))
    self.assertEqual(
        xml_diff.ChangeMap(
            added={'attr3': 'hello3'},
            removed={'attr1': 'hello'},
            modified={'attr2': xml_diff.Change('hello2', 'bye2')}), changes)

  def test_compare_subelements(self):
    p1 = ET.fromstring("""<parent>
      <tag1 attr="newfile2" attrkey="notneeded" />
      <tag1 attr="oldfile1" attrkey="dest1" />
      <tag2 attr="oldfile2" attrkey="dest2" />
    </parent>
    """)
    p2 = ET.fromstring("""<parent>
      <tag1 attr="newfile1" attrkey="dest1" />
      <tag2 attr="newfile2" attrkey="dest2" />
      <tag2 attr="somefile" attrkey="addedfile" />
    </parent>
    """)

    changes = xml_diff.compare_subelements(
        tag='tag1',
        p1=p1,
        p2=p2,
        ignored_attrs=set(),
        key_fn=lambda x: x.get('attrkey'),
        diff_fn=xml_diff.attribute_changes)
    self.assertEqual(changes.added, {})
    self.assertEqual(
        changes.removed,
        {'notneeded': '<tag1 attr="newfile2" attrkey="notneeded" />'})
    self.assertEqual(
        changes.modified, {
            'dest1':
                xml_diff.ChangeMap(
                    modified={'attr': xml_diff.Change('oldfile1', 'newfile1')})
        })
