# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob
import logging
import os
import re

from autotest_lib.client.bin import utils

_KERNEL_A = {'name': 'KERN-A', 'kernel': 2, 'root': 3}
_KERNEL_B = {'name': 'KERN-B', 'kernel': 4, 'root': 5}
_MINIOS_A = 'A'
_MINIOS_B = 'B'

# Time to wait for new kernel to be marked successful after auto update.
_KERNEL_UPDATE_TIMEOUT = 120

_BOOT_ERR_MSG = 'The active image slot did not change after the update.'

def _run(cmd, host=None):
    """
    Function to execue commands.

    This allows the util to be used by client and server tests.

    @param cmd: The command to execute
    @param host: The host to use in a server test. None to use utils.run.

    """
    if host is not None:
        return host.run(cmd)
    else:
        return utils.run(cmd)

def _cgpt(flag, kernel, host=None):
    """
    Helper function to return numeric cgpt value.

    @param flag: Additional flag to pass to cgpt
    @param kernel: The kernel we want to interact with.
    @param host: The DUT to execute the command on. None to execute locally.

    """
    rootdev = _run(['rootdev','-s', '-d'], host).stdout.strip()
    return int(_run(['cgpt', 'show', '-n', '-i', str(kernel['kernel']), flag,
                     rootdev], host).stdout.strip())

def get_kernel_state(host=None):
    """
    Returns the (<active>, <inactive>) kernel state as a pair.

    @param host: The DUT to execute the command on. None to execute locally.

    """
    rootdev = _run(['rootdev','-s'], host).stdout.strip()
    active_root = int(re.findall('\d+\Z', rootdev)[0])
    if active_root == _KERNEL_A['root']:
        return _KERNEL_A, _KERNEL_B
    elif active_root == _KERNEL_B['root']:
        return _KERNEL_B, _KERNEL_A
    else:
        raise Exception('Encountered unknown root partition: %s' % active_root)

def get_next_kernel(host=None):
    """
    Return the kernel that has priority for the next boot.

    @param host: The DUT to execute the command on. None to execute locally.

    """
    priority_a = _cgpt('-P', _KERNEL_A, host)
    priority_b = _cgpt('-P', _KERNEL_B, host)
    return _KERNEL_A if priority_a > priority_b else _KERNEL_B

def get_kernel_success(kernel, host=None):
    """
    Return boolean success flag for the specified kernel.

    @param kernel: Information of the given kernel, either _KERNEL_A
                   or _KERNEL_B.
    @param host: The DUT to execute the command on. None to execute locally.

    """
    return _cgpt('-S', kernel, host) != 0

def get_kernel_tries(kernel, host=None):
    """Return tries count for the specified kernel.

    @param kernel: Information of the given kernel, either _KERNEL_A
                   or _KERNEL_B.
    @param host: The DUT to execute the command on. None to execute locally.

    """
    return _cgpt('-T', kernel, host)


def verify_kernel_state_after_update(host=None, inactive_kernel=True):
    """
    Ensure the next kernel to boot is the expected kernel.

    This is useful for checking after completing an update.

    @param host: The DUT to execute the command on. None to execute locally.
    @param inactive_kernel: Indicates if the expected kernel is the inactive
                            kernel (True) or the active kernel (False).
    @returns the next kernel.

    """
    expected_kernel = get_kernel_state(host)[1 if inactive_kernel else 0]
    next_kernel = get_next_kernel(host)
    if next_kernel != expected_kernel:
        raise Exception(
                'The kernel for next boot is %s, but %s was expected.' %
                (next_kernel['name'], expected_kernel['name']))
    return next_kernel


def verify_boot_expectations(expected_kernel, error_message=_BOOT_ERR_MSG,
                             host=None):
    """Verifies that we fully booted into the expected kernel state.

    This method both verifies that we booted using the correct kernel
    state and that the OS has marked the kernel as good.

    @param expected_kernel: kernel that we are verifying with,
                            eg I expect to be booted onto partition 4.
    @param error_message: string include in except message text
                          if we booted with the wrong partition.
    @param host: The DUT to execute the command on. None to execute locally.

    """
    # Figure out the newly active kernel.
    active_kernel = get_kernel_state(host)[0]

    if active_kernel != expected_kernel:
        # Kernel crash reports should be wiped between test runs, but
        # may persist from earlier parts of the test, or from problems
        # with provisioning.
        #
        # Kernel crash reports will NOT be present if the crash happened
        # before encrypted stateful is mounted.
        kernel_crashes = glob.glob('/var/spool/crash/kernel.*.kcrash')
        if kernel_crashes:
            error_message += ': kernel_crash'
            logging.debug('Found %d kernel crash reports:',
                          len(kernel_crashes))
            # The crash names contain timestamps that may be useful:
            #   kernel.20131207.005945.12345.0.kcrash
            for crash in kernel_crashes:
                logging.debug('  %s', os.path.basename(crash))

        # Print out some information to make it easier to debug
        # the rollback.
        logging.debug('Dumping partition table.')
        _run('cgpt show $(rootdev -s -d)', host)
        logging.debug('Dumping crossystem for firmware debugging.')
        _run('crossystem --all', host)
        raise Exception(error_message)

    # Make sure chromeos-setgoodkernel runs.
    try:
        utils.poll_for_condition(
            lambda: (get_kernel_tries(active_kernel, host) == 0
                     and get_kernel_success(active_kernel, host)),
            timeout=_KERNEL_UPDATE_TIMEOUT, sleep_interval=5)
    except Exception:
        services_status = _run(['status', 'system-services'], host).stdout
        if services_status != 'system-services start/running\n':
            raise Exception('Chrome failed to reach login screen')
        else:
            raise Exception('update-engine failed to call '
                            'chromeos-setgoodkernel')


def get_minios_priority(host=None):
    """
    Returns the (<active>, <inactive>) MiniOS partition as a pair.

    @param host: The DUT to execute the command on. None to execute locally.

    """
    active = _run(['crossystem', 'minios_priority'], host).stdout.strip()
    if active != _MINIOS_A and active != _MINIOS_B:
        raise Exception('Encountered unknown MiniOS partition: %s' % active)
    return (active, _MINIOS_B if active == _MINIOS_A else _MINIOS_A)


def verify_minios_priority_after_update(host=None, expected=None):
    """
    Ensure the next MiniOS to boot is the expected one.

    This is useful for checking after completing an update.

    @param host: The DUT to execute the command on. None to execute locally.
    @param expected: The expected MiniOS partition for the next boot.
    """
    active = _run(['crossystem', 'minios_priority'], host).stdout.strip()
    if active != expected:
        raise Exception(
                'The MiniOS partition for next boot is %s, but %s was expected.'
                % (active, expected))
    else:
        logging.debug(
                'The MiniOS partition for next boot is %s, matches expected.',
                active)
