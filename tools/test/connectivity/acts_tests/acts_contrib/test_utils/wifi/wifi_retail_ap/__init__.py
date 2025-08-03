#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import collections.abc
import copy
import fcntl
import importlib
import logging
import os
import selenium
import time
from acts import logger
from selenium.webdriver.support.ui import Select
from selenium.webdriver.chrome.service import Service as ChromeService
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as expected_conditions
from webdriver_manager.chrome import ChromeDriverManager


BROWSER_WAIT_SHORT = 1
BROWSER_WAIT_MED = 3
BROWSER_WAIT_LONG = 30
BROWSER_WAIT_EXTRA_LONG = 60


def create(configs):
    """Factory method for retail AP class.

    Args:
        configs: list of dicts containing ap settings. ap settings must contain
        the following: brand, model, ip_address, username and password
    """
    SUPPORTED_APS = {
        ('Netgear', 'R7000'): {
            'name': 'NetgearR7000AP',
            'package': 'netgear_r7000'
        },
        ('Netgear', 'R7000NA'): {
            'name': 'NetgearR7000NAAP',
            'package': 'netgear_r7000'
        },
        ('Netgear', 'R7500'): {
            'name': 'NetgearR7500AP',
            'package': 'netgear_r7500'
        },
        ('Netgear', 'R7500NA'): {
            'name': 'NetgearR7500NAAP',
            'package': 'netgear_r7500'
        },
        ('Netgear', 'R7800'): {
            'name': 'NetgearR7800AP',
            'package': 'netgear_r7800'
        },
        ('Netgear', 'R8000'): {
            'name': 'NetgearR8000AP',
            'package': 'netgear_r8000'
        },
        ('Netgear', 'RAX80'): {
            'name': 'NetgearRAX80AP',
            'package': 'netgear_rax80'
        },
        ('Netgear', 'RAX120'): {
            'name': 'NetgearRAX120AP',
            'package': 'netgear_rax120'
        },
        ('Netgear', 'RAX200'): {
            'name': 'NetgearRAX200AP',
            'package': 'netgear_rax200'
        },
        ('Netgear', 'RAXE500'): {
            'name': 'NetgearRAXE500AP',
            'package': 'netgear_raxe500'
        },
        ('Netgear', 'RS700'): {
            'name': 'NetgearRS700AP',
            'package': 'netgear_rs700'
        },
        ('Brcm', 'Reference'): {
            'name': 'BrcmRefAP',
            'package': 'brcm_ref'
        },
        ('Google', 'Wifi'): {
            'name': 'GoogleWifiAP',
            'package': 'google_wifi'
        },
    }
    objs = []
    for config in configs:
        ap_id = (config['brand'], config['model'])
        if ap_id not in SUPPORTED_APS:
            raise KeyError('Invalid retail AP brand and model combination.')
        ap_class_dict = SUPPORTED_APS[ap_id]
        ap_package = 'acts_contrib.test_utils.wifi.wifi_retail_ap.{}'.format(
            ap_class_dict['package'])
        ap_package = importlib.import_module(ap_package)
        ap_class = getattr(ap_package, ap_class_dict['name'])
        objs.append(ap_class(config))
    return objs


def destroy(objs):
    for obj in objs:
        obj.teardown()


class BlockingBrowser(selenium.webdriver.chrome.webdriver.WebDriver):
    """Class that implements a blocking browser session on top of selenium.

    The class inherits from and builds upon splinter/selenium's webdriver class
    and makes sure that only one such webdriver is active on a machine at any
    single time. The class ensures single session operation using a lock file.
    The class is to be used within context managers (e.g. with statements) to
    ensure locks are always properly released.
    """

    def __init__(self, headless, timeout):
        """Constructor for BlockingBrowser class.

        Args:
            headless: boolean to control visible/headless browser operation
            timeout: maximum time allowed to launch browser
        """
        if int(selenium.__version__[0]) < 4:
            raise RuntimeError(
                'BlockingBrowser now requires selenium==4.0.0 or later. ')
        self.log = logger.create_tagged_trace_logger('ChromeDriver')
        self.chrome_options = selenium.webdriver.chrome.webdriver.Options()
        self.chrome_options.add_argument('--no-proxy-server')
        self.chrome_options.add_argument('--no-sandbox')
        self.chrome_options.add_argument('--crash-dumps-dir=/tmp')
        self.chrome_options.add_argument('--allow-running-insecure-content')
        self.chrome_options.add_argument('--ignore-certificate-errors')
        self.chrome_capabilities = selenium.webdriver.common.desired_capabilities.DesiredCapabilities.CHROME.copy(
        )
        self.chrome_capabilities['acceptSslCerts'] = True
        self.chrome_capabilities['acceptInsecureCerts'] = True
        if headless:
            self.chrome_options.add_argument('--headless')
            self.chrome_options.add_argument('--disable-gpu')
        os.environ['WDM_LOG'] = str(logging.NOTSET)
        self.executable_path = ChromeDriverManager().install()
        self.timeout = timeout

    def __enter__(self):
        """Entry context manager for BlockingBrowser.

        The enter context manager for BlockingBrowser attempts to lock the
        browser file. If successful, it launches and returns a chromedriver
        session. If an exception occurs while starting the browser, the lock
        file is released.
        """
        self.lock_file = open(self.executable_path, 'r')
        start_time = time.time()
        while time.time() < start_time + self.timeout:
            try:
                fcntl.flock(self.lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                time.sleep(BROWSER_WAIT_SHORT)
                continue
            try:
                self.driver = selenium.webdriver.Chrome(
                    service=ChromeService(self.executable_path),
                    options=self.chrome_options,
                    desired_capabilities=self.chrome_capabilities)
                self.session_id = self.driver.session_id
                return self
            except:
                fcntl.flock(self.lock_file, fcntl.LOCK_UN)
                self.lock_file.close()
                raise RuntimeError('Error starting browser. '
                                   'Releasing lock file.')
        raise TimeoutError('Could not start chrome browser in time.')

    def __exit__(self, exc_type, exc_value, traceback):
        """Exit context manager for BlockingBrowser.

        The exit context manager simply calls the parent class exit and
        releases the lock file.
        """
        try:
            self.driver.quit()
        except:
            raise RuntimeError('Failed to quit browser. Releasing lock file.')
        finally:
            fcntl.flock(self.lock_file, fcntl.LOCK_UN)
            self.lock_file.close()

    def restart(self):
        """Method to restart browser session without releasing lock file."""
        self.driver.quit()
        self.__enter__()

    def visit_persistent(self,
                         url,
                         page_load_timeout,
                         num_tries,
                         backup_url='about:blank',
                         check_for_element=None):
        """Method to visit webpages and retry upon failure.

        The function visits a URL and checks that the resulting URL matches
        the intended URL, i.e. no redirects have happened

        Args:
            url: the intended url
            page_load_timeout: timeout for page visits
            num_tries: number of tries before url is declared unreachable
            backup_url: url to visit if first url is not reachable. This can be
            used to simply refresh the browser and try again or to re-login to
            the AP
            check_for_element: element id to check for existence on page
        """
        self.driver.set_page_load_timeout(page_load_timeout)
        for idx in range(num_tries):
            try:
                self.driver.get(url)
            except:
                self.restart()

            page_reached = self.driver.current_url.split('/')[-1] == url.split(
                '/')[-1]
            if page_reached:
                if check_for_element:
                    time.sleep(BROWSER_WAIT_MED)
                    if self.is_element_visible(check_for_element):
                        break
                    else:
                        raise RuntimeError(
                            'Page reached but expected element not found.')
                else:
                    break
            else:
                try:
                    self.driver.get(backup_url)
                except:
                    self.restart()

            if idx == num_tries - 1:
                self.log.error('URL unreachable. Current URL: {}'.format(
                    self.url))
                raise RuntimeError('URL unreachable.')

    def get_element_value(self, element_name):
        """Function to look up and get webpage element value.

        Args:
            element_name: name of element to look up
        Returns:
            Value of element
        """
        #element = self.driver.find_element_by_name(element_name)
        element = self.driver.find_element(By.NAME, element_name)
        element_type = self.get_element_type(element_name)
        if element_type == 'checkbox':
            return element.is_selected()
        elif element_type == 'radio':
            items = self.driver.find_elements(By.NAME, element_name)
            for item in items:
                if item.is_selected():
                    return item.get_attribute('value')
        else:
            return element.get_attribute('value')

    def get_element_type(self, element_name):
        """Function to look up and get webpage element type.

        Args:
            element_name: name of element to look up
        Returns:
            Type of element
        """
        item = self.driver.find_element(By.NAME, element_name)
        type = item.get_attribute('type')
        return type

    def is_element_enabled(self, element_name):
        """Function to check if element is enabled/interactable.

        Args:
            element_name: name of element to look up
        Returns:
            Boolean indicating if element is interactable
        """
        item = self.driver.find_element(By.NAME, element_name)
        return item.is_enabled()

    def is_element_visible(self, element_name):
        """Function to check if element is visible.

        Args:
            element_name: name of element to look up
        Returns:
            Boolean indicating if element is visible
        """
        item = self.driver.find_element(By.NAME, element_name)
        return item.is_displayed()

    def set_element_value(self, element_name, value, select_method='value'):
        """Function to set webpage element value.

        Args:
            element_name: name of element to set
            value: value of element
            select_method: select method for dropdown lists (value/index/text)
        """
        element_type = self.get_element_type(element_name)
        if element_type == 'text' or element_type == 'password':
            item = self.driver.find_element(By.NAME, element_name)
            item.clear()
            item.send_keys(value)
        elif element_type == 'checkbox':
            item = self.driver.find_element(By.NAME, element_name)
            if value != item.is_selected():
                item.click()
        elif element_type == 'radio':
            items = self.driver.find_elements(By.NAME, element_name)
            for item in items:
                if item.get_attribute('value') == value:
                    item.click()
        elif element_type == 'select-one':
            select = Select(self.driver.find_element(By.NAME, element_name))
            if select_method == 'value':
                select.select_by_value(str(value))
            elif select_method == 'text':
                select.select_by_visible_text(value)
            elif select_method == 'index':
                select.select_by_index(value)
            else:
                raise RuntimeError(
                    '{} is not a valid select method.'.format(select_method))
        else:
            raise RuntimeError(
                'Element type {} not supported.'.format(element_type))

    def click_button(self, button_name):
        """Function to click button on webpage

        Args:
            button_name: name of button to click
        """
        button = self.driver.find_element(By.NAME, button_name)
        if button.get_attribute('type') == 'submit':
            button.click()
        else:
            raise RuntimeError('{} is not a button.'.format(button_name))

    def accept_alert_if_present(self, wait_for_alert=1):
        """Function to check for alert and accept if present

        Args:
            wait_for_alert: time (seconds) to wait for alert
        """
        try:
            selenium.webdriver.support.ui.WebDriverWait(
                self.driver,
                wait_for_alert).until(expected_conditions.alert_is_present())
            alert = self.driver.switch_to.alert
            alert.accept()
        except selenium.common.exceptions.TimeoutException:
            pass


class WifiRetailAP(object):
    """Base class implementation for retail ap.

    Base class provides functions whose implementation is shared by all aps.
    If some functions such as set_power not supported by ap, checks will raise
    exceptions.
    """

    def __init__(self, ap_settings):
        self.ap_settings = ap_settings.copy()
        self.log = logger.create_tagged_trace_logger('AccessPoint|{}'.format(
            self._get_control_ip_address()))
        # Capabilities variable describing AP capabilities
        self.capabilities = {
            'interfaces': [],
            'channels': {},
            'modes': {},
            'default_mode': None
        }
        for interface in self.capabilities['interfaces']:
            self.ap_settings.setdefault(interface, {})
        # Lock AP
        if self.ap_settings.get('lock_ap', 0):
            self.lock_timeout = self.ap_settings.get('lock_timeout', 3600)
            self._lock_ap()

    def teardown(self):
        """Function to perform destroy operations."""
        if self.ap_settings.get('lock_ap', 0):
            self._unlock_ap()

    def reset(self):
        """Function that resets AP.

        Function implementation is AP dependent and intended to perform any
        necessary reset operations as part of controller destroy.
        """

    def read_ap_settings(self):
        """Function that reads current ap settings.

        Function implementation is AP dependent and thus base class raises exception
        if function not implemented in child class.
        """
        raise NotImplementedError

    def validate_ap_settings(self):
        """Function to validate ap settings.

        This function compares the actual ap settings read from the web GUI
        with the assumed settings saved in the AP object. When called after AP
        configuration, this method helps ensure that our configuration was
        successful.
        Note: Calling this function updates the stored ap_settings

        Raises:
            ValueError: If read AP settings do not match stored settings.
        """
        assumed_ap_settings = copy.deepcopy(self.ap_settings)
        actual_ap_settings = self.read_ap_settings()

        if assumed_ap_settings != actual_ap_settings:
            self.log.warning(
                'Discrepancy in AP settings. Some settings may have been overwritten.'
            )

    def configure_ap(self, **config_flags):
        """Function that configures ap based on values of ap_settings.

        Function implementation is AP dependent and thus base class raises exception
        if function not implemented in child class.

        Args:
            config_flags: optional configuration flags
        """
        raise NotImplementedError

    def set_region(self, region):
        """Function that sets AP region.

        This function sets the region for the AP. Note that this may overwrite
        channel and bandwidth settings in cases where the new region does not
        support the current wireless configuration.

        Args:
            region: string indicating AP region
        """
        if region != self.ap_settings['region']:
            self.log.warning(
                'Updating region may overwrite wireless settings.')
        setting_to_update = {'region': region}
        self.update_ap_settings(setting_to_update)

    def set_radio_on_off(self, network, status):
        """Function that turns the radio on or off.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            status: boolean indicating on or off (0: off, 1: on)
        """
        setting_to_update = {network: {'status': int(status)}}
        self.update_ap_settings(setting_to_update)

    def set_ssid(self, network, ssid):
        """Function that sets network SSID.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            ssid: string containing ssid
        """
        setting_to_update = {network: {'ssid': str(ssid)}}
        self.update_ap_settings(setting_to_update)

    def set_channel(self, network, channel):
        """Function that sets network channel.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            channel: string or int containing channel
        """
        if channel not in self.capabilities['channels'][network]:
            self.log.error('Ch{} is not supported on {} interface.'.format(
                channel, network))
        setting_to_update = {network: {'channel': channel}}
        self.update_ap_settings(setting_to_update)

    def set_bandwidth(self, network, bandwidth):
        """Function that sets network bandwidth/mode.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            bandwidth: string containing mode, e.g. 11g, VHT20, VHT40, VHT80.
        """
        if 'bw' in bandwidth:
            bandwidth = bandwidth.replace('bw',
                                          self.capabilities['default_mode'])
        elif isinstance(bandwidth, int):
            bandwidth = str(bandwidth) + self.capabilities['default_mode']
        if bandwidth not in self.capabilities['modes'][network]:
            self.log.error('{} mode is not supported on {} interface.'.format(
                bandwidth, network))
        setting_to_update = {network: {'bandwidth': bandwidth}}
        self.update_ap_settings(setting_to_update)

    def set_channel_and_bandwidth(self, network, channel, bandwidth):
        """Function that sets network bandwidth/mode and channel.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            channel: string containing desired channel
            bandwidth: string containing mode, e.g. 11g, VHT20, VHT40, VHT80.
        """
        if 'bw' in bandwidth:
            bandwidth = bandwidth.replace('bw',
                                          self.capabilities['default_mode'])
        elif isinstance(bandwidth, int):
            bandwidth = str(bandwidth) + self.capabilities['default_mode']
        if bandwidth not in self.capabilities['modes'][network]:
            self.log.error('{} mode is not supported on {} interface.'.format(
                bandwidth, network))
        if channel not in self.capabilities['channels'][network]:
            self.log.error('Ch{} is not supported on {} interface.'.format(
                channel, network))
        setting_to_update = {
            network: {
                'bandwidth': bandwidth,
                'channel': channel
            }
        }
        self.update_ap_settings(setting_to_update)

    def set_power(self, network, power):
        """Function that sets network transmit power.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            power: string containing power level, e.g., 25%, 100%
        """
        if 'power' not in self.ap_settings[network].keys():
            self.log.error(
                'Cannot configure power on {} interface.'.format(network))
        setting_to_update = {network: {'power': power}}
        self.update_ap_settings(setting_to_update)

    def set_security(self, network, security_type, *password):
        """Function that sets network security setting and password.

        Args:
            network: string containing network identifier (2G, 5G_1, 5G_2)
            security: string containing security setting, e.g., WPA2-PSK
            password: optional argument containing password
        """
        if (len(password) == 1) and (type(password[0]) == str):
            setting_to_update = {
                network: {
                    'security_type': str(security_type),
                    'password': str(password[0])
                }
            }
        else:
            setting_to_update = {
                network: {
                    'security_type': str(security_type)
                }
            }
        self.update_ap_settings(setting_to_update)

    def set_rate(self):
        """Function that configures rate used by AP.

        Function implementation is not supported by most APs and thus base
        class raises exception if function not implemented in child class.
        """
        raise NotImplementedError

    def _update_settings_dict(self,
                              settings,
                              updates,
                              updates_requested=False,
                              status_toggle_flag=False):
        new_settings = copy.deepcopy(settings)
        for key, value in updates.items():
            if key not in new_settings.keys():
                raise KeyError('{} is an invalid settings key.'.format(key))
            elif isinstance(value, collections.abc.Mapping):
                new_settings[
                    key], updates_requested, status_toggle_flag = self._update_settings_dict(
                        new_settings.get(key, {}), value, updates_requested,
                        status_toggle_flag)
            elif new_settings[key] != value:
                new_settings[key] = value
                updates_requested = True
                if 'status' in key:
                    status_toggle_flag = True
        return new_settings, updates_requested, status_toggle_flag

    def update_ap_settings(self, dict_settings={}, **named_settings):
        """Function to update settings of existing AP.

        Function copies arguments into ap_settings and calls configure_retail_ap
        to apply them.

        Args:
            *dict_settings accepts single dictionary of settings to update
            **named_settings accepts named settings to update
            Note: dict and named_settings cannot contain the same settings.
        """
        settings_to_update = dict(dict_settings, **named_settings)
        if len(settings_to_update) != len(dict_settings) + len(named_settings):
            raise KeyError('The following keys were passed twice: {}'.format(
                (set(dict_settings.keys()).intersection(
                    set(named_settings.keys())))))

        self.ap_settings, updates_requested, status_toggle_flag = self._update_settings_dict(
            self.ap_settings, settings_to_update)

        if updates_requested:
            self.configure_ap(status_toggled=status_toggle_flag)

    def band_lookup_by_channel(self, channel):
        """Function that gives band name by channel number.

        Args:
            channel: channel number to lookup
        Returns:
            band: name of band which this channel belongs to on this ap, False
            if not supported
        """
        for key, value in self.capabilities['channels'].items():
            if channel in value:
                return key
        return False

    def _get_control_ip_address(self):
        """Function to get AP's Control Interface IP address."""
        if 'ssh_config' in self.ap_settings.keys():
            return self.ap_settings['ssh_config']['host']
        else:
            return self.ap_settings['ip_address']

    def _lock_ap(self):
        """Function to lock the ap while tests are running."""
        self.lock_file_path = '/tmp/{}_{}_{}.lock'.format(
            self.ap_settings['brand'], self.ap_settings['model'],
            self._get_control_ip_address())
        if not os.path.exists(self.lock_file_path):
            with open(self.lock_file_path, 'w'):
                pass
        self.lock_file = open(self.lock_file_path, 'r')
        start_time = time.time()
        self.log.info('Trying to acquire AP lock.')
        while time.time() < start_time + self.lock_timeout:
            try:
                fcntl.flock(self.lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                time.sleep(BROWSER_WAIT_SHORT)
                continue
            self.log.info('AP lock acquired.')
            return
        raise RuntimeError('Could not lock AP in time.')

    def _unlock_ap(self):
        """Function to unlock the AP when tests are done."""
        self.log.info('Releasing AP lock.')
        if hasattr(self, 'lock_file'):
            try:
                fcntl.flock(self.lock_file, fcntl.LOCK_UN)
                self.lock_file.close()
                self.log.info('Succussfully released AP lock file.')
            except:
                raise RuntimeError('Error occurred while unlocking AP.')
