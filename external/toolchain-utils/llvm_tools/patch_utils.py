# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Provides patch utilities for PATCHES.json file handling."""

import collections
import contextlib
import dataclasses
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import (
    Any,
    Callable,
    Dict,
    IO,
    Iterable,
    List,
    Optional,
    Tuple,
    Union,
)

import atomic_write_file


APPLIED_RE = re.compile(r"^Applying: (.+) \(#(\d+)\)$")
CHECKED_FILE_RE = re.compile(r"^checking file\s+(.*)$")
HUNK_FAILED_RE = re.compile(r"^Hunk #(\d+) FAILED at.*")
HUNK_HEADER_RE = re.compile(r"^@@\s+-(\d+),(\d+)\s+\+(\d+),(\d+)\s+@@")
HUNK_END_RE = re.compile(r"^--\s*$")
PATCH_SUBFILE_HEADER_RE = re.compile(r"^\+\+\+ [ab]/(.*)$")


@dataclasses.dataclass
class Hunk:
    """Represents a patch Hunk."""

    hunk_id: int
    """Hunk ID for the current file."""
    orig_start: int
    orig_hunk_len: int
    patch_start: int
    patch_hunk_len: int
    patch_hunk_lineno_begin: int
    patch_hunk_lineno_end: Optional[int]


def parse_patch_stream(patch_stream: IO[str]) -> Dict[str, List[Hunk]]:
    """Parse a patch file-like into Hunks.

    Args:
        patch_stream: A IO stream formatted like a git patch file.

    Returns:
        A dictionary mapping filenames to lists of Hunks present
        in the patch stream.
    """

    current_filepath = None
    current_hunk_id = 0
    current_hunk = None
    out = collections.defaultdict(list)
    for lineno, line in enumerate(patch_stream.readlines()):
        subfile_header = PATCH_SUBFILE_HEADER_RE.match(line)
        if subfile_header:
            current_filepath = subfile_header.group(1)
            if not current_filepath:
                raise RuntimeError("Could not get file header in patch stream")
            # Need to reset the hunk id, as it's per-file.
            current_hunk_id = 0
            continue
        hunk_header = HUNK_HEADER_RE.match(line)
        if hunk_header:
            if not current_filepath:
                raise RuntimeError(
                    "Parsed hunk before file header in patch stream"
                )
            if current_hunk:
                # Already parsing a hunk
                current_hunk.patch_hunk_lineno_end = lineno
            current_hunk_id += 1
            current_hunk = Hunk(
                hunk_id=current_hunk_id,
                orig_start=int(hunk_header.group(1)),
                orig_hunk_len=int(hunk_header.group(2)),
                patch_start=int(hunk_header.group(3)),
                patch_hunk_len=int(hunk_header.group(4)),
                patch_hunk_lineno_begin=lineno + 1,
                patch_hunk_lineno_end=None,
            )
            out[current_filepath].append(current_hunk)
            continue
        if current_hunk and HUNK_END_RE.match(line):
            current_hunk.patch_hunk_lineno_end = lineno
    return out


def parse_failed_patch_output(text: str) -> Dict[str, List[int]]:
    current_file = None
    failed_hunks = collections.defaultdict(list)
    for eline in text.split("\n"):
        checked_file_match = CHECKED_FILE_RE.match(eline)
        if checked_file_match:
            current_file = checked_file_match.group(1)
            continue
        failed_match = HUNK_FAILED_RE.match(eline)
        if failed_match:
            if not current_file:
                raise ValueError("Input stream was not parsable")
            hunk_id = int(failed_match.group(1))
            failed_hunks[current_file].append(hunk_id)
        else:
            failed_applied_patches = APPLIED_RE.match(eline)
            if failed_applied_patches:
                current_file = failed_applied_patches.group(1)
                hunk_id = int(failed_applied_patches.group(2))
                failed_hunks[current_file].append(hunk_id)
    return failed_hunks


@dataclasses.dataclass(frozen=True)
class PatchResult:
    """Result of a patch application."""

    succeeded: bool
    failed_hunks: Dict[str, List[Hunk]] = dataclasses.field(
        default_factory=dict
    )

    def __bool__(self):
        return self.succeeded

    def failure_info(self) -> str:
        if self.succeeded:
            return ""
        s = ""
        for file, hunks in self.failed_hunks.items():
            s += f"{file}:\n"
            for h in hunks:
                s += (
                    f"Lines {h.orig_start} to "
                    f"{h.orig_start + h.orig_hunk_len}\n"
                )
            s += "--------------------\n"
        return s


@dataclasses.dataclass
class PatchEntry:
    """Object mapping of an entry of PATCHES.json."""

    workdir: Path
    """Storage location for the patches."""
    metadata: Optional[Dict[str, Any]]
    platforms: Optional[List[str]]
    rel_patch_path: str
    version_range: Optional[Dict[str, Optional[int]]]
    verify_workdir: bool = True
    """Don't verify the workdir exists. Used for testing."""
    _parsed_hunks = None

    def __post_init__(self):
        if self.verify_workdir and not self.workdir.is_dir():
            raise ValueError(f"workdir {self.workdir} is not a directory")

    @classmethod
    def from_dict(cls, workdir: Path, data: Dict[str, Any]):
        """Instatiate from a dictionary.

        Dictionary must have at least the following key:
        {
            'rel_patch_path': '<relative patch path to workdir>',
        }

        Returns:
            A new PatchEntry.
        """
        return cls(
            workdir,
            data.get("metadata"),
            data.get("platforms"),
            data["rel_patch_path"],
            data.get("version_range"),
        )

    def to_dict(self) -> Dict[str, Any]:
        # We sort the metadata so that it doesn't matter
        # how it was passed to patch_utils.
        if self.metadata is None:
            sorted_metadata = None
        else:
            sorted_metadata = dict(
                sorted(self.metadata.items(), key=lambda x: x[0])
            )
        out: Dict[str, Any] = {
            "metadata": sorted_metadata,
        }
        if self.platforms:
            # To match patch_sync, only serialized when
            # non-empty and non-null.
            out["platforms"] = sorted(self.platforms)
        out.update(
            {
                "rel_patch_path": self.rel_patch_path,
                "version_range": self.version_range,
            }
        )
        return out

    def parsed_hunks(self) -> Dict[str, List[Hunk]]:
        # Minor caching here because IO is slow.
        if not self._parsed_hunks:
            with self.patch_path().open(encoding="utf-8") as f:
                self._parsed_hunks = parse_patch_stream(f)
        return self._parsed_hunks

    def patch_path(self) -> Path:
        return self.workdir / self.rel_patch_path

    def can_patch_version(self, svn_version: int) -> bool:
        """Is this patch meant to apply to `svn_version`?"""
        # Sometimes the key is there, but it's set to None.
        if not self.version_range:
            return True
        from_v = self.version_range.get("from") or 0
        until_v = self.version_range.get("until")
        if until_v is None:
            until_v = sys.maxsize
        return from_v <= svn_version < until_v

    def is_old(self, svn_version: int) -> bool:
        """Is this patch old compared to `svn_version`?"""
        if not self.version_range:
            return False
        until_v = self.version_range.get("until")
        # Sometimes the key is there, but it's set to None.
        if until_v is None:
            until_v = sys.maxsize
        return svn_version >= until_v

    def apply(
        self,
        root_dir: Path,
        patch_cmd: Optional[Callable] = None,
        extra_args: Optional[List[str]] = None,
    ) -> PatchResult:
        """Apply a patch to a given directory."""
        # Cmd to apply a patch in the src unpack path.
        abs_patch_path = self.patch_path().absolute()
        if not abs_patch_path.is_file():
            raise RuntimeError(
                f"Cannot apply: patch {abs_patch_path} is not a file"
            )

        if not patch_cmd:
            patch_cmd = gnu_patch

        if patch_cmd == gnu_patch:
            cmd = patch_cmd(root_dir, abs_patch_path) + (extra_args or [])
        else:
            cmd = patch_cmd(abs_patch_path) + (extra_args or [])

        try:
            subprocess.run(
                cmd, encoding="utf-8", check=True, stdout=subprocess.PIPE
            )
        except subprocess.CalledProcessError as e:
            parsed_hunks = self.parsed_hunks()
            failed_hunks_id_dict = parse_failed_patch_output(e.stdout)
            failed_hunks = {}
            if patch_cmd == gnu_patch:
                for path, failed_hunk_ids in failed_hunks_id_dict.items():
                    hunks_for_file = parsed_hunks[path]
                    failed_hunks[path] = [
                        hunk
                        for hunk in hunks_for_file
                        if hunk.hunk_id in failed_hunk_ids
                    ]
            elif failed_hunks_id_dict:
                # use git am
                failed_hunks = parsed_hunks

            return PatchResult(succeeded=False, failed_hunks=failed_hunks)
        return PatchResult(succeeded=True)

    def test_apply(
        self, root_dir: Path, patch_cmd: Optional[Callable] = None
    ) -> PatchResult:
        """Dry run applying a patch to a given directory."""
        extra_args = [] if patch_cmd == git_am else ["--dry-run"]
        return self.apply(root_dir, patch_cmd, extra_args)

    def title(self) -> str:
        if not self.metadata:
            return ""
        return self.metadata.get("title", "")


@dataclasses.dataclass(frozen=True)
class PatchInfo:
    """Holds info for a round of patch applications."""

    # str types are legacy. Patch lists should
    # probably be PatchEntries,
    applied_patches: List[PatchEntry]
    failed_patches: List[PatchEntry]
    # Can be deleted once legacy code is removed.
    non_applicable_patches: List[PatchEntry]
    # Can be deleted once legacy code is removed.
    disabled_patches: List[str]
    # Can be deleted once legacy code is removed.
    removed_patches: List[str]
    # Can be deleted once legacy code is removed.
    modified_metadata: Optional[str]

    def _asdict(self):
        return dataclasses.asdict(self)


def json_to_patch_entries(workdir: Path, json_fd: IO[str]) -> List[PatchEntry]:
    """Convert a json IO object to List[PatchEntry].

    Examples:
        >>> f = open('PATCHES.json')
        >>> patch_entries = json_to_patch_entries(Path(), f)
    """
    return [PatchEntry.from_dict(workdir, d) for d in json.load(json_fd)]


def json_str_to_patch_entries(workdir: Path, json_str: str) -> List[PatchEntry]:
    """Convert a json IO object to List[PatchEntry].

    Examples:
        >>> f = open('PATCHES.json').read()
        >>> patch_entries = json_str_to_patch_entries(Path(), f)
    """
    return [PatchEntry.from_dict(workdir, d) for d in json.loads(json_str)]


def _print_failed_patch(pe: PatchEntry, failed_hunks: Dict[str, List[Hunk]]):
    """Print information about a single failing PatchEntry.

    Args:
        pe: A PatchEntry that failed.
        failed_hunks: Hunks for pe which failed as dict:
          filepath: [Hunk...]
    """
    print(f"Could not apply {pe.rel_patch_path}: {pe.title()}", file=sys.stderr)
    for fp, hunks in failed_hunks.items():
        print(f"{fp}:", file=sys.stderr)
        for h in hunks:
            print(
                f"- {pe.rel_patch_path} "
                f"l:{h.patch_hunk_lineno_begin}...{h.patch_hunk_lineno_end}",
                file=sys.stderr,
            )


def apply_all_from_json(
    svn_version: int,
    llvm_src_dir: Path,
    patches_json_fp: Path,
    patch_cmd: Optional[Callable] = None,
    continue_on_failure: bool = False,
) -> PatchInfo:
    """Attempt to apply some patches to a given LLVM source tree.

    This relies on a PATCHES.json file to be the primary way
    the patches are applied.

    Args:
        svn_version: LLVM Subversion revision to patch.
        llvm_src_dir: llvm-project root-level source directory to patch.
        patches_json_fp: Filepath to the PATCHES.json file.
        continue_on_failure: Skip any patches which failed to apply,
          rather than throw an Exception.
    """
    with patches_json_fp.open(encoding="utf-8") as f:
        patches = json_to_patch_entries(patches_json_fp.parent, f)
    skipped_patches = []
    failed_patches = []
    applied_patches = []
    for pe in patches:
        applied, failed_hunks = apply_single_patch_entry(
            svn_version, llvm_src_dir, pe, patch_cmd
        )
        if applied:
            applied_patches.append(pe)
            continue
        if failed_hunks is not None:
            if continue_on_failure:
                failed_patches.append(pe)
                continue
            else:
                _print_failed_patch(pe, failed_hunks)
                raise RuntimeError(
                    "failed to apply patch " f"{pe.patch_path()}: {pe.title()}"
                )
        # Didn't apply, didn't fail, it was skipped.
        skipped_patches.append(pe)
    return PatchInfo(
        non_applicable_patches=skipped_patches,
        applied_patches=applied_patches,
        failed_patches=failed_patches,
        disabled_patches=[],
        removed_patches=[],
        modified_metadata=None,
    )


def apply_single_patch_entry(
    svn_version: int,
    llvm_src_dir: Path,
    pe: PatchEntry,
    patch_cmd: Optional[Callable] = None,
    ignore_version_range: bool = False,
) -> Tuple[bool, Optional[Dict[str, List[Hunk]]]]:
    """Try to apply a single PatchEntry object.

    Returns:
        Tuple where the first element indicates whether the patch applied, and
        the second element is a faild hunk mapping from file name to lists of
        hunks (if the patch didn't apply).
    """
    # Don't apply patches outside of the version range.
    if not ignore_version_range and not pe.can_patch_version(svn_version):
        return False, None
    # Test first to avoid making changes.
    test_application = pe.test_apply(llvm_src_dir, patch_cmd)
    if not test_application:
        return False, test_application.failed_hunks
    # Now actually make changes.
    application_result = pe.apply(llvm_src_dir, patch_cmd)
    if not application_result:
        # This should be very rare/impossible.
        return False, application_result.failed_hunks
    return True, None


def is_git_dirty(git_root_dir: Path) -> bool:
    """Return whether the given git directory has uncommitted changes."""
    if not git_root_dir.is_dir():
        raise ValueError(f"git_root_dir {git_root_dir} is not a directory")
    cmd = ["git", "ls-files", "-m", "--other", "--exclude-standard"]
    return (
        subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            check=True,
            cwd=git_root_dir,
            encoding="utf-8",
        ).stdout
        != ""
    )


def clean_src_tree(src_path):
    """Cleans the source tree of the changes made in 'src_path'."""

    reset_src_tree_cmd = ["git", "-C", src_path, "reset", "HEAD", "--hard"]

    subprocess.run(reset_src_tree_cmd, check=True)

    clean_src_tree_cmd = ["git", "-C", src_path, "clean", "-fd"]

    subprocess.run(clean_src_tree_cmd, check=True)


@contextlib.contextmanager
def git_clean_context(git_root_dir: Path):
    """Cleans up a git directory when the context exits."""
    if is_git_dirty(git_root_dir):
        raise RuntimeError("Cannot setup clean context; git_root_dir is dirty")
    try:
        yield
    finally:
        clean_src_tree(git_root_dir)


def _write_json_changes(
    patches: List[Dict[str, Any]], file_io: IO[str], indent_len=2
):
    """Write JSON changes to file, does not acquire new file lock."""
    json.dump(patches, file_io, indent=indent_len, separators=(",", ": "))
    # Need to add a newline as json.dump omits it.
    file_io.write("\n")


def predict_indent(patches_lines: List[str]) -> int:
    """Given file lines, predict and return the max indentation unit."""
    indents = [len(x) - len(x.lstrip(" ")) for x in patches_lines]
    if all(x % 4 == 0 for x in indents):
        return 4
    if all(x % 2 == 0 for x in indents):
        return 2
    if all(x == 0 for x in indents):
        return 0
    return 1


def update_version_ranges(
    svn_version: int,
    llvm_src_dir: Path,
    patches_json_fp: Path,
    patch_cmd: Optional[Callable] = None,
) -> PatchInfo:
    """Reduce the version ranges of failing patches.

    Patches which fail to apply will have their 'version_range.until'
    field reduced to the passed in svn_version.

    Modifies the contents of patches_json_fp.

    Args:
        svn_version: LLVM revision number.
        llvm_src_dir: llvm-project directory path.
        patches_json_fp: Filepath to the PATCHES.json file.
        patch_cmd: option to apply patch.

    Returns:
        PatchInfo for applied and disabled patches.
    """
    with patches_json_fp.open(encoding="utf-8") as f:
        contents = f.read()
    indent_len = predict_indent(contents.splitlines())
    patch_entries = json_str_to_patch_entries(
        patches_json_fp.parent,
        contents,
    )
    modified_entries, applied_patches = update_version_ranges_with_entries(
        svn_version, llvm_src_dir, patch_entries, patch_cmd
    )
    with atomic_write_file.atomic_write(patches_json_fp, encoding="utf-8") as f:
        _write_json_changes(
            [p.to_dict() for p in patch_entries], f, indent_len=indent_len
        )
    for entry in modified_entries:
        print(
            f"Stopped applying {entry.rel_patch_path} ({entry.title()}) "
            f"for r{svn_version}"
        )
    return PatchInfo(
        non_applicable_patches=[],
        applied_patches=applied_patches,
        failed_patches=[],
        disabled_patches=[p.rel_patch_path for p in modified_entries],
        removed_patches=[],
        modified_metadata=str(patches_json_fp) if modified_entries else None,
    )


def update_version_ranges_with_entries(
    svn_version: int,
    llvm_src_dir: Path,
    patch_entries: Iterable[PatchEntry],
    patch_cmd: Optional[Callable] = None,
) -> Tuple[List[PatchEntry], List[PatchEntry]]:
    """Test-able helper for UpdateVersionRanges.

    Args:
        svn_version: LLVM revision number.
        llvm_src_dir: llvm-project directory path.
        patch_entries: PatchEntry objects to modify.

    Returns:
        Tuple of (modified entries, applied patches)

    Post:
        Modifies patch_entries in place.
    """
    modified_entries: List[PatchEntry] = []
    applied_patches: List[PatchEntry] = []
    active_patches = (
        pe for pe in patch_entries if pe.can_patch_version(svn_version)
    )
    with git_clean_context(llvm_src_dir):
        for pe in active_patches:
            test_result = pe.test_apply(llvm_src_dir, patch_cmd)
            if not test_result:
                if pe.version_range is None:
                    pe.version_range = {}
                pe.version_range["until"] = svn_version
                modified_entries.append(pe)
            else:
                # We have to actually apply the patch so that future patches
                # will stack properly.
                if not pe.apply(llvm_src_dir, patch_cmd).succeeded:
                    raise RuntimeError(
                        "Could not apply patch that dry ran successfully"
                    )
                applied_patches.append(pe)

    return modified_entries, applied_patches


def remove_old_patches(
    svn_version: int, llvm_src_dir: Path, patches_json_fp: Path
) -> PatchInfo:
    """Remove patches that don't and will never apply for the future.

    Patches are determined to be "old" via the "is_old" method for
    each patch entry.

    Args:
        svn_version: LLVM SVN version.
        llvm_src_dir: LLVM source directory.
        patches_json_fp: Location to edit patches on.

    Returns:
        PatchInfo for modified patches.
    """
    with patches_json_fp.open(encoding="utf-8") as f:
        contents = f.read()
    indent_len = predict_indent(contents.splitlines())
    patch_entries = json_str_to_patch_entries(
        llvm_src_dir,
        contents,
    )
    oldness = [(entry, entry.is_old(svn_version)) for entry in patch_entries]
    filtered_entries = [entry.to_dict() for entry, old in oldness if not old]
    with atomic_write_file.atomic_write(patches_json_fp, encoding="utf-8") as f:
        _write_json_changes(filtered_entries, f, indent_len=indent_len)
    removed_entries = [entry for entry, old in oldness if old]
    plural_patches = "patch" if len(removed_entries) == 1 else "patches"
    print(f"Removed {len(removed_entries)} old {plural_patches}:")
    for r in removed_entries:
        print(f"- {r.rel_patch_path}: {r.title()}")

    return PatchInfo(
        non_applicable_patches=[],
        applied_patches=[],
        failed_patches=[],
        disabled_patches=[],
        removed_patches=[p.rel_patch_path for p in removed_entries],
        modified_metadata=str(patches_json_fp) if removed_entries else None,
    )


def git_am(patch_path: Path) -> List[Union[str, Path]]:
    cmd = ["git", "am", "--3way", str(patch_path)]
    return cmd


def gnu_patch(root_dir: Path, patch_path: Path) -> List[Union[str, Path]]:
    cmd = [
        "patch",
        "-d",
        str(root_dir.absolute()),
        "-f",
        "-E",
        "-p1",
        "--no-backup-if-mismatch",
        "-i",
        str(patch_path),
    ]
    return cmd
