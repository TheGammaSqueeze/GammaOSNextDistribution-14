#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# crowdin_sync.py
#
# Updates Crowdin source translations and pushes translations
# directly to LineageOS' Gerrit.
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

import argparse
import os
import sys

from signal import signal, SIGINT

import download
import from_zip
import gerrit
import upload
import utils
import wiki

_DIR = os.path.dirname(os.path.realpath(__file__))
_COMMITS_CREATED = False
_DONE = False


# ############################################################################ #


def parse_args():
    parser = argparse.ArgumentParser(
        description="Synchronising LineageOS' translations with Crowdin"
    )
    parser.add_argument("-u", "--username", help="Gerrit username")
    parser.add_argument("-b", "--branch", help="LineageOS branch", required=True)
    parser.add_argument("-c", "--config", help="Custom yaml config")
    parser.add_argument(
        "--upload-sources", action="store_true", help="Upload sources to Crowdin"
    )
    parser.add_argument(
        "--upload-translations",
        action="store_true",
        help="Upload translations to Crowdin",
    )
    parser.add_argument(
        "--download", action="store_true", help="Download translations from Crowdin"
    )
    parser.add_argument(
        "-g",
        "--gerrit",
        choices=["abandon", "vote", "submit"],
        help="Abandon, Vote (CR+1/V+1) on or submit (incl. CR+2/V+1) open translation commits",
    )
    parser.add_argument(
        "-m", "--message", help="Specify the gerrit abandon/vote message"
    )
    parser.add_argument(
        "-o", "--owner", help="Specify the owner of the commits to submit"
    )
    parser.add_argument(
        "-p",
        "--path-to-crowdin",
        help="Path to crowdin executable (will look in PATH by default)",
        default="crowdin",
    )
    parser.add_argument(
        "--unzip", nargs="+", help="Specify a translation zip to treat like a download"
    )
    parser.add_argument(
        "-w",
        "--generate_wiki_list",
        action="store_true",
        help="Get the proofreader list for the wiki"
    )
    return parser.parse_args()


def sig_handler(signal_received, frame):
    global _DONE
    print("")
    print("SIGINT or CTRL-C detected. Exiting gracefully")
    _DONE = True
    exit(0)


# ################################### MAIN ################################### #


def main():
    signal(SIGINT, sig_handler)
    args = parse_args()
    default_branch = args.branch

    username = utils.get_username(args)
    if args.gerrit == "abandon":
        gerrit.abandon(default_branch, username, args.owner, args.message)
        sys.exit(0)
    elif args.gerrit == "submit":
        gerrit.submit(default_branch, username, args.owner)
        sys.exit(0)
    elif args.gerrit == "vote":
        gerrit.vote(default_branch, username, args.owner, args.message)
        sys.exit(0)

    base_path = utils.get_base_path(default_branch)
    config_dict = utils.get_config_dict(args.config, default_branch)

    if args.path_to_crowdin == "crowdin" and not utils.check_dependencies():
        sys.exit(1)

    if args.upload_sources:
        upload.upload_sources_crowdin(default_branch, config_dict, args.path_to_crowdin)
    elif args.upload_translations:
        upload.upload_translations_crowdin(
            default_branch, config_dict, args.path_to_crowdin
        )
    elif args.download:
        xml_files = utils.get_xml_files(base_path, default_branch)
        download.download_crowdin(
            base_path,
            default_branch,
            xml_files,
            username,
            config_dict,
            args.path_to_crowdin,
        )
    elif args.unzip:
        xml_files = utils.get_xml_files(base_path, default_branch)
        from_zip.unzip(args.unzip, base_path, default_branch, xml_files, username)
    elif args.generate_wiki_list:
        wiki.generate_wiki_list(config_dict["files"])

    if download.has_created_commits() or upload.has_uploaded():
        print("\nDone!")
        sys.exit(0)
    else:
        print("\nNothing to commit")
        sys.exit(2)


if __name__ == "__main__":
    main()
