# -*- coding: utf-8 -*-
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Utility for setting the /etc/lsb-release file of an image."""

from __future__ import print_function


# LSB keys:
# Set google-specific version numbers:
# CHROMEOS_RELEASE_BOARD is the target board identifier.
# CHROMEOS_RELEASE_BRANCH_NUMBER is the ChromeOS branch number
# CHROMEOS_RELEASE_BUILD_NUMBER is the ChromeOS build number
# CHROMEOS_RELEASE_BUILD_TYPE is the type of build (official, from developers,
# etc..)
# CHROMEOS_RELEASE_CHROME_MILESTONE is the Chrome milestone (also named Chrome
#   branch).
# CHROMEOS_RELEASE_DESCRIPTION is the version displayed by Chrome; see
#   chrome/browser/chromeos/chromeos_version_loader.cc.
# CHROMEOS_RELEASE_NAME is a human readable name for the build.
# CHROMEOS_RELEASE_PATCH_NUMBER is the patch number for the current branch.
# CHROMEOS_RELEASE_TRACK and CHROMEOS_RELEASE_VERSION are used by the software
#   update service.
# CHROMEOS_RELEASE_KEYSET is the named of the keyset used to sign this build.
# TODO(skrul):  Remove GOOGLE_RELEASE once Chromium is updated to look at
#   CHROMEOS_RELEASE_VERSION for UserAgent data.
LSB_KEY_NAME = 'CHROMEOS_RELEASE_NAME'
LSB_KEY_AUSERVER = 'CHROMEOS_AUSERVER'
LSB_KEY_DEVSERVER = 'CHROMEOS_DEVSERVER'
LSB_KEY_TRACK = 'CHROMEOS_RELEASE_TRACK'
LSB_KEY_BUILD_TYPE = 'CHROMEOS_RELEASE_BUILD_TYPE'
LSB_KEY_DESCRIPTION = 'CHROMEOS_RELEASE_DESCRIPTION'
LSB_KEY_BOARD = 'CHROMEOS_RELEASE_BOARD'
LSB_KEY_KEYSET = 'CHROMEOS_RELEASE_KEYSET'
LSB_KEY_UNIBUILD = 'CHROMEOS_RELEASE_UNIBUILD'
LSB_KEY_BRANCH_NUMBER = 'CHROMEOS_RELEASE_BRANCH_NUMBER'
LSB_KEY_BUILD_NUMBER = 'CHROMEOS_RELEASE_BUILD_NUMBER'
LSB_KEY_CHROME_MILESTONE = 'CHROMEOS_RELEASE_CHROME_MILESTONE'
LSB_KEY_PATCH_NUMBER = 'CHROMEOS_RELEASE_PATCH_NUMBER'
LSB_KEY_VERSION = 'CHROMEOS_RELEASE_VERSION'
LSB_KEY_BUILDER_PATH = 'CHROMEOS_RELEASE_BUILDER_PATH'
LSB_KEY_GOOGLE_RELEASE = 'GOOGLE_RELEASE'
LSB_KEY_APPID_RELEASE = 'CHROMEOS_RELEASE_APPID'
LSB_KEY_APPID_BOARD = 'CHROMEOS_BOARD_APPID'
LSB_KEY_APPID_CANARY = 'CHROMEOS_CANARY_APPID'
LSB_KEY_ARC_VERSION = 'CHROMEOS_ARC_VERSION'
LSB_KEY_ARC_ANDROID_SDK_VERSION = 'CHROMEOS_ARC_ANDROID_SDK_VERSION'

CANARY_APP_ID = '{90F229CE-83E2-4FAF-8479-E368A34938B1}'
