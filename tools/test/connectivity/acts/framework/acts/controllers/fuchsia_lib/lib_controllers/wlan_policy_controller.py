#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import subprocess
import time

from acts import logger
from acts import signals

from acts.controllers.fuchsia_lib.ffx import FFX, FFXError, FFXTimeout
from acts.controllers.fuchsia_lib.sl4f import SL4F

SAVED_NETWORKS = "saved_networks"
CLIENT_STATE = "client_connections_state"
CONNECTIONS_ENABLED = "ConnectionsEnabled"
CONNECTIONS_DISABLED = "ConnectionsDisabled"

STATE_CONNECTED = 'Connected'
STATE_CONNECTING = 'Connecting'
STATE_DISCONNECTED = 'Disconnected'
STATE_CONNECTION_STOPPED = 'ConnectionStopped'

FUCHSIA_DEFAULT_WLAN_CONFIGURE_TIMEOUT = 30


class WlanPolicyControllerError(signals.ControllerError):
    pass


class WlanPolicyController:
    """Contains methods related to the wlan policy layer, to be used in the
    FuchsiaDevice object.
    """

    def __init__(self, sl4f: SL4F, ffx: FFX):
        self.client_controller = False
        self.preserved_networks_and_client_state = None
        self.policy_configured = False
        self.sl4f = sl4f
        self.ffx = ffx
        self.log = logger.create_tagged_trace_logger(
            f'WlanPolicyController | {ffx.ip}')

    # TODO(b/231252355): Lower default timeout to 15s once ffx becomes more
    # performant and/or reliable.
    def configure_wlan(
            self,
            preserve_saved_networks: bool,
            timeout_sec: int = FUCHSIA_DEFAULT_WLAN_CONFIGURE_TIMEOUT) -> None:
        """Sets up wlan policy layer.

        Args:
            preserve_saved_networks: whether to clear existing saved
                networks and client state, to be restored at test close.
            timeout: time to wait for device to configure WLAN.
        """
        end_time_sec = time.time() + timeout_sec

        # Kill basemgr (Component v1 version of session manager)
        while time.time() < end_time_sec:
            response = self.sl4f.basemgr_lib.killBasemgr()
            if not response.get('error'):
                self.log.debug('Basemgr kill call successfully issued.')
                break
            self.log.debug(response['error'])
            time.sleep(1)
        else:
            raise WlanPolicyControllerError(
                'Failed to issue successful basemgr kill call.')

        # Stop the session manager, which also holds the Policy controller.
        try:
            result = self.ffx.run(
                'component destroy /core/session-manager/session:session',
                skip_status_code_check=True)

            if result.returncode == 0:
                self.log.debug(f"Stopped session: {result.stdout}.")
            else:
                if (b'InstanceNotFound' in result.stderr
                        or b'instance was not found' in result.stderr):
                    self.log.debug(f'Instance was not found: {result.stderr}.')
                else:
                    raise WlanPolicyControllerError(
                        f'Failed to stop the session: {result.stderr}.')
        except FFXTimeout or FFXError as e:
            raise WlanPolicyControllerError from e

        # Acquire control of policy layer
        controller_errors = []
        while time.time() < end_time_sec:
            # Create a client controller
            response = self.sl4f.wlan_policy_lib.wlanCreateClientController()
            if response.get('error'):
                controller_errors.append(response['error'])
                self.log.debug(response['error'])
                time.sleep(1)
                continue
            # Attempt to use the client controller (failure indicates a closed
            # channel, meaning the client controller was rejected.
            response = self.sl4f.wlan_policy_lib.wlanGetSavedNetworks()
            if response.get('error'):
                controller_errors.append(response['error'])
                self.log.debug(response['error'])
                time.sleep(1)
                continue
            break
        else:
            self.log.warning(
                "Failed to create and use a WLAN policy client controller. Errors: ["
                + "; ".join(controller_errors) + "]")
            raise WlanPolicyControllerError(
                'Failed to create and use a WLAN policy client controller.')

        self.log.info('ACTS tests now have control of the WLAN policy layer.')

        if preserve_saved_networks and not self.preserved_networks_and_client_state:
            self.preserved_networks_and_client_state = self.remove_and_preserve_networks_and_client_state(
            )
        if not self.start_client_connections():
            raise WlanPolicyControllerError(
                'Failed to start client connections during configuration.')

        self.policy_configured = True

    def _deconfigure_wlan(self):
        if not self.stop_client_connections():
            raise WlanPolicyControllerError(
                'Failed to stop client connections during deconfiguration.')
        self.policy_configured = False

    def clean_up(self) -> None:
        if self.preserved_networks_and_client_state:
            # It is possible for policy to have been configured before, but
            # deconfigured before test end. In this case, in must be setup
            # before restoring networks
            if not self.policy_configured:
                self.configure_wlan()
            self.restore_preserved_networks_and_client_state()

    def start_client_connections(self):
        """Allow device to connect to networks via policy layer (including
        autoconnecting to saved networks).

        Returns:
            True, if successful. False otherwise."""
        start_response = self.sl4f.wlan_policy_lib.wlanStartClientConnections()
        if start_response.get('error'):
            self.log.error('Failed to start client connections. Err: %s' %
                           start_response['error'])
            return False
        return True

    def stop_client_connections(self):
        """Prevent device from connecting and autoconnecting to networks via the
        policy layer.

        Returns:
            True, if successful. False otherwise."""
        stop_response = self.sl4f.wlan_policy_lib.wlanStopClientConnections()
        if stop_response.get('error'):
            self.log.error('Failed to stop client connections. Err: %s' %
                           stop_response['error'])
            return False
        return True

    def save_and_connect(self, ssid, security, password=None, timeout=30):
        """ Saves and connects to the network. This is the policy version of
        connect and check_connect_response because the policy layer
        requires a saved network and the policy connect does not return
        success or failure

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable
            timeout: int, time in seconds to wait for connection

        Returns:
            True, if successful. False otherwise.
        """
        # Save network and check response
        if not self.save_network(ssid, security, password=password):
            return False
        # Make connect call and check response
        self.sl4f.wlan_policy_lib.wlanSetNewListener()
        if not self.send_connect_command(ssid, security):
            return False
        return self.wait_for_connect(ssid, security, timeout=timeout)

    def save_and_wait_for_autoconnect(self,
                                      ssid,
                                      security,
                                      password=None,
                                      timeout=30):
        """Saves a network and waits, expecting an autoconnection to the newly
        saved network. This differes from save_and_connect, as it doesn't
        expressly trigger a connection first. There are cases in which an
        autoconnect won't occur after a save (like if the device is connected
        already), so this should be used with caution to test very specific
        situations.

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable
            timeout: int, time in seconds to wait for connection

        Returns:
            True, if successful. False otherwise.
        """
        if not self.save_network(ssid, security, password=password):
            return False
        return self.wait_for_connect(ssid, security, timeout=timeout)

    def remove_and_wait_for_disconnect(self,
                                       ssid,
                                       security_type,
                                       password=None,
                                       state=None,
                                       status=None,
                                       timeout=30):
        """Removes a single network and waits for a disconnect. It is not
        guaranteed the device will stay disconnected, as it may autoconnect
        to a different saved network.

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable
            state: string, The connection state we are expecting, ie "Disconnected" or
                "Failed"
            status: string, The disconnect status we expect, it "ConnectionStopped" or
                "ConnectionFailed"
            timeout: int, time in seconds to wait for connection

        Returns:
            True, if successful. False otherwise.
        """
        self.sl4f.wlan_policy_lib.wlanSetNewListener()
        if not self.remove_network(ssid, security_type, password=password):
            return False
        return self.wait_for_disconnect(ssid,
                                        security_type,
                                        state=state,
                                        status=status,
                                        timeout=timeout)

    def remove_all_networks_and_wait_for_no_connections(self, timeout=30):
        """Removes all networks and waits until device is not connected to any
        networks. This should be used as the policy version of disconnect.

        Returns:
            True, if successful. False otherwise.
        """
        self.sl4f.wlan_policy_lib.wlanSetNewListener()
        if not self.remove_all_networks():
            self.log.error('Failed to remove all networks. Cannot continue to '
                           'wait_for_no_connections.')
            return False
        return self.wait_for_no_connections(timeout=timeout)

    def save_network(self, ssid, security_type, password=None):
        """Save a network via the policy layer.

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable

        Returns:
            True, if successful. False otherwise.
        """
        save_response = self.sl4f.wlan_policy_lib.wlanSaveNetwork(
            ssid, security_type, target_pwd=password)
        if save_response.get('error'):
            self.log.error('Failed to save network %s with error: %s' %
                           (ssid, save_response['error']))
            return False
        return True

    def remove_network(self, ssid, security_type, password=None):
        """Remove a saved network via the policy layer.

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable

        Returns:
            True, if successful. False otherwise.
        """
        remove_response = self.sl4f.wlan_policy_lib.wlanRemoveNetwork(
            ssid, security_type, target_pwd=password)
        if remove_response.get('error'):
            self.log.error('Failed to remove network %s with error: %s' %
                           (ssid, remove_response['error']))
            return False
        return True

    def remove_all_networks(self):
        """Removes all saved networks from device.

        Returns:
            True, if successful. False otherwise.
        """
        remove_all_response = self.sl4f.wlan_policy_lib.wlanRemoveAllNetworks()
        if remove_all_response.get('error'):
            self.log.error('Error occurred removing all networks: %s' %
                           remove_all_response['error'])
            return False
        return True

    def get_saved_networks(self):
        """Retrieves saved networks from device.

        Returns:
            list of saved networks

        Raises:
            WlanPolicyControllerError, if retrieval fails.
        """
        saved_networks_response = self.sl4f.wlan_policy_lib.wlanGetSavedNetworks(
        )
        if saved_networks_response.get('error'):
            raise WlanPolicyControllerError(
                'Failed to retrieve saved networks: %s' %
                saved_networks_response['error'])
        return saved_networks_response['result']

    def send_connect_command(self, ssid, security_type):
        """Sends a connect command to a network that is already saved. This does
        not wait to guarantee the connection is successful (for that, use
        save_and_connect).

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            password: string, the credential of the network if applicable

        Returns:
            True, if command send successfully. False otherwise.
        """
        connect_response = self.sl4f.wlan_policy_lib.wlanConnect(
            ssid, security_type)
        if connect_response.get('error'):
            self.log.error(
                'Error occurred when sending policy connect command: %s' %
                connect_response['error'])
            return False
        return True

    def wait_for_connect(self, ssid, security_type, timeout=30):
        """ Wait until the device has connected to the specified network.
        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            timeout: int, seconds to wait for a update showing connection
        Returns:
            True if we see a connect to the network, False otherwise.
        """
        security_type = str(security_type)
        # Wait until we've connected.
        end_time = time.time() + timeout
        while time.time() < end_time:
            time_left = max(1, int(end_time - time.time()))

            try:
                update = self.sl4f.wlan_policy_lib.wlanGetUpdate(
                    timeout=time_left)
            except TimeoutError:
                self.log.error('Timed out waiting for response from device '
                               'while waiting for network with SSID "%s" to '
                               'connect. Device took too long to connect or '
                               'the request timed out for another reason.' %
                               ssid)
                self.sl4f.wlan_policy_lib.wlanSetNewListener()
                return False
            if update.get('error'):
                # This can occur for many reasons, so it is not necessarily a
                # failure.
                self.log.debug('Error occurred getting status update: %s' %
                               update['error'])
                continue

            for network in update['result']['networks']:
                if network['id']['ssid'] == ssid or network['id'][
                        'type_'].lower() == security_type.lower():
                    if 'state' not in network:
                        raise WlanPolicyControllerError(
                            'WLAN status missing state field.')
                    elif network['state'].lower() == STATE_CONNECTED.lower():
                        return True
            # Wait a bit before requesting another status update
            time.sleep(1)
        # Stopped getting updates because out timeout
        self.log.error('Timed out waiting for network with SSID "%s" to '
                       "connect" % ssid)
        return False

    def wait_for_disconnect(self,
                            ssid,
                            security_type,
                            state=None,
                            status=None,
                            timeout=30):
        """ Wait for a disconnect of the specified network on the given device. This
        will check that the correct connection state and disconnect status are
        given in update. If we do not see a disconnect after some time,
        return false.

        Args:
            ssid: string, the network name
            security: string, security type of network (see sl4f.wlan_policy_lib)
            state: string, The connection state we are expecting, ie "Disconnected" or
                "Failed"
            status: string, The disconnect status we expect, it "ConnectionStopped" or
                "ConnectionFailed"
            timeout: int, seconds to wait before giving up

        Returns: True if we saw a disconnect as specified, or False otherwise.
        """
        if not state:
            state = STATE_DISCONNECTED
        if not status:
            status = STATE_CONNECTION_STOPPED

        end_time = time.time() + timeout
        while time.time() < end_time:
            time_left = max(1, int(end_time - time.time()))
            try:
                update = self.sl4f.wlan_policy_lib.wlanGetUpdate(
                    timeout=time_left)
            except TimeoutError:
                self.log.error(
                    'Timed out waiting for response from device '
                    'while waiting for network with SSID "%s" to '
                    'disconnect. Device took too long to disconnect '
                    'or the request timed out for another reason.' % ssid)
                self.sl4f.wlan_policy_lib.wlanSetNewListener()
                return False

            if update.get('error'):
                # This can occur for many reasons, so it is not necessarily a
                # failure.
                self.log.debug('Error occurred getting status update: %s' %
                               update['error'])
                continue
            # Update should include network, either connected to or recently disconnected.
            if len(update['result']['networks']) == 0:
                raise WlanPolicyControllerError(
                    'WLAN state update is missing network.')

            for network in update['result']['networks']:
                if network['id']['ssid'] == ssid or network['id'][
                        'type_'].lower() == security_type.lower():
                    if 'state' not in network or 'status' not in network:
                        raise WlanPolicyControllerError(
                            'Client state summary\'s network is missing fields'
                        )
                    # If still connected, we will wait for another update and check again
                    elif network['state'].lower() == STATE_CONNECTED.lower():
                        continue
                    elif network['state'].lower() == STATE_CONNECTING.lower():
                        self.log.error(
                            'Update is "Connecting", but device should already be '
                            'connected; expected disconnect')
                        return False
                    # Check that the network state and disconnect status are expected, ie
                    # that it isn't ConnectionFailed when we expect ConnectionStopped
                    elif network['state'].lower() != state.lower(
                    ) or network['status'].lower() != status.lower():
                        self.log.error(
                            'Connection failed: a network failure occurred that is unrelated'
                            'to remove network or incorrect status update. \nExpected state: '
                            '%s, Status: %s,\nActual update: %s' %
                            (state, status, network))
                        return False
                    else:
                        return True
            # Wait a bit before requesting another status update
            time.sleep(1)
        # Stopped getting updates because out timeout
        self.log.error('Timed out waiting for network with SSID "%s" to '
                       'connect' % ssid)
        return False

    def wait_for_no_connections(self, timeout=30):
        """ Waits to see that there are no existing connections the device. This
        is the simplest way to watch for disconnections when only a single
        network is saved/present.

        Args:
            timeout: int, time in seconds to wait to see no connections

        Returns:
            True, if successful. False, if still connected after timeout.
        """
        # If there are already no existing connections when this function is called,
        # then an update won't be generated by the device, and we'll time out.
        # Force an update by getting a new listener.
        self.sl4f.wlan_policy_lib.wlanSetNewListener()
        end_time = time.time() + timeout
        while time.time() < end_time:
            time_left = max(1, int(end_time - time.time()))
            try:
                update = self.sl4f.wlan_policy_lib.wlanGetUpdate(
                    timeout=time_left)
            except TimeoutError:
                self.log.info(
                    "Timed out getting status update while waiting for all"
                    " connections to end.")
                self.sl4f.wlan_policy_lib.wlanSetNewListener()
                return False

            if update["error"] != None:
                self.log.info("Failed to get status update")
                return False
            # If any network is connected or being connected to, wait for them
            # to disconnect.
            if any(network['state'].lower() in
                   {STATE_CONNECTED.lower(),
                    STATE_CONNECTING.lower()}
                   for network in update['result']['networks']):
                continue
            else:
                return True
        return False

    def remove_and_preserve_networks_and_client_state(self):
        """ Preserves networks already saved on devices before removing them to
        setup up for a clean test environment. Records the state of client
        connections before tests.

        Raises:
            WlanPolicyControllerError, if the network removal is unsuccessful
        """
        # Save preexisting saved networks
        preserved_networks_and_state = {}
        saved_networks_response = self.sl4f.wlan_policy_lib.wlanGetSavedNetworks(
        )
        if saved_networks_response.get('error'):
            raise WlanPolicyControllerError(
                'Failed to get preexisting saved networks: %s' %
                saved_networks_response['error'])
        if saved_networks_response.get('result') != None:
            preserved_networks_and_state[
                SAVED_NETWORKS] = saved_networks_response['result']

        # Remove preexisting saved networks
        if not self.remove_all_networks():
            raise WlanPolicyControllerError(
                'Failed to clear networks and disconnect at FuchsiaDevice creation.'
            )

        self.sl4f.wlan_policy_lib.wlanSetNewListener()
        update_response = self.sl4f.wlan_policy_lib.wlanGetUpdate()
        update_result = update_response.get('result', {})
        if update_result.get('state'):
            preserved_networks_and_state[CLIENT_STATE] = update_result['state']
        else:
            self.log.warn('Failed to get update; test will not start or '
                          'stop client connections at the end of the test.')

        self.log.info('Saved networks cleared and preserved.')
        return preserved_networks_and_state

    def restore_preserved_networks_and_client_state(self):
        """ Restore saved networks and client state onto device if they have
        been preserved.
        """
        if not self.remove_all_networks():
            self.log.warn('Failed to remove saved networks before restore.')
        restore_success = True
        for network in self.preserved_networks_and_client_state[
                SAVED_NETWORKS]:
            if not self.save_network(network["ssid"], network["security_type"],
                                     network["credential_value"]):
                self.log.warn('Failed to restore network (%s).' %
                              network['ssid'])
                restore_success = False
        starting_state = self.preserved_networks_and_client_state[CLIENT_STATE]
        if starting_state == CONNECTIONS_ENABLED:
            state_restored = self.start_client_connections()
        else:
            state_restored = self.stop_client_connections()
        if not state_restored:
            self.log.warn('Failed to restore client connections state.')
            restore_success = False
        if restore_success:
            self.log.info('Preserved networks and client state restored.')
            self.preserved_networks_and_client_state = None
        return restore_success
