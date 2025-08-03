#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Updates the Manifest file for LLVM.

Often used to pull a new PGO profile in.
"""

import argparse
import contextlib
import logging
from pathlib import Path
import re
import subprocess
import sys
from typing import Generator, List

import pgo_tools


@contextlib.contextmanager
def temporarily_add_llvm_next_pgo_to_src_uri(
    llvm_9999_ebuild: Path,
) -> Generator[None, None, None]:
    old_contents = llvm_9999_ebuild.read_text(encoding="utf-8")

    profdata_prefix = "gs://chromeos-localmirror/distfiles/llvm-profdata-"
    profdata_re = re.compile(
        # Leave room for a suffix on this, in case we're on the Nth version of
        # llvm-profdata for some reason.
        re.escape(profdata_prefix + "${LLVM_HASH}")
        + r"\S*\.xz\s"
    )
    found_urls = list(profdata_re.finditer(old_contents))
    if len(found_urls) != 1:
        raise ValueError(
            f"Want 1 instance of {profdata_re} in {llvm_9999_ebuild}; found "
            f"{len(found_urls)}"
        )

    # Insert the new profdata URL right after the old one. The combination of
    # USE variables gating this file doesn't have to make sense; the URL just
    # has to be visible to Portage.

    # Note that the regex ended with `\s`, so `.end()` will be after a space.
    insert_url = profdata_prefix + "${LLVM_NEXT_HASH}.xz "
    insert_point = found_urls[0].end()
    new_contents = (
        old_contents[:insert_point] + insert_url + old_contents[insert_point:]
    )

    llvm_9999_ebuild.write_text(new_contents, encoding="utf-8")
    try:
        yield
    finally:
        llvm_9999_ebuild.write_text(old_contents, encoding="utf-8")


def update_manifest(llvm_9999_ebuild: Path):
    subprocess.run(
        ["ebuild", llvm_9999_ebuild, "manifest"],
        check=True,
        stdin=subprocess.DEVNULL,
    )


def main(argv: List[str]) -> None:
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    pgo_tools.exit_if_not_in_chroot()

    my_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--llvm-next",
        action="store_true",
        help="Also update for the llvm-next PGO profile.",
    )
    parser.add_argument(
        "--chromiumos-overlay",
        default=my_dir.parent.parent / "chromiumos-overlay",
        type=Path,
        help="The chromiumos-overlay directory to work in. Default %(default)s",
    )
    opts = parser.parse_args(argv)

    llvm_9999 = opts.chromiumos_overlay / "sys-devel/llvm/llvm-9999.ebuild"
    if opts.llvm_next:
        with temporarily_add_llvm_next_pgo_to_src_uri(llvm_9999):
            update_manifest(llvm_9999)
    else:
        update_manifest(llvm_9999)


if __name__ == "__main__":
    main(sys.argv[1:])
