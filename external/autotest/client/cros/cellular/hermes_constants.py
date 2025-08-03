# Lint as: python2, python3
# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This module provides bindings for HermesManager DBus constants, such as
interface names, enumerations, and errors.

"""

#Hermes DBus Binding errors
DBUS_HERMES_UNKNOWN = 'org.chromium.Hermes.Error.Unknown'
DBUS_HERMES_UNSUPPORTED = 'org.chromium.Hermes.Error.Unsupported'
DBUS_HERMES_WRONGSTATE = 'rg.chromium.Hermes.Error.WrongState'

#Hermes DBus other errors
DBUS_HERMES_PROFILE_ALREADY_DISABLED = 'org.chromium.Hermes.Error.AlreadyDisabled'
DBUS_HERMES_PROFILE_ALREADY_ENABLED = 'org.chromium.Hermes.Error.AlreadyEnabled'
DBUS_HERMES_BAD_NOTIFICATION = 'org.chromium.Hermes.Error.BadNotification'
DBUS_HERMES_BAD_REQUEST = 'org.chromium.Hermes.Error.BadRequest'
DBUS_HERMES_INTERNAL_LPA_FAILURE = 'org.chromium.Hermes.Error.InternalLpaFailure'
DBUS_HERMES_INVALID_ACTIVATION_CODE = 'org.chromium.Hermes.Error.InvalidActivationCode'
DBUS_HERMES_INVALID_ICCID = 'org.chromium.Hermes.Error.InvalidIccid'
DBUS_HERMES_INVALID_PARAM = 'org.chromium.Hermes.Error.InvalidParameter'
DBUS_HERMES_MALFORMED_RESPONSE = 'org.chromium.Hermes.Error.MalformedResponse'
DBUS_HERMES_NEED_CONFIRMATION_CODE = 'org.chromium.Hermes.Error.NeedConfirmationCode'
DBUS_HERMES_NO_RESPONSE = 'org.chromium.Hermes.Error.NoResponse'
DBUS_HERMES_PENDING_PROFILE = 'org.chromium.Hermes.Error.PendingProfile'
DBUS_HERMES_SEND_APDU_FAILURE = 'org.chromium.Hermes.Error.SendApduFailur'
DBUS_HERMES_SEND_HTTP_FAILURE = 'org.chromium.Hermes.Error.SendHttpsFailure'
DBUS_HERMES_SEND_NOTIFICATION_FAILURE = 'org.chromium.Hermes.Error.SendNotificationFailure'
DBUS_HERMES_TEST_PROFILE_INPROD = 'org.chromium.Hermes.Error.TestProfileInProd'

# Interfaces
# Standard Interfaces
I_PROPERTIES = 'org.freedesktop.DBus.Properties'
I_INTROSPECTABLE = 'org.freedesktop.DBus.Introspectable'
I_OBJECT_MANAGER = 'org.freedesktop.DBus.ObjectManager'

#
# For eSIM interactions.
#
HERMES_SERVICE = 'org.chromium.Hermes'
HERMES_OBJECT = '/org/chromium/Hermes'
HERMES_MANAGER_OBJECT = '/org/chromium/Hermes/Manager'
HERMES_MANAGER_IFACE = 'org.chromium.Hermes.Manager'

HERMES_EUICC_OBJECT = '/org/chromium/Hermes/Euicc'
HERMES_EUICC_IFACE = 'org.chromium.Hermes.Euicc'

HERMES_PROFILE_OBJECT = '/org/chromium/Hermes/Profile'
HERMES_PROFILE_IFACE = 'org.chromium.Hermes.Profile'


EUICC_ENUMERATION_TIMEOUT = 20
EUICC_ENABLE_DISABLE_TIMEOUT = 10
PROFILE_ENABLE_DISABLE_TIMEOUT = 10
PROFILE_REFRESH_TIMEOUT = 10
# Amount of time to wait between attempts to connect to HermesManager.
CONNECT_WAIT_INTERVAL_SECONDS = 20
HERMES_RESTART_WAIT_SECONDS   = 30
# DBus method reply timeout in milliseconds
HERMES_DBUS_METHOD_REPLY_TIMEOUT = 120 * 1000

def ProfileStateToString(state):
    """
    Returns a string for the given state.

    @param state: Profile state value.

    @return A string that describes the given state.

    """
    PROFILE_STATE_STRINGS = [
        'PENDING',
        'INACTIVE',
        'ACTIVE'
    ]
    return PROFILE_STATE_STRINGS[state]


def ProfileClassToString(pclass):
    """
    Returns a string for the given class.

    @param state: Profile class value.

    @return A string that describes the given class.

    """
    PROFILE_CLASS_STRINGS = [
        'TESTING',
        'PROVISIONING',
        'OPERATIONAL'
    ]
    return PROFILE_CLASS_STRINGS[pclass]
