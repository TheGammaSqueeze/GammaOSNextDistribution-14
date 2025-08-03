import os
import unittest
from unittest import mock

from autotest_lib.client.common_lib.cros.cfm.usb import usb_port_manager

# Fake GPIO value for testing.
GUADO_GPIO_BASE = '100'
# GPIO offset for the port on bus 1, port 2.
GUADO_GPIO_OFFSET = '56'
# Expected index -- base + offset
GUADO_GPIO_INDEX = '156'
GUADO_GPIO_PATH = '/sys/class/gpio'
GUADO_CONTROLLER = 'INT3437:00'

FIZZ_GPIO = 3


def host_run_expected_guado(command):
    """
    Defines mock values to return from host.run(). This covers expected calls
    from usb_port_manager._find_gpio_base_index().
    """
    ret = mock.Mock()

    if command == 'ls -d /sys/class/gpio/gpiochip*':
        ret.stdout = os.path.join(GUADO_GPIO_PATH, 
            'gpiochip%s' % GUADO_GPIO_BASE)
    elif command.startswith('cat') and command.endswith('/label'):
        ret.stdout = GUADO_CONTROLLER
    elif command.startswith('cat') and command.endswith('/base'):
        ret.stdout = GUADO_GPIO_BASE

    return ret

# pylint: disable=missing-docstring
class UsbPortManagerTest(unittest.TestCase):

    def test_power_off_gpio_unexported_guado(self):
        host = mock.Mock()
        host.get_board = mock.Mock(return_value='board: guado')

        host.run = mock.Mock(side_effect=host_run_expected_guado)
        host.path_exists = mock.Mock(return_value=False)
        port_manager = usb_port_manager.UsbPortManager(host)
        port_manager.set_port_power([(1, 2)], False)
        expected_runs = [
            mock.call('ls -d /sys/class/gpio/gpiochip*'),
            mock.call('cat /sys/class/gpio/gpiochip%s/label' % GUADO_GPIO_BASE),
            mock.call('cat /sys/class/gpio/gpiochip%s/base' % GUADO_GPIO_BASE),
            mock.call('echo %s > /sys/class/gpio/export' %  GUADO_GPIO_INDEX),
            mock.call('echo out > {path}/gpio{index}/direction'.format(
                path=GUADO_GPIO_PATH, index=GUADO_GPIO_INDEX)),
            mock.call('echo 0 > {path}/gpio{index}/value'.format(
                path=GUADO_GPIO_PATH, index=GUADO_GPIO_INDEX)),
            mock.call('echo %s > /sys/class/gpio/unexport' % GUADO_GPIO_INDEX),
        ]
        host.run.assert_has_calls(expected_runs)

    def test_power_on_gpio_exported_guado(self):
        host = mock.Mock()
        host.get_board = mock.Mock(return_value='board: guado')

        host.run = mock.Mock(side_effect=host_run_expected_guado)
        host.path_exists = mock.Mock(return_value=True)
        port_manager = usb_port_manager.UsbPortManager(host)
        port_manager.set_port_power([(1, 2)], True)
        expected_runs = [
            mock.call('ls -d /sys/class/gpio/gpiochip*'),
            mock.call('cat /sys/class/gpio/gpiochip%s/label' % GUADO_GPIO_BASE),
            mock.call('cat /sys/class/gpio/gpiochip%s/base' % GUADO_GPIO_BASE),
            mock.call('echo out > {path}/gpio{index}/direction'.format(
                path=GUADO_GPIO_PATH, index=GUADO_GPIO_INDEX)),
            mock.call('echo 1 > {path}/gpio{index}/value'.format(
                path=GUADO_GPIO_PATH, index=GUADO_GPIO_INDEX)),
        ]
        host.run.assert_has_calls(expected_runs)

    def test_power_off_gpio_fizz(self):
        host = mock.Mock()
        host.get_board = mock.Mock(return_value='board: fizz')
        host.run = mock.Mock()
        port_manager = usb_port_manager.UsbPortManager(host)
        port_manager.set_port_power([(1, 2)], False)
        expected_runs = [
                mock.call('ectool gpioset USB%s_ENABLE 0' % FIZZ_GPIO)
        ]
        host.run.assert_has_calls(expected_runs)

    def test_power_on_gpio_fizz(self):
        host = mock.Mock()
        host.get_board = mock.Mock(return_value='board: fizz')
        host.run = mock.Mock()
        port_manager = usb_port_manager.UsbPortManager(host)
        port_manager.set_port_power([(1, 2)], True)
        expected_runs = [
                mock.call('ectool gpioset USB%s_ENABLE 1' % FIZZ_GPIO)
        ]
        host.run.assert_has_calls(expected_runs)
