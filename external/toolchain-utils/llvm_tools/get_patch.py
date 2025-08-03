#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Get patches from a patch source, and integrate them into ChromiumOS.

Example Usage:
    # Apply a Pull request.
    $ get_patch.py -s HEAD p:74791
    # Apply several patches.
    $ get_patch.py -s 82e851a407c5 p:74791 47413bb27
    # Use another llvm-project dir.
    $ get_patch.py -s HEAD -l ~/llvm-project 47413bb27
"""

import argparse
import dataclasses
import json
import logging
from pathlib import Path
import random
import re
import subprocess
import tempfile
import textwrap
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple, Union
from urllib import request

import atomic_write_file
import git_llvm_rev
import patch_utils


CHROMIUMOS_OVERLAY_PATH = Path("src/third_party/chromiumos-overlay")
LLVM_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "sys-devel/llvm"
COMPILER_RT_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "sys-libs/compiler-rt"
LIBCXX_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "sys-libs/libcxx"
LIBUNWIND_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "sys-libs/llvm-libunwind"
SCUDO_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "sys-libs/scudo"
LLDB_PKG_PATH = CHROMIUMOS_OVERLAY_PATH / "dev-util/lldb-server"

LLVM_PROJECT_PATH = Path("src/third_party/llvm-project")
PATCH_METADATA_FILENAME = "PATCHES.json"


class CherrypickError(ValueError):
    """ValueError for a cherry-pick has been seen before."""


class CherrypickVersionError(ValueError):
    """ValueError that highlights the cherry-pick is before the start_ref."""


@dataclasses.dataclass
class LLVMGitRef:
    """Represents an LLVM git ref."""

    git_ref: str
    _rev: Optional[git_llvm_rev.Rev] = None  # Used for caching

    @classmethod
    def from_rev(cls, llvm_dir: Path, rev: git_llvm_rev.Rev) -> "LLVMGitRef":
        return cls(
            git_llvm_rev.translate_rev_to_sha(
                git_llvm_rev.LLVMConfig("origin", llvm_dir), rev
            ),
            _rev=rev,
        )

    def to_rev(self, llvm_dir: Path) -> git_llvm_rev.Rev:
        if self._rev:
            return self._rev
        self._rev = git_llvm_rev.translate_sha_to_rev(
            git_llvm_rev.LLVMConfig("origin", llvm_dir),
            self.git_ref,
        )
        return self._rev


@dataclasses.dataclass(frozen=True)
class LLVMPullRequest:
    """Represents an upstream GitHub Pull Request number."""

    number: int


@dataclasses.dataclass
class PatchContext:
    """Represents the state of the chromiumos source during patching."""

    llvm_project_dir: Path
    chromiumos_root: Path
    start_ref: LLVMGitRef
    platforms: Iterable[str]
    dry_run: bool = False

    def apply_patches(
        self, patch_source: Union[LLVMGitRef, LLVMPullRequest]
    ) -> None:
        """Create .patch files and add them to PATCHES.json.

        Post:
            Unless self.dry_run is True, writes the patch contents to
            the respective <pkg>/files/ workdir for each applicable
            patch, and the JSON files are updated with the new entries.

        Raises:
            TypeError: If the patch_source is not a
                LLVMGitRef or LLVMPullRequest.
        """
        new_patch_entries = self.make_patches(patch_source)
        self.apply_entries_to_json(new_patch_entries)

    def apply_entries_to_json(
        self,
        new_patch_entries: Iterable[patch_utils.PatchEntry],
    ) -> None:
        """Add some PatchEntries to the appropriate PATCHES.json."""
        workdir_mappings: Dict[Path, List[patch_utils.PatchEntry]] = {}
        for pe in new_patch_entries:
            workdir_mappings[pe.workdir] = workdir_mappings.get(
                pe.workdir, []
            ) + [pe]
        for workdir, pes in workdir_mappings.items():
            patches_json_file = workdir / PATCH_METADATA_FILENAME
            with patches_json_file.open(encoding="utf-8") as f:
                orig_contents = f.read()
            old_patch_entries = patch_utils.json_str_to_patch_entries(
                workdir, orig_contents
            )
            indent_len = patch_utils.predict_indent(orig_contents.splitlines())
            if not self.dry_run:
                with atomic_write_file.atomic_write(
                    patches_json_file, encoding="utf-8"
                ) as f:
                    json.dump(
                        [pe.to_dict() for pe in old_patch_entries + pes],
                        f,
                        indent=indent_len,
                    )
                    f.write("\n")

    def make_patches(
        self, patch_source: Union[LLVMGitRef, LLVMPullRequest]
    ) -> List[patch_utils.PatchEntry]:
        """Create PatchEntries for a given LLVM change and returns them.

        Returns:
            A list of PatchEntries representing the patches for each
            package for the given patch_source.

        Post:
            Unless self.dry_run is True, writes the patch contents to
            the respective <pkg>/files/ workdir for each applicable
            patch.

        Raises:
            TypeError: If the patch_source is not a
                LLVMGitRef or LLVMPullRequest.
        """

        # This is just a dispatch method to the actual methods.
        if isinstance(patch_source, LLVMGitRef):
            return self._make_patches_from_git_ref(patch_source)
        if isinstance(patch_source, LLVMPullRequest):
            return self._make_patches_from_pr(patch_source)
        raise TypeError(
            f"patch_source was invalid type {type(patch_source).__name__}"
        )

    def _make_patches_from_git_ref(
        self,
        patch_source: LLVMGitRef,
    ) -> List[patch_utils.PatchEntry]:
        packages = get_changed_packages(
            self.llvm_project_dir, patch_source.git_ref
        )
        new_patch_entries: List[patch_utils.PatchEntry] = []
        for workdir in self._workdirs_for_packages(packages):
            rel_patch_path = f"cherry/{patch_source.git_ref}.patch"
            if (workdir / "cherry").is_dir():
                rel_patch_path = f"cherry/{patch_source.git_ref}.patch"
            else:
                # Some packages don't have a cherry directory.
                rel_patch_path = f"{patch_source.git_ref}.patch"
            if not self._is_valid_patch_range(self.start_ref, patch_source):
                raise CherrypickVersionError(
                    f"'from' ref {self.start_ref} is later or"
                    f" same as than 'until' ref {patch_source}"
                )
            pe = patch_utils.PatchEntry(
                workdir=workdir,
                metadata={
                    "title": get_commit_subj(
                        self.llvm_project_dir, patch_source.git_ref
                    ),
                    "info": [],
                },
                platforms=list(self.platforms),
                rel_patch_path=rel_patch_path,
                version_range={
                    "from": self.start_ref.to_rev(self.llvm_project_dir).number,
                    "until": patch_source.to_rev(self.llvm_project_dir).number,
                },
            )
            # Before we actually do any modifications, check if the patch is
            # already applied.
            if self.is_patch_applied(pe):
                raise CherrypickError(
                    f"Patch at {pe.rel_patch_path}"
                    " already exists in PATCHES.json"
                )
            contents = git_format_patch(
                self.llvm_project_dir,
                patch_source.git_ref,
            )
            if not self.dry_run:
                _write_patch(pe.title(), contents, pe.patch_path())
            new_patch_entries.append(pe)
        return new_patch_entries

    def _make_patches_from_pr(
        self, patch_source: LLVMPullRequest
    ) -> List[patch_utils.PatchEntry]:
        json_response = get_llvm_github_pull(patch_source.number)
        github_ctx = GitHubPRContext(json_response, self.llvm_project_dir)
        rel_patch_path = f"{github_ctx.full_title_cleaned}.patch"
        contents, packages = github_ctx.git_squash_chain_patch()
        new_patch_entries = []
        for workdir in self._workdirs_for_packages(packages):
            pe = patch_utils.PatchEntry(
                workdir=workdir,
                metadata={
                    "title": github_ctx.full_title,
                    "info": [],
                },
                rel_patch_path=rel_patch_path,
                platforms=list(self.platforms),
                version_range={
                    "from": self.start_ref.to_rev(self.llvm_project_dir).number,
                    "until": None,
                },
            )
            # Before we actually do any modifications, check if the patch is
            # already applied.
            if self.is_patch_applied(pe):
                raise CherrypickError(
                    f"Patch at {pe.rel_patch_path}"
                    " already exists in PATCHES.json"
                )
            if not self.dry_run:
                _write_patch(pe.title(), contents, pe.patch_path())
            new_patch_entries.append(pe)
        return new_patch_entries

    def _workdirs_for_packages(self, packages: Iterable[Path]) -> List[Path]:
        return [self.chromiumos_root / pkg / "files" for pkg in packages]

    def is_patch_applied(self, to_check: patch_utils.PatchEntry) -> bool:
        """Return True if the patch is applied in PATCHES.json."""
        patches_json_file = to_check.workdir / PATCH_METADATA_FILENAME
        with patches_json_file.open(encoding="utf-8") as f:
            patch_entries = patch_utils.json_to_patch_entries(
                to_check.workdir, f
            )
        return any(
            p.rel_patch_path == to_check.rel_patch_path for p in patch_entries
        )

    def _is_valid_patch_range(
        self, from_ref: LLVMGitRef, to_ref: LLVMGitRef
    ) -> bool:
        return (
            from_ref.to_rev(self.llvm_project_dir).number
            < to_ref.to_rev(self.llvm_project_dir).number
        )


def get_commit_subj(git_root_dir: Path, ref: str) -> str:
    """Return a given commit's subject."""
    logging.debug("Getting commit subject for %s", ref)
    subj = subprocess.run(
        ["git", "show", "-s", "--format=%s", ref],
        cwd=git_root_dir,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        check=True,
    ).stdout.strip()
    logging.debug("  -> %s", subj)
    return subj


def git_format_patch(git_root_dir: Path, ref: str) -> str:
    """Format a patch for a single git ref.

    Args:
        git_root_dir: Root directory for a given local git repository.
        ref: Git ref to make a patch for.

    Returns:
        The patch file contents.
    """
    logging.debug("Formatting patch for %s^..%s", ref, ref)
    proc = subprocess.run(
        ["git", "format-patch", "--stdout", f"{ref}^..{ref}"],
        cwd=git_root_dir,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        check=True,
    )
    contents = proc.stdout.strip()
    if not contents:
        raise ValueError(f"No git diff between {ref}^..{ref}")
    logging.debug("Patch diff is %d lines long", contents.count("\n"))
    return contents


def get_llvm_github_pull(pull_number: int) -> Dict[str, Any]:
    """Get information about an LLVM pull request.

    Returns:
        A dictionary containing the JSON response from GitHub.

    Raises:
        RuntimeError when the network response is not OK.
    """

    pull_url = (
        f"https://api.github.com/repos/llvm/llvm-project/pulls/{pull_number}"
    )
    # TODO(ajordanr): If we are ever allowed to use the 'requests' library
    # we should move to that instead of urllib.
    req = request.Request(
        url=pull_url,
        headers={
            "X-GitHub-Api-Version": "2022-11-28",
            "Accept": "application/vnd.github+json",
        },
    )
    with request.urlopen(req) as f:
        if f.status >= 400:
            raise RuntimeError(
                f"GitHub response was not OK: {f.status} {f.reason}"
            )
        response = f.read().decode("utf-8")
    return json.loads(response)


class GitHubPRContext:
    """Metadata and pathing context for a GitHub pull request checkout."""

    def __init__(
        self,
        response: Dict[str, Any],
        llvm_project_dir: Path,
    ) -> None:
        """Create a GitHubPRContext from a GitHub pulls api call.

        Args:
            response: A dictionary formed from the JSON sent by
                the github pulls API endpoint.
            llvm_project_dir: Path to llvm-project git directory.
        """
        try:
            self.clone_url = response["head"]["repo"]["clone_url"]
            self._title = response["title"]
            self.body = response["body"]
            self.base_ref = response["base"]["sha"]
            self.head_ref = response["head"]["sha"]
            self.llvm_project_dir = llvm_project_dir
            self.number = int(response["number"])
            self._fetched = False
        except (ValueError, KeyError):
            logging.error("Failed to parse GitHub response:\n%s", response)
            raise

    @property
    def full_title(self) -> str:
        return f"[PR{self.number}] {self._title}"

    @property
    def full_title_cleaned(self) -> str:
        return re.sub(r"\W", "-", self.full_title)

    def git_squash_chain_patch(self) -> Tuple[str, Set[Path]]:
        """Replicate a squashed merge commit as a patch file.

        Args:
            git_root_dir: Root directory for a given local git repository
                which contains the base_ref.
            output: File path to write the patch to.

        Returns:
            The patch file contents.
        """
        self._fetch()
        idx = random.randint(0, 2**32)
        tmpbranch_name = f"squash-branch-{idx}"

        with tempfile.TemporaryDirectory() as dir_str:
            worktree_parent_dir = Path(dir_str)
            commit_message_file = worktree_parent_dir / "commit_message"
            # Need this separate from the commit message, otherwise the
            # dir will be non-empty.
            worktree_dir = worktree_parent_dir / "worktree"
            with commit_message_file.open("w", encoding="utf-8") as f:
                f.write(self.full_title)
                f.write("\n\n")
                f.write(
                    "\n".join(
                        textwrap.wrap(
                            self.body, width=72, replace_whitespace=False
                        )
                    )
                )
                f.write("\n")

            logging.debug("Base ref: %s", self.base_ref)
            logging.debug("Head ref: %s", self.head_ref)
            logging.debug(
                "Creating worktree at '%s' with branch '%s'",
                worktree_dir,
                tmpbranch_name,
            )
            self._run(
                [
                    "git",
                    "worktree",
                    "add",
                    "-b",
                    tmpbranch_name,
                    worktree_dir,
                    self.base_ref,
                ],
                self.llvm_project_dir,
            )
            try:
                self._run(
                    ["git", "merge", "--squash", self.head_ref], worktree_dir
                )
                self._run(
                    [
                        "git",
                        "commit",
                        "-a",
                        "-F",
                        commit_message_file,
                    ],
                    worktree_dir,
                )
                changed_packages = get_changed_packages(
                    worktree_dir, (self.base_ref, "HEAD")
                )
                patch_contents = git_format_patch(worktree_dir, "HEAD")
            finally:
                logging.debug(
                    "Cleaning up worktree and deleting branch %s",
                    tmpbranch_name,
                )
                self._run(
                    ["git", "worktree", "remove", worktree_dir],
                    self.llvm_project_dir,
                )
                self._run(
                    ["git", "branch", "-D", tmpbranch_name],
                    self.llvm_project_dir,
                )
        return (patch_contents, changed_packages)

    def _fetch(self) -> None:
        if not self._fetched:
            logging.debug(
                "Fetching from %s and setting FETCH_HEAD to %s",
                self.clone_url,
                self.head_ref,
            )
            self._run(
                ["git", "fetch", self.clone_url, self.head_ref],
                cwd=self.llvm_project_dir,
            )
            self._fetched = True

    @staticmethod
    def _run(
        cmd: List[Union[str, Path]],
        cwd: Path,
        stdin: int = subprocess.DEVNULL,
    ) -> subprocess.CompletedProcess:
        """Helper for subprocess.run."""
        return subprocess.run(
            cmd,
            cwd=cwd,
            stdin=stdin,
            stdout=subprocess.PIPE,
            encoding="utf-8",
            check=True,
        )


def get_changed_packages(
    llvm_project_dir: Path, ref: Union[str, Tuple[str, str]]
) -> Set[Path]:
    """Returns package paths which changed over a given ref.

    Args:
        llvm_project_dir: Path to llvm-project
        ref: Git ref to check diff of. If set to a tuple, compares the diff
            between the first and second ref.

    Returns:
        A set of package paths which were changed.
    """
    if isinstance(ref, tuple):
        ref_from, ref_to = ref
    elif isinstance(ref, str):
        ref_from = ref + "^"
        ref_to = ref
    else:
        raise TypeError(f"ref was {type(ref)}; need a tuple or a string")

    logging.debug("Getting git diff between %s..%s", ref_from, ref_to)
    proc = subprocess.run(
        ["git", "diff", "--name-only", f"{ref_from}..{ref_to}"],
        check=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        cwd=llvm_project_dir,
    )
    changed_paths = proc.stdout.splitlines()
    logging.debug("Found %d changed files", len(changed_paths))
    # Some LLVM projects are built by LLVM ebuild on x86, so always apply the
    # patch to LLVM ebuild
    packages = {LLVM_PKG_PATH}
    for changed_path in changed_paths:
        if changed_path.startswith("compiler-rt"):
            packages.add(COMPILER_RT_PKG_PATH)
            if "scudo" in changed_path:
                packages.add(SCUDO_PKG_PATH)
        elif changed_path.startswith("libunwind"):
            packages.add(LIBUNWIND_PKG_PATH)
        elif changed_path.startswith("libcxx") or changed_path.startswith(
            "libcxxabi"
        ):
            packages.add(LIBCXX_PKG_PATH)
        elif changed_path.startswith("lldb"):
            packages.add(LLDB_PKG_PATH)
    return packages


def _has_repo_child(path: Path) -> bool:
    """Check if a given directory has a repo child.

    Useful for checking if a directory has a chromiumos source tree.
    """
    child_maybe = path / ".repo"
    return path.is_dir() and child_maybe.is_dir()


def _autodetect_chromiumos_root(
    parent: Optional[Path] = None,
) -> Optional[Path]:
    """Find the root of the chromiumos source tree from the current workdir.

    Returns:
        The root directory of the current chromiumos source tree.
        If the current working directory is not within a chromiumos source
        tree, then this returns None.
    """
    if parent is None:
        parent = Path.cwd()
    if parent.resolve() == Path.root:
        return None
    if _has_repo_child(parent):
        return parent
    return _autodetect_chromiumos_root(parent.parent)


def _write_patch(title: str, contents: str, path: Path) -> None:
    """Actually write the patch contents to a file."""
    # This is mostly separated for mocking.
    logging.info("Writing patch '%s' to '%s'", title, path)
    path.write_text(contents, encoding="utf-8")


def validate_patch_args(
    positional_args: List[str],
) -> List[Union[LLVMGitRef, LLVMPullRequest]]:
    """Checks that each ref_or_pr_num is in a valid format."""
    patch_sources = []
    for arg in positional_args:
        patch_source: Union[LLVMGitRef, LLVMPullRequest]
        if arg.startswith("p:"):
            try:
                pull_request_num = int(arg.lstrip("p:"))
            except ValueError as e:
                raise ValueError(
                    f"GitHub Pull Request '{arg}' was not in the format of"
                    f" 'p:NNNN': {e}"
                )
            logging.info("Patching remote GitHub PR '%s'", pull_request_num)
            patch_source = LLVMPullRequest(pull_request_num)
        else:
            logging.info("Patching local ref '%s'", arg)
            patch_source = LLVMGitRef(arg)
        patch_sources.append(patch_source)
    return patch_sources


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for this script."""

    parser = argparse.ArgumentParser(
        "get_patch",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "-c",
        "--chromiumos-root",
        help="""Path to the chromiumos source tree root.
        Tries to autodetect if not passed.
        """,
    )
    parser.add_argument(
        "-l",
        "--llvm",
        help="""Path to the llvm dir.
        Tries to autodetect from chromiumos root if not passed.
        """,
    )
    parser.add_argument(
        "-s",
        "--start-ref",
        default="HEAD",
        help="""The starting ref for which to apply patches.
        """,
    )
    parser.add_argument(
        "-p",
        "--platform",
        action="append",
        help="""Apply this patch to the give platform. Common options include
        'chromiumos' and 'android'. Can be specified multiple times to
        apply to multiple platforms. If not passed, platform is set to
        'chromiumos'.
        """,
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run normally, but don't make any changes. Read-only mode.",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Enable verbose logging.",
    )
    parser.add_argument(
        "ref_or_pr_num",
        nargs="+",
        help="""Git ref or GitHub PR number to make patches.
        To patch a GitHub PR, use the syntax p:NNNN (e.g. 'p:123456').
        """,
        type=str,
    )
    args = parser.parse_args()

    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.DEBUG if args.verbose else logging.INFO,
    )

    args.patch_sources = validate_patch_args(args.ref_or_pr_num)
    if args.chromiumos_root:
        if not _has_repo_child(args.chromiumos_root):
            parser.error("chromiumos root directly passed but has no .repo")
        logging.debug("chromiumos root directly passed; found and verified")
    elif tmp := _autodetect_chromiumos_root():
        logging.debug("chromiumos root autodetected; found and verified")
        args.chromiumos_root = tmp
    else:
        parser.error(
            "Could not autodetect chromiumos root. Use '-c' to pass the "
            "chromiumos root path directly."
        )

    if not args.llvm:
        if (args.chromiumos_root / LLVM_PROJECT_PATH).is_dir():
            args.llvm = args.chromiumos_root / LLVM_PROJECT_PATH
        else:
            parser.error(
                "Could not autodetect llvm-project dir. Use '-l' to pass the "
                "llvm-project directly"
            )
    return args


def main() -> None:
    """Entry point for the program."""

    args = parse_args()

    # For the vast majority of cases, we'll only want to set platform to
    # ["chromiumos"], so let's make that the default.
    platforms: List[str] = args.platform if args.platform else ["chromiumos"]

    ctx = PatchContext(
        chromiumos_root=args.chromiumos_root,
        llvm_project_dir=args.llvm,
        start_ref=LLVMGitRef(args.start_ref),
        platforms=platforms,
        dry_run=args.dry_run,
    )
    for patch_source in args.patch_sources:
        ctx.apply_patches(patch_source)


if __name__ == "__main__":
    main()
