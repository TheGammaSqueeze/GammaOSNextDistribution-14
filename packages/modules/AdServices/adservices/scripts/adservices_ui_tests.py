#  Copyright (C) 2023 The Android Open Source Project
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

import unittest
import xml.etree.ElementTree as ET
import os

from generate_adservices_public_xml import AdServicesUiUtil

class AdServiceUiTests(unittest.TestCase):

    TEST_STRINGS_DIR = 'test_strings.xml'
    TEST_STRINGS_XML = '''
    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="permlab_accessAdServicesTopics">access AdServices Topics API</string>
    <string name="permdesc_accessAdServicesTopics">Allows an application to access AdServices Topics API.</string>
    </resources>
    '''

    TEST_PUBLIC_DIR = 'test_public.xml'
    TEST_PUBLIC_XML = '''
    <resources>
    <public type="string" name="permlab_accessAdServicesTopics" id="0x7f017fff" />
    <public type="string" name="permdesc_accessAdServicesTopics" id="0x7f017ffe" />
    </resources>
    '''

    util = AdServicesUiUtil()

    def _generate_test_files(self):
        test_strings_xml = ET.ElementTree(ET.fromstring(self.TEST_STRINGS_XML))
        test_public_xml = ET.ElementTree(ET.fromstring(self.TEST_PUBLIC_XML))

        test_strings_xml.write(self.TEST_STRINGS_DIR)
        test_public_xml.write(self.TEST_PUBLIC_DIR)

    def _delete_test_files(self):
        os.remove(self.TEST_STRINGS_DIR)
        os.remove(self.TEST_PUBLIC_DIR)

    def _update_strings_xml(self, n):
        root = ET.parse(self.TEST_STRINGS_DIR).getroot()
        for string in [f"testString{i}" for i in range(n)]:
            added_element = ET.SubElement(root, 'string')
            added_element.set('name', string)

        ET.indent(root, space='    ')
        with open(self.TEST_STRINGS_DIR, "w+") as file:
            file.write(ET.tostring(root, encoding="unicode"))

    def test_adding_strings(self):
        self._generate_test_files()

        new_strings_count = 5
        self._update_strings_xml(new_strings_count)
        self.util.update_public_xml(self.TEST_STRINGS_DIR, self.TEST_PUBLIC_DIR)

        old_root = ET.ElementTree(ET.fromstring(self.TEST_PUBLIC_XML)).getroot()
        old_mapping = {node.attrib['name']:node.attrib['id'] for node in old_root}

        root = ET.parse(self.TEST_PUBLIC_DIR).getroot()
        mapping = {node.attrib['name']:node.attrib['id'] for node in root}

        assert(len(old_mapping) + new_strings_count == len(mapping))
        assert(len(mapping) == len(set(mapping.values())))
        for name, _id in mapping.items():
            if name in old_mapping:
                assert(_id == old_mapping[name])


        self._delete_test_files()

if __name__ == '__main__':
    unittest.main()