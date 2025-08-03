#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# from_zip.py
#
# Helper script for extracting translations from one or more zips and
# uploading them to LineageOS' gerrit
#
# Copyright (C) 2022 The LineageOS Project
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

import os
import zipfile

from pathlib import Path

import download


def unzip(zip_files, base_path, branch, xml, username):
    print("\nUnzipping files")
    extracted = []
    number = 1

    for zip_file in zip_files:
        if not zipfile.is_zipfile(zip_file):
            print(
                f"WARNING: Specified file is not a valid zip file, skipping '{zip_file}'"
            )
            continue

        print(f"File {number}/{len(zip_files)}")
        with zipfile.ZipFile(zip_file, "r") as my_zip:
            for zip_info in my_zip.infolist():
                filename = zip_info.filename
                if filename.startswith(branch) and filename.endswith(".xml"):
                    p = Path(filename)
                    # get rid of the parent folder
                    filename = os.path.join(*list(p.parts[1:]))
                    zip_info.filename = filename
                    if filename not in extracted:
                        extracted.append(filename)
                    my_zip.extract(zip_info, path=base_path)
        number += 1

    if len(extracted) > 0:
        download.upload_translations_gerrit(extracted, xml, base_path, branch, username)
    else:
        print("Nothing extracted or no new files found!")
