#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# upload.py
#
# Helper script for uploading translation and translation source
# to crowdin
#
# Copyright (C) 2014-2016 The CyanogenMod Project
# Copyright (C) 2017-2022 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import utils
import sys

_HAS_UPLOADED = False


def upload_sources_crowdin(branch, config_dict, crowdin_path):
    global _HAS_UPLOADED
    for i, cfg in enumerate(config_dict["files"]):
        print(f"\nUploading sources to Crowdin ({config_dict['headers'][i]})")
        cmd = [
            crowdin_path,
            "upload",
            "sources",
            f"--branch={branch}",
            f"--config={cfg}",
        ]
        comm, ret = utils.run_subprocess(cmd, show_spinner=True)
        if ret != 0:
            print(f"Failed to upload:\n{comm[1]}", file=sys.stderr)
            sys.exit(1)
    _HAS_UPLOADED = True


def upload_translations_crowdin(branch, config_dict, crowdin_path):
    global _HAS_UPLOADED
    for i, cfg in enumerate(config_dict["files"]):
        print(f"\nUploading translations to Crowdin ({config_dict['headers'][i]})")
        cmd = [
            crowdin_path,
            "upload",
            "translations",
            f"--branch={branch}",
            "--no-translate-hidden",
            "--import-eq-suggestions",
            "--auto-approve-imported",
            f"--config={cfg}",
        ]
        comm, ret = utils.run_subprocess(cmd, show_spinner=True)
        if ret != 0:
            print(f"Failed to upload:\n{comm[1]}", file=sys.stderr)
            sys.exit(1)
    _HAS_UPLOADED = True


def has_uploaded():
    return _HAS_UPLOADED
