#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# utils.py
#
# Utility functions for crowdin_sync.py and it's dependencies
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

import itertools
import os
import sys

from distutils.util import strtobool
from lxml import etree
from threading import Thread
from time import sleep
from subprocess import Popen, PIPE

_DIR = os.path.dirname(os.path.realpath(__file__))
_DONE = False


def run_subprocess(cmd, silent=False, show_spinner=False):
    t = start_spinner(show_spinner)
    p = Popen(cmd, stdout=PIPE, stderr=PIPE, universal_newlines=True)
    comm = p.communicate()
    exit_code = p.returncode
    if exit_code != 0 and not silent:
        print(
            "There was an error running the subprocess.\n"
            "cmd: %s\n"
            "exit code: %d\n"
            "stdout: %s\n"
            "stderr: %s" % (cmd, exit_code, comm[0], comm[1]),
            file=sys.stderr,
        )
    stop_spinner(t)
    return comm, exit_code


def start_spinner(show_spinner):
    global _DONE
    _DONE = False
    if not show_spinner:
        return None
    t = Thread(target=spin_cursor)
    t.start()
    return t


def stop_spinner(t):
    global _DONE
    if t is None:
        return
    _DONE = True
    t.join(1)


def spin_cursor():
    global _DONE
    spinner = itertools.cycle([".", "..", "...", "....", "....."])
    while not _DONE:
        sys.stdout.write("\x1b[1K\r")
        output = next(spinner)
        sys.stdout.write(output)
        sys.stdout.flush()
        sleep(0.5)
    sys.stdout.write("\x1b[1K\r     ")


def check_run(cmd):
    p = Popen(cmd, stdout=sys.stdout, stderr=sys.stderr)
    ret = p.wait()
    if ret != 0:
        joined = " ".join(cmd)
        print(f"Failed to run cmd: {joined}", file=sys.stderr)
        sys.exit(ret)


def find_xml(base_path):
    for dp, dn, file_names in os.walk(base_path):
        for f in file_names:
            if os.path.splitext(f)[1] == ".xml":
                yield os.path.join(dp, f)


def get_username(args):
    username = args.username
    if (args.gerrit or args.download or args.unzip) and username is None:
        # try getting the username from git
        msg, code = run_subprocess(
            ["git", "config", "--get", "review.review.lineageos.org.username"],
            silent=True,
        )
        has_username = False
        if code == 0:
            username = msg[0].strip("\n")
            if username != "":
                has_username = user_prompt(
                    f"Argument -u/--username was not specified but found '{username}', "
                    f"continue?"
                )
            else:
                print("Argument -u/--username is required!")
        if not has_username:
            sys.exit(1)
    return username


def user_prompt(question):
    while True:
        user_input = input(question + " [y/n]: ")
        try:
            return bool(strtobool(user_input))
        except ValueError:
            print("Please use y/n or yes/no.\n")


# ################################# PREPARE ################################## #


def check_dependencies():
    # Check for Java version of crowdin
    cmd = ["which", "crowdin"]
    msg, code = run_subprocess(cmd, silent=True)
    if code != 0:
        print("You have not installed crowdin.", file=sys.stderr)
        return False
    return True


def load_xml(x):
    try:
        return etree.parse(x)
    except etree.XMLSyntaxError:
        print(f"Malformed {x}", file=sys.stderr)
        return None
    except Exception:
        print(f"You have no {x}", file=sys.stderr)
        return None


def check_files(files):
    for f in files:
        if not os.path.isfile(f):
            print(f"You have no {f}.", file=sys.stderr)
            return False
    return True


def get_base_path(default_branch):
    base_path_branch_suffix = default_branch.replace("-", "_").replace(".", "_").upper()
    base_path_env = f"LINEAGE_CROWDIN_BASE_PATH_{base_path_branch_suffix}"
    base_path = os.getenv(base_path_env)
    if base_path is None:
        cwd = os.getcwd()
        print(f"You have not set {base_path_env}. Defaulting to {cwd}")
        base_path = cwd
    if not os.path.isdir(base_path):
        print(f"{base_path_env} is not a real directory: {base_path}")
        sys.exit(1)

    return base_path


def get_xml_files(base_path, default_branch):
    xml_android = load_xml(x=f"{base_path}/android/default.xml")
    if xml_android is None:
        sys.exit(1)

    xml_extra = load_xml(x=f"{_DIR}/config/{default_branch}_extra_packages.xml")
    if xml_extra is None:
        sys.exit(1)

    xml_snippet = load_xml(x=f"{base_path}/android/snippets/lineage.xml")
    if xml_snippet is not None:
        xml_files = (xml_android, xml_snippet, xml_extra)
    else:
        xml_files = (xml_android, xml_extra)

    return xml_files


def get_config_dict(config, default_branch):
    config_dict = {}
    if config:
        config_dict["headers"] = ["custom config"]
        config_dict["files"] = [f"{_DIR}/config/{config}"]
    else:
        config_dict["headers"] = [
            "AOSP supported languages",
            "non-AOSP supported languages",
        ]
        config_dict["files"] = [
            f"{_DIR}/config/{default_branch}.yaml",
            f"{_DIR}/config/{default_branch}_aosp.yaml",
        ]
    if not check_files(config_dict["files"]):
        sys.exit(1)
    return config_dict


def get_gerrit_base_cmd(username):
    cmd = ["ssh", "-p", "29418", f"{username}@review.lineageos.org", "gerrit"]
    return cmd
