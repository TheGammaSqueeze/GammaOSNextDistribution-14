# Lint as: python2, python3
# Copyright 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging, os
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import constants


_RM_FILES = ['/home/chronos/.oobe_completed',
             '/home/chronos/Local\ State',
             '/var/cache/shill/default.profile']
# TODO(b/187793661) Delete /var/lib/whitelist once migration is finished.
_RM_DIRS = [
        '/home/.shadow/*',
        os.path.join(constants.DEVICESETTINGS_DIR, '*'),
        '/var/lib/whitelist/*',
        '/var/cache/app_pack',
        '/var/lib/tpm',
]


class NoTPMPasswordException(Exception):
    """No TPM Password could be found."""
    pass


def TPMStatus(client):
    """Returns a dictionary with TPM status.

    @param client: client object to run commands on.
    """
    out = client.run('tpm_manager_client status --nonsensitive').stdout.strip()
    lines = out.split('\n')[1:-1]
    status = {}
    for item in lines:
        item = item.split(':')
        if not item[0]:
            continue
        if len(item) == 1:
            item.append('')
        item = [x.strip() for x in item]
        item[1] = True if item[1] == 'true' else item[1]
        item[1] = False if item[1] == 'false' else item[1]
        status[item[0]] = item[1]
    return status


def ClearTPMServer(client, out_dir):
    """Clears the TPM and reboots from a server-side autotest.

    @param client: client object to run commands on.
    @param out_dir: temporary directory.
    """
    client.run('stop ui')
    ClearTPMOwnerRequest(client)


def ClearTPMOwnerRequest(client, wait_for_ready=False, timeout=60):
    """Clears the TPM using crossystem command.

    @param client: client object to run commands on.
    @param wait_for_ready: wait until the TPM status is ready
    @param timeout: number of seconds to wait for the TPM to become ready.
    """
    ownership_id = client.run('hwsec-ownership-id id')
    if not ownership_id.exit_status == 0:
        raise error.TestFail('Unable to get ownership ID.')

    ownership_id = ownership_id.stdout.strip()

    logging.info('Sending Clear TPM owner request')
    client.run('crossystem clear_tpm_owner_request=1')
    CleanupAndReboot(client)

    if wait_for_ready:
        status = 1
        end_time = time.time() + timeout
        # Wait for the ownership ID changed.
        while status != 0 and time.time() < end_time:
            status = client.run('hwsec-ownership-id diff id=' + ownership_id,
                                ignore_status=True).exit_status
            time.sleep(1)
        if status != 0:
            raise error.TestFail('Failed to clear TPM.')


def ClearTPMIfOwned(client):
    """Clear the TPM only if device is already owned.

    @param client: client object to run commands on."""
    tpm_status = TPMStatus(client)
    logging.info('TPM status: %s', tpm_status)
    if tpm_status['is_owned']:
        logging.info('Clearing TPM because this device is owned.')
        ClearTPMOwnerRequest(client)


def CleanupAndReboot(client):
    """Cleanup and reboot the device.

    @param client: client object to run commands on.
    """
    full_rm = 'sudo rm -rf ' + ' '.join(_RM_FILES + _RM_DIRS)
    client.run(full_rm, ignore_status=True)
    client.run('sync', ignore_status=True)
    client.reboot()


def FwmpIsAllZero(get_fwmp_output):
    """Check if firmware management parameters are all zero.

    @param get_fwmp_output: output from the command
        'cryptohome --action=get_firmware_management_parameters'.
    """
    return ('flags=0x00000000' in get_fwmp_output and
            'hash=0000000000000000000000000000000000000000000000000000000000000000'
            in get_fwmp_output)
