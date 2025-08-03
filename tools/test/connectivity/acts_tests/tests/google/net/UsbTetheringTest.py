from acts import asserts
from acts import base_test
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.net import net_test_utils as nutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from scapy.all import get_if_raw_hwaddr
from scapy.layers.dns import DNS, DNSQR
from scapy.layers.inet import IP, ICMP, UDP, TCP, RandShort, sr1
from scapy.layers.inet6 import IPv6, ICMPv6EchoRequest
from scapy.config import conf
from scapy.sendrecv import send, sendp, srp1
from scapy.layers.l2 import Ether
import time

DUMSYS_CMD = "dumpsys connectivity tethering"
UPSTREAM_WANTED_STRING = "Upstream wanted"
CURRENT_UPSTREAM_STRING = "Current upstream interface"
SSID = wutils.WifiEnums.SSID_KEY
GOOGLE_DNS_IP_ADDRESS = "8.8.8.8"
DEFAULT_DOMAIN_NAME = "www.google.com"
DEFAULT_DOMAIN_NAME_IPV4 = "ipv4.google.com"
DEFAULT_DOMAIN_NAME_IPV6 = "ipv6.google.com"


class UsbTetheringTest(base_test.BaseTestClass):
  """Tests for USB tethering.

  Prerequisite:
  1. Android phone should connect to the desktop with USB cable
  2. DUT should be able to connect to cellular network and Wi-Fi network
  3. Set the CAP_NET_RAW capability before run the test.
     e.g., `sudo setcap cap_net_raw=eip /usr/local/bin/act.py`
  """

  def setup_class(self):
    self.dut = self.android_devices[0]
    self.USB_TETHERED = False

    nutils.verify_lte_data_and_tethering_supported(self.dut)
    nutils.set_cap_net_raw_capability()
    req_params = ("wifi_network",)
    self.unpack_userparams(req_params)
    # Enable USB tethering and get the USB network interface
    iflist_before = nutils.get_if_list()
    serial = self.dut.device_info['serial']
    nutils.start_usb_tethering(self.dut)
    self.dut.recreate_services(serial)
    self.iface = nutils.wait_for_new_iface(iflist_before)
    conf.route6.resync()
    self.log.info("USB tethering interface: %s." % self.iface)
    if not self.check_upstream_ready():
      raise asserts.fail("Upstream interface is not active.")

  def teardown_class(self):
    nutils.stop_usb_tethering(self.dut)
    self.USB_TETHERED = False
    wutils.reset_wifi(self.dut)

  def on_fail(self, test_name, begin_time):
    self.dut.take_bug_report(test_name, begin_time)

  @test_tracker_info(uuid="d4da7695-4342-4564-b7b0-0a30895f23eb")
  def test_icmp_connectivity(self):
    """Tests connectivity under ICMP.

    Steps:
    1. Enable USB tethering on Android devices
    2. Generate ICMP packet and send to target IP address
    3. Verify that the response contains an ICMP layer
    """
    icmp = IP(dst=GOOGLE_DNS_IP_ADDRESS)/ICMP()
    resp = sr1(icmp, timeout=2, iface=self.iface)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(ICMP), "Failed to send ICMP: " + resp_msg)

  @test_tracker_info(uuid="0dc7d049-11bf-42f9-918a-263f4470a7e8")
  def test_icmpv6_connectivity(self):
    """Tests connectivity under ICMPv6.

    Steps:
    1. Enable USB tethering on Android devices
    2. Generate ICMPv6 echo request packet and send to target URL
    3. Verify that the response contains an IPv6 layer
    """
    icmpv6 = IPv6(dst=DEFAULT_DOMAIN_NAME_IPV6)/ICMPv6EchoRequest()
    resp = sr1(icmpv6, timeout=2, iface=self.iface)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(IPv6), "Failed to send ICMPv6: " + resp_msg)

  @test_tracker_info(uuid="34aaffb8-8dd4-4a1f-a158-2732b8df5e59")
  def test_dns_query_connectivity(self):
    """Tests connectivity of DNS query.

    Steps:
    1. Enable USB tethering on Android devices
    2. Generate DNS query and send to target DNS server
    3. Verify that the response contains a DNS layer
    """
    dnsqr = IP(dst=GOOGLE_DNS_IP_ADDRESS) \
            /UDP(sport=RandShort(), dport=53) \
            /DNS(rd=1, qd=DNSQR(qname=DEFAULT_DOMAIN_NAME))
    resp = sr1(dnsqr, timeout=2, iface=self.iface)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(DNS), "Failed to send DNS query: " + resp_msg)

  @test_tracker_info(uuid="b9bed0fa-3178-4456-92e0-736b3a8cc181")
  def test_tcp_connectivity(self):
    """Tests connectivity under TCP.

    Steps:
    1. Enable USB tethering on Android devices
    2. Generate TCP packet and send to target URL
    3. Verify that the response contains a TCP layer
    """
    tcp = IP(dst=DEFAULT_DOMAIN_NAME)/TCP(dport=[80, 443])
    resp = sr1(tcp, timeout=2, iface=self.iface)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(TCP), "Failed to send TCP packet:" + resp_msg)

  @test_tracker_info(uuid="5e2f31f4-0b18-44be-a1ba-d82bf9050996")
  def test_tcp_ipv6_connectivity(self):
    """Tests connectivity under IPv6.

    Steps:
    1. Enable USB tethering on Android devices
    2. Generate IPv6 packet and send to target URL (e.g., ipv6.google.com)
    3. Verify that the response contains an IPv6 layer
    """
    tcp_ipv6 = IPv6(dst=DEFAULT_DOMAIN_NAME_IPV6)/TCP(dport=[80, 443])
    resp = sr1(tcp_ipv6, timeout=2, iface=self.iface)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(IPv6), "Failed to send TCP packet over IPv6, resp: " + resp_msg)

  def _send_http_get(self, destination, interface):
    try:
        syn_ack = srp1(Ether() / IPv6(dst=destination) / TCP(dport=80, flags="S"), timeout=2, iface=interface)
        sendp(Ether() / IPv6(dst=destination) / TCP(dport=80, sport=syn_ack[TCP].dport,seq=syn_ack[TCP].ack, ack=syn_ack[TCP].seq + 1, flags='A'), iface=interface)
        http_get_str = "GET / HTTP/1.1\r\nHost:" + destination + "\r\nAccept-Encoding: gzip, deflate\r\n\r\n"
        req = Ether() / IPv6(dst=destination)/TCP(dport=80, sport=syn_ack[TCP].dport, seq=syn_ack[TCP].ack, ack=syn_ack[TCP].seq + 1, flags='A')/http_get_str
        return srp1(req, timeout=2, iface=interface, retry = 3, filter = "tcp port 80")
    except TypeError:
        self.log.warn("Packet is sent but no answer from the server %s." % destination)
        return None

  @test_tracker_info(uuid="96115afb-e0d3-40a8-8f04-b64cedc6588f")
  def test_http_connectivity(self):
    """Tests connectivity under HTTP.

    Steps:
    1. Enable USB tethering on Android devices
    2. Implement TCP 3-way handshake to simulate HTTP GET
    3. Verify that the 3-way handshake works and response contains a TCP layer
    """
    resp = None
    for _ in range(3):
      resp = self._send_http_get(DEFAULT_DOMAIN_NAME, self.iface)
      if resp:
        break
      time.sleep(3)
    resp_msg = resp.show(dump=True) if resp else "null"
    asserts.assert_true(
        resp and resp.haslayer(TCP), "Failed to send HTTP request, resp: " + resp_msg)

  @test_tracker_info(uuid="140a064b-1ab0-4a92-8bdb-e52dde03d5b8")
  def test_usb_tethering_over_wifi(self):
    """Tests connectivity over Wi-Fi.

    Steps:
    1. Connects to a Wi-Fi network
    2. Enable USB tethering
    3. Verifies Wi-Fi is preferred upstream over data connection
    """

    wutils.start_wifi_connection_scan_and_ensure_network_found(
        self.dut, self.wifi_network[SSID])
    wutils.wifi_connect(self.dut, self.wifi_network)
    wifi_network = self.dut.droid.connectivityGetActiveNetwork()
    self.log.info("wifi network %s" % wifi_network)

    self.USB_TETHERED = True
    self.real_hwaddr = get_if_raw_hwaddr(self.iface)

    output = self.dut.adb.shell(DUMSYS_CMD)
    for line in output.split("\n"):
      if UPSTREAM_WANTED_STRING in line:
        asserts.assert_true("true" in line, "Upstream interface is not active")
        self.log.info("Upstream interface is active")
      if CURRENT_UPSTREAM_STRING in line:
        asserts.assert_true("wlan" in line, "WiFi is not the upstream "
                            "interface")
        self.log.info("WiFi is the upstream interface")

  def check_upstream_ready(self, retry=3):
    """Check the upstream is activated

    Check the upstream is activated with retry
    """
    for i in range(0, retry):
      output = self.dut.adb.shell(DUMSYS_CMD)
      for line in output.split("\n"):
        if UPSTREAM_WANTED_STRING in line:
          if "true" in line:
            self.log.info("Upstream interface is active")
          elif i == retry:
            return False
    return True
