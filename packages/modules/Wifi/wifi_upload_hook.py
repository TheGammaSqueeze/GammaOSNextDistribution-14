#!/usr/bin/python3

#  Copyright (C) 2022 The Android Open Source Project
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

from __future__ import print_function

from argparse import ArgumentParser
import subprocess
import sys

BASE_DIR = "service/ServiceWifiResources/res/"
OVERLAY_FILE = BASE_DIR + "values/overlayable.xml"
CONFIG_FILE = BASE_DIR + "values/config.xml"
STRING_FILE = BASE_DIR + "values/strings.xml"
STYLES_FILE = BASE_DIR + "values/styles.xml"
DRAWABLE_DIR = BASE_DIR + "drawable/"
LAYOUT_DIR = BASE_DIR + "layout/"

BASE_WIFI_SERVICE_DIR = "service/java/com/android/server/wifi/"
XML_UTIL_FILE = BASE_WIFI_SERVICE_DIR + "util/XmlUtil.java"
CLOUD_BACKUP_RESTORE_FILE = BASE_WIFI_SERVICE_DIR + "WifiBackupRestore.java"
CLOUD_BACKUP_PARSER_FILE = BASE_WIFI_SERVICE_DIR + "WifiBackupDataV1Parser.java"

def is_commit_msg_valid(commit_msg, checkResource, checkXmlTag):
    isValid = True
    for line in commit_msg.splitlines():
        line = line.strip().lower()
        if checkResource:
            if line.startswith('updated-overlayable'):
                isValid = True
                checkResource = False
        if checkXmlTag:
            isValid = False
            if line.startswith('reviewed-cloud-b&r'):
                isValid = True
                checkXmlTag = False

    return isValid

def is_in_aosp():
    branches = subprocess.check_output(['git', 'branch', '-vv']).splitlines()

    for branch in branches:
        # current branch starts with a '*'
        if branch.startswith(b'*'):
            return b'[aosp/' in branch

    # otherwise assume in AOSP
    return True

def get_changed_resource_file(commit_files):
    for commit_file in commit_files:
        if commit_file == STRING_FILE:
            return STRING_FILE
        if commit_file == CONFIG_FILE:
            return commit_file
        if commit_file == STYLES_FILE:
            return commit_file
        if commit_file.startswith(DRAWABLE_DIR):
            return commit_file
        if commit_file.startswith(LAYOUT_DIR):
            return commit_file
    return None

def get_changed_xml_related_file(commit_files):
    for commit_file in commit_files:
        if commit_file == XML_UTIL_FILE:
            return commit_file
        if commit_file == CLOUD_BACKUP_RESTORE_FILE:
            return commit_file
        if commit_file == CLOUD_BACKUP_PARSER_FILE:
            return commit_file
    return None

def is_commit_msg_has_translation_bug_id(commit_msg):
    for line in commit_msg.splitlines():
        line = line.strip().lower()
        if line.startswith('bug: 294871353'):
            return True
    return False

def is_xml_tag_in_commits():
    cmd_run = subprocess.Popen('git show | grep -iE "XML_TAG"',
        shell=True,stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    out, err = cmd_run.communicate()
    for line in out.splitlines():
        if line.startswith(b'+') or line.startswith(b'-'):
            print('This commit has xml tag changed: "{changed_line}".'.format(changed_line=line.decode("utf-8")))
            print()
            return True
    return False

def main():
    parser = ArgumentParser(description='Check if the dependency file has been updated')
    parser.add_argument('commit_msg', type=str, help='commit message')
    parser.add_argument('commit_files', type=str, nargs='*', help='files changed in the commit')
    args = parser.parse_args()

    commit_msg = args.commit_msg
    commit_files = args.commit_files
    if is_in_aosp():
        return 0

    changed_resource_file = get_changed_resource_file(commit_files)

    if changed_resource_file:
        if changed_resource_file == STRING_FILE:
            if not is_commit_msg_has_translation_bug_id(commit_msg):
                print('This commit has changed: "{changed_file}".'.format(changed_resource_file=changed_resource_file))
                print()
                print('Please add the following line to your commit message')
                print()
                print('Bug: 294871353')
                print()
                return 1

        if not is_commit_msg_valid(commit_msg, True, False):
            print('This commit has changed: "{changed_file}".'.format(changed_file=changed_resource_file))
            print()
            print('If this change added/changed/removed overlayable resources used by the Wifi Module, ')
            print('please update the "{overlay_file}".'.format(overlay_file=OVERLAY_FILE))
            print('and acknowledge you have done so by adding this line to your commit message:')
            print()
            print('Updated-Overlayable: TRUE')
            print()
            print('Otherwise, please explain why the Overlayable does not need to be updated:')
            print()
            print('Updated-Overlayable: Not applicable - changing default value')
            print()
            return 1
    changed_xml_related_file = get_changed_xml_related_file(commit_files)
    if changed_xml_related_file and is_xml_tag_in_commits():
        if not is_commit_msg_valid(commit_msg, False, True):
            print('This commit has changed: "{changed_file}".'.format(changed_file=changed_xml_related_file))
            print()
            print('If this change added/changed/removed xml resources used by the Wifi Module, ')
            print('please review if it may break/miss the cloud B&R , check "{b_r_file}".'.format(b_r_file=CLOUD_BACKUP_PARSER_FILE))
            print('and acknowledge you have done so by adding this line to your commit message:')
            print()
            print('Reviewed-Cloud-B&R: TRUE')
            print()
            print('Otherwise, please explain why the cloud B&R does not need to be updated:')
            print()
            print('Reviewed-Cloud-B&R: Not applicable - not xml format change')
            print()
            return 1

    return 0

if __name__ == '__main__':
    exit_code = main()
    sys.exit(exit_code)
