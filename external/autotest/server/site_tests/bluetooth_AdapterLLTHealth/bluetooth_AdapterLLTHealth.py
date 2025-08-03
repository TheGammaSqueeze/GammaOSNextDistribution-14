# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""A Batch of Bluetooth LE LLT health tests"""

from autotest_lib.server.cros.bluetooth import advertisements_data

DEFAULT_MIN_ADV_INTERVAL = 200
DEFAULT_MAX_ADV_INTERVAL = 500

from autotest_lib.server.cros.bluetooth.\
     bluetooth_adapter_controller_role_tests \
     import bluetooth_AdapterControllerRoleTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests \
     import BluetoothAdapterQuickTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_hidreports_tests \
     import BluetoothAdapterHIDReportTests
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_better_together \
     import BluetoothAdapterBetterTogether


class bluetooth_AdapterLLTHealth(BluetoothAdapterHIDReportTests,
                                 bluetooth_AdapterControllerRoleTests,
                                 BluetoothAdapterBetterTogether):
    """A Batch of Bluetooth LE LLT health tests. This test is written
       as a batch of tests in order to reduce test time, since auto-test
       ramp up time is costly. The batch is using BluetoothAdapterQuickTests
       wrapper methods to start and end a test and a batch of tests.

       This class can be called to run the entire test batch or to run a
       specific test only
    """

    test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
    batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


    def discover_and_pair(self, device):
        """Discovers and pairs given device. Automatically connects too.

           @param device: meta object for bt peer device
        """
        self.test_discover_device(device.address)
        self.test_pairing(device.address, device.pin, trusted=True)
        self.test_connection_by_adapter(device.address)


    def start_connectable_advertisement(self):
        """ Initiate connectable advertising from DUT """
        # Register and start advertising instance
        # We ignore failure because the test isn't able to verify
        # the min/max advertising intervals, but this is ok.
        self.test_reset_advertising()
        self.test_set_advertising_intervals(DEFAULT_MIN_ADV_INTERVAL,
                                            DEFAULT_MAX_ADV_INTERVAL)
        self.test_register_advertisement(advertisements_data.ADVERTISEMENTS[0],
                                         1)


    def pair_and_test_central(self, peripheral):
        """Connects DUT as central to a peripheral device.

           @param peripheral: meta object for bt peer device
        """
        # Pair the central device first -
        # necessary for later connection to peripheral
        self.pair_adapter_to_device(peripheral)
        self.test_device_set_discoverable(peripheral, False)

        self.start_connectable_advertisement()
        # Discover DUT from peer
        self.test_discover_by_device(peripheral)
        # Connect to DUT from peer, putting DUT in peripheral role
        self.test_connection_by_device(peripheral)
        self.test_reset_advertising()


    @test_wrapper('LLT: 1 Central 1 Peripheral. Order of connection CP',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1
                  })
    def llt_1c1p_connect_cp(self):
        """Tests llt with two peer devices.
           Connects DUT as central to first device
           and as peripheral to second device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]
        # Establish connection from DUT as LE Central
        self.discover_and_pair(central)

        self.test_hid_device_created(central.address)
        # Verify data transfer over the DUT LE central Connection
        self.test_mouse_left_click(central)

        # Now establish second connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Verify data over LE Central connection again
        self.test_mouse_left_click(central)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central.address)
        self.test_disconnection_by_device(peripheral)


    @test_wrapper('LLT: 1 Central 1 Peripheral. Order of connection PC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1
                  })
    def llt_1c1p_connect_pc(self):
        """Tests llt with two peer devices,
           Connects DUT as peripheral to first device
           and as central to second device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]
        # Establish the first connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Establish  second connection from DUT as LE Central
        self.discover_and_pair(central)
        self.test_hid_device_created(central.address)
        # Verify data transfer over the DUT LE Central Connection
        self.test_mouse_left_click(central)
        # Verfiy LE peripheral connection again
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central.address)
        self.test_disconnection_by_device(peripheral)


    @test_wrapper('LLT: 1 Central 1 Peripheral while DUT advertising.'
                  'Order of connection PC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1
                  })
    def llt_1c1p_connect_pc_while_adv(self):
        """Tests llt with two peer devices, while DUT advertising.
           Connects DUT while advertising
           as peripheral to first device
           and as central to second device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]
        # Establish the first connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Establish second connection from DUT as LE Central
        # while advertising in progress
        self.start_connectable_advertisement()
        self.discover_and_pair(central)
        self.test_hid_device_created(central.address)

        # Verify data transfer over the DUT LE Central Connection
        self.test_mouse_left_click(central)
        # Verfiy LE Peripheral connection again
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central.address)
        self.test_disconnection_by_device(peripheral)
        self.test_reset_advertising()


    @test_wrapper('LLT: 2 Central 1 Peripheral. Order of connection CCP',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2c1p_connect_ccp(self):
        """Tests llt with three peer devices.
           Connects DUT as central to first and second devices,
           connects DUT as peripheral to third device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central_1 = self.devices['BLE_PHONE'][0]
        central_2 = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]
        # Establish two connections from DUT as LE Central
        self.discover_and_pair(central_2)
        self.test_hid_device_created(central_2.address)

        # Verify data transfer over two DUT LE Central Connections
        self.test_mouse_left_click(central_2)

        central_1.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_1.address)

        # Establish third connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Verify data transfer over two DUT LE Central Connections
        self.test_mouse_left_click(central_2)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_1.address)
        self.test_disconnection_by_adapter(central_2.address)
        self.test_disconnection_by_device(peripheral)


    @test_wrapper('LLT: 2 Central 1 Peripheral. Order of connection PCC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2c1p_connect_pcc(self):
        """Tests llt with three peer devices.
           Connects DUT as peripheral to first device
           and as central to second and third device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central_1 = self.devices['BLE_PHONE'][0]
        central_2 = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]

        # Establish the first connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Establish connections from DUT as LE Central
        self.discover_and_pair(central_2)
        self.test_hid_device_created(central_2.address)

        # Verify data transfer over two DUT LE Central Connections
        self.test_mouse_left_click(central_2)

        # Establish third connection
        central_1.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_1.address)

        # Verify once again data transfer over DUT LE Peripheral connection
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_1.address)
        self.test_disconnection_by_adapter(central_2.address)
        self.test_disconnection_by_device(peripheral)


    @test_wrapper('LLT: 2 Central 1 Peripheral. Order of connection CPC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2c1p_connect_cpc(self):
        """Tests llt with three peer devices.
           Connects DUT as central to first device,
           as peripheral to second and as central to third device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central_1 = self.devices['BLE_PHONE'][0]
        central_2 = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]

        # Establish the first connection with DUT as LE Central
        central_1.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_1.address)

        # Establish the second connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Establish third connections from DUT as LE Central
        self.discover_and_pair(central_2)
        self.test_hid_device_created(central_2.address)

        # Verify data transfer over second LE Central Connections
        self.test_mouse_left_click(central_2)
        # Verify once again data transfer over DUT LE Peripheral connection
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_1.address)
        self.test_disconnection_by_adapter(central_2.address)
        self.test_disconnection_by_device(peripheral)


    @test_wrapper('LLT: 2 Central 1 Peripheral while DUT advertising.'
                  'Order of connection PCC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2c1p_connect_pcc_while_adv(self):
        """Tests llt with three peer devices.
           Connects DUT as peripheral to first device
           and as central to second and third device while advertising,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central_1 = self.devices['BLE_MOUSE'][0]
        central_2 = self.devices['BLE_PHONE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]

        # Establish the first connection with DUT as LE Peripheral
        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Connect as first LE Central while DUT is advertising
        self.start_connectable_advertisement()
        self.discover_and_pair(central_1)
        self.test_hid_device_created(central_1.address)

        # Establish second LE connection from DUT as LE Central
        central_2.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_2.address)

        # Verify data transfer over first LE Central Connections
        self.test_mouse_left_click(central_1)
        # Verify once again data transfer over DUT LE Peripheral connection
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_1.address)
        self.test_disconnection_by_adapter(central_2.address)
        self.test_disconnection_by_device(peripheral)
        self.test_reset_advertising()


    @test_wrapper('LLT: 2 Central 1 Peripheral while DUT Advertising.'
                  'Order of connection CPC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2c1p_connect_cpc_while_adv(self):
        """Tests llt with three peer devices.
           Connects DUT while advertising as central to first device,
           as peripheral to second and as central to third device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        central_1 = self.devices['BLE_PHONE'][0]
        central_2 = self.devices['BLE_MOUSE'][0]
        peripheral = self.devices['BLE_KEYBOARD'][0]

        self.bluetooth_le_facade = self.bluetooth_facade
        # Establish the first connection with DUT as LE Central
        # while advertising in progress
        self.start_connectable_advertisement()
        central_1.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_1.address)

        # Establish the second connection with DUT as LE Peripheral
        # Try transferring data over connection
        self.pair_and_test_central(peripheral)
        self.run_keyboard_tests(peripheral)

        # Establish third connections from DUT as LE Central
        self.discover_and_pair(central_2)
        self.test_hid_device_created(central_2.address)

        # Verify data transfer over second LE Central Connections
        self.test_mouse_left_click(central_2)
        # Verify once again data transfer over DUT LE Peripheral connection
        self.run_keyboard_tests(peripheral)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_1.address)
        self.test_disconnection_by_adapter(central_2.address)
        self.test_disconnection_by_device(peripheral)
        self.test_reset_advertising()


    @test_wrapper('LLT: 1 Central 2 Peripheral. Order of connection CPP',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2p1c_connect_cpp(self):
        """Tests llt with three peer devices.
           Connects DUT as central to first device
           and as peripheral to second and third devices,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        peripheral_1 = self.devices['BLE_KEYBOARD'][0]
        central_peer = self.devices['BLE_PHONE'][0]
        peripheral_2 = self.devices['BLE_MOUSE'][0]

        # Establish connection from DUT as LE Central
        central_peer.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_peer.address)

        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral_1)
        self.run_keyboard_tests(peripheral_1)

        # Establish and Verify second LE peripheral connection
        self.pair_and_test_central(peripheral_2)

        # Try transferring data over connection
        self.test_mouse_left_click(peripheral_2)
        # Verify traffic from LE Peripheral connections again
        self.run_keyboard_tests(peripheral_1)
        self.test_mouse_left_click(peripheral_2)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_peer.address)
        self.test_disconnection_by_device(peripheral_1)
        self.test_disconnection_by_device(peripheral_2)


    @test_wrapper('LLT: 1 Central 2 Peripheral. Order of connection PCP',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2p1c_connect_pcp(self):
        """Tests llt with three peer devices.
           Connects DUT as peripheral to first device,
           as central to second and as peripheral to third devices,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        peripheral_1 = self.devices['BLE_KEYBOARD'][0]
        central_peer = self.devices['BLE_PHONE'][0]
        peripheral_2 = self.devices['BLE_MOUSE'][0]

        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral_1)
        self.run_keyboard_tests(peripheral_1)

        # Establish connection from DUT as LE Central
        central_peer.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_peer.address)

        # Establish and Verify second LE peripheral connection
        self.pair_and_test_central(peripheral_2)

        # Try transferring data over connection
        self.test_mouse_left_click(peripheral_2)
        # Verify traffic from LE Peripheral connections again
        self.run_keyboard_tests(peripheral_1)
        self.test_mouse_left_click(peripheral_2)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_peer.address)
        self.test_disconnection_by_device(peripheral_1)
        self.test_disconnection_by_device(peripheral_2)


    @test_wrapper('LLT: 1 Central 2 Peripheral. Order of connection PPC',
                  devices={
                          'BLE_KEYBOARD': 1,
                          'BLE_MOUSE': 1,
                          'BLE_PHONE': 1
                  })
    def llt_2p1c_connect_ppc(self):
        """Tests llt with three peer devices.
           Connects DUT as peripheral to first and second devices
           and as central to third device,
           sends small amount of data over the connection
        """

        self.verify_controller_capability(
                        required_roles=['central-peripheral'])

        peripheral_1 = self.devices['BLE_KEYBOARD'][0]
        central_peer = self.devices['BLE_PHONE'][0]
        peripheral_2 = self.devices['BLE_MOUSE'][0]

        self.bluetooth_le_facade = self.bluetooth_facade

        # Connect to DUT from peer, putting DUT in peripheral role
        # Try transferring data over connection
        self.pair_and_test_central(peripheral_1)
        self.run_keyboard_tests(peripheral_1)

        # Establish and Verify second LE peripheral connection
        self.pair_and_test_central(peripheral_2)

        # Try transferring data over connection
        self.test_mouse_left_click(peripheral_2)

        # Verify data transfer over two DUT LE Central Connections
        central_peer.RemoveDevice(self.bluetooth_facade.address)
        self.test_smart_unlock_llt(address=central_peer.address)
        # Verify traffic from LE Peripheral connections again
        self.run_keyboard_tests(peripheral_1)
        self.test_mouse_left_click(peripheral_2)

        # Disconnect connections from DUT
        self.test_disconnection_by_adapter(central_peer.address)
        self.test_disconnection_by_device(peripheral_1)
        self.test_disconnection_by_device(peripheral_2)


    @batch_wrapper('LLT Health')
    def llt_health_batch_run(self, num_iterations=1, test_name=None):
        """Run the LE LLT health test batch or a specific given test.
           The wrapper of this method is implemented in batch_decorator.
           Using the decorator a test batch method can implement the only its
           core tests invocations and let the decorator handle the wrapper,
           which is taking care for whether to run a specific test or the
           batch as a whole, and running the batch in iterations

           @param num_iterations: how many iterations to run
           @param test_name: specific test to run otherwise None to run the
                             whole batch
        """
        self.llt_1c1p_connect_cp()
        self.llt_1c1p_connect_pc()
        self.llt_1c1p_connect_pc_while_adv()
        self.llt_2c1p_connect_ccp()
        self.llt_2c1p_connect_pcc()
        self.llt_2c1p_connect_cpc()
        self.llt_2c1p_connect_pcc_while_adv()
        self.llt_2c1p_connect_cpc_while_adv()
        self.llt_2p1c_connect_cpp()
        self.llt_2p1c_connect_pcp()
        self.llt_2p1c_connect_ppc()


    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Run the batch of Bluetooth LE LLT health tests

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of rounds to execute the test
        @test_name: the test to run, or None for all tests
        """

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.llt_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
