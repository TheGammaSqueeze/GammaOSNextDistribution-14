#!/usr/bin/env python3
# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Utilities to file bugs."""

import datetime
import enum
import json
import os
import threading
from typing import Any, Dict, List, Optional, Union


X20_PATH = "/google/data/rw/teams/c-compiler-chrome/prod_bugs"

# List of 'well-known' bug numbers to tag as parents.
RUST_MAINTENANCE_METABUG = 322195383
RUST_SECURITY_METABUG = 322195192


# These constants are sourced from
# //google3/googleclient/chrome/chromeos_toolchain/bug_manager/bugs.go
class WellKnownComponents(enum.IntEnum):
    """A listing of "well-known" components recognized by our infra."""

    CrOSToolchainPublic = -1
    CrOSToolchainPrivate = -2
    AndroidRustToolchain = -3


class _FileNameGenerator:
    """Generates unique file names. This container is thread-safe.

    The names generated have the following properties:
        - successive, sequenced calls to `get_json_file_name()` will produce
          names that sort later in lists over time (e.g.,
          [generator.generate_json_file_name() for _ in range(10)] will be in
          sorted order).
        - file names cannot collide with file names generated on the same
          machine (ignoring machines with unreasonable PID reuse).
        - file names are incredibly unlikely to collide when generated on
          multiple machines, as they have 8 bytes of entropy in them.
    """

    _RANDOM_BYTES = 8
    _MAX_OS_ENTROPY_VALUE = 1 << _RANDOM_BYTES * 8
    # The intent of this is "the maximum possible size of our entropy string,
    # so we can zfill properly below." Double the value the OS hands us, since
    # we add to it in `generate_json_file_name`.
    _ENTROPY_STR_SIZE = len(str(2 * _MAX_OS_ENTROPY_VALUE))

    def __init__(self):
        self._lock = threading.Lock()
        self._entropy = int.from_bytes(
            os.getrandom(self._RANDOM_BYTES), byteorder="little", signed=False
        )

    def generate_json_file_name(self, now: datetime.datetime):
        with self._lock:
            my_entropy = self._entropy
            self._entropy += 1

        now_str = now.isoformat("T", "seconds") + "Z"
        entropy_str = str(my_entropy).zfill(self._ENTROPY_STR_SIZE)
        pid = os.getpid()
        return f"{now_str}_{entropy_str}_{pid}.json"


_GLOBAL_NAME_GENERATOR = _FileNameGenerator()


def _WriteBugJSONFile(
    object_type: str,
    json_object: Dict[str, Any],
    directory: Optional[Union[os.PathLike, str]],
):
    """Writes a JSON file to `directory` with the given bug-ish object.

    Args:
        object_type: name of the object we're writing.
        json_object: object to write.
        directory: the directory to write to. Uses X20_PATH if None.
    """
    final_object = {
        "type": object_type,
        "value": json_object,
    }

    if directory is None:
        directory = X20_PATH

    now = datetime.datetime.now(tz=datetime.timezone.utc)
    file_path = os.path.join(
        directory, _GLOBAL_NAME_GENERATOR.generate_json_file_name(now)
    )
    temp_path = file_path + ".in_progress"
    try:
        with open(temp_path, "w", encoding="utf-8") as f:
            json.dump(final_object, f)
        os.rename(temp_path, file_path)
    except:
        os.remove(temp_path)
        raise
    return file_path


def AppendToExistingBug(
    bug_id: int, body: str, directory: Optional[os.PathLike] = None
):
    """Sends a reply to an existing bug."""
    _WriteBugJSONFile(
        "AppendToExistingBugRequest",
        {
            "body": body,
            "bug_id": bug_id,
        },
        directory,
    )


def CreateNewBug(
    component_id: int,
    title: str,
    body: str,
    assignee: Optional[str] = None,
    cc: Optional[List[str]] = None,
    directory: Optional[os.PathLike] = None,
    parent_bug: int = 0,
):
    """Sends a request to create a new bug.

    Args:
        component_id: The component ID to add. Anything from WellKnownComponents
            also works.
        title: Title of the bug. Must be nonempty.
        body: Body of the bug. Must be nonempty.
        assignee: Assignee of the bug. Must be either an email address, or a
            "well-known" assignee (detective, mage).
        cc: A list of emails to add to the CC list. Must either be an email
            address, or a "well-known" individual (detective, mage).
        directory: The directory to write the report to. Defaults to our x20
            bugs directory.
        parent_bug: The parent bug number for this bug. If none should be
            specified, pass the value 0.
    """
    obj = {
        "component_id": component_id,
        "subject": title,
        "body": body,
    }

    if assignee:
        obj["assignee"] = assignee

    if cc:
        obj["cc"] = cc

    if parent_bug:
        obj["parent_bug"] = parent_bug

    _WriteBugJSONFile("FileNewBugRequest", obj, directory)


def SendCronjobLog(
    cronjob_name: str,
    failed: bool,
    message: str,
    turndown_time_hours: int = 0,
    directory: Optional[os.PathLike] = None,
    parent_bug: int = 0,
):
    """Sends the record of a cronjob to our bug infra.

    Args:
        cronjob_name: The name of the cronjob. Expected to remain consistent
            over time.
        failed: Whether the job failed or not.
        message: Any seemingly relevant context. This is pasted verbatim in a
            bug, if the cronjob infra deems it worthy.
        turndown_time_hours: If nonzero, this cronjob will be considered turned
            down if more than `turndown_time_hours` pass without a report of
            success or failure. If zero, this job will not automatically be
            turned down.
        directory: The directory to write the report to. Defaults to our x20
            bugs directory.
        parent_bug: The parent bug number for the bug filed for this cronjob,
            if any. If none should be specified, pass the value 0.
    """
    json_object = {
        "name": cronjob_name,
        "message": message,
        "failed": failed,
    }

    if turndown_time_hours:
        json_object["cronjob_turndown_time_hours"] = turndown_time_hours

    if parent_bug:
        json_object["parent_bug"] = parent_bug

    _WriteBugJSONFile("CronjobUpdate", json_object, directory)
