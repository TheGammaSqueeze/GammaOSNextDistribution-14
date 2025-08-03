# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Atomic file writing utilities.

Provides atomic_write(...), which allows atomically replacing the contents
of a file.
"""

import contextlib
import logging
import os
from pathlib import Path
import tempfile
from typing import Iterator, Literal, Optional, Union


@contextlib.contextmanager
def atomic_write(
    fp: Union[Path, str],
    mode: Literal["w", "wb"] = "w",
    encoding: Optional[str] = None,
) -> Iterator:
    """Write to a filepath atomically.

    This works by a temp file swap, created with a .tmp suffix in
    the same directory briefly until being renamed to the desired
    filepath.

    In the event an exception is raised during the write, the
    temporary file is deleted and the original filepath is untouched.

    Examples:
        >>> with atomic_write("my_file.txt", encoding="utf-8") as f:
        >>>     f.write("Hello world!")
        >>>     # my_file.txt is still unmodified
        >>> # "f" is closed here, and my_file.txt is written to.

    Args:
        fp: Filepath to open.
        mode: File mode; can be 'w', 'wb'. Default 'w'.
        encoding: the encoding to use (defaults to None).

    Raises:
        ValueError when the mode is invalid.
    """
    if isinstance(fp, str):
        fp = Path(fp)
    if mode not in ("w", "wb"):
        raise ValueError(f"mode {mode} not accepted")

    # We use mkstemp here because we want to handle the closing and
    # replacement ourselves.
    result = tempfile.mkstemp(
        prefix=fp.name,
        suffix=".tmp",
        dir=fp.parent,
    )
    fd, tmp_path = (result[0], Path(result[1]))

    try:
        with os.fdopen(fd, mode=mode, encoding=encoding) as f:
            yield f
    except:
        try:
            tmp_path.unlink()
        except Exception as e:
            logging.exception("unexpected error removing temporary file %s", e)
        raise
    tmp_path.replace(fp)
