# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.network import hostap_config
from autotest_lib.server.cros.network import perf_test_manager as perf_manager
"""
This file defines the expected throughput values that should be used with the network_WiFi_Perf.*
tests.

The expected throughput values depend on the following parameters:
1- The test type:
    a) TCP_BIDIRECTIONAL
    b) TCP_RX
    c) TCP_TX
    a) UDP_BIDIRECTIONAL
    b) UDP_RX
    c) UDP_TX
    Note: The thoughput is viewed from the DUT perspective:
        streaming to DUT = RX
        streaming from DUT = TX
        simultaneous TX + RX = BIDIERECTIONAL
2- The Connection mode:
    a) 80211n
    b) 80211ac
3- The channel width:
    a) 20 MHz
    b) 40 MHz
    c) 80 MHz
"""

expected_throughput_wifi = {
        perf_manager.PerfTestTypes.TEST_TYPE_TCP_BIDIRECTIONAL: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20: (0, 0),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (0, 0),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (0, 0)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80: (0, 0)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (0, 0),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40: (0, 0)
                }
        },
        perf_manager.PerfTestTypes.TEST_TYPE_UDP_BIDIRECTIONAL: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20: (0, 0),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (0, 0),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (0, 0)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80: (0, 0)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (0, 0),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40: (0, 0)
                }
        },
        perf_manager.PerfTestTypes.TEST_TYPE_TCP_RX: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20:
                        (61, 86),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (115, 166),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (115, 166)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80:
                        (200, 400)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (74, 103),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40:
                        (153, 221)
                }
        },
        perf_manager.PerfTestTypes.TEST_TYPE_TCP_TX: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20:
                        (61, 86),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (115, 166),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (115, 166)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80:
                        (200, 400)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (74, 103),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40:
                        (153, 221)
                }
        },
        perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20:
                        (72, 101),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (135, 195),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (135, 195)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80:
                        (347, 500)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (87, 121),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40:
                        (180, 260)
                }
        },
        perf_manager.PerfTestTypes.TEST_TYPE_UDP_TX: {
                hostap_config.HostapConfig.MODE_11N_PURE: {
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_20:
                        (72, 101),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_PLUS:
                        (135, 195),
                        hostap_config.HostapConfig.HT_CHANNEL_WIDTH_40_MINUS:
                        (135, 195)
                },
                hostap_config.HostapConfig.MODE_11AC_MIXED: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_80:
                        (347, 500)
                },
                hostap_config.HostapConfig.MODE_11AC_PURE: {
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_20:
                        (87, 121),
                        hostap_config.HostapConfig.VHT_CHANNEL_WIDTH_40:
                        (180, 260)
                }
        }
}


def get_expected_throughput_wifi(test_type, mode, channel_width):
    """returns the expected throughput for WiFi only performance tests.

    @param test_type: the PerfTestTypes test type.

    @param mode: the WiFi mode such as 80211n.

    @param channel_width: the channel width used in the test.

    @return a tuple of two integers (must,should) of the expected throughputs in Mbps.

    """
    if test_type in expected_throughput_wifi:
        if mode in expected_throughput_wifi[test_type]:
            if channel_width in expected_throughput_wifi[test_type][mode]:
                return expected_throughput_wifi[test_type][mode][channel_width]
    ret_mode = hostap_config.HostapConfig.VHT_NAMES[channel_width]
    if ret_mode is None:
        ret_mode = hostap_config.HostapConfig.HT_NAMES[channel_width]
    raise error.TestFail(
            'Failed to find the expected throughput from the key values, test type = %s, mode = %s, channel width = %s'
            % (test_type, mode, ret_mode))


"""These are special exceptions for specific boards that define the maximum
throughput in Mbps that we expect boards to be able to achieve. Generally, these
boards were qualified before the advent of platform throughput requirements, and
therefore are exempted from meeting certain requirements. Each board must be
annotated with a bug which includes the history on why the specific expectations
for that board.
"""
max_throughput_expectation_for_boards = {
        # caroline throughput results tracked in b:200743117.
        "caroline": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 200
        },
        # elm throughput results tracked in b:201806809.
        "elm": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_TX: 200,
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 300
        },
        # eve throughput results tracked in b:200743117.
        "eve": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 275
        },
        # kukui throughput results tracked in b:201807413.
        "kukui": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 300
        },
        # nami throughput results tracked in b:200743117.
        "nami": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 140
        },
        # trogdor throughput results tracked in b:201807655.
        "trogdor": {
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 250
        },
        # veyron_fievel throughput results tracked in b:199946512.
        "veyron_fievel": {
                perf_manager.PerfTestTypes.TEST_TYPE_TCP_TX: 130,
                perf_manager.PerfTestTypes.TEST_TYPE_TCP_RX: 70,
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_TX: 130,
                perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX: 130
        }
}


def get_board_max_expectation(test_type, board_name):
    """Returns the maximum throughput expectation for a given board in a given
    test type, or None if the board has no exception for that test type.

    @param test_type: the PerfTestTypes test type.
    @param board_name: string name of the board, as defined by
    SiteLinuxSystem.board field.

    @return integer value for maximum throughput expectation (in Mbps) for the
    given boardand test type, or None if the maximum is not defined.
    """
    board_maximums = max_throughput_expectation_for_boards.get(board_name)
    if not board_maximums:
        return None
    return board_maximums.get(test_type)
