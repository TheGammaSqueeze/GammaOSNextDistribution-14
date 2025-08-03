#!/usr/bin/python3
# Copyright 2021 The Android Open Source Project
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

import argparse
import datetime
import os
import os.path
import re
import subprocess
import sys
"""
Helper script for importing a new snapshot of the official Wayland sources.

Usage: ./import_official_snapshot.py 1.18.0
"""


def git(cmd, check=True):
    return subprocess.run(['git'] + cmd,
                          capture_output=True,
                          check=check,
                          text=True)


def git_get_hash(commit):
    return git(['show-ref', '--head', '--hash', commit]).stdout.strip()


def git_is_ref(ref):
    return git(
        ['show-ref', '--head', '--hash', '--verify', f'refs/heads/{ref}'],
        check=False).returncode == 0


def get_git_files(version):
    return set(
        git(['ls-tree', '-r', '--name-only',
             f'{version}^{{tree}}']).stdout.split())


def assert_no_uncommitted_changes():
    r = git(['diff-files', '--quiet', '--ignore-submodules'], check=False)
    if r.returncode:
        sys.exit('Error: Your tree is dirty')

    r = git(
        ['diff-index', '--quiet', '--ignore-submodules', '--cached', 'HEAD'],
        check=False)
    if r.returncode:
        sys.exit('Error: You have staged changes')


def metadata_read_current_version():
    with open('METADATA', 'rt') as metadata_file:
        metadata = metadata_file.read()

    match = re.search(r'version: "([^"]*)"', metadata)
    if not match:
        sys.exit('Error: Unable to determine current version from METADATA')
    return match.group(1)


def metadata_read_git_url():
    with open('METADATA', 'rt') as metadata_file:
        metadata = metadata_file.read()

    match = re.search(r'url\s*{\s*type:\s*GIT\s*value:\s*"([^"]*)"\s*}',
                      metadata)
    if not match:
        sys.exit('Error: Unable to determine GIT url from METADATA')
    return match.group(1)


def setup_and_update_official_source_remote(official_source_git_url):
    r = git(['remote', 'get-url', 'official-source'], check=False)
    if r.returncode or r.stdout != official_source_git_url:
        # Not configured as expected.
        print(
            f'  Configuring official-source remote {official_source_git_url}')
        git(['remote', 'remove', 'official-source'], check=False)
        git(['remote', 'add', 'official-source', official_source_git_url])

    print('  Syncing official-source')
    git(['remote', 'update', 'official-source'])


def get_local_files():
    result = []
    for root, dirs, files in os.walk('.'):
        # Don't include ./.git
        if root == '.' and '.git' in dirs:
            dirs.remove('.git')
        for name in files:
            result.append(os.path.join(root, name)[2:])
    return result


def determine_files_to_preserve(current_version):
    local_files = set(get_local_files())

    current_official_files = get_git_files(current_version)

    android_added_files = local_files - current_official_files

    return android_added_files


def update_metadata_version_and_import_date(version):
    now = datetime.datetime.now()

    with open('METADATA', 'rt') as metadata_file:
        metadata = metadata_file.read()

    metadata = re.sub(r'version: "[^"]*"', f'version: "{version}"', metadata)
    metadata = re.sub(
        r'last_upgrade_date {[^}]*}',
        (f'last_upgrade_date {{ year: {now.year} month: {now.month} '
         f'day: {now.day} }}'), metadata)

    with open('METADATA', 'wt') as metadata_file:
        metadata_file.write(metadata)


def configure_wayland_version_header(version):
    with open('./src/wayland-version.h.in', 'rt') as template_file:
        content = template_file.read()

    (major, minor, micro) = version.split('.')

    content = re.sub(r'@WAYLAND_VERSION_MAJOR@', major, content)
    content = re.sub(r'@WAYLAND_VERSION_MINOR@', minor, content)
    content = re.sub(r'@WAYLAND_VERSION_MICRO@', micro, content)
    content = re.sub(r'@WAYLAND_VERSION@', version, content)

    with open('./src/wayland-version.h', 'wt') as version_header:
        version_header.write(content)

    # wayland-version.h is in .gitignore, so we explicitly have to force-add it.
    git(['add', '-f', './src/wayland-version.h'])


def import_sources(version, preserve_files, update_metdata=True):
    start_hash = git_get_hash('HEAD')

    # Use `git-read-tree` to start with a pure copy of the imported version
    git(['read-tree', '-m', '-u', f'{version}^{{tree}}'])

    git(['commit', '-m', f'To squash: Clean import of {version}'])

    print('  Adding Android metadata')

    # Restore the needed Android files
    git(['restore', '--staged', '--worktree', '--source', start_hash] +
        list(sorted(preserve_files)))

    if update_metdata:
        update_metadata_version_and_import_date(version)
        configure_wayland_version_header(version)

    git(['commit', '-a', '-m', f'To squash: Update versions {version}'])


def apply_and_reexport_patches(version, patches, use_cherry_pick=False):
    if not patches:
        return

    print(f'  Applying {len(patches)} Android patches')

    try:
        if use_cherry_pick:
            git(['cherry-pick'] + patches)
        else:
            git(['am'] + patches)
    except subprocess.CalledProcessError as e:
        if 'patch failed' not in e.stderr:
            raise
        # Print out the captured error mess
        sys.stderr.write(f'''
Failure applying patches to Wayland {version} via:
    {e.cmd}

Once the patches have been resolved, please re-export the patches with:

    git rm patches/*.diff
    git format-patch HEAD~{len(patches)}..HEAD --no-stat --no-signature \\
        --numbered --zero-commit --suffix=.diff --output-directory patches

... and also add them to the final squashed commit.
                '''.strip())

        sys.stdout.write(e.stdout)
        sys.exit(e.stderr)

    patch_hashes = list(
        reversed(
            git(['log', f'-{len(patches)}',
                 '--pretty=format:%H']).stdout.split()))

    # Clean out the existing patches
    git(['rm', 'patches/*.diff'])

    # Re-export the patches, omitting information that might change
    git([
        'format-patch', f'HEAD~{len(patches)}..HEAD', '--no-stat',
        '--no-signature', '--numbered', '--zero-commit', '--suffix=.diff',
        '--output-directory', 'patches'
    ])

    # Add back all the exported patches
    git(['add', 'patches/*.diff'])

    # Create a commit for the exported patches if there are any differences.
    r = git(['diff-files', '--quiet', '--ignore-submodules'], check=False)
    if r.returncode:
        git(['commit', '-a', '-m', f'To squash: Update patches for {version}'])

    return patch_hashes


def main():
    parser = argparse.ArgumentParser(description=(
        "Helper script for importing a snapshot of the Wayland sources."))
    parser.add_argument('version',
                        nargs='?',
                        default=None,
                        help='The official version to import')
    parser.add_argument(
        '--no-validate-existing',
        dest='validate_existing',
        default=True,
        action='store_false',
        help='Whether to validate the current tree against upstream + patches')
    parser.add_argument('--no-squash',
                        dest='squash',
                        default=True,
                        action='store_false',
                        help='Whether to squash the import to a single commit')
    args = parser.parse_args()

    print(
        f'Preparing to importing Wayland core sources version {args.version}')

    assert_no_uncommitted_changes()

    official_source_git_url = metadata_read_git_url()
    current_version = metadata_read_current_version()

    setup_and_update_official_source_remote(official_source_git_url)

    # Get the list of Android added files to preserve
    preserve_files = determine_files_to_preserve(current_version)

    # Filter the list to get all patches that we will need to apply
    patch_files = sorted(path for path in preserve_files
                         if path.startswith('patches/'))

    # Detect any add/add conflicts before we begin
    new_files = get_git_files(args.version or current_version)
    add_add_conflicts = preserve_files.intersection(new_files)
    if add_add_conflicts:
        sys.exit(f'''
Error: The new version of Wayland adds files that are also added for Android:
{add_add_conflicts}
                '''.strip())

    import_branch_name = f'import_{args.version}' if args.version else None

    if import_branch_name and git_is_ref(import_branch_name):
        sys.exit(f'''
Error: Branch name {import_branch_name} already exists. Please delete or rename.
                '''.strip())

    initial_commit_hash = git_get_hash('HEAD')

    # Begin a branch for the version import, if a new version is being imported
    if import_branch_name:
        git(['checkout', '-b', import_branch_name])
        git([
            'commit', '--allow-empty', '-m',
            f'Update to Wayland {args.version}'
        ])

    patch_hashes = None

    if args.validate_existing:
        print(f'Importing {current_version} to validate all current fixups')
        import_sources(current_version, preserve_files, update_metdata=False)
        patch_hashes = apply_and_reexport_patches(current_version, patch_files)

        r = git(
            ['diff', '--quiet', '--ignore-submodules', initial_commit_hash],
            check=False)
        if r.returncode:
            sys.exit(f'''
Failed to recreate the pre-import tree by importing the prior Wayland version
{current_version} and applying the Android fixups.

This is likely due to changes having been made to the Wayland sources without
a corresponding patch file in patches/.

To see the differences detected, run:

git diff {initial_commit_hash}
                    '''.strip())

    if args.version:
        print(f'Importing {args.version}')
        import_sources(args.version, preserve_files)
        apply_and_reexport_patches(
            args.version,
            (patch_hashes if patch_hashes is not None else patch_files),
            use_cherry_pick=(patch_hashes is not None))

        if args.squash:
            print('Squashing to one commit')
            git(['reset', '--soft', initial_commit_hash])
            git([
                'commit', '--allow-empty', '-m', f'''
Update to Wayland {args.version}

Automatic import using "./import_official_snapshot.py {args.version}"
                '''.strip()
            ])


if __name__ == '__main__':
    main()
