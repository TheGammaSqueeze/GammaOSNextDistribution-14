#!/usr/bin/python3
#
# Copyright (C) 2021 Paul Keith <javelinanddart@gmail.com>
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 and
# only version 2 as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.

import errno
from glob import glob
import os
import subprocess
import sys
from xml.etree import ElementTree

# Get external packages
try:
    from parse import parse
except ImportError:
    print('Please install the "parse" package via pip3.')
    exit(errno.ENOPKG)
try:
    import requests
except ImportError:
    print('Please install the "requests" package via pip3.')
    exit(errno.ENOPKG)

# Change working directory to the location of this script
# This fixes relative path references when calling this script from
# outside of the directory containing it
os.chdir(sys.path[0])

# Definitions for privileged permissions
ANDROID_MANIFEST_XML = \
    'https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-21.0/core/res/AndroidManifest.xml'
ANDROID_XML_NS = '{http://schemas.android.com/apk/res/android}'
privileged_permissions = set()
privileged_permission_mask = {'privileged', 'signature'}

# Get AndroidManifest.xml
req = requests.get(ANDROID_MANIFEST_XML)

# Parse AndroidManifest.xml to get signature|privileged permissions
root = ElementTree.fromstring(req.text)
for perm in root.findall('permission'):
    # Get name of permission
    name = perm.get(f'{ANDROID_XML_NS}name')
    # Get the protection levels on the permission
    levels = set(
        perm.get(f'{ANDROID_XML_NS}protectionLevel').split('|'))
    # Check if the protections include signature and privileged
    levels_masked = levels & privileged_permission_mask
    if len(levels_masked) == len(privileged_permission_mask):
        privileged_permissions.add(name)

# List of partitions to check priv-app permissions on
partitions = ['product', 'system_ext']

# Definitions for privapp-permissions
# Dictionary with structure:
#     partition: permissions_dictionary
# Where permissions_dictionary has the structure:
#     package_name : (set(allowed_permissions), set(requested_permissions))
privapp_permissions_dict = {x: {} for x in partitions}

# Definitions for privapp-permission allowlists
GLOB_XML_STR = '../*/proprietary/{}/etc/permissions/privapp-permissions*.xml'

# Parse allowlists to extract allowed privileged permissions
for partition in partitions:
    # Get pointer to permissions_dictionary for the partition
    perm_dict = privapp_permissions_dict[partition]
    # Loop over all the XMLs in the partition we want
    for allowlist in glob(GLOB_XML_STR.format(partition)):
        # Get root of XML
        tree = ElementTree.parse(allowlist)
        root = tree.getroot()
        # Loop through and find packages
        for package in root.findall('privapp-permissions'):
            name = package.get('package')
            # Create empty entry if it's not in the dictionary
            if name not in perm_dict:
                perm_dict[name] = (set(), set())
            # Get all permissions and add them to dictionary
            for permission in package.findall('permission'):
                perm_dict[name][0].add(permission.get('name'))
            for permission in package.findall('deny-permission'):
                perm_dict[name][0].add(permission.get('name'))

# Definitions for parsing APKs
GLOB_APK_STR = '../*/proprietary/{}/priv-app/*/*.apk'
AAPT_CMD = ['aapt', 'd', 'permissions']

# Extract requested privileged permissions from all priv-app APKs
for partition in partitions:
    # Get pointer to permissions_dictionary for the partition
    perm_dict = privapp_permissions_dict[partition]
    # Loop over all the APKs in the partition we want
    for apk in glob(GLOB_APK_STR.format(partition)):
        # Run 'aapt d permissions' on APK
        aapt_output = subprocess.check_output(AAPT_CMD + [apk],
                                              stderr=subprocess.STDOUT).decode(encoding='UTF-8')
        lines = aapt_output.splitlines()
        # Extract package name from the output
        # Output looks like:
        #     package: my.package.name
        package_name = parse('package: {}', lines[0])[0]
        # Create empty entry if package is not in dict
        if package_name not in perm_dict:
            perm_dict[package_name] = (set(), set())
        # Extract 'uses-permission' lines from the rest of the output
        # Relevant output looks like:
        #     uses-permission: name='permission'
        for line in lines[1:]:
            # Extract permission name and add it to the dictionary if it's
            # one of the privileged permissions we extracted earlier
            if perm_name := parse('uses-permission: name=\'{}\'', line):
                if perm_name[0] in privileged_permissions:
                    perm_dict[package_name][1].add(perm_name[0])

# Keep track of exit code
rc = 0

# Find all the missing permissions
for partition in partitions:
    # Get pointer to permissions_dictionary for the partition
    perm_dict = privapp_permissions_dict[partition]
    # Loop through all the packages and compare permission sets
    for package in perm_dict:
        # Get the sets of permissions
        # Format is (allowed, requested)
        perm_sets = perm_dict[package]
        # Compute the set difference requested - allowed
        # This gives us all the permissions requested that were not allowed
        perm_diff = perm_sets[1] - perm_sets[0]
        # If any permissions are left, set exit code to EPERM and print output
        if len(perm_diff) > 0:
            rc = errno.EPERM
            sys.stderr.write(
                f"Package {package} on partition {partition} is missing these permissions:\n")
            for perm in perm_diff:
                sys.stderr.write(f" - {perm}\n")

# Exit program
exit(rc)
