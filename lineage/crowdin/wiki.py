#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# utils.py
#
# Helper script to get crowdin information for the wiki
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
import requests

from html import escape

import utils

crowdin_url = "https://api.crowdin.com/api/v2/projects"
token = None

# These people are global proofreaders / managers and wouldn't appear for their languages otherwise
users_to_append = {
    "de": [
        {"username": "maniac103", "fullName": "Danny Baumann"},
        {"username": "BadDaemon", "fullName": "Michael W"},
    ],
    "el": [{"username": "mikeioannina", "fullName": "Michael Bestas"}],
    "en-AU": [{"username": "forkbomb", "fullName": "Simon Shields"}],
    "en-PT": [{"username": "javelinanddart", "fullName": "Joseph Annareddy"}],
    "it": [{"username": "linuxx", "fullName": "Joey Rizzoli"}],
    "nl": [{"username": "eddywitkamp", "fullName": "Eddy Witkamp"}],
}


def generate_wiki_list(config_files):
    print("\nGenerating proofreader list")
    t = utils.start_spinner(True)

    project_ids = get_project_ids(config_files)
    languages = get_languages(project_ids)
    managers = get_managers(project_ids)
    global_proofreaders, proofreaders = get_proofreaders(project_ids, languages)

    utils.stop_spinner(t)

    generate_output(managers, global_proofreaders, proofreaders)


def generate_output(managers, global_proofreaders, proofreaders):
    # Generate actual output
    print("\n")
    print("managers:")
    for m in managers:
        print_user(m)
    print("global_proofreaders:")
    for p in global_proofreaders:
        print_user(p)
    print("languages:")
    for language in sorted(proofreaders):
        names = []
        for u in proofreaders[language]:
            if (
                u["fullName"] is not None
                and len(u["fullName"]) > 0
                and u["fullName"] != u["username"]
            ):
                name = f"{u['fullName']} ({u['username']})"
            else:
                name = f"{u['username']}"
            name = name.replace("_", "\\_")  # We don't want to risk bold or italic text
            names.append(f"'{escape(name)}'")
        print(f"    - name: {language}")
        print(f"      proofreaders: [{', '.join(sorted(names, key=str.casefold))}]")
    exit()


def print_user(user):
    username = user["username"]
    full_name = user["fullName"]
    if username is None or username == "":
        username = full_name
    elif full_name is None or full_name == "":
        full_name = username
    print(f"    - name: {full_name}")
    print(f"      nick: {username}")


def get_project_ids(config_files):
    ids = []
    for f in config_files:
        with open(f, "r") as fh:
            for line in fh.readlines():
                if "project_id" in line:
                    ids.append(int(line.strip("\n").split(": ")[1]))
    return ids


def get_from_api(url):
    resp = requests.get(url, headers=get_headers())
    if resp.status_code != 200:
        print(f"Error retrieving data - {resp.json()}")
        exit(-1)
    return resp.json()


def get_languages(project_ids):
    languages = {}
    for project_id in project_ids:
        json = get_from_api(f"{crowdin_url}/{project_id}")
        target_languages = json["data"]["targetLanguages"]
        for lang in target_languages:
            languages.setdefault(lang["id"], {"name": lang["name"]})
    return languages


def get_headers():
    global token
    if token is None:
        get_access_token()
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    return headers


def get_access_token():
    global token
    token = os.getenv("LINEAGE_CROWDIN_API_TOKEN")
    if token is None:
        print(
            "Could not determine api token, please export LINEAGE_CROWDIN_API_TOKEN to the environment!"
        )
        exit(-1)


def get_managers(project_ids):
    managers = []
    for project_id in project_ids:
        members = get_from_api(f"{crowdin_url}/{project_id}/members?role=manager")
        for data in members["data"]:
            user = {
                "username": data["data"]["username"],
                "fullName": data["data"]["fullName"],
            }
            if user["username"] == "LineageOS":
                continue
            if user not in managers:
                managers.append(user)
    return managers


def get_proofreaders(project_ids, languages):
    global_proofreaders = []
    proofreaders = {}
    # Add the languages
    for key in languages:
        if key not in proofreaders:
            proofreaders.setdefault(key, {"name": languages[key]["name"], "users": []})
    # Append the known proofreaders
    for key in users_to_append:
        for u in users_to_append[key]:
            proofreaders[key]["users"].append(u)
    # Get and append all others
    for project_id in project_ids:
        members = get_from_api(f"{crowdin_url}/{project_id}/members?role=proofreader")
        for data in members["data"]:
            user = {
                "username": data["data"]["username"],
                "fullName": data["data"]["fullName"],
            }
            if "permissions" not in data["data"]:
                if user not in global_proofreaders:
                    global_proofreaders.append(user)
                continue
            languages = [
                lang
                for lang in data["data"]["permissions"]
                if data["data"]["permissions"][lang] == "proofreader"
            ]
            for language in languages:
                # We might have the same user in several projects, only add them once
                if user not in proofreaders[language]["users"]:
                    proofreaders[language]["users"].append(user)
    # Cleanup languages that don't have proofreaders
    to_be_deleted = [p for p in proofreaders if len(proofreaders[p]["users"]) == 0]
    for key in to_be_deleted:
        del proofreaders[key]
    proofreaders_final = {}
    for key in proofreaders:
        proofreaders_final.setdefault(
            proofreaders[key]["name"], proofreaders[key]["users"]
        )
    return global_proofreaders, proofreaders_final
