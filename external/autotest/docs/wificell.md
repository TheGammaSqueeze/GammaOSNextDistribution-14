# Running WiFi tests

Most WiFi tests specify `DEPENDENCIES = 'wificell'` in their control file,
which means they require not only an autotest server and a DUT, but also a
special test-enabled Access Point (AP). Additionally, some tests require a
packet capture (pcap) device or a signal attenuator. For instructions on how
to set up a wifcell for testing, visit [Setting up a WiFi Test Cell](https://chromeos.google.com/partner/dlm/docs/component-qual/settinguptestcell.html).

The basics of running a wificell autotest are the same as any other, except
that autotest also needs to know where to find your test AP. For some
configurations, this is sufficient:

```bash
# Run a 5HT40 test with DUT at 'my-host' and AP at 'my-host-router'.
test_that my-host network_WiFi_SimpleConnect.wifi_check5HT40
````

This works for most of the ChromeOS lab WiFi cells, where we configure DNS to
pair a DUT at address `${HOST}` with its companion AP at an address
`${HOST}-router`. See below for more info on addressing your test AP.

## What is a test AP?

A test AP can come in various forms, but as of this writing, it is typically a
ChromeOS based router / access point such as Whirlwind or Gale, running a
testbed-ap variant of a ChromeOS test image in Developer Mode. We have
previously supported other consumer routers, running OpenWRT. Setting up a test
AP is not in the scope for this document.

The key purpose of a test AP is to run a variety of [hostapd] instances, such
that we can test our DUTs using different PHY, cipher, etc., configurations.

In autotest, a test AP is represented by a `LinuxRouter` object, in
[site\_linux\_router].

## What suites should I run?

There are a variety of WiFi-related suites, but developers are commonly
interested in the functionality (`wifi_matfunc`) and performance (`wifi_perf`)
suites.

## Configuring DNS entries for test APs

Autotest assumes that if you have a DUT at address `${HOST}`, then your AP is
at an address `${HOST}-router` (see [dnsname\_mangler]). This is configured
automatically by the lab team for most ChromeOS lab WiFi setups.

For custom/local testing without modifying your DNS server, one can accomplish
this by adding entries to your `/etc/hosts` file. Alternatively, you can supply
the `router_addr=` and `pcap_addr=` arguments to autotest. For example:

```bash
# DUT at 'my-host', AP at 'my-other-router', and PCAP at 'my-other-pcap'
test_that --args="router_addr=my-other-router pcap_addr=my-other-pcap" \
        my-host suite:wifi_matfunc
```

If the test is using
[Tast](https://chromium.googlesource.com/chromiumos/platform/tast/) instead of
autotest, you can pass the `router` and `pcap` arguments to `tast run` instead:

```bash
# DUT at 'my-host', AP at 'my-other-router', and PCAP at 'my-other-pcap'
tast run -var="router=my-other-router" -var="pcap=my-other-pcap" my-host \
        wifi.ChannelHop
```

Also, note that if a pcap device isn't found at `${HOST}-pcap`, then we often
can utilize the test AP to capture packets as well. The test framework does
this by creating one or more monitor-mode interfaces in addition to the AP-mode
interface(s) normally used for tests. Note that 802.11 radios cannot both
transmit and receive at the same time, so this mode operates with slightly
degraded functionality. In particular, while a typical mac80211-based AP driver
can capture many aspects of its own transmitted frames (e.g., 802.11 headers
are constructed in software), it cannot monitor how those frames really look
over the air, so it will likely be missing most physical-layer information
(e.g., bitrates, modulation, frequency) or firmware-controlled behaviors (e.g.,
802.11 ACKs).

For example, consider the following AP + monitor capture, filtered for
[AP-transmitted frames](https://screenshot.googleplex.com/DWSaResO583) and
[AP-received frames](https://screenshot.googleplex.com/5EsZvbBpKEc) (links are
Google-internal). While the AP-transmitted frames contain 802.11 header
information like MAC-layer addresses and sequence numbers, only the received
frames contain information like frequency and bitrate. As such, if you need
this sort of information for debugging your tests, ensure you are using a
dedicated pcap device. Note that all supported tests should support running in
either configuration.

[dnsname\_mangler]: ../server/cros/dnsname_mangler.py
[hostapd]: https://w1.fi/hostapd/
[site\_linux\_router]: ../server/site_linux_router.py
