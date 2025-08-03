# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Provides utilities to read and edit the ChromiumOS Manifest entries.

While this code reads and edits the internal manifest, it should only operate
on toolchain projects (llvm-project, etc.) which are public.
"""

from pathlib import Path
import shutil
import subprocess
from typing import List, Union
from xml.etree import ElementTree

import atomic_write_file


LLVM_PROJECT_PATH = "src/third_party/llvm-project"


class FormattingError(Exception):
    """Error occurred when formatting the manifest."""


class UpdateManifestError(Exception):
    """Error occurred when updating the manifest."""


def make_xmlparser():
    """Return a new xmlparser with custom TreeBuilder."""
    return ElementTree.XMLParser(
        target=ElementTree.TreeBuilder(insert_comments=True)
    )


def update_chromeos_manifest(revision: str, src_tree: Path) -> Path:
    """Replaces the manifest project revision with 'revision'.

    Notably, this function reformats the manifest file to preserve
    the formatting as specified by 'cros format'.

    Args:
        revision: Revision (git sha) to use in the manifest.
        src_tree: Path to the root of the source tree checkout.

    Returns:
        The manifest path.

    Post:
        The llvm-project revision info in the chromeos repo manifest
        is updated with 'revision'.

    Raises:
        UpdateManifestError: The manifest could not be changed.
        FormattingError: The manifest could not be reformatted.
    """
    manifest_path = get_chromeos_manifest_path(src_tree)
    parser = make_xmlparser()
    xmltree = ElementTree.parse(manifest_path, parser)
    update_chromeos_manifest_tree(revision, xmltree.getroot())
    with atomic_write_file.atomic_write(manifest_path, mode="wb") as f:
        xmltree.write(f, encoding="utf-8")
    format_manifest(manifest_path)
    return manifest_path


def get_chromeos_manifest_path(src_tree: Path) -> Path:
    """Return the path to the toolchain manifest."""
    return src_tree / "manifest-internal" / "_toolchain.xml"


def update_chromeos_manifest_tree(revision: str, xmlroot: ElementTree.Element):
    """Update the revision info for LLVM for a manifest XML root."""

    # This exists mostly for testing.
    def is_llvm_project(child):
        return (
            child.tag == "project" and child.attrib["path"] == LLVM_PROJECT_PATH
        )

    finder = (child for child in xmlroot if is_llvm_project(child))
    llvm_project_elem = next(finder, None)
    # Element objects can be falsy, so we need to explicitly check None.
    if llvm_project_elem is not None:
        # Update the llvm revision git sha
        llvm_project_elem.attrib["revision"] = revision
    else:
        raise UpdateManifestError("xmltree did not have llvm-project")


def format_manifest(repo_manifest: Path):
    """Use cros format to format the given manifest."""
    if not shutil.which("cros"):
        raise FormattingError(
            "unable to format manifest, 'cros'" " executable not in PATH"
        )
    cmd: List[Union[str, Path]] = ["cros", "format", repo_manifest]
    subprocess.run(cmd, check=True)
