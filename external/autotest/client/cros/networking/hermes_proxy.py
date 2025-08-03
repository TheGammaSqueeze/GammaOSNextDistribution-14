# Lint as: python2, python3
# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This module provides bindings for Hermes.

"""
import dbus
import logging
import dbus.mainloop.glib
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.cellular import cellular_logging
from autotest_lib.client.cros.cellular import hermes_constants
from autotest_lib.client.cros.cellular import mm1_constants

log = cellular_logging.SetupCellularLogging('Hermes')

def _is_unknown_dbus_binding_exception(e):
    return (isinstance(e, dbus.exceptions.DBusException) and
            e.get_dbus_name() in [mm1_constants.DBUS_SERVICE_UNKNOWN,
                                  mm1_constants.DBUS_UNKNOWN_METHOD,
                                  mm1_constants.DBUS_UNKNOWN_OBJECT,
                                  mm1_constants.DBUS_UNKNOWN_INTERFACE])

class HermesManagerProxyError(Exception):
    """Exceptions raised by HermesManager1ProxyError and it's children."""
    pass

class HermesManagerProxy(object):
    """A wrapper around a DBus proxy for HermesManager."""

    @classmethod
    def get_hermes_manager(cls, bus=None, timeout_seconds=10):
        """Connect to HermesManager over DBus, retrying if necessary.

        After connecting to HermesManager, this method will verify that
        HermesManager is answering RPCs.

        @param bus: D-Bus bus to use, or specify None and this object will
            create a mainloop and bus.
        @param timeout_seconds: float number of seconds to try connecting
            A value <= 0 will cause the method to return immediately,
            without trying to connect.
        @return a HermesManagerProxy instance if we connected, or None
            otherwise.
        @raise HermesManagerProxyError if it fails to connect to
            HermesManager.

        """
        def _connect_to_hermes_manager(bus):
            try:
                # We create instance of class on which this classmethod was
                # called. This way, calling get_hermes_manager
                # SubclassOfHermesManagerProxy._connect_to_hermes_manager()
                # will get a proxy of the right type
                return cls(bus=bus)
            except dbus.exceptions.DBusException as e:
                if _is_unknown_dbus_binding_exception(e):
                    return None
                raise HermesManagerProxyError(
                    'Error connecting to HermesManager. DBus error: |%s|',
                    repr(e))

        utils.poll_for_condition(
            condition=lambda: _connect_to_hermes_manager(bus) is not None,
            exception=HermesManagerProxyError(
                'Timed out connecting to HermesManager dbus'),
            desc='Waiting for hermes to start',
            timeout=timeout_seconds,
            sleep_interval=hermes_constants.CONNECT_WAIT_INTERVAL_SECONDS)
        connection = _connect_to_hermes_manager(bus)
        return connection

    def __init__(self, bus=None):
        if bus is None:
            dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
            bus = dbus.SystemBus()
        self._bus = bus
        self._manager = dbus.Interface(
            self._bus.get_object(hermes_constants.HERMES_SERVICE,
                                 hermes_constants.HERMES_MANAGER_OBJECT),
            hermes_constants.HERMES_MANAGER_IFACE)

    @property
    def manager(self):
        """@return the DBus Hermes Manager object."""
        return self._manager

    @property
    def iface_properties(self):
        """@return org.freedesktop.DBus.Properties DBus interface."""
        return dbus.Interface(self._manager, hermes_constants.I_PROPERTIES)

    def properties(self, iface=hermes_constants.HERMES_MANAGER_IFACE):
        """
        Return the properties associated with the specified interface.

        @param iface: Name of interface to retrieve the properties from.
        @return array of properties.

        """
        return self.iface_properties.GetAll(iface)

    def get_available_euiccs(self):
        """
        Return AvailableEuiccs property from manager interface

        @return array of euicc paths

        """
        available_euiccs = self.properties()
        if len(available_euiccs) <= 0:
            return None

        return available_euiccs.get('AvailableEuiccs')

    def get_first_inactive_euicc(self):
        """
        Read all euiccs objects in loop and get an non active euicc object

        @return non active euicc object

        """
        try:
            euiccs = self.get_available_euiccs()
            euicc_obj = None
            for euicc in euiccs:
                euicc_obj = self.get_euicc(euicc)
                props = euicc_obj.properties()
                if not props.get('IsActive'):
                    break
            return euicc_obj
        except dbus.DBusException as e:
            logging.error('get non active euicc failed with error:%s', e)

    def get_first_active_euicc(self):
        """
        Read all euiccs and get an active euicc object
        by reading isactive property of each euicc object

        @return active euicc dbus object path

        """
        try:
            euiccs = self.get_available_euiccs()
            euicc_obj = None
            for euicc in euiccs:
                euicc_obj = self.get_euicc(euicc)
                props = euicc_obj.properties()
                if props.get('IsActive'):
                    break
            return euicc_obj
        except dbus.DBusException as e:
            logging.error('get active euicc failed with error:%s', e)

    def get_euicc(self, euicc_path):
        """
        Create a proxy object for given euicc path

        @param euicc_path: available euicc dbus path as string
        @return euicc proxy dbus object

        """
        if not euicc_path:
            logging.debug('No euicc path given for %s', euicc_path)
            raise error.TestFail('No euicc path given for' + euicc_path)

        try:
            euicc_proxy = EuiccProxy(self._bus, euicc_path)
            props = euicc_proxy.properties()
            if not props:
                raise error.TestFail('No euicc props found for ' + euicc_path)
            return euicc_proxy
        except dbus.exceptions.DBusException as e:
            if _is_unknown_dbus_binding_exception(e):
                return None
            raise HermesManagerProxyError(
                'Failed to obtain dbus object for the euicc. DBus error: '
                '|%s|', repr(e))

    def get_profile_from_iccid(self, iccid):
        """
        Generic function to get profile based on given iccid

        @return euicc object and profile object

        """
        logging.debug('Get profile from given iccid:%s', iccid)
        euiccs = self.get_available_euiccs()
        for euicc in euiccs:
            euicc_obj = self.get_euicc(euicc)
            if euicc_obj.get_profile_from_iccid(iccid) != None:
                return euicc_obj, euicc.get_profile_from_iccid
        return None

    def set_debug_logging(self):
        self.manager.SetLogging('DEBUG')

    def get_profile_iccid(self, profile_path):
        profile_proxy = ProfileProxy(self._bus, profile_path)
        props = profile_proxy.properties()
        return props.get('Iccid')

# End of Manager class

class ProfileProxy(object):
    """A wrapper around a DBus proxy for Hermes profile object."""

    # Amount of time to wait for a state transition.
    STATE_TRANSITION_WAIT_SECONDS = 10

    def __init__(self, bus, path):
        self._bus = bus
        self._path = path
        self._profile = self._bus.get_object(
            hermes_constants.HERMES_SERVICE, path)

    def enable(self):
        """ Enables a profile """
        profile_interface = dbus.Interface(
            self.profile, hermes_constants.HERMES_PROFILE_IFACE)
        logging.debug('ProfileProxy Manager enable_profile')
        return profile_interface.Enable(
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)

    def disable(self):
        """ Disables a profile """
        profile_interface = dbus.Interface(
            self.profile, hermes_constants.HERMES_PROFILE_IFACE)
        logging.debug('ProfileProxy Manager disable_profile')
        return profile_interface.Disable(
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)

    @property
    def profile(self):
        """@return the DBus profiles object."""
        return self._profile

    @property
    def path(self):
        """@return profile path."""
        return self._path

    @property
    def iface_properties(self):
        """@return org.freedesktop.DBus.Properties DBus interface."""
        return dbus.Interface(self._profile, dbus.PROPERTIES_IFACE)

    def iface_profile(self):
        """@return org.freedesktop.HermesManager.Profile DBus interface."""
        return dbus.Interface(self._profile,
                              hermes_constants.HERMES_PROFILE_IFACE)

    def properties(self, iface=hermes_constants.HERMES_PROFILE_IFACE):
        """Return the properties associated with the specified interface.
        @param iface: Name of interface to retrieve the properties from.
        @return array of properties.
        """
        return self.iface_properties.GetAll(iface)

    # Get functions for each property from properties
    #"Iccid", "ServiceProvider", "MccMnc", "ActivationCode", "State"
    #"ProfileClass", "Name", "Nickname"
    @property
    def iccid(self):
        """ @return iccid of profile also confirmation code """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('Iccid')

    @property
    def serviceprovider(self):
        """ @return serviceprovider of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('ServiceProvider')

    @property
    def mccmnc(self):
        """ @return mccmnc of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('MccMnc')

    @property
    def activationcode(self):
        """ @return activationcode of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('ActivationCode')

    @property
    def state(self):
        """ @return state of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('State')

    @property
    def profileclass(self):
        """ @return profileclass of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('ProfileClass')

    @property
    def name(self):
        """ @return name of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('Name')

    @property
    def nickname(self):
        """ @return nickname of profile """
        props = self.properties(hermes_constants.HERMES_PROFILE_IFACE)
        return props.get('Nickname')

class EuiccProxy(object):
    """A wrapper around a DBus proxy for Hermes euicc object."""

    def __init__(self, bus, path):
        self._bus = bus
        self._euicc = self._bus.get_object(
            hermes_constants.HERMES_SERVICE, path)

    @property
    def euicc(self):
        """@return the DBus Euicc object."""
        return self._euicc

    @property
    def iface_properties(self):
        """@return org.freedesktop.DBus.Properties DBus interface."""
        return dbus.Interface(self._euicc, dbus.PROPERTIES_IFACE)

    @property
    def iface_euicc(self):
        """@return org.freedesktop.HermesManager.Euicc DBus interface."""
        return dbus.Interface(self._euicc, hermes_constants.HERMES_EUICC_IFACE)

    def properties(self, iface=hermes_constants.HERMES_EUICC_IFACE):
        """
        Return the properties associated with the specified interface.

        @param iface: Name of interface to retrieve the properties from.
        @return array of properties.

        """
        return self.iface_properties.GetAll(iface)

    def request_installed_profiles(self):
        """Refreshes/Loads current euicc object profiles.
        """
        self.iface_euicc.RequestInstalledProfiles(
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)

    def request_pending_profiles(self, root_smds):
        """Refreshes/Loads current euicc object pending profiles.
        @return profile objects
        """
        logging.debug(
            'Request pending profile call here for %s bus %s',
                self._euicc, self._bus)
        return self.iface_euicc.RequestPendingProfiles(
                    dbus.String(root_smds),
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)

    def is_test_euicc(self):
        """
      Returns if the eUICC is a test eSIM. Automatically chooses the correct
      TLS certs to use for the eUICC
      """
        try:
            logging.info('Calling Euicc.IsTestEuicc')
            return self.iface_euicc.IsTestEuicc()
        except dbus.DBusException as e:
            logging.error('IsTestEuicc failed with error: %s', e)

    def use_test_certs(self, is_test_certs):
        """
        Sets Hermes daemon to test mode, required to run autotests

        Set to true if downloading profiles from an SMDX with a test
        certificate. This method used to download profiles to an esim from a
        test CI.

        @param is_test_certs boolean to set true or false

        """
        try:
            logging.info('Hermes call UseTestCerts')
            self.iface_euicc.UseTestCerts(dbus.Boolean(is_test_certs))
        except dbus.DBusException as e:
            logging.error('Hermes UseTestCerts failed with error:%s', e)

    def install_profile_from_activation_code(self, act_code, conf_code):
        """ Install the profile from given act code, confirmation code """
        profile = self.iface_euicc.InstallProfileFromActivationCode(
                    act_code,
                    conf_code,
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)
        return profile

    def install_pending_profile(self, profile_path, conf_code):
        """ Install the profile from given confirmation code"""
        profile = self.iface_euicc.InstallPendingProfile(
                    profile_path,
                    conf_code,
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)
        return profile

    def uninstall_profile(self, profile_path):
        """ uninstall the given profile"""
        self.iface_euicc.UninstallProfile(
                    profile_path,
                    timeout=hermes_constants.HERMES_DBUS_METHOD_REPLY_TIMEOUT)

    def get_installed_profiles(self):
        """
        Return all the available profiles objects.

        Every call to |get_installed_profiles| obtains a fresh DBus proxy
        for the profiles. So, if the profiles DBus object has changed between
        two calls to this method, the proxy returned will be for the currently
        available profiles.

        @return a dict of profiles objects. Return None if no profile is found.
        @raise HermesManagerProxyError if any corrupted profile found.

        """
        if self.installedprofiles is None:
            return None
        try:
            profiles_dict = {}
            for profile in self.installedprofiles:
                profile_proxy = ProfileProxy(self._bus, profile)
                profiles_dict[profile] = profile_proxy
            logging.debug('Get installed profiles for current euicc')
            return profiles_dict
        except dbus.exceptions.DBusException as e:
            if _is_unknown_dbus_binding_exception(e):
                return None
            raise HermesManagerProxyError(
                'Failed to obtain dbus object for the profiles. DBus error: '
                '|%s|', repr(e))

    def get_profile_from_iccid(self, iccid):
        """@return profile object having given iccid or none if not found"""
        profiles = self.installedprofiles
        for profile in profiles:
            profile_proxy = ProfileProxy(self._bus, profile)
            props = profile_proxy.properties()
            if props.get('Iccid') == iccid:
                return profile_proxy
        return None

    def get_pending_profiles(self):
        """
        Read all pending profiles of current euicc and create & return dict of
        all pending profiles

        @return dictionary of pending profiles proxy dbus objects

        """
        try:
            logging.debug('Hermes euicc getting pending profiles')

            if self.pendingprofiles is None:
                return None

            profiles_dict = {}
            # Read & Create each profile object and add to dictionary
            for profile in self.pendingprofiles:
                profile_proxy = ProfileProxy(self._bus, profile)
                profiles_dict[profile] = profile_proxy
                logging.debug('Hermes euicc pending profile: %s', profile)
            return profiles_dict
        except dbus.exceptions.DBusException as e:
            if _is_unknown_dbus_binding_exception(e):
                return None
            raise HermesManagerProxyError(
                'Failed to obtain dbus object for the profiles. DBus error: '
                '|%s|', repr(e))

    @property
    def get_eid(self):
        """@return Eid string property of euicc"""
        props = self.properties()
        return props.get('Eid')

    @property
    def installedprofiles(self):
        """@return the installedprofiles ao property of euicc"""
        props = self.properties()
        return props.get('InstalledProfiles')

    @property
    def isactive(self):
        """@return the isactive property of euicc"""
        props = self.properties()
        return props.get('IsActive')

    @property
    def pendingprofiles(self):
        """@return the pendingprofiles ao property of euicc"""
        props = self.properties()
        return props.get('PendingProfiles')
