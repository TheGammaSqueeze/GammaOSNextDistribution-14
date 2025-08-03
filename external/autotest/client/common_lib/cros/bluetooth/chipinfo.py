# Lint as: python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from typing import NamedTuple


class ChipInfo(NamedTuple):
    """Checks vendor support for the specific chipsets."""
    aosp_support: bool
    msft_support: bool
    msft_ocf: int


_chip_info = {
        'MVL-8897': ChipInfo(False, False, 0),
        'MVL-8997': ChipInfo(False, False, 0),
        'QCA-6174A-5-USB': ChipInfo(False, False, 0),
        'QCA-6174A-3-UART': ChipInfo(False, False, 0),
        'QCA-WCN6856': ChipInfo(True, True, 0x0170),
        'WCN3991': ChipInfo(True, True, 0x0170),
        'WCN6750': ChipInfo(True, True, 0x0170),
        'Intel-AX200': ChipInfo(False, True, 0x001e),
        'Intel-AX201': ChipInfo(False, True, 0x001e),
        'Intel-AC9260': ChipInfo(False, True, 0x001e),
        'Intel-AC9560': ChipInfo(False, True, 0x001e),
        'Intel-AC7260': ChipInfo(False, False, 0),
        'Intel-AC7265': ChipInfo(False, False, 0),
        'Realtek-RTL8822C-USB': ChipInfo(True, False, 0),
        'Realtek-RTL8822C-UART': ChipInfo(True, False, 0),
        'Realtek-RTL8852A-USB': ChipInfo(True, False, 0),
        'Mediatek-MTK7921-USB': ChipInfo(True, True, 0x0130),
        'Mediatek-MTK7921-SDIO': ChipInfo(True, True, 0x0130)
}


def query(chip_name):
    """Returns chip info for the specific chipset name.

    @param chip_name: chipset name.

    @return: named tuple ChipInfo(aosp_support, msft_support, msft_ocf).
    """

    chip_info = _chip_info.get(chip_name)
    if chip_info is None:
        raise error.TestError('Chipset name %r does not exist, please update '
                              'the list of chipsets' % chip_name)
    return chip_info