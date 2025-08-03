"""Class to configure wireless settings."""

import time

from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.openwrt_lib.network_settings import SERVICE_DNSMASQ
from acts.controllers.openwrt_lib.network_settings import ServiceManager


LEASE_FILE = "/tmp/dhcp.leases"
OPEN_SECURITY = "none"
PSK1_SECURITY = "psk"
PSK_SECURITY = "psk2"
WEP_SECURITY = "wep"
ENT_SECURITY = "wpa2"
OWE_SECURITY = "owe"
SAE_SECURITY = "sae"
SAEMIXED_SECURITY = "sae-mixed"
ENABLE_RADIO = "0"
DISABLE_RADIO = "1"
ENABLE_HIDDEN = "1"
RADIO_2G = "radio1"
RADIO_5G = "radio0"


class WirelessSettingsApplier(object):
  """Class for wireless settings.

  Attributes:
    ssh: ssh object for the AP.
    service_manager: Object manage service configuration
    wireless_configs: a list of
      acts.controllers.openwrt_lib.wireless_config.WirelessConfig.
    channel_2g: channel for 2G band.
    channel_5g: channel for 5G band.
  """

  def __init__(self, ssh, configs, channel_2g, channel_5g, radio_2g=RADIO_2G, radio_5g=RADIO_5G):
    """Initialize wireless settings.

    Args:
      ssh: ssh connection object.
      configs: a list of
        acts.controllers.openwrt_lib.wireless_config.WirelessConfig.
      channel_2g: channel for 2G band.
      channel_5g: channel for 5G band.
    """
    self.ssh = ssh
    self.service_manager = ServiceManager(ssh)
    self.wireless_configs = configs
    self.channel_2g = channel_2g
    self.channel_5g = channel_5g
    self.radio_2g = radio_2g
    self.radio_5g = radio_5g

  def apply_wireless_settings(self):
    """Configure wireless settings from a list of configs."""
    default_2g_iface = "default_" + self.radio_2g
    default_5g_iface = "default_" + self.radio_5g

    # set channels for 2G and 5G bands
    self.ssh.run("uci set wireless.%s.channel='%s'" % (self.radio_2g, self.channel_2g))
    self.ssh.run("uci set wireless.%s.channel='%s'" % (self.radio_5g, self.channel_5g))
    if self.channel_5g == 165:
      self.ssh.run("uci set wireless.%s.htmode='VHT20'" % self.radio_5g)
    elif self.channel_5g in [149,153,157,161,165]:
      self.ssh.run("iw reg set US")
    elif self.channel_5g == 132 or self.channel_5g == 136:
      self.ssh.run("iw reg set ZA")
      self.ssh.run("uci set wireless.%s.htmode='VHT40'" % self.radio_5g)

    if self.channel_2g == 13:
      self.ssh.run("iw reg set AU")

    # disable default OpenWrt SSID
    self.ssh.run("uci set wireless.%s.disabled='%s'" %
                 (default_2g_iface, DISABLE_RADIO))
    self.ssh.run("uci set wireless.%s.disabled='%s'" %
                 (default_5g_iface, DISABLE_RADIO))

    # Enable radios
    self.ssh.run("uci set wireless.%s.disabled='%s'" % (self.radio_2g, ENABLE_RADIO))
    self.ssh.run("uci set wireless.%s.disabled='%s'" % (self.radio_5g, ENABLE_RADIO))

    for config in self.wireless_configs:

      # configure open network
      if config.security == OPEN_SECURITY:
        if config.band == hostapd_constants.BAND_2G:
          self.ssh.run("uci set wireless.%s.ssid='%s'" %
                       (default_2g_iface, config.ssid))
          self.ssh.run("uci set wireless.%s.disabled='%s'" %
                       (default_2g_iface, ENABLE_RADIO))
          if config.hidden:
            self.ssh.run("uci set wireless.%s.hidden='%s'" %
                         (default_2g_iface, ENABLE_HIDDEN))
        elif config.band == hostapd_constants.BAND_5G:
          self.ssh.run("uci set wireless.%s.ssid='%s'" %
                       (default_5g_iface, config.ssid))
          self.ssh.run("uci set wireless.%s.disabled='%s'" %
                       (default_5g_iface, ENABLE_RADIO))
          if config.hidden:
            self.ssh.run("uci set wireless.%s.hidden='%s'" %
                         (default_5g_iface, ENABLE_HIDDEN))
        continue

      self.ssh.run("uci set wireless.%s='wifi-iface'" % config.name)
      if config.band == hostapd_constants.BAND_2G:
        self.ssh.run("uci set wireless.%s.device='%s'" % (config.name, self.radio_2g))
      else:
        self.ssh.run("uci set wireless.%s.device='%s'" % (config.name, self.radio_5g))
      self.ssh.run("uci set wireless.%s.network='%s'" %
                   (config.name, config.iface))
      self.ssh.run("uci set wireless.%s.mode='ap'" % config.name)
      self.ssh.run("uci set wireless.%s.ssid='%s'" %
                   (config.name, config.ssid))
      self.ssh.run("uci set wireless.%s.encryption='%s'" %
                   (config.name, config.security))
      if config.security == PSK_SECURITY or config.security == SAE_SECURITY\
              or config.security == PSK1_SECURITY\
              or config.security == SAEMIXED_SECURITY:
        self.ssh.run("uci set wireless.%s.key='%s'" %
                     (config.name, config.password))
      elif config.security == WEP_SECURITY:
        self.ssh.run("uci set wireless.%s.key%s='%s'" %
                     (config.name, config.wep_key_num, config.wep_key))
        self.ssh.run("uci set wireless.%s.key='%s'" %
                     (config.name, config.wep_key_num))
      elif config.security == ENT_SECURITY:
        self.ssh.run("uci set wireless.%s.auth_secret='%s'" %
                     (config.name, config.radius_server_secret))
        self.ssh.run("uci set wireless.%s.auth_server='%s'" %
                     (config.name, config.radius_server_ip))
        self.ssh.run("uci set wireless.%s.auth_port='%s'" %
                     (config.name, config.radius_server_port))
      if config.ieee80211w:
        self.ssh.run("uci set wireless.%s.ieee80211w='%s'" %
                     (config.name, config.ieee80211w))
      if config.hidden:
        self.ssh.run("uci set wireless.%s.hidden='%s'" %
                     (config.name, ENABLE_HIDDEN))

    self.ssh.run("uci commit wireless")
    self.ssh.run("cp %s %s.tmp" % (LEASE_FILE, LEASE_FILE))

  def cleanup_wireless_settings(self):
    """Reset wireless settings to default."""
    self.ssh.run("wifi down")
    self.ssh.run("rm -f /etc/config/wireless")
    self.ssh.run("wifi config")
    if self.channel_5g == 132:
      self.ssh.run("iw reg set US")
    self.ssh.run("cp %s.tmp %s" % (LEASE_FILE, LEASE_FILE))
    self.service_manager.restart(SERVICE_DNSMASQ)
    time.sleep(9)

