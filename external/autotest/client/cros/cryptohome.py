# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging, os, random, re, shutil, string, time

import common

from autotest_lib.client.cros import constants
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.client.cros.tpm import *

ATTESTATION_CMD = '/usr/bin/attestation_client'
CRYPTOHOME_CMD = '/usr/sbin/cryptohome'
TPM_MANAGER_CMD = '/usr/bin/tpm_manager_client'
GUEST_USER_NAME = '$guest'
UNAVAILABLE_ACTION = 'Unknown action or no action given.'
MOUNT_RETRY_COUNT = 20
TEMP_MOUNT_PATTERN = '/home/.shadow/%s/temporary_mount'
VAULT_PATH_PATTERN = '/home/.shadow/%s/vault'

DBUS_PROTOS_DEP = 'dbus_protos'

LEC_KEY = 'low_entropy_credentials_supported'


def get_user_hash(user):
    """Get the user hash for the given user."""
    return utils.system_output(['cryptohome', '--action=obfuscate_user',
                                '--user=%s' % user])


def user_path(user):
    """Get the user mount point for the given user."""
    return utils.system_output(['cryptohome-path', 'user', user])


def system_path(user):
    """Get the system mount point for the given user."""
    return utils.system_output(['cryptohome-path', 'system', user])


def temporary_mount_path(user):
    """Get the vault mount path used during crypto-migration for the user.

    @param user: user the temporary mount should be for
    """
    return TEMP_MOUNT_PATTERN % (get_user_hash(user))


def vault_path(user):
    """ Get the vault path for the given user.

    @param user: The user who's vault path should be returned.
    """
    return VAULT_PATH_PATTERN % (get_user_hash(user))


def ensure_clean_cryptohome_for(user, password=None):
    """Ensure a fresh cryptohome exists for user.

    @param user: user who needs a shiny new cryptohome.
    @param password: if unset, a random password will be used.
    """
    if not password:
        password = ''.join(random.sample(string.ascii_lowercase, 6))
    unmount_vault(user)
    remove_vault(user)
    mount_vault(user, password, create=True)


def get_tpm_password():
    """Get the TPM password.

    Returns:
        A TPM password
    """
    out = run_cmd(TPM_MANAGER_CMD + ' status')
    match = re.search('owner_password: (\w*)', out)
    password = ''
    if match:
        hex_pass = match.group(1)
        password = ''.join(
                chr(int(hex_pass[i:i + 2], 16))
                for i in range(0, len(hex_pass), 2))
    return password


def get_fwmp(cleared_fwmp=False):
    """Get the firmware management parameters.

    Args:
        cleared_fwmp: True if the space should not exist.

    Returns:
        The dictionary with the FWMP contents, for example:
        { 'flags': 0xbb41,
          'developer_key_hash':
            "\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\
             000\000\000\000\000\000\000\000\000\000\000",
        }
        or a dictionary with the Error if the FWMP doesn't exist and
        cleared_fwmp is True
        { 'error': 'CRYPTOHOME_ERROR_FIRMWARE_MANAGEMENT_PARAMETERS_INVALID' }

    Raises:
         ChromiumOSError if any expected field is not found in the cryptohome
         output. This would typically happen when FWMP state does not match
         'cleared_fwmp'
    """
    out = run_cmd(CRYPTOHOME_CMD +
                    ' --action=get_firmware_management_parameters')

    if cleared_fwmp:
        if tpm_utils.FwmpIsAllZero(out):
            return {}
        fields = ['error']
    else:
        fields = ['flags', 'developer_key_hash']

    status = {}
    for field in fields:
        match = re.search('%s: (\S+)\s' % field, out)
        if not match:
            raise ChromiumOSError('Invalid FWMP field %s: "%s".' %
                                  (field, out))
        status[field] = match.group(1).strip()
    return status


def set_fwmp(flags, developer_key_hash=None):
    """Set the firmware management parameter contents.

    Args:
        developer_key_hash: a string with the developer key hash

    Raises:
         ChromiumOSError cryptohome cannot set the FWMP contents
    """
    cmd = (CRYPTOHOME_CMD +
          ' --action=set_firmware_management_parameters '
          '--flags=' + flags)
    if developer_key_hash:
        cmd += ' --developer_key_hash=' + developer_key_hash

    out = run_cmd(cmd)
    if 'SetFirmwareManagementParameters success' not in out:
        raise ChromiumOSError('failed to set FWMP: %s' % out)


def is_tpm_lockout_in_effect():
    """Returns true if the TPM lockout is in effect; false otherwise."""
    status = get_tpm_da_info()
    return status.get('dictionary_attack_lockout_in_effect', None)


def get_login_status():
    """Query the login status

    Returns:
        A login status dictionary containing:
        { 'owner_user_exists': True|False }
    """
    out = run_cmd(CRYPTOHOME_CMD + ' --action=get_login_status')
    status = {}
    for field in ['owner_user_exists']:
        match = re.search('%s: (true|false)' % field, out)
        if not match:
            raise ChromiumOSError('Invalid login status: "%s".' % out)
        status[field] = match.group(1) == 'true'
    return status


def get_install_attribute_status():
    """Query the install attribute status

    Returns:
        A status string, which could be:
          "UNKNOWN"
          "TPM_NOT_OWNED"
          "FIRST_INSTALL"
          "VALID"
          "INVALID"
    """
    out = run_cmd(CRYPTOHOME_CMD + ' --action=install_attributes_get_status')
    return out.strip()


def lock_install_attributes(attrs):
    """Set and lock install attributes for the device.

    @param attrs: dict of install attributes.
    """

    take_tpm_ownership()
    wait_for_install_attributes_ready()
    for name, value in attrs.items():
        args = [
                CRYPTOHOME_CMD, '--action=install_attributes_set',
                '--name="%s"' % name,
                '--value="%s"' % value
        ]
        cmd = ' '.join(args)
        if (utils.system(cmd, ignore_status=True) != 0):
            return False

    out = run_cmd(CRYPTOHOME_CMD + ' --action=install_attributes_finalize')
    return (out.strip() == 'InstallAttributesFinalize(): 1')


def wait_for_install_attributes_ready():
    """Wait until install attributes are ready.
    """
    cmd = CRYPTOHOME_CMD + ' --action=install_attributes_is_ready'
    utils.poll_for_condition(
            lambda: run_cmd(cmd).strip() == 'InstallAttributesIsReady(): 1',
            timeout=300,
            exception=error.TestError(
                    'Timeout waiting for install attributes to be ready'))


def get_tpm_attestation_status():
    """Get the TPM attestation status.  Works similar to get_tpm_status().
    """
    out = run_cmd(ATTESTATION_CMD + ' status')
    status = {}
    for field in ['prepared_for_enrollment', 'enrolled']:
        match = re.search('%s: (true|false)' % field, out)
        if not match:
            raise ChromiumOSError('Invalid attestation status: "%s".' % out)
        status[field] = match.group(1) == 'true'
    return status


def take_tpm_ownership(wait_for_ownership=True):
    """Take TPM ownership.

    Args:
        wait_for_ownership: block until TPM is owned if true
    """
    run_cmd(CRYPTOHOME_CMD + ' --action=tpm_take_ownership')
    if wait_for_ownership:
        # Note that waiting for the 'Ready' flag is more correct than waiting
        # for the 'Owned' flag, as the latter is set by cryptohomed before some
        # of the ownership tasks are completed.
        utils.poll_for_condition(
                lambda: get_tpm_status()['Ready'],
                timeout=300,
                exception=error.TestError('Timeout waiting for TPM ownership'))


def verify_ek():
    """Verify the TPM endorsement key.

    Returns true if EK is valid.
    """
    cmd = CRYPTOHOME_CMD + ' --action=tpm_verify_ek'
    return (utils.system(cmd, ignore_status=True) == 0)


def remove_vault(user):
    """Remove the given user's vault from the shadow directory."""
    logging.debug('user is %s', user)
    user_hash = get_user_hash(user)
    logging.debug('Removing vault for user %s with hash %s', user, user_hash)
    cmd = CRYPTOHOME_CMD + ' --action=remove --force --user=%s' % user
    run_cmd(cmd)
    # Ensure that the vault does not exist.
    if os.path.exists(os.path.join(constants.SHADOW_ROOT, user_hash)):
        raise ChromiumOSError('Cryptohome could not remove the user\'s vault.')


def remove_all_vaults():
    """Remove any existing vaults from the shadow directory.

    This function must be run with root privileges.
    """
    for item in os.listdir(constants.SHADOW_ROOT):
        abs_item = os.path.join(constants.SHADOW_ROOT, item)
        if os.path.isdir(os.path.join(abs_item, 'vault')):
            logging.debug('Removing vault for user with hash %s', item)
            shutil.rmtree(abs_item)


def mount_vault(user, password, create=False, key_label=None):
    """Mount the given user's vault. Mounts should be created by calling this
    function with create=True, and can be used afterwards with create=False.
    Only try to mount existing vaults created with this function.

    """
    args = [CRYPTOHOME_CMD, '--action=mount_ex', '--user=%s' % user,
            '--password=%s' % password, '--async']
    if create:
        args += ['--create']
        if key_label is None:
            key_label = 'bar'
    if key_label is not None:
        args += ['--key_label=%s' % key_label]
    logging.info(run_cmd(' '.join(args)))
    # Ensure that the vault exists in the shadow directory.
    user_hash = get_user_hash(user)
    if not os.path.exists(os.path.join(constants.SHADOW_ROOT, user_hash)):
        retry = 0
        mounted = False
        while retry < MOUNT_RETRY_COUNT and not mounted:
            time.sleep(1)
            logging.info("Retry %s", str(retry + 1))
            run_cmd(' '.join(args))
            # TODO: Remove this additional call to get_user_hash(user) when
            # crbug.com/690994 is fixed
            user_hash = get_user_hash(user)
            if os.path.exists(os.path.join(constants.SHADOW_ROOT, user_hash)):
                mounted = True
            retry += 1
        if not mounted:
            raise ChromiumOSError('Cryptohome vault not found after mount.')
    # Ensure that the vault is mounted.
    if not is_permanent_vault_mounted(user=user, allow_fail=True):
        raise ChromiumOSError('Cryptohome created a vault but did not mount.')


def mount_guest():
    """Mount the guest vault."""
    args = [CRYPTOHOME_CMD, '--action=mount_guest_ex']
    logging.info(run_cmd(' '.join(args)))
    # Ensure that the guest vault is mounted.
    if not is_guest_vault_mounted(allow_fail=True):
        raise ChromiumOSError('Cryptohome did not mount guest vault.')


def test_auth(user, password):
    """Test key auth."""
    cmd = [CRYPTOHOME_CMD, '--action=check_key_ex', '--user=%s' % user,
           '--password=%s' % password, '--async']
    out = run_cmd(' '.join(cmd))
    logging.info(out)
    return 'Key authenticated.' in out


def add_le_key(user, password, new_password, new_key_label):
    """Add low entropy key."""
    args = [CRYPTOHOME_CMD, '--action=add_key_ex', '--key_policy=le',
            '--user=%s' % user, '--password=%s' % password,
            '--new_key_label=%s' % new_key_label,
            '--new_password=%s' % new_password]
    logging.info(run_cmd(' '.join(args)))


def remove_key(user, password, remove_key_label):
    """Remove a key."""
    args = [CRYPTOHOME_CMD, '--action=remove_key_ex', '--user=%s' % user,
            '--password=%s' % password,
            '--remove_key_label=%s' % remove_key_label]
    logging.info(run_cmd(' '.join(args)))


def get_supported_key_policies(host=None):
    """Get supported key policies."""
    args = [CRYPTOHOME_CMD, '--action=get_supported_key_policies']
    if host is not None:
        out = host.run(args).stdout
    else:
        out = run_cmd(' '.join(args))
    logging.info(out)
    policies = {}
    for line in out.splitlines():
        match = re.search('([^:]+): (true|false)', line.strip())
        if match:
            policies[match.group(1)] = match.group(2) == 'true'
    return policies


def is_low_entropy_credentials_supported(host=None):
    """ Returns True if low entropy credentials are supported."""
    key_policies = get_supported_key_policies(host)
    return LEC_KEY in key_policies and key_policies[LEC_KEY]


def unmount_vault(user=None):
    """Unmount the given user's vault.

    Once unmounting for a specific user is supported, the user parameter will
    name the target user. See crosbug.com/20778.
    """
    run_cmd(CRYPTOHOME_CMD + ' --action=unmount')
    # Ensure that the vault is not mounted.
    if user is not None and is_vault_mounted(user, allow_fail=True):
        raise ChromiumOSError('Cryptohome did not unmount the user.')


def __get_mount_info(mount_point, allow_fail=False):
    """Get information about the active mount at a given mount point."""
    cryptohomed_path = '/proc/$(pgrep cryptohomed)/mounts'
    # 'cryptohome-namespace-mounter' is currently only used for Guest sessions.
    mounter_exe = 'cryptohome-namespace-mounter'
    mounter_pid = 'pgrep -o -f %s' % mounter_exe
    mounter_path = '/proc/$(%s)/mounts' % mounter_pid

    status = utils.system(mounter_pid, ignore_status=True)
    # Only check for these mounts if the mounter executable is running.
    if status == 0:
        try:
            logging.debug('Active %s mounts:\n' % mounter_exe +
                          utils.system_output('cat %s' % mounter_path))
            ns_mount_line = utils.system_output(
                'grep %s %s' % (mount_point, mounter_path),
                ignore_status=allow_fail)
        except Exception as e:
            logging.error(e)
            raise ChromiumOSError('Could not get info about cryptohome vault '
                                  'through %s. See logs for complete '
                                  'mount-point.'
                                  % os.path.dirname(str(mount_point)))
        return ns_mount_line.split()

    try:
        logging.debug('Active cryptohome mounts:\n%s',
                      utils.system_output('cat %s' % cryptohomed_path))
        mount_line = utils.system_output(
            'grep %s %s' % (mount_point, cryptohomed_path),
            ignore_status=allow_fail)
    except Exception as e:
        logging.error(e)
        raise ChromiumOSError('Could not get info about cryptohome vault '
                              'through %s. See logs for complete mount-point.'
                              % os.path.dirname(str(mount_point)))
    return mount_line.split()


def __get_user_mount_info(user, allow_fail=False):
    """Get information about the active mounts for a given user.

    Returns the active mounts at the user's user and system mount points. If no
    user is given, the active mount at the shared mount point is returned
    (regular users have a bind-mount at this mount point for backwards
    compatibility; the guest user has a mount at this mount point only).
    """
    return [__get_mount_info(mount_point=user_path(user),
                             allow_fail=allow_fail),
            __get_mount_info(mount_point=system_path(user),
                             allow_fail=allow_fail)]


def is_vault_mounted(user, regexes=None, allow_fail=False):
    """Check whether a vault is mounted for the given user.

    user: If no user is given, the shared mount point is checked, determining
      whether a vault is mounted for any user.
    regexes: dictionary of regexes to matches against the mount information.
      The mount filesystem for the user's user and system mounts point must
      match one of the keys.
      The mount source point must match the selected device regex.

    In addition, if mounted over ext4, we check the directory is encrypted.
    """
    if regexes is None:
        regexes = {
            constants.CRYPTOHOME_FS_REGEX_ANY :
               constants.CRYPTOHOME_DEV_REGEX_ANY
        }
    user_mount_info = __get_user_mount_info(user=user, allow_fail=allow_fail)
    for mount_info in user_mount_info:
        # Look at each /proc/../mount lines that match mount point for a given
        # user user/system mount (/home/user/.... /home/root/...)

        # We should have at least 3 arguments (source, mount, type of mount)
        if len(mount_info) < 3:
            return False

        device_regex = None
        for fs_regex in regexes.keys():
            if re.match(fs_regex, mount_info[2]):
                device_regex = regexes[fs_regex]
                break

        if not device_regex:
            # The third argument in not the expected mount point type.
            return False

        # Check if the mount source match the device regex: it can be loose,
        # (anything) or stricter if we expect guest filesystem.
        if not re.match(device_regex, mount_info[0]):
            return False

    return True


def is_guest_vault_mounted(allow_fail=False):
    """Check whether a vault is mounted for the guest user.
       It should be a mount of an ext4 partition on a loop device
       or be backed by tmpfs.
    """
    return is_vault_mounted(
            user=GUEST_USER_NAME,
            regexes={
                    # Remove tmpfs support when it becomes unnecessary as all guest
                    # modes will use ext4 on a loop device.
                    constants.CRYPTOHOME_FS_REGEX_EXT4:
                    constants.CRYPTOHOME_DEV_REGEX_LOOP_DEVICE,
                    constants.CRYPTOHOME_FS_REGEX_TMPFS:
                    constants.CRYPTOHOME_DEV_REGEX_GUEST,
            },
            allow_fail=allow_fail)


def is_permanent_vault_mounted(user, allow_fail=False):
    """Check if user is mounted over ecryptfs or ext4 crypto. """
    return is_vault_mounted(
            user=user,
            regexes={
                    constants.CRYPTOHOME_FS_REGEX_ECRYPTFS:
                    constants.CRYPTOHOME_DEV_REGEX_REGULAR_USER_SHADOW,
                    constants.CRYPTOHOME_FS_REGEX_EXT4:
                    constants.CRYPTOHOME_DEV_REGEX_REGULAR_USER_DEVICE,
            },
            allow_fail=allow_fail)


def get_mounted_vault_path(user, allow_fail=False):
    """Get the path where the decrypted data for the user is located."""
    return os.path.join(constants.SHADOW_ROOT, get_user_hash(user), 'mount')


def canonicalize(credential):
    """Perform basic canonicalization of |email_address|.

    Perform basic canonicalization of |email_address|, taking into account that
    gmail does not consider '.' or caps inside a username to matter. It also
    ignores everything after a '+'. For example,
    c.masone+abc@gmail.com == cMaSone@gmail.com, per
    http://mail.google.com/support/bin/answer.py?hl=en&ctx=mail&answer=10313
    """
    if not credential:
        return None

    parts = credential.split('@')
    if len(parts) != 2:
        raise error.TestError('Malformed email: ' + credential)

    (name, domain) = parts
    name = name.partition('+')[0]
    if (domain == constants.SPECIAL_CASE_DOMAIN):
        name = name.replace('.', '')
    return '@'.join([name, domain]).lower()


def crash_cryptohomed():
    """Let cryptohome crash."""
    # Try to kill cryptohomed so we get something to work with.
    pid = run_cmd('pgrep cryptohomed')
    try:
        pid = int(pid)
    except ValueError as e:  # empty or invalid string
        raise error.TestError('Cryptohomed was not running')
    utils.system('kill -ABRT %d' % pid)
    # CONT just in case cryptohomed had a spurious STOP.
    utils.system('kill -CONT %d' % pid)
    utils.poll_for_condition(
        lambda: utils.system('ps -p %d' % pid,
                             ignore_status=True) != 0,
            timeout=180,
            exception=error.TestError(
                'Timeout waiting for cryptohomed to coredump'))


def create_ecryptfs_homedir(user, password):
    """Creates a new home directory as ecryptfs.

    If a home directory for the user exists already, it will be removed.
    The resulting home directory will be mounted.

    @param user: Username to create the home directory for.
    @param password: Password to use when creating the home directory.
    """
    unmount_vault(user)
    remove_vault(user)
    args = [
            CRYPTOHOME_CMD,
            '--action=mount_ex',
            '--user=%s' % user,
            '--password=%s' % password,
            '--key_label=foo',
            '--ecryptfs',
            '--create']
    logging.info(run_cmd(' '.join(args)))
    if not is_vault_mounted(
            user,
            regexes={
                    constants.CRYPTOHOME_FS_REGEX_ECRYPTFS:
                    constants.CRYPTOHOME_DEV_REGEX_REGULAR_USER_SHADOW
            },
            allow_fail=True):
        raise ChromiumOSError('Ecryptfs home could not be created')


def do_dircrypto_migration(user, password, timeout=600):
    """Start dircrypto migration for the user.

    @param user: The user to migrate.
    @param password: The password used to mount the users vault
    @param timeout: How long in seconds to wait for the migration to finish
    before failing.
    """
    unmount_vault(user)
    args = [
            CRYPTOHOME_CMD,
            '--action=mount_ex',
            '--to_migrate_from_ecryptfs',
            '--user=%s' % user,
            '--password=%s' % password]
    logging.info(run_cmd(' '.join(args)))
    if not __get_mount_info(temporary_mount_path(user), allow_fail=True):
        raise ChromiumOSError('Failed to mount home for migration')
    args = [CRYPTOHOME_CMD, '--action=migrate_to_dircrypto', '--user=%s' % user]
    logging.info(run_cmd(' '.join(args)))
    utils.poll_for_condition(
        lambda: not __get_mount_info(
                temporary_mount_path(user), allow_fail=True),
        timeout=timeout,
        exception=error.TestError(
                'Timeout waiting for dircrypto migration to finish'))


def change_password(user, password, new_password):
    """Change user password."""
    args = [
            CRYPTOHOME_CMD,
            '--action=migrate_key_ex',
            '--user=%s' % user,
            '--old_password=%s' % password,
            '--password=%s' % new_password]
    out = run_cmd(' '.join(args))
    logging.info(out)
    if 'Key migration succeeded.' not in out:
        raise ChromiumOSError('Key migration failed.')
