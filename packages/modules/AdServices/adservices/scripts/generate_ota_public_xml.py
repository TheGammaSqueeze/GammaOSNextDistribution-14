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

# This script generates and updates the public.xml file for sample ota app automatically.
# Must be run after adding new strings to sample ota app.

ADSERVICES_PUBLIC_RES_FILE = '../apk/publicres/values/public.xml'
OTA_PUBLIC_RES_FILE = '../samples/ota/res/values/public.xml'
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

adservices_dict = {}
adservices_public_xml = ET.parse(ADSERVICES_PUBLIC_RES_FILE).getroot()
for x in adservices_public_xml:
    adservices_dict[x.attrib['name']] = x.attrib['id']

ota_strings_xml = ET.parse(OTA_STRING_FILE).getroot()
public_xml = ET.Element('resources')
for x in ota_strings_xml:
    cur_element = ET.SubElement(public_xml, 'public')
    cur_element.set('type', 'string')
    cur_element.set('name', x.attrib['name'])
    if x.attrib['name'] not in adservices_dict:
        print(f"ERROR: {x.attrib['name']} is not in adservices strings.xml")
        exit(0)
    cur_element.set('id', adservices_dict[x.attrib['name']])

ET.indent(public_xml, space='    ')
if os.path.exists(OTA_PUBLIC_RES_FILE):
    os.remove(OTA_PUBLIC_RES_FILE)
with open(OTA_PUBLIC_RES_FILE, 'w') as f:
    f.write(COPYRIGHT_TEXT)
    f.write(ET.tostring(public_xml, encoding="unicode"))
