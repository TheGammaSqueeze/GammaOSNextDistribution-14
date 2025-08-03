# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A collection of tools used by the PGO scripts here."""

import contextlib
import logging
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
from typing import Any, Dict, Generator, IO, List, Optional, Union


Command = List[Union[str, Path]]


def run(
    command: Command,
    cwd: Optional[Path] = None,
    check: bool = True,
    extra_env: Optional[Dict[str, str]] = None,
    stdout: Union[IO[Any], int, None] = None,
    stderr: Union[IO[Any], int, None] = None,
) -> subprocess.CompletedProcess:
    """Convenient wrapper around subprocess.run."""
    if extra_env:
        env = dict(os.environ)
        env.update(extra_env)
    else:
        env = None

    if logging.getLogger().isEnabledFor(logging.DEBUG):
        c = " ".join(shlex.quote(str(x)) for x in command)
        dir_extra = f" in {cwd}" if cwd is not None else ""
        logging.debug("Running `%s`%s", c, dir_extra)

    return subprocess.run(
        command,
        check=check,
        cwd=cwd,
        env=env,
        encoding="utf-8",
        stdin=subprocess.DEVNULL,
        stdout=stdout,
        stderr=stderr,
    )


def installed_llvm_has_pgo_generate_enabled() -> bool:
    """Returns whether the currently-installed LLVM has USE=pgo_generate."""
    equery_output = run(
        ["equery", "--no-color", "--no-pipe", "u", "sys-devel/llvm"],
        stdout=subprocess.PIPE,
    ).stdout

    # The output of `equery` is in the format:
    # `${default_state_if_emerged} ${state_of_installed_pkg} llvm_pgo_generate`
    #
    # The relevant bit is the second.
    r = re.compile(r"^ [+-] ([+-]) llvm_pgo_generate\s", re.MULTILINE)
    results = r.findall(equery_output)
    if not results:
        raise ValueError(
            "No llvm_pgo_generate line found in USE for sys-devel/llvm"
        )

    if len(results) > 1:
        raise ValueError(
            "Multiple llvm_pgo_generate line found in USE for sys-devel/llvm"
        )

    return results[0] == "+"


def quickpkg_llvm() -> Path:
    """Runs quickpkg to generate an LLVM binpkg."""
    if installed_llvm_has_pgo_generate_enabled():
        # If you do want this, feel free to find this check and bypass it.
        # There's nothing _inherently wrong_ with using a +pgo_generate LLVM.
        # It'll just take *a lot* of extra time (2.5x+) for no reason. If you
        # want to start fresh:
        # ```
        # sudo rm -rf /var/lib/portage/pkgs/sys-devel/llvm*tbz2 && \
        #    sudo emerge -G sys-devel/llvm
        # ```
        raise ValueError(
            "Base LLVM version has pgo_generate enabled; this is "
            "almost definitely not what you want. You can "
            "quickly restore to a non-pgo_generate LLVM by "
            "running `sudo emerge -G sys-devel/llvm`."
        )

    logging.info("Building binpackage for existing sys-devel/llvm installation")
    quickpkg_result = run(
        ["quickpkg", "sys-devel/llvm"], stdout=subprocess.PIPE
    ).stdout
    # We have to scrape for the package's name, since the package generated is
    # for the _installed_ version of LLVM, which might not match the current
    # ebuild's version.
    matches = re.findall(
        r"Building package for sys-devel/(llvm-[^ ]+) ", quickpkg_result
    )
    if len(matches) != 1:
        raise ValueError(
            f"Couldn't determine LLVM version from {quickpkg_result!r};"
            f"candidates: {matches}"
        )

    llvm_ver = matches[0]
    pkg = Path("/var/lib/portage/pkgs/sys-devel", llvm_ver + ".tbz2")
    assert pkg.exists(), f"expected binpkg at {pkg} not found"
    return pkg


def generate_quickpkg_restoration_command(quickpkg_path: Path) -> Command:
    """Returns a command you can run to restore the quickpkg'ed package."""
    package_ver = quickpkg_path.stem
    category = quickpkg_path.parent.name
    return ["sudo", "emerge", "--usepkgonly", f"={category}/{package_ver}"]


def is_in_chroot() -> bool:
    """Returns whether this script was invoked inside of the chroot."""
    return Path("/etc/cros_chroot_version").exists()


def exit_if_not_in_chroot():
    """Calls sys.exit if this script was not run inside of the chroot."""
    if not is_in_chroot():
        sys.exit("Run me inside of the chroot.")


def exit_if_in_chroot():
    """Calls sys.exit if this script was run inside of the chroot."""
    if is_in_chroot():
        sys.exit("Run me outside of the chroot.")


@contextlib.contextmanager
def temporary_file(prefix: Optional[str] = None) -> Generator[Path, None, None]:
    """Yields a temporary file name, and cleans it up on exit.

    This differs from NamedTemporaryFile in that it doesn't keep a handle to
    the file open. This is useful if the temporary file is intended to be
    passed to another process (e.g., through a flag), since that other process
    might/might not overwite the file, leading to subtle bugs about reusing the
    File-like aspects of NamedTemporaryFile.
    """
    fd, tmp = tempfile.mkstemp(prefix=prefix)
    tmp_path = Path(tmp)
    try:
        os.close(fd)
        yield tmp_path
    finally:
        tmp_path.unlink(missing_ok=True)
