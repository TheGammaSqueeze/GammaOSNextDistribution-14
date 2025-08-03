# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Helper functions to communicate with Git."""

import datetime
import re
import subprocess
from pathlib import Path

import hashtags
import reviewers

ANDROID_SPECIFIC_FILES = ["*Android.bp", "Android.mk", "CleanSpec.mk", "LICENSE",
                          "NOTICE", "METADATA", "TEST_MAPPING", ".git",
                          ".gitignore", "patches", "post_update.sh", "OWNERS",
                          "README.android", "cargo2android*", "MODULE_LICENSE_*",
                          "rules.mk", "cargo2rulesmk*", "cargo_embargo*"]

UNWANTED_TAGS = ["*alpha*", "*Alpha*", "*beta*", "*Beta*", "*rc*", "*RC*", "*test*"]


def fetch(proj_path: Path, remote_name: str, branch: str | None = None) -> None:
    """Runs git fetch.

    Args:
        proj_path: Path to Git repository.
        remote_name: A string to specify remote names.
    """
    cmd = ['git', 'fetch', '--tags', remote_name] + ([branch] if branch is not None else [])
    subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True)


def add_remote(proj_path: Path, name: str, url: str) -> None:
    """Adds a git remote.

    Args:
        proj_path: Path to Git repository.
        name: Name of the new remote.
        url: Url of the new remote.
    """
    cmd = ['git', 'remote', 'add', name, url]
    subprocess.run(cmd, cwd=proj_path, check=True)


def remove_remote(proj_path: Path, name: str) -> None:
    """Removes a git remote."""
    cmd = ['git', 'remote', 'remove', name]
    subprocess.run(cmd, cwd=proj_path, check=True)


def list_remotes(proj_path: Path) -> dict[str, str]:
    """Lists all Git remotes.

    Args:
        proj_path: Path to Git repository.

    Returns:
        A dict from remote name to remote url.
    """
    def parse_remote(line: str) -> tuple[str, str]:
        split = line.split()
        return split[0], split[1]

    cmd = ['git', 'remote', '-v']
    out = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                         text=True).stdout
    lines = out.splitlines()
    return dict([parse_remote(line) for line in lines])


def detect_default_branch(proj_path: Path, remote_name: str) -> str:
    """Gets the name of the upstream's default branch to use."""
    cmd = ['git', 'remote', 'show', remote_name]
    out = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                         text=True).stdout
    lines = out.splitlines()
    for line in lines:
        if "HEAD branch" in line:
            return line.split()[-1]
    raise RuntimeError(
        f"Could not find HEAD branch in 'git remote show {remote_name}'"
    )


def get_sha_for_branch(proj_path: Path, branch: str):
    """Gets the hash SHA for a branch."""
    cmd = ['git', 'rev-parse', branch]
    return subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                          text=True).stdout.strip()


def get_most_recent_tag(proj_path: Path, branch: str) -> str | None:
    """Finds the most recent tag that is reachable from HEAD."""
    cmd = ['git', 'describe', '--tags', branch, '--abbrev=0'] + \
          [f'--exclude={unwanted_tag}' for unwanted_tag in UNWANTED_TAGS]
    try:
        out = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                            text=True).stdout.strip()
        return out
    except subprocess.CalledProcessError as ex:
        if "fatal: No names found" in ex.stderr:
            return None
        if "fatal: No tags can describe" in ex.stderr:
            return None
        raise


# pylint: disable=redefined-outer-name
def get_commit_time(proj_path: Path, commit: str) -> datetime.datetime:
    """Gets commit time of one commit."""
    cmd = ['git', 'show', '-s', '--format=%ct', commit]
    out = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                         text=True).stdout
    return datetime.datetime.fromtimestamp(int(out.strip()))


def list_remote_branches(proj_path: Path, remote_name: str) -> list[str]:
    """Lists all branches for a remote."""
    cmd = ['git', 'branch', '-r']
    lines = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                           text=True).stdout.splitlines()
    stripped = [line.strip() for line in lines]
    remote_path = remote_name + '/'
    return [
        line[len(remote_path):] for line in stripped
        if line.startswith(remote_path)
    ]


def list_local_branches(proj_path: Path) -> list[str]:
    """Lists all local branches."""
    cmd = ['git', 'branch', '--format=%(refname:short)']
    lines = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                           text=True).stdout.splitlines()
    return lines


COMMIT_PATTERN = r'^[a-f0-9]{40}$'
COMMIT_RE = re.compile(COMMIT_PATTERN)


# pylint: disable=redefined-outer-name
def is_commit(commit: str) -> bool:
    """Whether a string looks like a SHA1 hash."""
    return bool(COMMIT_RE.match(commit))


def merge(proj_path: Path, branch: str) -> None:
    """Merges a branch."""
    try:
        cmd = ['git', 'merge', branch, '--no-commit']
        subprocess.run(cmd, cwd=proj_path, check=True)
    except subprocess.CalledProcessError as err:
        if hasattr(err, "output"):
            print(err.output)
        if not merge_conflict(proj_path):
            raise


def merge_conflict(proj_path: Path) -> bool:
    """Checks if there was a merge conflict."""
    cmd = ['git', 'ls-files', '--unmerged']
    out = subprocess.run(cmd, capture_output=True, cwd=proj_path, check=True,
                         text=True).stdout
    return bool(out)


def add_file(proj_path: Path, file_name: str) -> None:
    """Stages a file."""
    cmd = ['git', 'add', file_name]
    subprocess.run(cmd, cwd=proj_path, check=True)


def remove_gitmodules(proj_path: Path) -> None:
    """Deletes .gitmodules files."""
    cmd = ['find', '.', '-name', '.gitmodules', '-delete']
    subprocess.run(cmd, cwd=proj_path, check=True)


def delete_branch(proj_path: Path, branch_name: str) -> None:
    """Force delete a branch."""
    cmd = ['git', 'branch', '-D', branch_name]
    subprocess.run(cmd, cwd=proj_path, check=True)


def start_branch(proj_path: Path, branch_name: str) -> None:
    """Starts a new repo branch."""
    subprocess.run(['repo', 'start', branch_name], cwd=proj_path, check=True)


def commit(proj_path: Path, message: str, no_verify: bool) -> None:
    """Commits changes."""
    cmd = ['git', 'commit', '-m', message] + (['--no-verify'] if no_verify is True else [])
    subprocess.run(cmd, cwd=proj_path, check=True)


def commit_amend(proj_path: Path) -> None:
    """Commits changes."""
    cmd = ['git', 'commit', '--amend', '--no-edit']
    subprocess.run(cmd, cwd=proj_path, check=True)


def checkout(proj_path: Path, branch_name: str) -> None:
    """Checkouts a branch."""
    cmd = ['git', 'checkout', branch_name]
    subprocess.run(cmd, cwd=proj_path, check=True)


def detach_to_android_head(proj_path: Path) -> None:
    """Detaches the project HEAD back to the manifest revision."""
    # -d detaches the project back to the manifest revision without updating.
    # -l avoids fetching new revisions from the remote. This might be superfluous with
    # -d, but I'm not sure, and it certainly doesn't harm anything.
    subprocess.run(['repo', 'sync', '-l', '-d', proj_path], cwd=proj_path, check=True)


def push(proj_path: Path, remote_name: str, has_errors: bool) -> None:
    """Pushes change to remote."""
    cmd = ['git', 'push', remote_name, 'HEAD:refs/for/main']
    if revs := reviewers.find_reviewers(str(proj_path)):
        cmd.extend(['-o', revs])
    if tag := hashtags.find_hashtag(proj_path):
        cmd.extend(['-o', 't=' + tag])
    if has_errors:
        cmd.extend(['-o', 'l=Verified-1'])
    subprocess.run(cmd, cwd=proj_path, check=True)


def reset_hard(proj_path: Path) -> None:
    """Resets current HEAD and discards changes to tracked files."""
    cmd = ['git', 'reset', '--hard']
    subprocess.run(cmd, cwd=proj_path, check=True)


def clean(proj_path: Path) -> None:
    """Removes untracked files and directories."""
    cmd = ['git', 'clean', '-fdx']
    subprocess.run(cmd, cwd=proj_path, check=True)


def is_valid_url(proj_path: Path, url: str) -> bool:
    cmd = ['git', "ls-remote", url]
    return subprocess.run(cmd, cwd=proj_path, check=False, stdin=subprocess.DEVNULL,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                          start_new_session=True).returncode == 0


def diff(proj_path: Path, sha_or_tag: str) -> str:
    files = []
    for file in ANDROID_SPECIFIC_FILES:
        file = ":!" + file
        files.append(file)
    try:
        cmd = ['git', 'diff', sha_or_tag, '--stat', '--'] + files
        out = subprocess.run(cmd, capture_output=True, cwd=proj_path,
                             check=True, text=True).stdout
        return out
    except subprocess.CalledProcessError as err:
        return f"Could not calculate the diff: {err}"


def is_ancestor(proj_path: Path, ancestor: str, child: str) -> bool:
    cmd = ['git', 'merge-base', '--is-ancestor', ancestor, child]
    # https://git-scm.com/docs/git-merge-base#Documentation/git-merge-base.txt---is-ancestor
    # Exit status of 0 means yes, 1 means no, and all others mean an error occurred.
    # Although a commit is an ancestor of itself, we don't want to return True
    # if ancestor points to the same commit as child.
    if get_sha_for_branch(proj_path, ancestor) == child:
        return False
    try:
        subprocess.run(
            cmd,
            cwd=proj_path,
            text=True,
            stderr=subprocess.STDOUT,
            check=True,
            stdout=subprocess.PIPE
        )
        return True
    except subprocess.CalledProcessError as ex:
        if ex.returncode == 1:
            return False
        raise
