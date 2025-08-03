"""Tests for Bluetooth PAN profile functionalities."""

import contextlib
import time

from mobly import test_runner
from mobly import signals
from mobly.controllers.android_device_lib import jsonrpc_client_base
from blueberry.utils import blueberry_base_test
from blueberry.utils import bt_test_utils

# Timeout to wait for NAP service connection to be specific state in second.
CONNECTION_TIMEOUT_SECS = 20

# Interval time between ping requests in second.
PING_INTERVAL_TIME_SEC = 2

# Timeout to wait for ping success in second.
PING_TIMEOUT_SEC = 60

# A URL is used to verify internet by ping request.
TEST_URL = 'http://www.google.com'

# A string representing SIM State is ready.
SIM_STATE_READY = 'READY'


class BluetoothPanTest(blueberry_base_test.BlueberryBaseTest):
  """Test class for Bluetooth PAN(Personal Area Networking) profile.

  Test internet connection sharing via Bluetooth between two Android devices.
  One device which is referred to as NAP(Network Access Point) uses Bluetooth
  tethering to share internet connection with another device which is referred
  to as PANU(Personal Area Networking User).
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.pan_connect_attempts = None

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothPanTest, self).setup_class()
    # Number of attempts to initiate connection. 5 attempts as default.
    self.pan_connect_attempts = self.user_params.get('pan_connect_attempts', 5)

    for device in self.android_devices:
      device.init_setup()
      device.sl4a_setup()
      device.mac_address = device.get_bluetooth_mac_address()

      # Check if the device has inserted a SIM card.
      if device.sl4a.telephonyGetSimState() != SIM_STATE_READY:
        raise signals.TestError('SIM card is not ready on Device "%s".' %
                                device.serial)

    self.primary_device = self.android_devices[0]
    self.secondary_device = self.android_devices[1]

  def teardown_test(self):
    """Standard Mobly teardown test.

    Reset every devices when a test finished.
    """
    super(BluetoothPanTest, self).teardown_test()
    # Revert debug tags.
    for device in self.android_devices:
      device.debug_tag = device.serial
      device.factory_reset_bluetooth()

  def wait_for_nap_service_connection(
      self,
      device,
      connected_mac_addr,
      state_connected=True):
    """Waits for NAP service connection to be expected state.

    Args:
      device: AndroidDevice, A device is used to check this connection.
      connected_mac_addr: String, Bluetooth Mac address is needed to be checked.
      state_connected: Bool, NAP service connection is established as expected
          if True, else terminated as expected.

    Raises:
      TestFailure: Raised if NAP service connection is not expected state.
    """
    def is_device_connected():
      """Returns True if connected else False."""
      connected_devices = (device.sl4a.
                           bluetoothPanGetConnectedDevices())
      # Check if the Bluetooth mac address is in the connected device list.
      return connected_mac_addr in [d['address'] for d in connected_devices]

    bt_test_utils.wait_until(
        timeout_sec=CONNECTION_TIMEOUT_SECS,
        condition_func=is_device_connected,
        func_args=[],
        expected_value=state_connected,
        exception=signals.TestFailure(
            'NAP service connection failed to be %s in %ds.' %
            ('established' if state_connected else 'terminated',
             CONNECTION_TIMEOUT_SECS)))

  def initiate_nap_service_connection(
      self,
      initiator_device,
      connected_mac_addr):
    """Initiates NAP service connection.

    Args:
      initiator_device: AndroidDevice, A device intiating connection.
      connected_mac_addr: String, Bluetooth Mac address of connected device.

    Raises:
      TestFailure: Raised if NAP service connection fails to be established.
    """
    count = 0
    for _ in range(self.pan_connect_attempts):
      count += 1
      try:
        initiator_device.sl4a.bluetoothConnectBonded(connected_mac_addr)
        self.wait_for_nap_service_connection(
            device=initiator_device,
            connected_mac_addr=connected_mac_addr,
            state_connected=True)
        return
      except signals.TestFailure:
        if count == self.pan_connect_attempts:
          raise signals.TestFailure(
              'NAP service connection still failed to be established '
              'after retried %d times.' %
              self.pan_connect_attempts)

  def terminate_nap_service_connection(
      self,
      initiator_device,
      connected_mac_addr):
    """Terminates NAP service connection.

    Args:
      initiator_device: AndroidDevice, A device intiating disconnection.
      connected_mac_addr: String, Bluetooth Mac address of connected device.
    """
    initiator_device.log.info('Terminate NAP service connection.')
    initiator_device.sl4a.bluetoothDisconnectConnected(connected_mac_addr)
    self.wait_for_nap_service_connection(
        device=initiator_device,
        connected_mac_addr=connected_mac_addr,
        state_connected=False)

  @contextlib.contextmanager
  def establish_nap_service_connection(self, nap_device, panu_device):
    """Establishes NAP service connection between both Android devices.

    The context is used to form a basic network connection between devices
    before executing a test.

    Steps:
      1. Disable Mobile data to avoid internet access on PANU device.
      2. Make sure Mobile data available on NAP device.
      3. Enable Bluetooth from PANU device.
      4. Enable Bluetooth tethering on NAP device.
      5. Initiate a connection from PANU device.
      6. Check if PANU device has internet access via the connection.

    Args:
      nap_device: AndroidDevice, A device sharing internet connection via
          Bluetooth tethering.
      panu_device: AndroidDevice, A device gaining internet access via
          Bluetooth tethering.

    Yields:
      None, the context just execute a pre procedure for PAN testing.

    Raises:
      signals.TestError: raised if a step fails.
      signals.TestFailure: raised if PANU device fails to access internet.
    """
    nap_device.debug_tag = 'NAP'
    panu_device.debug_tag = 'PANU'
    try:
      # Disable Mobile data to avoid internet access on PANU device.
      panu_device.log.info('Disabling Mobile data...')
      panu_device.sl4a.setMobileDataEnabled(False)
      self.verify_internet(
          allow_access=False,
          device=panu_device,
          exception=signals.TestError(
              'PANU device "%s" still connected to internet when Mobile data '
              'had been disabled.' % panu_device.serial))

      # Make sure NAP device has Mobile data for internet sharing.
      nap_device.log.info('Enabling Mobile data...')
      nap_device.sl4a.setMobileDataEnabled(True)
      self.verify_internet(
          allow_access=True,
          device=nap_device,
          exception=signals.TestError(
              'NAP device "%s" did not have internet access when Mobile data '
              'had been enabled.' % nap_device.serial))

      # Enable Bluetooth tethering from NAP device.
      nap_device.set_bluetooth_tethering(status_enabled=True)
      # Wait until Bluetooth tethering stabilizes. This waiting time avoids PANU
      # device initiates a connection to NAP device immediately when NAP device
      # enables Bluetooth tethering.
      time.sleep(5)

      nap_device.activate_pairing_mode()
      panu_device.log.info('Pair to NAP device "%s".' % nap_device.serial)
      panu_device.pair_and_connect_bluetooth(nap_device.mac_address)

      # Initiate a connection to NAP device.
      panu_device.log.info('Initiate a connection to NAP device "%s".' %
                           nap_device.serial)
      self.initiate_nap_service_connection(
          initiator_device=panu_device,
          connected_mac_addr=nap_device.mac_address)

      # Check if PANU device can access internet via NAP service connection.
      self.verify_internet(
          allow_access=True,
          device=panu_device,
          exception=signals.TestFailure(
              'PANU device "%s" failed to access internet via NAP service '
              'connection.' % panu_device.serial))
      yield
    finally:
      # Disable Bluetooth tethering from NAP device.
      nap_device.set_bluetooth_tethering(status_enabled=False)
      panu_device.sl4a.setMobileDataEnabled(True)

  def verify_internet(self, allow_access, device, exception):
    """Verifies that internet is in expected state.

    Continuously make ping request to a URL for internet verification.

    Args:
      allow_access: Bool, Device can have internet access as expected if True,
          else no internet access as expected.
      device: AndroidDevice, Device to be check internet state.
      exception: Exception, Raised if internet is not in expected state.
    """
    device.log.info('Verify that internet %s be used.' %
                    ('can' if allow_access else 'can not'))

    def http_ping():
      """Returns True if http ping success else False."""
      try:
        return bool(device.sl4a.httpPing(TEST_URL))
      except jsonrpc_client_base.ApiError as e:
        # ApiError is raised by httpPing() when no internet.
        device.log.debug(str(e))
      return False

    bt_test_utils.wait_until(
        timeout_sec=PING_TIMEOUT_SEC,
        condition_func=http_ping,
        func_args=[],
        expected_value=allow_access,
        exception=exception,
        interval_sec=PING_INTERVAL_TIME_SEC)

  def test_gain_internet_and_terminate_nap_connection(self):
    """Test that DUT can access internet and terminate NAP service connection.

    In this test case, primary device is PANU and secondary device is NAP. While
    a connection has established between both devices, PANU should be able to
    use internet and terminate the connection to disable internet access.

    Steps:
      1. Establish NAP service connection between both devices.
      2. Terminal the connection from PANU device.
      3. Verify that PANU device cannot access internet.
    """
    with self.establish_nap_service_connection(
        nap_device=self.secondary_device,
        panu_device=self.primary_device):

      # Terminate the connection from DUT.
      self.terminate_nap_service_connection(
          initiator_device=self.primary_device,
          connected_mac_addr=self.secondary_device.mac_address)

      # Verify that PANU device cannot access internet.
      self.verify_internet(
          allow_access=False,
          device=self.primary_device,
          exception=signals.TestFailure(
              'PANU device "%s" can still access internet when it had '
              'terminated NAP service connection.' %
              self.primary_device.serial))

  def test_share_internet_and_disable_bluetooth_tethering(self):
    """Test that DUT can share internet and stop internet sharing.

    In this test case, primary device is NAP and secondary device is PANU. While
    a connection has established between both devices, NAP should be able to
    share internet and disable Bluetooth thethering to stop internet sharing.

    Steps:
      1. Establish NAP service connection between both devices.
      3. Disable Bluetooth tethering from NAP device.
      4. Verify that PANU device cannot access internet.
    """
    with self.establish_nap_service_connection(
        nap_device=self.primary_device,
        panu_device=self.secondary_device):

      # Disable Bluetooth tethering from DUT and check if the nap connection is
      # terminated.
      self.primary_device.set_bluetooth_tethering(status_enabled=False)
      self.wait_for_nap_service_connection(
          device=self.primary_device,
          connected_mac_addr=self.secondary_device.mac_address,
          state_connected=False)

      # Verify that PANU device cannot access internet.
      self.verify_internet(
          allow_access=False,
          device=self.secondary_device,
          exception=signals.TestFailure(
              'PANU device "%s" can still access internet when it had '
              'terminated NAP service connection.' %
              self.secondary_device.serial))


if __name__ == '__main__':
  test_runner.main()
