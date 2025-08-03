"""Controller for Asus AXE11000 access point."""

import time
from acts import logger
from selenium import webdriver
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import Select

MOBLY_CONTROLLER_CONFIG_NAME = "AsusAXE11000AP"
ACTS_CONTROLLER_REFERENCE_NAME = "access_points"

# Access point UI parameters
USERNAME = "login_username"
PASSWORD = "login_passwd"
SIGN_IN_ID = "button"
APPLY_BUTTON = "apply_btn"
APPLY_BUTTON_ID = "applyButton"
WIRELESS_SETTINGS = "Advanced_Wireless_Content_menu"
GENERAL_TAB = "Advanced_Wireless_Content_tab"
PROFESSIONAL_TAB = "Advanced_WAdvanced_Content_tab"
HE_MODE_ID = "he_mode_field"
WL_UNIT = "wl_unit"
WL_11AX = "wl_11ax"
WL_RADIO = "wl_radio"
WL_CLOSED = "wl_closed"
RADIO = "radio"
BAND_2G_CHANNEL = "band0_channel"
BAND_5G_CHANNEL = "band1_channel"
BAND_6G_CHANNEL = "band2_channel"
BAND_2G_AUTH = "band0_auth_mode_x"
BAND_5G_AUTH = "band1_auth_mode_x"
BAND_6G_AUTH = "band2_auth_mode_x"
BAND_2G_SSID = "band0_ssid"
BAND_5G_SSID = "band1_ssid"
BAND_6G_SSID = "band2_ssid"
BAND_2G_PSK = "band0_wpa_psk"
BAND_5G_PSK = "band1_wpa_psk"
BAND_6G_PSK = "band2_wpa_psk"
BAND_2G_RAD_IP = "band0_radius_ipaddr"
BAND_5G_RAD_IP = "band1_radius_ipaddr"
BAND_2G_RAD_PORT = "band0_radius_port"
BAND_5G_RAD_PORT = "band1_radius_port"
BAND_2G_RAD_KEY = "band0_radius_key"
BAND_5G_RAD_KEY = "band1_radius_key"
SMART_CONNECT = "smartcon_enable_field"
BROWSER_WAIT_SHORT_TIMEOUT = 6
BROWSER_WAIT_TIMEOUT = 15
BROWSER_WAIT_LONG_TIMEOUT = 90
BROWSER_WAIT_VERY_LONG_TIMEOUT = 180

# Access point supported modes, channels
VALID_BANDS = ["2g", "5g", "6g"]
WL_BAND_VALUE = {"2g": "0", "5g": "1", "6g": "2"}
CHANNELS_2G = {
    0: "0",
    1: "1",
    2: "2",
    3: "3",
    4: "4",
    5: "5",
    6: "6",
    7: "7",
    8: "8",
    9: "9",
    10: "10",
    11: "11"
}
CHANNELS_5G = {
    0: "0",
    36: "36/160",
    40: "40/160",
    44: "44/160",
    48: "48/160",
    52: "52/160",
    56: "56/160",
    60: "60/160",
    64: "64/160",
    100: "100/160",
    104: "104/160",
    108: "108/160",
    112: "112/160",
    116: "116/160",
    120: "120/160",
    124: "124/160",
    128: "128/160",
    132: "132/80",
    136: "136/80",
    140: "140/80",
    144: "144/80",
    149: "149/80",
    153: "153/80",
    157: "157/80",
    161: "161/80",
    165: "165"
}
CHANNELS_6G = {
    0: "0",
    37: "6g37/160",
    53: "6g53/160",
    69: "6g69/160",
    85: "6g85/160",
    101: "6g101/160",
    117: "6g117/160",
    133: "6g133/160",
    149: "6g149/160",
    165: "6g165/160",
    181: "6g181/160",
    197: "6g197/160",
    213: "6g213/160"
}


def create(configs):
  """Creates ap controllers from a json config."""
  return [AsusAXE11000AP(c) for c in configs]


def destroy(aps):
  """Destroys a list of ap controllers."""
  for ap in aps:
    ap.reset_to_default_ap_settings()
    ap.driver.quit()


class AsusAXE11000AP(object):
  """Asus AXE11000 AccessPoint controller.

  Controller class for Asus AXE11000 6GHz AP. This class provides methods to
  configure the AP with different settings required for 11ax and 6GHz testing.
  The controller uses chrome webdriver to communicate with the AP.

  The controller object is initiated in the test class. The ACTS test runner
  calls this controller using the 'AsusAXE11000AP' keyword in the ACTS config
  file. The AP is reset to default settings and this is handled during the
  test teardown.

  Attributes:
    ip: IP address to reach the AP.
    port: Port numnber to reach the AP.
    protocol: Protcol to reach the AP (http/https).
    username: Username to login to the AP.
    password: Password to login to the AP.
    config_page: web url to login to the AP.
    ap_settings: AP settings configured at any given point.
    default_ap_settings: Default AP settings before running the tests.
    driver: chrome webdriver object to update the settings.
  """

  def __init__(self, config):
    """Initialize AP.

    Creates a chrome webdriver object based on the router parameters.
    The webdriver will login to the router and goes to the wireless settings
    page. This object will be used to change the router settings required for
    the test cases. Required parameters are <ip_address>, <port>, <protocol>,
    <admin_username> and <admin_password>.

    Url: <procotol>://<ip_address>:<port>/Main_Login.asp
    Login: <admin_username>/<admin_password>

    Args:
      config: dict, dictionary of router parameters required for webdriver.
    """
    self.ip = config["ip_address"]
    self.port = config["port"]
    self.protocol = config["protocol"]
    self.username = config["admin_username"]
    self.password = config["admin_password"]
    lambda_msg = lambda msg: "[AsusAXE11000AP|%s] %s" % (self.ip, msg)
    self.log = logger.create_logger(lambda_msg)
    self.ap_settings = {"2g": {}, "5g": {}, "6g": {},}
    self.config_page = (
        "{protocol}://{ip_address}:{port}/Main_Login.asp").format(
            protocol=self.protocol, ip_address=self.ip, port=self.port)
    self.chrome_options = Options()
    self.chrome_options.add_argument("--headless")
    self.chrome_options.add_argument("--no-sandbox")
    self.driver = webdriver.Chrome(options=self.chrome_options)
    self.driver.implicitly_wait(BROWSER_WAIT_TIMEOUT*2)
    self.driver.get(self.config_page)
    self.driver.find_element_by_name(USERNAME).send_keys(self.username)
    self.driver.find_element_by_name(PASSWORD).send_keys(self.password)
    self.driver.find_element_by_id(SIGN_IN_ID).click()
    self._wait_for_web_element(self.driver.find_element_by_id,
                               WIRELESS_SETTINGS)
    self.driver.find_element_by_id(WIRELESS_SETTINGS).click()
    self._wait_for_web_element(self.driver.find_element_by_id, SMART_CONNECT)
    self._update_ap_settings()
    self.default_ap_settings = self.ap_settings.copy()

  ### Helper methods ###

  def _wait_for_web_element(self,
                            find_element,
                            element,
                            attribute=None,
                            value=None):
    """Verifies click actions/selections work.

    Args:
      find_element: func(), webdriver method to call
      element: str, web element to look for. Ex: id, class, name
      attribute: str, attribute to get from a webelement
      value: str, verify attribute is set to the correct value

    Raises:
      ValueError: An error occurred if expected attribute not found.
    """
    curr_time = time.time()
    while time.time() < curr_time + BROWSER_WAIT_TIMEOUT*4:
      time.sleep(2)
      try:
        x = find_element(element)
        if attribute and str(value) not in x.get_attribute(attribute):
          raise ValueError("Attribute is not set to the right value")
        return
      except NoSuchElementException:
        pass
    raise ValueError("Failed to find web element: %s" % element)

  def _update_ap_settings_2g_band(self):
    """Read settings configured on 2g band.

    Parameters Updated:
      security type: open, wpa2-psk, wpa3-sae or wpa2-ent.
      ssid: SSID of the wifi network.
      password: password of the wifi network (if psk or sae network).
      radius server ip: Radius server IP addr (if ent network).
      radius server port: Radius server Port number (if ent network).
      radius server secret: Radius server secret (if ent network).
      channel: 2G band channel.
    """
    dict_2g = {}
    dict_2g["security"] = self.driver.find_element_by_name(
        BAND_2G_AUTH).get_attribute("value")
    dict_2g["SSID"] = self.driver.find_element_by_name(
        BAND_2G_SSID).get_attribute("value")
    if dict_2g["security"] == "psk2" or dict_2g["security"] == "sae":
      dict_2g["password"] = self.driver.find_element_by_name(
          BAND_2G_PSK).get_attribute("value")
    elif dict_2g["security"] == "wpa2":
      dict_2g["radius_ip_addr"] = self.driver.find_element_by_name(
          BAND_2G_RAD_IP).get_attribute("value")
      dict_2g["radius_port"] = self.driver.find_element_by_name(
          BAND_2G_RAD_PORT).get_attribute("value")
      dict_2g["radius_secret"] = self.driver.find_element_by_name(
          BAND_2G_RAD_KEY).get_attribute("value")
    channel_field = self._get_webdriver_elements_for_channels("2g")
    ch_val = self.driver.find_element_by_name(channel_field).get_attribute(
        "value")
    channel = 0
    for key, val in CHANNELS_2G.items():
      if val == ch_val:
        channel = key
        break
    self.ap_settings["2g"] = dict_2g.copy()
    self.ap_settings["2g"]["channel"] = channel

  def _update_ap_settings_5g_band(self):
    """Read settings configured on 5g band.

    Parameters Updated:
      security type: open, wpa2-psk, wpa3-sae or wpa2-ent.
      ssid: SSID of the wifi network.
      password: password of the wifi network (if psk or sae network).
      radius server ip: Radius server IP addr (if ent network).
      radius server port: Radius server Port number (if ent network).
      radius server secret: Radius server secret (if ent network).
      channel: 5G band channel.
    """
    dict_5g = {}
    dict_5g["security"] = self.driver.find_element_by_name(
        BAND_5G_AUTH).get_attribute("value")
    dict_5g["SSID"] = self.driver.find_element_by_name(
        BAND_5G_SSID).get_attribute("value")
    if dict_5g["security"] == "psk2" or dict_5g["security"] == "sae":
      dict_5g["password"] = self.driver.find_element_by_name(
          BAND_5G_PSK).get_attribute("value")
    elif dict_5g["security"] == "wpa2":
      dict_5g["radius_ip_addr"] = self.driver.find_element_by_name(
          BAND_5G_RAD_IP).get_attribute("value")
      dict_5g["radius_port"] = self.driver.find_element_by_name(
          BAND_5G_RAD_PORT).get_attribute("value")
      dict_5g["radius_secret"] = self.driver.find_element_by_name(
          BAND_5G_RAD_KEY).get_attribute("value")
    channel_field = self._get_webdriver_elements_for_channels("5g")
    ch_val = self.driver.find_element_by_name(channel_field).get_attribute(
        "value")
    channel = 0
    for key, val in CHANNELS_5G.items():
      if val == ch_val:
        channel = key
        break
    self.ap_settings["5g"] = dict_5g.copy()
    self.ap_settings["5g"]["channel"] = channel

  def _update_ap_settings_6g_band(self):
    """Read settings configured on 6g band.

    Parameters Updated:
      security type: wpa3-owe, wpa3-sae.
      ssid: SSID of the wifi network.
      password: password of the wifi network (if sae network).
      channel: 6G band channel.
    """
    dict_6g = {}
    dict_6g["security"] = self.driver.find_element_by_name(
        BAND_6G_AUTH).get_attribute("value")
    dict_6g["SSID"] = self.driver.find_element_by_name(
        BAND_6G_SSID).get_attribute("value")
    if dict_6g["security"] == "sae":
      dict_6g["password"] = self.driver.find_element_by_name(
          BAND_6G_PSK).get_attribute("value")
    channel_field = self._get_webdriver_elements_for_channels("6g")
    ch_val = self.driver.find_element_by_name(channel_field).get_attribute(
        "value")
    channel = 0
    for key, val in CHANNELS_6G.items():
      if val == ch_val:
        channel = key
        break
    self.ap_settings["6g"] = dict_6g.copy()
    self.ap_settings["6g"]["channel"] = channel

  def _update_ap_settings(self):
    """Read AP settings of 2G, 5G and 6G bands.

    This method reads the wifi network currently configured on any particular
    band. The settings are updated to self.ap_settings object.
    """
    self.driver.refresh()
    self._update_ap_settings_2g_band()
    self._update_ap_settings_5g_band()
    self._update_ap_settings_6g_band()

  def _get_webdriver_elements_for_channels(self, band):
    """Return webdriver elements for the band to configure channel.

    Args:
      band: str, Wifi band to configure. Ex: 2g, 5g, 6g.

    Returns:
      channel field for the specific band.
    """
    channel_field = BAND_2G_CHANNEL
    if band == "5g":
      channel_field = BAND_5G_CHANNEL
    elif band == "6g":
      channel_field = BAND_6G_CHANNEL
    return channel_field

  def _set_channel(self, band, channel):
    """Configure channel on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      channel: int, Channel to set.

    Raises:
      ValueError: An error occurred due to invalid band or configuration.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    if (band == "2g" and channel not in CHANNELS_2G) or (
        band == "5g" and
        channel not in CHANNELS_5G) or (band == "6g" and
                                        channel not in CHANNELS_6G):
      raise ValueError("Channel %s is not supported in band %s" %
                       (channel, band))
    channel_field = self._get_webdriver_elements_for_channels(band)
    channels_val_dict = CHANNELS_6G
    if band == "2g":
      channels_val_dict = CHANNELS_2G
    elif band == "5g":
      channels_val_dict = CHANNELS_5G
    channel = channels_val_dict[channel]

    # Set channel
    if self.driver.find_element_by_name(channel_field).get_attribute(
        "value") != channel:
      css_selector = "select[name=%s]" % channel_field
      Select(self.driver.find_element_by_css_selector(
          css_selector)).select_by_value(channel)
      time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)

  def _configure_personal_network(self, band, auth, ssid=None, password=None):
    """Configure wpa3 sae/wpa2 psk network on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      auth: str, WPA2 PSK or WPA3 SAE security.
      ssid: str, ssid of the wifi network.
      password: str, password of the wifi network.

    Raises:
      ValueError: An error occurred due to invalid band or configuration.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    if band == "6g" and auth == "psk2":
      raise ValueError("AP doesn't support WPA2 PSK on 6g band.")
    (auth_field, ssid_field,
     psk_field) = self._get_webdriver_elements_for_personal_auth(band)

    # configure personal network
    css_selector = "select[name=%s]" % auth_field
    Select(self.driver.find_element_by_css_selector(
        css_selector)).select_by_value(auth)
    time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)
    if ssid:
      self.driver.find_element_by_name(ssid_field).clear()
      self.driver.find_element_by_name(ssid_field).send_keys(ssid)
    if password:
      self.driver.find_element_by_name(psk_field).clear()
      self.driver.find_element_by_name(psk_field).send_keys(password)

  def _configure_open_owe_network(self, band, auth, ssid=None):
    """Configure wpa3 owe/open network on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      auth: str, WPA2 PSK or WPA3 SAE security.
      ssid: str, ssid of the wifi network.

    Raises:
      ValueError: An error occurred due to invalid band or configuration.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    if band == "6g" and auth == "open":
      raise ValueError("AP doesn't support open network on 6g band.")
    if (band == "2g" or band == "5g") and auth == "owe":
      raise ValueError("AP doesn't support OWE on 2g and 5g bands.")
    (auth_field, ssid_field,
     _) = self._get_webdriver_elements_for_personal_auth(band)

    # Configure wifi network
    css_selector = "select[name=%s]" % auth_field
    Select(self.driver.find_element_by_css_selector(
        css_selector)).select_by_value(auth)
    time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)
    if ssid:
      self.driver.find_element_by_name(ssid_field).clear()
      self.driver.find_element_by_name(ssid_field).send_keys(ssid)

  def _configure_wpa2_ent_network(self, band, radius_ip, radius_port,
                                  radius_secret, ssid=None):
    """Configure wpa2 ent network on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g.
      radius_ip: str, radius server ip addr.
      radius_port: str, radius server port number.
      radius_secret: str, radius server secret.
      ssid: str, ssid of the wifi network.

    Raises:
      ValueError: An error occurred due to invalid band or configuration.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    if band == "6g":
      raise ValueError("6GHz doesn't support enterprise network on this AP.")
    (auth_field, ssid_field,
     _) = self._get_webdriver_elements_for_personal_auth(band)
    (rad_ip_field, rad_port_field,
     rad_key_field) = self._get_webdriver_elements_for_ent_auth(band)

    # Set enterprise network
    css_selector = "select[name=%s]" % auth_field
    Select(self.driver.find_element_by_css_selector(
        css_selector)).select_by_value("wpa2")
    time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)
    if ssid:
      self.driver.find_element_by_name(ssid_field).clear()
      self.driver.find_element_by_name(ssid_field).send_keys(ssid)
    self.driver.find_element_by_name(rad_ip_field).clear()
    self.driver.find_element_by_name(rad_ip_field).send_keys(radius_ip)
    self.driver.find_element_by_name(rad_port_field).clear()
    self.driver.find_element_by_name(rad_port_field).send_keys(radius_port)
    self.driver.find_element_by_name(rad_key_field).clear()
    self.driver.find_element_by_name(rad_key_field).send_keys(radius_secret)

  def _get_webdriver_elements_for_personal_auth(self, band):
    """Return webdriver elements for the band to configure personal auth.

    Args:
      band: str, Wifi band to configure. Ex: 2g, 5g, 6g.

    Returns:
      tuple of auth, ssid, psk field for the band.
    """
    auth_field = BAND_2G_AUTH
    ssid_field = BAND_2G_SSID
    psk_field = BAND_2G_PSK
    if band == "5g":
      auth_field = BAND_5G_AUTH
      ssid_field = BAND_5G_SSID
      psk_field = BAND_5G_PSK
    elif band == "6g":
      auth_field = BAND_6G_AUTH
      ssid_field = BAND_6G_SSID
      psk_field = BAND_6G_PSK
    return (auth_field, ssid_field, psk_field)

  def _get_webdriver_elements_for_ent_auth(self, band):
    """Return webdriver elements for the band to configure ent auth.

    Args:
      band: str, Wifi band to configure. Ex: 2g, 5g, 6g.

    Returns:
      tuple of radius server IP, port, secret for the band.
    """
    rad_ip_field = BAND_2G_RAD_IP
    rad_port_field = BAND_2G_RAD_PORT
    rad_key_field = BAND_2G_RAD_KEY
    if band == "5g":
      rad_ip_field = BAND_5G_RAD_IP
      rad_port_field = BAND_5G_RAD_PORT
      rad_key_field = BAND_5G_RAD_KEY
    return (rad_ip_field, rad_port_field, rad_key_field)

  ### Methods to configure AP ###

  def set_channel_and_apply(self, band, channel):
    """Set channel for specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      channel: int, Channel to set.
    """
    # Go back to General tab in advanced settings
    self.driver.find_element_by_id(GENERAL_TAB).click()
    self._wait_for_web_element(self.driver.find_element_by_id, SMART_CONNECT)

    channel_field = self._get_webdriver_elements_for_channels(band)
    self._set_channel(band, channel)
    self.driver.find_element_by_id(APPLY_BUTTON_ID).click()
    time.sleep(BROWSER_WAIT_LONG_TIMEOUT)
    self._wait_for_web_element(self.driver.find_element_by_name,
                               channel_field, "value", channel)
    self._update_ap_settings()

  def get_configured_channel(self, band):
    """Get the channel configured on specific band.

    Args:
      band: str, Wifi band to check. Ex: eg, 5g, 6g.

    Returns:
      Channel configured on the band.

    Raises:
      ValueError: An error occurred due to invalid band.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    return self.ap_settings[band]["channel"]

  def configure_ap(self, network_dict):
    """Configure AP with settings for different bands.

    Args:
      network_dict: dict, dictionary that holds configuration for each band.
    """
    # Go back to General tab in advanced settings
    self.driver.refresh()
    self.driver.find_element_by_id(GENERAL_TAB).click()
    self._wait_for_web_element(self.driver.find_element_by_id, SMART_CONNECT)

    # configure wireless settings
    self.log.info("Network dictionary: %s" % network_dict)
    for band in network_dict:
      security = network_dict[band]["security"]
      ssid = network_dict[band]["SSID"] if "SSID" in network_dict[
          band] else None
      password = network_dict[band]["password"] if "password" in network_dict[
          band] else None
      if security == "open" or security == "owe":
        self._configure_open_owe_network(band, security, ssid)
      elif security == "psk2" or security == "sae":
        self._configure_personal_network(band, security, ssid, password)
      elif network_dict[band]["security"] == "wpa2":
        self._configure_wpa2_ent_network(
            band,
            network_dict[band]["radius_server_ip"],
            network_dict[band]["radius_server_port"],
            network_dict[band]["radius_server_secret"],
            ssid)

    for band in network_dict:
      if "channel" in network_dict[band]:
        self._set_channel(band, network_dict[band]["channel"])
    self.driver.find_element_by_id(APPLY_BUTTON_ID).click()
    time.sleep(BROWSER_WAIT_LONG_TIMEOUT)

    # update ap settings
    self._update_ap_settings()

    # configure hidden or 11ax mode
    for band in network_dict:
      apply_settings = False
      if "hidden" in network_dict[band]:
        res = self._configure_hidden_network(band, network_dict[band]["hidden"])
        apply_settings = apply_settings or res
      if "11ax" in network_dict[band]:
        res = self._configure_11ax_mode(band, network_dict[band]["11ax"])
        apply_settings = apply_settings or res
      if apply_settings:
        self.driver.find_element_by_id(APPLY_BUTTON).click()
        time.sleep(BROWSER_WAIT_VERY_LONG_TIMEOUT)

  def get_wifi_network(self, band):
    """Get wifi network configured on the AP for the specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.

    Returns:
      Wifi network as a dictionary.

    Raises:
      ValueError: An error occurred due to invalid band.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)
    wifi_network = {}
    wifi_network["SSID"] = self.ap_settings[band]["SSID"]
    if "password" in self.ap_settings[band]:
      wifi_network["password"] = self.ap_settings[band]["password"]
    security = self.ap_settings[band]["security"]
    if security == "sae" or security == "owe":
      wifi_network["security"] = security
    return wifi_network

  def _configure_hidden_network(self, band, val):
    """Configure hidden network for a specific band.

    Args:
      band: str, Wifi band to configure hidden network.
      val: str, String value to configure.

    Returns:
      True if settings applied, False if not.

    Raises:
      ValueError: An error occurred due to invalid band.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)

    # Go to Professional tab in advanced settings
    self.driver.find_element_by_id(PROFESSIONAL_TAB).click()
    self._wait_for_web_element(self.driver.find_element_by_id, HE_MODE_ID)

    # Select the requested band from the drop down menu
    css_selector = "select[name=%s]" % WL_UNIT
    Select(
        self.driver.find_element_by_css_selector(css_selector)).select_by_value(
            WL_BAND_VALUE[band])  # (TODO: gmoturu@) find if selection worked
    time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)

    # Configure hidden network
    state = True if val == "1" else False
    return_result = False
    if self.driver.find_element_by_name(WL_CLOSED).is_selected() != state:
      css_selector = "input[name='%s'][value='%s']" % (WL_CLOSED, val)
      self.driver.find_element_by_css_selector(css_selector).click()
      time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)
      return_result = True

    return return_result

  def configure_hidden_network_and_apply(self, band, state=True):
    """Configure hidden network for a specific band.

    Args:
      band: str, Wifi band to configure hidden network.
      state: bool, Set the wifi network as hidden if True, False if not.
    """
    val = "1" if state else "0"
    if self._configure_hidden_network(band, val):
      self.driver.find_element_by_id(APPLY_BUTTON).click()
      time.sleep(BROWSER_WAIT_VERY_LONG_TIMEOUT)
      if self.driver.find_element_by_name(WL_CLOSED).is_selected() != state:
        raise ValueError("Failed to configure hidden network on band: %s" % band)

      # Go back to General tab in advanced settings
      self.driver.find_element_by_id(GENERAL_TAB).click()
      self._wait_for_web_element(self.driver.find_element_by_id, SMART_CONNECT)

  def _configure_11ax_mode(self, band, val):
    """Configure 11ax mode on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      val: str, String value to configure.

    Returns:
      True if settings are applied, False if not.

    Raises:
      ValueError: An error occurred due to invalid band.
    """
    band = band.lower()
    if band not in VALID_BANDS:
      raise ValueError("Band %s is not valid" % band)

    # Go to Professional tab in advanced settings
    self.driver.find_element_by_id(PROFESSIONAL_TAB).click()
    self._wait_for_web_element(self.driver.find_element_by_id, HE_MODE_ID)

    # Select the requested band from the drop down menu
    css_selector = "select[name=%s]" % WL_UNIT
    Select(
        self.driver.find_element_by_css_selector(css_selector)).select_by_value(
            WL_BAND_VALUE[band])  # (TODO: gmoturu@) find if selection worked
    time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)

    # Configure 11ax
    return_result = False
    if self.driver.find_element_by_name(WL_11AX).get_attribute(
        "value") != val:
      css_selector = "select[name=%s]" % WL_11AX
      Select(self.driver.find_element_by_css_selector(
          css_selector)).select_by_value(val)
      time.sleep(BROWSER_WAIT_SHORT_TIMEOUT)
      return_result = True

    return return_result

  def configure_11ax_mode_and_apply(self, band, state=True):
    """Configure 11ax mode on a specific band.

    Args:
      band: str, Wifi band to check. Ex: 2g, 5g, 6g.
      state: bool, Enable 11ax if True, disable if False
    """
    val = "1" if state else "0"
    if self._configure_11ax_mode(band, val):
      self.driver.find_element_by_id(APPLY_BUTTON).click()
      time.sleep(BROWSER_WAIT_VERY_LONG_TIMEOUT)
      self._wait_for_web_element(self.driver.find_element_by_name, WL_11AX,
                                 "value", val)

      # Go back to General tab in advanced settings
      self.driver.find_element_by_id(GENERAL_TAB).click()
      self._wait_for_web_element(self.driver.find_element_by_id, SMART_CONNECT)

  def reset_to_default_ap_settings(self):
    """Reset AP to the default settings."""
    if self.default_ap_settings != self.ap_settings:
      self.configure_ap(self.default_ap_settings)

