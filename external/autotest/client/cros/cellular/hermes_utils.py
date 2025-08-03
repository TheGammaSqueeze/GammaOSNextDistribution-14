# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import dbus
import logging
import random

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.cellular import hermes_constants
from autotest_lib.client.cros.networking import hermes_proxy
from autotest_lib.client.cros.networking import mm1_proxy

# Helper functions
def connect_to_hermes():
    """
    Attempts to connect to a DBus object.

    @raise: error.TestFail if connection fails.

    """
    hermes_manager = 'None'
    try:
        hermes_manager = \
            hermes_proxy.HermesManagerProxy().get_hermes_manager()
    except dbus.DBusException as e:
        logging.error('get_hermes_manager error:%s', e)
        raise error.TestFail('Connect to Hermes failed')
    if hermes_manager is 'None':
        raise error.TestFail('Could not get connection to Hermes')
    return hermes_manager

def request_installed_profiles(euicc_path, hermes_manager):
    """
    Check euicc at given path

    @param euicc_path: path of the sim given
    @return a dict of profiles objects. Returns None if no profile is found
    @raise: error.TestFail if no euicc matching given path

    """
    euicc = hermes_manager.get_euicc(euicc_path)
    if not euicc:
        raise error.TestFail('No euicc found at:', euicc_path)

    euicc.request_installed_profiles()
    installed_profiles = euicc.get_installed_profiles()
    if not installed_profiles:
        logging.info('No installed profiles on euicc:%s', euicc_path)
    return euicc, installed_profiles

def install_profile(euicc_path, hermes_manager, is_prod_ci):
    """
    Install a profile on the euicc at euicc_path.

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param is_prod_ci: true if it is prodci test and false for testci
    @return iccid: iccid of the installed profile or None

    """
    if not is_prod_ci:
        is_smds_test = random.choice([True, False])
        logging.info('is_smds_test %s', is_smds_test)
        if is_smds_test:
            installed_iccid = install_pending_profile_test(
            euicc_path, hermes_manager)
        else:
            installed_iccid = install_profile_test(
            euicc_path, hermes_manager)
    else:
        installed_iccid = get_profile(
            euicc_path, hermes_manager, False)
    return installed_iccid

def uninstall_all_profiles(euicc_path, hermes_manager):
    """
    Uninstalls all installed test profiles

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @raise error.TestFail if any dbus exception happens

    """
    try:
        euicc, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)

        profiles_count = len(installed_profiles)
        if profiles_count is 0: return

        # also skips iccid 89010000001234567882 - R&S as its TESTING profile
        for profile in installed_profiles.keys():
            if ((hermes_constants.ProfileStateToString(
                installed_profiles[profile].state) == 'INACTIVE') and
                (hermes_constants.ProfileClassToString(
                installed_profiles[profile].profileclass) !=
                    'TESTING')):

                logging.info('Uninstalling profile - iccid:%s',
                            installed_profiles[profile].iccid)
                euicc.uninstall_profile(profile)
        logging.info('Uninstall done')
    except dbus.DBusException as e:
        logging.error('Failed to uninstall a profile error:%s', e)
        raise error.TestFail('Failed to uninstall profile')


def initialize_test(is_prod_ci_test):
    """
    Initialize euicc paths, connect to hermes, set test mode

    @param is_prod_ci_test:  true if it is prodci test and false for testci

    """
    logging.info('===initialize_test started===')
    mm_proxy = mm1_proxy.ModemManager1Proxy.get_proxy()

    logging.info('Connect to Hermes')
    hermes_manager = connect_to_hermes()

    # Always euicc/0 is prod one and euicc/1 is for test esim profiles
    # we prefer to hardcode euicc/0, since it acts as a check that Hermes
    # is able to initialize without any error. If hermes encounters an
    # error, hermes will start exposing objects such as
    # self.prod_euicc_path = "/org/chromium/Hermes/euicc/22"
    # self.test_euicc_path = "/org/chromium/Hermes/euicc/23"

    euicc = None
    euicc_path = None
    for path in hermes_manager.get_available_euiccs():
        logging.info("Found euicc at %s", path)
        is_prod_euicc = not hermes_manager.get_euicc(path).is_test_euicc()
        if is_prod_euicc == is_prod_ci_test:
            euicc_path = path
            euicc = hermes_manager.get_euicc(euicc_path)
            break

    if not euicc:
        raise error.TestFail("Initialize test failed, " +
                             "prod" if is_prod_ci_test else "test" +
                             " euicc not found")

    euicc.use_test_certs(not is_prod_ci_test)

    if not is_prod_ci_test:
        uninstall_all_profiles(euicc_path, hermes_manager)
    logging.info('===initialize_test done===\n')
    return  mm_proxy, hermes_manager, euicc_path

def validate_profile_state(euicc_path, hermes_manager, iccid, is_enable):
    """
    Validates given profile(iccid) state

    Check state of changed profile

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param iccid: iccid of the profile enabled/disabled
    @param is_enable: true to enable profile and false to disable
    @raise error.TestFail if any dbus exception happens

    """
    try:
        target_state = 'ACTIVE' if is_enable else 'INACTIVE'
        _, installed_profiles = \
        request_installed_profiles(euicc_path, hermes_manager)

        # check profile with given iccid has target_state and rest are opposite
        for profile in installed_profiles.values():
            # check for inactive profiles when enabled except enabled one
            if iccid == profile.iccid:
                if not (hermes_constants.ProfileStateToString(profile.state) ==
                    target_state):
                    logging.error('profile:%s not in %s state',
                    profile.iccid, target_state)
                    raise error.TestFail('validate_profile_state failed')

        logging.info('validate_profile_state succeeded')
    except dbus.DBusException as e:
        logging.error('Profile %s error:%s', target_state, e)
        raise error.TestFail('validate_profile_state failed')

def set_profile_state(
    is_active, euicc_path=None, hermes_manager=None,  iccid=None, profile=None):
    """
    Enable or Disable already enabled/disabled profile

    @param is_active: True to enable, False to disable profile
    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param iccid: profile iccid to enable
    @param profile: profile object to enable/disable
    @raise error.TestFail if expected error not resulted

    """
    logging.info('set_profile_state start')
    if euicc_path and iccid:
        euicc = hermes_manager.get_euicc(euicc_path)
        profile = euicc.get_profile_from_iccid(iccid)

    if is_active:
        profile.enable()
    else:
        profile.disable()
    logging.info('set_profile_state done')

def get_profile_state(euicc_path, hermes_manager, iccid):
    """
    get profile state

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param iccid: profile iccid to find state
    @return True if profile state is Active and False if state is Inactive

    """
    if euicc_path and iccid:
        euicc = hermes_manager.get_euicc(euicc_path)
        profile = euicc.get_profile_from_iccid(iccid)

    return True if (hermes_constants.ProfileStateToString(profile.state) ==
                    'ACTIVE') else False

def get_profile(euicc_path, hermes_manager, is_active):
    """
    Returns a active/inactive profile on given euicc

    This is to get already enabled or disabled profile. if not found enabled
    profile, enable an inactive profile and if not found disable profile
    disable an active profile

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param is_active: True to get active profile, False to get inactive profile
    @return iccid: iccid of the active/inactive profile as requested
    @raise error.TestFail if any dbus exception happens

    """
    try:
        _, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)

        profile_found = False
        iccid = None
        profile_needed = 'Enabled' if is_active else 'Disabled'
        # Find active/inactive profile
        target_state = 'ACTIVE' if is_active else 'INACTIVE'

        for profile in installed_profiles.values():
            # skipping TESTING profiles to prevent install/uninstall operations
            if (hermes_constants.ProfileClassToString(
                                profile.profileclass) == 'TESTING'):
                continue

            if not (hermes_constants.ProfileStateToString(profile.state) ==
                                target_state):
                set_profile_state(is_active, profile=profile)

            profile_found = True
            return profile.iccid

        if not profile_found:
            logging.error('No installed profile which is %s', profile_needed)
        return iccid
    except dbus.DBusException as e:
        raise error.TestFail('get_profile failed :', repr(e))

def get_iccid_of_disabled_profile(euicc_path, hermes_manager, is_prod_ci):
    """
    Get profile with disabled status and return its iccid

    For test esim install new profile and return iccid of that profile
    For prod esim having two profiles is prerequisite, return disabled profile

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param is_prod_ci:  true if it is prodci test and false for testci
    @return iccid: iccid of the installed profile or None

    """
    if not is_prod_ci:
        installed_iccid = install_profile_test(euicc_path, hermes_manager)
    else:
        # get disabled profile on a prod esim, if not exist then do disable one
        _, installed_profiles = \
        request_installed_profiles(euicc_path, hermes_manager)
        for profile in installed_profiles.values():
            if (hermes_constants.ProfileClassToString(profile.profileclass) ==
                    'TESTING'):
                continue

            if (hermes_constants.ProfileStateToString(profile.state) ==
                    'INACTIVE'):
                return profile.iccid

        installed_iccid = get_profile(euicc_path, hermes_manager, False)

    return installed_iccid

# Test functions
def enable_or_disable_profile_test(
    euicc_path, hermes_manager, iccid, is_enable):
    """
    Validates enable/disable profile api DBus call

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param iccid: iccid of the profile to be enabled/disabled
    @param is_enable: true to enable profile and false to disable
    @raise error.TestFail if any dbus exception happens

    """
    try:
        logging.info('===enable_or_disable_profile_test started===')
        profile_action = 'Enable' if is_enable else 'Disable'
        logging.info('%s :', profile_action)
        euicc, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)
        # Profile objects maybe stale if IsActive is false
        # Switch to the euicc we are interested in before
        # performing an op.

        profile_found = False
        target_state = 'ACTIVE' if is_enable else 'INACTIVE'
        # Find active or inactive profile to enable/disable
        for profile in installed_profiles.values():
            if not (hermes_constants.ProfileStateToString(profile.state) ==
                    target_state):
                if iccid is None or iccid == profile.iccid:
                    logging.info('Profile to %s:%s', profile_action,
                                profile.iccid)
                    profile_found = True
                    set_profile_state(is_enable, profile=profile)
                    logging.info('===enable_or_disable_profile_test '
                                'succeeded===\n')
                    break
        if not profile_found:
            raise error.TestFail('enable_or_disable_profile_test failed -'
                    'No profile to ' + profile_action)
        # Check profile state
        validate_profile_state(euicc_path, hermes_manager, iccid, is_enable)
    except dbus.DBusException as e:
        logging.error('Profile %s error:%s', profile_action, e)
        raise error.TestFail('enable_or_disable_profile_test Failed')

def install_profile_test(euicc_path, hermes_manager):
    """
    Validates InstallProfileFromActivationCode api on test euicc

    use SMDS calls to find iccid, activation code from pending profiles
    and install those profiles, this requires profiles generated based
    on EID of test esims in lab devices

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @return iccid: iccid of the installed profile or None
    @raise error.TestFail if any dbus exception happens

    """
    try:
        # get all pending profiles which are generated on DUT EID
        # Read all profiles activation code from pending profile dict
        # Install a profile from activation code, have iccid and
        # Check the presence of this profile after installation

        logging.info('===install_profile_test started===')
        activation_code = None
        confirmation_code = ""
        iccid = None
        euicc = None

        euicc, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)

        euicc.request_pending_profiles(dbus.String('prod.smds.rsp.goog'))
        logging.info('euicc chosen:%s', euicc_path)
        profiles_pending = euicc.get_pending_profiles()
        if not profiles_pending:
            logging.error('install_profile_test: pending profile not found')
            raise error.TestFail('No pending profile found on euicc:',
                                 euicc_path)

        profile_path_to_install, profile_to_install = \
            list(profiles_pending.items())[0]
        logging.debug('First pending profile:%s', profile_path_to_install)

        iccid = profile_to_install.iccid
        activation_code = profile_to_install.activationcode

        logging.info('Installing iccid:%s act_code:%s conf_code:%s',
                     iccid, activation_code, confirmation_code)
        # Install
        euicc.install_profile_from_activation_code(
            activation_code, confirmation_code)

        # Check if iccid found in installed profiles, installation success
        installed_profiles = euicc.get_installed_profiles()

        if ((installed_profiles[profile_path_to_install] is None) or
            (installed_profiles[profile_path_to_install].iccid !=
             profile_to_install.iccid)):
            logging.error('install_profile_test failed. Test Failed.')
            raise error.TestFail('No installed profile found on euicc:',
                                 euicc_path)

        logging.info('===install_profile_test succeeded===\n')
        return iccid
    except dbus.DBusException as e:
        logging.error('Failed to install a pending profile')
        raise error.TestFail('install_profile_test failed with ',
                             repr(e))

def install_pending_profile_test(euicc_path, hermes_manager):
    """
    Validates InstallPendingProfile api on test euicc
    Find a profile from list of esim pending profiles which is not
    installed yet and install that profile

    Required to create pending profiles for each EID(euicc sim) in lab dut.
    create profiles from stork giving lab devices EID. puts profiles in
    pending state for that euicc when RequestPendingProfiles called.

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @return iccid: iccid of the installed profile or None
    @raise error.TestFail if any dbus exception happens

    """
    logging.info('===install_pending_profile_test started===')
    profile_to_install = None

    euicc, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)

    euicc.request_pending_profiles(dbus.String('prod.smds.rsp.goog'))
    profiles_pending = euicc.get_pending_profiles()
    if not profiles_pending:
        logging.error(
            'install_pending_profile_test: pending profile not found')
        raise error.TestFail('No pending profile found on euicc:',
                             euicc_path)

    profile_path_to_install, profile_to_install = list(profiles_pending.items())[0]
    iccid = profile_to_install.iccid
    activation_code = profile_to_install.activationcode

    logging.info('Installing profile:%s iccid:%s act_code:%s',
                 profile_path_to_install, iccid, activation_code)

    try:
        # Install
        profile = euicc.install_pending_profile(
            profile_path_to_install, "")
        logging.info('Installed pending profile is %s', profile)
        if not profile:
            logging.error('No profile object returned after install')
            return None
    except dbus.DBusException as e:
        logging.error('Failed to install pending profile:%s', e)
        raise error.TestFail('Failed to install pending profile',
                                   repr(e))

    # Find above installed profile, if not exists raise test failure
    installed_profiles = euicc.get_installed_profiles()
    if ((installed_profiles[profile_path_to_install] is None) or
        (installed_profiles[profile_path_to_install].iccid !=
         profile_to_install.iccid)):
        raise error.TestFail('Install pending profile failed :',
                             profile_path_to_install)

    logging.info('===install_pending_profile_test succeeded===\n')
    return iccid

def uninstall_profile_test(euicc_path, hermes_manager, iccid):
    """
    Validates UninstallProfile api by uninstalling any randomly
    selected installed profile

    @param euicc_path: esim path based on testci/prodci
    @param hermes_manager: hermes manager object
    @param iccid: iccid of the profile to be uninstalled
    @raise error.TestFail if any dbus exception happens

    """
    logging.info('===uninstall_profile_test started===')
    # Getinstalled profiles list and randomly uninstall a profile
    try:
        euicc, installed_profiles = \
            request_installed_profiles(euicc_path, hermes_manager)

        profile_to_uninstall = euicc.get_profile_from_iccid(iccid)
        if not profile_to_uninstall:
            raise error.TestFail('No valid profile found at:', euicc_path)

        profile_path = profile_to_uninstall.path
        uninstalled_profile = None

        # Hermes does not support uninstalling test profiles yet.
        if hermes_constants.ProfileClassToString(
                profile_to_uninstall.profileclass) != 'TESTING':
            logging.info('profile to uninstall is:%s', profile_path)
            euicc.uninstall_profile(profile_path)
            uninstalled_profile = profile_path
            logging.info('uninstall_profile_test succeeded')

        if not uninstalled_profile:
            raise error.TestFail(
                'uninstall_profile_test failed - No uninstallable profile')

        # Try to find the uninstalled profile, if exists raise test failure
        profiles_installed = euicc.get_installed_profiles()
        for profile in profiles_installed.keys():
            if uninstalled_profile in profile:
                raise error.TestFail('uninstall_profile_test profile Failed')
        logging.info('===uninstall_profile_test succeeded===\n')
    except dbus.DBusException as e:
        raise error.TestFail('Failed to uninstall profile', e)
