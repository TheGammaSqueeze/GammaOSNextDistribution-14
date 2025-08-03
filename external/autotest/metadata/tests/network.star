
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'network/ChromeCellularEndToEnd',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_ChromeCellularEndToEnd.network_ChromeCellularEndToEnd',
        ),
        test_common.define_test(
            'network/ChromeCellularNetworkPresent',
            suites = ['network_ui'],
            main_package = '',
        ),
        test_common.define_test(
            'network/ChromeCellularNetworkProperties',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/ChromeCellularSmokeTest',
            suites = ['network_ui'],
            main_package = '',
        ),
        test_common.define_test(
            'network/ChromeWifiConfigure',
            suites = ['network_ui'],
            main_package = 'autotest_lib.client.site_tests.network_ChromeWifiConfigure.network_ChromeWifiConfigure',
        ),
        test_common.define_test(
            'network/ChromeWifiEndToEnd',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_ChromeWifiEndToEnd.network_ChromeWifiEndToEnd',
        ),
        test_common.define_test(
            'network/DhcpBrokenDefaultGateway',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpBrokenDefaultGateway.network_DhcpBrokenDefaultGateway',
        ),
        test_common.define_test(
            'network/DhcpClasslessStaticRoute',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpClasslessStaticRoute.network_DhcpClasslessStaticRoute',
        ),
        test_common.define_test(
            'network/DhcpFQDN',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpFQDN.network_DhcpFQDN',
        ),
        test_common.define_test(
            'network/DhcpFailureWithStaticIP',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpFailureWithStaticIP.network_DhcpFailureWithStaticIP',
        ),
        test_common.define_test(
            'network/DhcpMTU',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpMTU.network_DhcpMTU',
        ),
        test_common.define_test(
            'network/DhcpNak',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpNak.network_DhcpNak',
        ),
        test_common.define_test(
            'network/DhcpNegotiationSuccess',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpNegotiationSuccess.network_DhcpNegotiationSuccess',
        ),
        test_common.define_test(
            'network/DhcpNegotiationTimeout',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpNegotiationTimeout.network_DhcpNegotiationTimeout',
        ),
        test_common.define_test(
            'network/DhcpNonAsciiParameter',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpNonAsciiParameter.network_DhcpNonAsciiParameter',
        ),
        test_common.define_test(
            'network/DhcpRenew',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpRenew.network_DhcpRenew',
        ),
        test_common.define_test(
            'network/DhcpRenewWithOptionSubset',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpRenewWithOptionSubset.network_DhcpRenewWithOptionSubset',
        ),
        test_common.define_test(
            'network/DhcpRequestHostName',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpRequestHostName.network_DhcpRequestHostName',
        ),
        test_common.define_test(
            'network/DhcpStaticIP',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpStaticIP.network_DhcpStaticIP',
        ),
        test_common.define_test(
            'network/DhcpVendorEncapsulatedOptions',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_DhcpVendorEncapsulatedOptions.network_DhcpVendorEncapsulatedOptions',
        ),
        test_common.define_test(
            'network/DhcpWpadNegotiation',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_DhcpWpadNegotiation.network_DhcpWpadNegotiation',
        ),
        test_common.define_test(
            'network/Dhcpv6Basic',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_Dhcpv6Basic.network_Dhcpv6Basic',
        ),
        test_common.define_test(
            'network/DiskFull',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/EthCaps',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_EthCaps.network_EthCaps',
        ),
        test_common.define_test(
            'network/EthCapsServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/EthernetStressPlug',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_EthernetStressPlug.network_EthernetStressPlug',
        ),
        test_common.define_test(
            'network/FirewallHolePunch',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_FirewallHolePunch.network_FirewallHolePunch',
        ),
        test_common.define_test(
            'network/FirewallHolePunchServer',
            suites = ['network_nightly'],
            main_package = '',
        ),
        test_common.define_test(
            'network/Ipv6SimpleNegotiation',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_Ipv6SimpleNegotiation.network_Ipv6SimpleNegotiation',
        ),
        test_common.define_test(
            'network/NegotiatedLANSpeed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_NegotiatedLANSpeed.network_NegotiatedLANSpeed',
        ),
        test_common.define_test(
            'network/PortalStress',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/ProxyResolver',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_ProxyResolver.network_ProxyResolver',
        ),
        test_common.define_test(
            'network/RackWiFiConnect',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_RackWiFiConnect.network_RackWiFiConnect',
        ),
        test_common.define_test(
            'network/RestartShill',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/RoamWifiEndToEnd',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.network_RoamWifiEndToEnd.network_RoamWifiEndToEnd',
        ),
        test_common.define_test(
            'network/StressServoEthernetPlug',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFiCaps',
            suites = ['network_nightly'],
            main_package = 'autotest_lib.client.site_tests.network_WiFiCaps.network_WiFiCaps',
        ),
        test_common.define_test(
            'network/WiFiHECaps',
            suites = ['wifi_flaky'],
            main_package = 'autotest_lib.client.site_tests.network_WiFiHECaps.network_WiFiHECaps',
        ),
        test_common.define_test(
            'network/WiFiResume',
            suites = ['bvt-perbuild', 'network_nightly', 'wificell-cq'],
            main_package = 'autotest_lib.client.site_tests.network_WiFiResume.network_WiFiResume',
        ),
        test_common.define_test(
            'network/WiFi_AssocConfigPerformance',
            suites = ['wifi_flaky'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_BSSTMReq',
            suites = ['wifi_matfunc', 'wifi_release'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_BT_AntennaCoex',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_CSA',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_CSADisconnect',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ChannelHop',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ChannelScanDwellTime',
            suites = ['wifi_perf'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ChaosConfigFailure',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ChaosConfigSniffer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ChromeEndToEnd',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ConnectOnResume',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_DarkResumeActiveScans',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_DisableEnable',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_DisableRandomMACAddress',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_FastReconnectInDarkResume',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_GTK',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_HiddenRemains',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_HiddenScan',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_LinkMonitorFailure',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_MalformedProbeResp',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_Manual',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_MultiAuth',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_OverlappingBSSScan',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_PMKSACaching',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_PTK',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_Prefer5Ghz',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ProfileBasic',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ProfileGUID',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RandomMACAddress',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RateControl',
            suites = ['wifi_flaky'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_Reassociate',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_Reset',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RetryConnectHidden',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamDbus',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamDiagnostics',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamEndToEnd',
            suites = ['wifi_endtoend', 'wifi_release'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamNatural',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamSuspend',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_RoamSuspendEndToEnd',
            suites = ['wifi_endtoend'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_SSIDSwitchBack',
            suites = ['wifi_matfunc'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_ScanPerformance',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_SecChange',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_SetOptionalDhcpProperties',
            suites = ['wifi_matfunc', 'wificell-cq'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_SuspendTwice',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_UpdateRouter',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_VerifyAttenuator',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_VerifyRouter',
            suites = ['wifi_update_router'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_WakeOnDisconnect',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_WakeOnSSID',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_WakeOnWiFiThrottling',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WiFi_WoWLAN',
            suites = ['wifi_lucidsleep'],
            main_package = '',
        ),
        test_common.define_test(
            'network/WlanRegulatory',
            suites = ['wifi_matfunc'],
            main_package = 'autotest_lib.client.site_tests.network_WlanRegulatory.network_WlanRegulatory',
        )
    ]
