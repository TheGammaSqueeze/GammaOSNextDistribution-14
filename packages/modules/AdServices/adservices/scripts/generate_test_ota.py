#!/usr/bin/env python
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

import xml.etree.ElementTree as ET
import datetime
import os
import re

# This script automatically generates public.xml and strings.xml for ota sample app.
# This is used if we want to update the testing ARSC file in MDD server for tests.
# This creates test strings that have an extra exclamation mark (!).

ADSERVICES_PUBLIC_RES_FILE = '../apk/publicres/values/public.xml'
OTA_PUBLIC_RES_FILE = '../samples/ota/res/values/public.xml'
ADSERVICES_STRING_FILE = '../apk/res/values/strings.xml'
OTA_STRING_FILE = '../samples/ota/res/values/strings.xml'
COPYRIGHT_TEXT = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) {datetime.date.today().year} The Android Open Source Project

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
'''

adservices_public_dict = {}
adservices_string_dict = {}
adservices_public_xml = ET.parse(ADSERVICES_PUBLIC_RES_FILE).getroot()
adservices_strings_xml = ET.parse(ADSERVICES_STRING_FILE).getroot()
for x in adservices_public_xml:
    adservices_public_dict[x.attrib['name']] = x.attrib['id']
for x in adservices_strings_xml:
    adservices_string_dict[x.attrib['name']] = x.text

strings_xml = ET.Element('resources')
strings_xml.set('xmlns:xliff', 'urn:oasis:names:tc:xliff:document:1.2')
public_xml = ET.Element('resources')
for x in adservices_strings_xml:
    cur_element = ET.SubElement(strings_xml, 'string')
    cur_element.set('name',  x.attrib['name'])
    x_str = ET.tostring(x, encoding="unicode")
    if 'xliff:document' in x_str:
        # Has special substitution in string
        cur_text = re.search(r'>(.*?)</string>', x_str).group(1)
        new_str = cur_text.replace('ns0:g','xliff:g')
    elif '@string/' in x.text:
        new_str = adservices_string_dict[x.text[8:]]
    else:
        new_str = x.text
    new_str += "!"
    cur_element.text = new_str

    cur_element = ET.SubElement(public_xml, 'public')
    cur_element.set('type', 'string')
    cur_element.set('name', x.attrib['name'])
    if x.attrib['name'] not in adservices_public_dict:
        print(f"ERROR: {x.attrib['name']} is not in adservices strings.xml")
        exit(0)
    cur_element.set('id', adservices_public_dict[x.attrib['name']])

ET.indent(strings_xml, space='    ')
if os.path.exists(OTA_STRING_FILE):
    os.remove(OTA_STRING_FILE)
with open(OTA_STRING_FILE, 'w') as f:
    f.write(COPYRIGHT_TEXT)
    s = ET.tostring(strings_xml, encoding="unicode")
    s = s.replace('&lt;', '<')
    s = s.replace('&gt;', '>')
    f.write(s)

ET.indent(public_xml, space='    ')
if os.path.exists(OTA_PUBLIC_RES_FILE):
    os.remove(OTA_PUBLIC_RES_FILE)
with open(OTA_PUBLIC_RES_FILE, 'w') as f:
    f.write(COPYRIGHT_TEXT)
    f.write(ET.tostring(public_xml, encoding="unicode"))
