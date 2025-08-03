#!/usr/bin/env python3

# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Wrapper script to automatically lock devices for crosperf."""

import argparse
import contextlib
import dataclasses
import json
import os
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple


# Have to do sys.path hackery because crosperf relies on PYTHONPATH
# modifications.
PARENT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(PARENT_DIR)


def main(sys_args: List[str]) -> Optional[str]:
    """Run crosperf_autolock. Returns error msg or None"""
    args, leftover_args = parse_args(sys_args)
    fleet_params = [
        CrosfleetParams(
            board=args.board, pool=args.pool, lease_time=args.lease_time
        )
        for _ in range(args.num_leases)
    ]
    if not fleet_params:
        return (
            "No board names identified. If you want to use"
            " a known host, just use crosperf directly."
        )
    try:
        _run_crosperf(fleet_params, args.dut_lock_timeout, leftover_args)
    except BoardLockError as e:
        _eprint("ERROR:", e)
        _eprint('May need to login to crosfleet? Run "crosfleet login"')
        _eprint(
            "The leases may also be successful later on. "
            'Check with "crosfleet dut leases"'
        )
        return "crosperf_autolock failed"
    except BoardReleaseError as e:
        _eprint("ERROR:", e)
        _eprint('May need to re-run "crosfleet dut abandon"')
        return "crosperf_autolock failed"
    return None


def parse_args(args: List[str]) -> Tuple[Any, List]:
    """Parse the CLI arguments."""
    parser = argparse.ArgumentParser(
        "crosperf_autolock",
        description="Wrapper around crosperf"
        " to autolock DUTs from crosfleet.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--board",
        type=str,
        help="Space or comma separated list of boards to lock",
        required=True,
        default=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--num-leases",
        type=int,
        help="Number of boards to lock.",
        metavar="NUM",
        default=1,
    )
    parser.add_argument(
        "--pool", type=str, help="Pool to pull from.", default="DUT_POOL_QUOTA"
    )
    parser.add_argument(
        "--dut-lock-timeout",
        type=float,
        metavar="SEC",
        help="Number of seconds we want to try to lease a board"
        " from crosfleet. This option does NOT change the"
        " lease length.",
        default=600,
    )
    parser.add_argument(
        "--lease-time",
        type=int,
        metavar="MIN",
        help="Number of minutes to lock the board. Max is 1440.",
        default=1440,
    )
    parser.epilog = (
        "For more detailed flags, you have to read the args taken by the"
        " crosperf executable. Args are passed transparently to crosperf."
    )
    return parser.parse_known_args(args)


class BoardLockError(Exception):
    """Error to indicate failure to lock a board."""

    def __init__(self, msg: str):
        self.msg = "BoardLockError: " + msg
        super().__init__(self.msg)


class BoardReleaseError(Exception):
    """Error to indicate failure to release a board."""

    def __init__(self, msg: str):
        self.msg = "BoardReleaseError: " + msg
        super().__init__(self.msg)


@dataclasses.dataclass(frozen=True)
class CrosfleetParams:
    """Dataclass to hold all crosfleet parameterizations."""

    board: str
    pool: str
    lease_time: int


def _eprint(*msg, **kwargs):
    print(*msg, file=sys.stderr, **kwargs)


def _run_crosperf(
    crosfleet_params: List[CrosfleetParams],
    lock_timeout: float,
    leftover_args: List[str],
):
    """Autolock devices and run crosperf with leftover arguments.

    Raises:
      BoardLockError: When board was unable to be locked.
      BoardReleaseError: When board was unable to be released.
    """
    if not crosfleet_params:
        raise ValueError("No crosfleet params given; cannot call crosfleet.")

    # We'll assume all the boards are the same type, which seems to be the case
    # in experiments that actually get used.
    passed_board_arg = crosfleet_params[0].board
    with contextlib.ExitStack() as stack:
        dut_hostnames = []
        for param in crosfleet_params:
            print(
                f"Sent lock request for {param.board} for {param.lease_time} minutes"
                '\nIf this fails, you may need to run "crosfleet dut abandon <...>"'
            )
            # May raise BoardLockError, abandoning previous DUTs.
            dut_hostname = stack.enter_context(
                crosfleet_machine_ctx(
                    param.board,
                    param.lease_time,
                    lock_timeout,
                    {"label-pool": param.pool},
                )
            )
            if dut_hostname:
                print(f"Locked {param.board} machine: {dut_hostname}")
                dut_hostnames.append(dut_hostname)

        # We import crosperf late, because this import is extremely slow.
        # We don't want the user to wait several seconds just to get
        # help info.
        import crosperf

        for dut_hostname in dut_hostnames:
            crosperf.Main(
                [
                    sys.argv[0],
                    "--no_lock",
                    "True",
                    "--remote",
                    dut_hostname,
                    "--board",
                    passed_board_arg,
                ]
                + leftover_args
            )


@contextlib.contextmanager
def crosfleet_machine_ctx(
    board: str,
    lease_minutes: int,
    lock_timeout: float,
    dims: Dict[str, Any],
    abandon_timeout: float = 120.0,
) -> Any:
    """Acquire dut from crosfleet, and release once it leaves the context.

    Args:
      board: Board type to lease.
      lease_minutes: Length of lease, in minutes.
      lock_timeout: How long to wait for a lock until quitting.
      dims: Dictionary of dimension arguments to pass to crosfleet's '-dims'
      abandon_timeout: How long to wait for releasing until quitting.

    Yields:
      A string representing the crosfleet DUT hostname.

    Raises:
      BoardLockError: When board was unable to be locked.
      BoardReleaseError: When board was unable to be released.
    """
    # This lock may raise an exception, but if it does, we can't release
    # the DUT anyways as we won't have the dut_hostname.
    dut_hostname = crosfleet_autolock(board, lease_minutes, dims, lock_timeout)
    try:
        yield dut_hostname
    finally:
        if dut_hostname:
            crosfleet_release(dut_hostname, abandon_timeout)


def crosfleet_autolock(
    board: str, lease_minutes: int, dims: Dict[str, Any], timeout_sec: float
) -> str:
    """Lock a device using crosfleet, paramaterized by the board type.

    Args:
      board: Board of the DUT we want to lock.
      lease_minutes: Number of minutes we're trying to lease the DUT for.
      dims: Dictionary of dimension arguments to pass to crosfleet's '-dims'
      timeout_sec: Number of seconds to try to lease the DUT. Default 120s.

    Returns:
      The hostname of the board, or empty string if it couldn't be parsed.

    Raises:
      BoardLockError: When board was unable to be locked.
    """
    crosfleet_cmd_args = [
        "crosfleet",
        "dut",
        "lease",
        "-json",
        '-reason="crosperf autolock"',
        f"-board={board}",
        f"-minutes={lease_minutes}",
    ]
    if dims:
        dims_arg = ",".join(f"{k}={v}" for k, v in dims.items())
        crosfleet_cmd_args.extend(["-dims", f"{dims_arg}"])

    try:
        output = subprocess.check_output(
            crosfleet_cmd_args, timeout=timeout_sec, encoding="utf-8"
        )
    except subprocess.CalledProcessError as e:
        raise BoardLockError(
            f"crosfleet dut lease failed with exit code: {e.returncode}"
        )
    except subprocess.TimeoutExpired as e:
        raise BoardLockError(
            f"crosfleet dut lease timed out after {timeout_sec}s;"
            " please abandon the dut manually."
        )

    try:
        json_obj = json.loads(output)
        dut_hostname = json_obj["DUT"]["Hostname"]
        if not isinstance(dut_hostname, str):
            raise TypeError("dut_hostname was not a string")
    except (json.JSONDecodeError, IndexError, KeyError, TypeError) as e:
        raise BoardLockError(
            f"crosfleet dut lease output was parsed incorrectly: {e!r};"
            f" observed output was {output}"
        )
    return _maybe_append_suffix(dut_hostname)


def crosfleet_release(dut_hostname: str, timeout_sec: float = 120.0):
    """Release a crosfleet device.

    Consider using the context managed crosfleet_machine_context

    Args:
      dut_hostname: Name of the device we want to release.
      timeout_sec: Number of seconds to try to release the DUT. Default is 120s.

    Raises:
      BoardReleaseError: Potentially failed to abandon the lease.
    """
    crosfleet_cmd_args = [
        "crosfleet",
        "dut",
        "abandon",
        dut_hostname,
    ]
    exit_code = subprocess.call(crosfleet_cmd_args, timeout=timeout_sec)
    if exit_code != 0:
        raise BoardReleaseError(
            f'"crosfleet dut abandon" had exit code {exit_code}'
        )


def _maybe_append_suffix(hostname: str) -> str:
    if hostname.endswith(".cros") or ".cros." in hostname:
        return hostname
    return hostname + ".cros"


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
