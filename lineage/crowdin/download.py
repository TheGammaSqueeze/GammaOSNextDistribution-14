#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# download.py
#
# Helper script for downloading translation source and
# uploading it to LineageOS' gerrit
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

import git
import os
import re
import shutil
import sys

from lxml import etree

import utils

_COMMITS_CREATED = False


def download_crowdin(base_path, branch, xml, username, config_dict, crowdin_path):
    extracted = []
    for i, cfg in enumerate(config_dict["files"]):
        print(f"\nDownloading translations from Crowdin ({config_dict['headers'][i]})")
        cmd = [crowdin_path, "download", f"--branch={branch}", f"--config={cfg}"]
        comm, ret = utils.run_subprocess(cmd, show_spinner=True)
        if ret != 0:
            print(f"Failed to download:\n{comm[1]}", file=sys.stderr)
            sys.exit(1)
        extracted += get_extracted_files(comm[0], branch)

    upload_translations_gerrit(extracted, xml, base_path, branch, username)


def get_extracted_files(comm, branch):
    # Get all files that Crowdin pushed
    # We need to manually parse the shell output
    extracted = []
    for p in comm.split("\n"):
        if "Extracted" in p:
            path = re.sub(r".*Extracted:\s*", "", p)
            path = path.replace("'", "").replace(f"/{branch}", "")
            extracted.append(path)
    return extracted


def upload_translations_gerrit(extracted, xml, base_path, branch, username):
    print("\nUploading translations to Gerrit")
    items = [x for xml_file in xml for x in xml_file.findall("//project")]
    all_projects = []

    for path in extracted:
        path = path.strip()
        if not path:
            continue

        if "/res" not in path:
            print(f"WARNING: Cannot determine project root dir of [{path}], skipping.")
            continue

        # Usually the project root is everything before /res
        # but there are special cases where /res is part of the repo name as well
        parts = path.split("/res")
        if len(parts) == 2:
            project_path = parts[0]
        elif len(parts) == 3:
            project_path = parts[0] + "/res" + parts[1]
        else:
            print(f"WARNING: Splitting the path not successful for [{path}], skipping")
            continue

        project_path = project_path.strip("/")
        if project_path == path.strip("/"):
            print(f"WARNING: Cannot determine project root dir of [{path}], skipping.")
            continue

        if project_path in all_projects:
            continue

        # When a project has multiple translatable files, Crowdin will
        # give duplicates.
        # We don't want that (useless empty commits), so we save each
        # project in all_projects and check if it's already in there.
        all_projects.append(project_path)

        # Search android/default.xml or config/%(branch)_extra_packages.xml
        # for the project's name
        result_path = None
        result_project = None
        for project in items:
            path = project.get("path")
            if not (project_path + "/").startswith(path + "/"):
                continue
            # We want the longest match, so projects in subfolders of other projects are also
            # taken into account
            if result_path is None or len(path) > len(result_path):
                result_path = path
                result_project = project

        # Just in case no project was found
        if result_path is None:
            continue

        if project_path != result_path:
            if result_path in all_projects:
                continue
            project_path = result_path
            all_projects.append(project_path)

        project_branch = result_project.get("revision") or branch
        project_name = result_project.get("name")

        push_as_commit(
            extracted, base_path, project_path, project_name, project_branch, username
        )


def push_as_commit(
    extracted_files, base_path, project_path, project_name, branch, username
):
    global _COMMITS_CREATED
    print(f"\nCommitting {project_name} on branch {branch}: ")

    # Get path
    path = os.path.join(base_path, project_path)
    if not path.endswith(".git"):
        path = os.path.join(path, ".git")

    # Create repo object
    repo = git.Repo(path)

    # Strip all comments, find incomplete product strings and remove empty files
    for f in extracted_files:
        if f.startswith(project_path):
            clean_xml_file(os.path.join(base_path, f), repo)

    # Add all files to commit
    count = add_to_commit(extracted_files, repo, project_path)
    if count == 0:
        print("Nothing to commit")
        return

    # Create commit; if it fails, probably empty so skipping
    try:
        repo.git.commit(m="Automatic translation import")
    except Exception as e:
        print(e, "Failed to commit, probably empty: skipping", file=sys.stderr)
        return

    # Push commit
    try:
        repo.git.push(
            f"ssh://{username}@review.lineageos.org:29418/{project_name}",
            f"HEAD:refs/for/{branch}%topic=translation",
        )
        print("Successfully pushed!")
    except Exception as e:
        print(e, "Failed to push!", file=sys.stderr)
        return

    _COMMITS_CREATED = True


def clean_xml_file(path, repo):
    # We don't want to create every file, just work with those already existing
    if not os.path.isfile(path):
        print(f"Called clean_xml_file, but not a file: {path}")
        return
    print(f"Cleaning file {path}")

    try:
        fh = open(path, "r+")
    except OSError:
        print(f"Something went wrong while opening file {path}")
        return

    xml = fh.read()
    content = ""

    # Take the original xml declaration and prepend it
    declaration = xml.split("\n")[0]
    if "<?" in declaration:
        content = declaration + "\n"
        start_pos = xml.find("\n") + 1
        xml = xml[start_pos:]

    try:
        parser = etree.XMLParser(strip_cdata=False)
        tree = etree.parse(path, parser=parser).getroot()
    except etree.XMLSyntaxError as err:
        print(f"{path}: XML Error: {err}")
        filename, ext = os.path.splitext(path)
        if ext == ".xml":
            reset_file(path, repo)
        return

    # Remove strings with 'product=*' attribute but no 'product=default'
    # This will ensure aapt2 will not throw an error when building these
    already_removed = []
    product_strings = tree.xpath("//string[@product]")
    for ps in product_strings:
        # if we already removed the items, don't process them
        if ps in already_removed:
            continue
        string_name = ps.get("name")
        strings_with_same_name = tree.xpath("//string[@name='{0}']".format(string_name))

        # We want to find strings with product='default' or no product attribute at all
        has_product_default = False
        for string in strings_with_same_name:
            product = string.get("product")
            if product is None or product == "default":
                has_product_default = True
                break

        # Every occurrence of the string has to be removed when no string with the same name and
        # 'product=default' (or no product attribute) was found
        if not has_product_default:
            print(
                f"{path}: Found string '{string_name}' with missing 'product=default' attribute"
            )
            for string in strings_with_same_name:
                tree.remove(string)
                already_removed.append(string)

    header = ""
    comments = tree.xpath("//comment()")
    for c in comments:
        p = c.getparent()
        if p is None:
            # Keep all comments in header
            header += str(c).replace("\\n", "\n").replace("\\t", "\t") + "\n"
            continue
        # remove the other comments
        p.remove(c)

    # Remove string(-array)s that are marked as non-translatable
    non_translatable = tree.xpath('/resources/*[@translatable="false"]')
    for n in non_translatable:
        tree.remove(n)

    # Take the original xml declaration and prepend it
    declaration = xml.split("\n")[0]
    if "<?" in declaration:
        content = declaration + "\n"

    content += etree.tostring(
        tree, pretty_print=True, encoding="unicode", xml_declaration=False
    )

    if header != "":
        content = content.replace("?>\n", "?>\n" + header)

    # Sometimes spaces are added, we don't want them
    content = re.sub(r"[ ]*</resources>", r"</resources>", content)

    # Overwrite file with content stripped by all comments
    fh.seek(0)
    fh.write(content)
    fh.truncate()
    fh.close()

    # Remove files which don't have any translated strings
    content_list = list(tree)
    if len(content_list) == 0:
        print(f"Removing {path}")
        os.remove(path)
        # If that was the last file in the folder, we need to remove the folder as well
        dir_name = os.path.dirname(path)
        if os.path.isdir(dir_name):
            if not os.listdir(dir_name):
                print(f"Removing {dir_name}")
                os.rmdir(dir_name)


def add_to_commit(extracted_files, repo, project_path):
    # Add or remove the files extracted by the download command to the commit
    count = 0

    # Modified and untracked files
    modified = repo.git.ls_files(m=True, o=True)
    for m in modified.split("\n"):
        path = os.path.join(project_path, m)
        if path in extracted_files:
            repo.git.add(m)
            count += 1

    deleted = repo.git.ls_files(d=True)
    for d in deleted.split("\n"):
        path = os.path.join(project_path, d)
        if path in extracted_files:
            repo.git.rm(d)
            count += 1

    return count


# For files which we can't process due to errors, create a backup
# and checkout the file to get it back to the previous state
def reset_file(filepath, repo):
    backup_file = None
    parts = filepath.split("/")
    found = False
    for s in parts:
        current_part = s
        if not found and s.startswith("res"):
            current_part = s + "_backup"
            found = True
        if backup_file is None:
            backup_file = current_part
        else:
            backup_file = backup_file + "/" + current_part

    path, filename = os.path.split(backup_file)
    if not os.path.exists(path):
        os.makedirs(path)
    if os.path.exists(backup_file):
        i = 1
        while os.path.exists(backup_file + str(i)):
            i += 1
        backup_file = backup_file + str(i)
    shutil.copy(filepath, backup_file)
    repo.git.checkout(filepath)


def has_created_commits():
    return _COMMITS_CREATED
