# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A list of advertisements data for testing purpose."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from six.moves import range


ADVERTISEMENT1 = {
        'Path': '/org/bluez/test/advertisement1',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff01': [0x1a, 0x1b, 0x1c, 0x1d, 0x1e]
        },
        'ServiceUUIDs': ['180D', '180F'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9991': [0x11, 0x12, 0x13, 0x14, 0x15]
        },
        'ScanResponseData': {
                '0x16': [0xcd, 0xab] + list(range(1, 21))
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': 10
}

ADVERTISEMENT2 = {
        'Path': '/org/bluez/test/advertisement2',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff02': [0x2a, 0x2b, 0x2c, 0x2d, 0x2e]
        },
        'ServiceUUIDs': ['1821'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9992': [0x21, 0x22, 0x23, 0x24, 0x25]
        },
        'ScanResponseData': {
                '0x16': [0xcd, 0xab] + list(range(22, 42))
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': 7
}

ADVERTISEMENT3 = {
        'Path': '/org/bluez/test/advertisement3',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff03': [0x3a, 0x3b, 0x3c, 0x3d, 0x3e]
        },
        'ServiceUUIDs': ['1819', '180E'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9993': [0x31, 0x32, 0x33, 0x34, 0x35]
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': 4
}

ADVERTISEMENT4 = {
        'Path': '/org/bluez/test/advertisement4',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff04': [0x4a, 0x4b, 0x4c, 0x4d, 0x4e]
        },
        'ServiceUUIDs': ['1808', '1810'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9994': [0x41, 0x42, 0x43, 0x44, 0x45]
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': 1
}

ADVERTISEMENT5 = {
        'Path': '/org/bluez/test/advertisement5',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff05': [0x5a, 0x5b, 0x5c, 0x5d, 0x5e]
        },
        'ServiceUUIDs': ['1818', '181B'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9995': [0x51, 0x52, 0x53, 0x54, 0x55]
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': -2
}

ADVERTISEMENT6 = {
        'Path': '/org/bluez/test/advertisement6',
        'Type': 'peripheral',
        'ManufacturerData': {
                '0xff06': [0x6a, 0x6b, 0x6c, 0x6d, 0x6e]
        },
        'ServiceUUIDs': ['1820'],
        'SolicitUUIDs': [],
        'ServiceData': {
                '9996': [0x61, 0x62, 0x63, 0x64, 0x65]
        },
        'Discoverable': True,
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100,
        'TxPower': -5
}

NEARBY_BROADCAST_ADV = {
        'Path': '/org/bluez/test/advertisement7',
        'Type': 'broadcast',
        'ManufacturerData': {
                '0xFE2C': [0x6a, 0x6b, 0x6c, 0x6d, 0x6e]
        },
        'ServiceUUIDs': ['FE2C'],
        'SolicitUUIDs': [],
        'ServiceData': {
                'FE2C': [0x61, 0x62, 0x63, 0x64, 0x65]
        },
        'IncludeTxPower': True,
        'MinInterval': 100,
        'MaxInterval': 100
}


#
# Nearby Mediums Fast Advertisement requirement is to put Service UUIDs and
# flags in advertising payload, and 20 bytes of Service data in Scan response
#
NEARBY_MEDIUMS_FAST_ADV = {
        'Path': '/org/bluez/test/advertisement8',
        'Type': 'peripheral',
        'ServiceUUIDs': ['FEF3'],
        'ScanResponseData': {
                '0x16': [0xf3, 0xfe] + list(range(1, 21))
        },
        'MinInterval': 100,
        'MaxInterval': 100
}

ADVERTISEMENTS = [ADVERTISEMENT1, ADVERTISEMENT2, ADVERTISEMENT3,
                  ADVERTISEMENT4, ADVERTISEMENT5, ADVERTISEMENT6]
