# Lint as: python2, python3
# Copyright (c) 2010 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Note: this test has been ported to hardware.MemCheck Tast test.
# Any change made here should be applied to the Tast test as well.

__author__ = 'kdlucas@chromium.org (Kelly Lucas)'

import logging
import re

from autotest_lib.client.bin import utils
from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error


class platform_MemCheck(test.test):
    """
    Verify memory usage looks correct.
    """
    version = 1
    swap_disksize_file = '/sys/block/zram0/disksize'

    def run_once(self):
        errors = 0
        keyval = dict()
        # The total memory will shrink if the system bios grabs more of the
        # reserved memory. We derived the value below by giving a small
        # cushion to allow for more system BIOS usage of ram. The memref value
        # is driven by the supported netbook model with the least amount of
        # total memory.  ARM and x86 values differ considerably.
        cpuType = utils.get_cpu_arch()
        memref = 986392
        vmemref = 102400
        if cpuType == "arm":
            memref = 700000
            vmemref = 210000

        os_reserve_min = 600000
        os_reserve_ratio = 0.04

        # size reported in /sys/block/zram0/disksize is in byte
        swapref = int(utils.read_one_line(self.swap_disksize_file)) / 1024

        less_refs = ['MemTotal', 'VmallocTotal']
        approx_refs = ['SwapTotal']

        # read physical HW size from mosys and adjust memref if need
        cmd = 'mosys memory spd print geometry -s size_mb'
        phy_size_run = utils.run(cmd)
        logging.info('Ran command: `%s`', cmd)
        logging.info('Output: "%s"', phy_size_run.stdout)
        phy_size = 0
        for line in phy_size_run.stdout.split():
            phy_size += int(line)
        # memref is in KB but phy_size is in MB
        phy_size *= 1024
        keyval['PhysicalSize'] = phy_size

        # scale OS reserve size with memory size
        os_reserve = max(os_reserve_min, int(phy_size * os_reserve_ratio))
        memref = max(memref, phy_size - os_reserve)

        ref = {'MemTotal': memref,
               'SwapTotal': swapref,
               'VmallocTotal': vmemref,
              }

        board = utils.get_board()
        logging.info('board: %s, phy_size: %d memref: %d',
                      board, phy_size, memref)

        error_list = []

        for k in ref:
            value = utils.read_from_meminfo(k)
            keyval[k] = value
            if k in less_refs:
                if value < ref[k]:
                    logging.warning('%s is %d', k, value)
                    logging.warning('%s should be at least %d', k, ref[k])
                    errors += 1
                    error_list += [k]
            elif k in approx_refs:
                if value < ref[k] * 0.9 or ref[k] * 1.1 < value:
                    logging.warning('%s is %d', k, value)
                    logging.warning('%s should be within 10%% of %d', k, ref[k])
                    errors += 1
                    error_list += [k]

        # Log memory type
        cmd = 'mosys memory spd print type -s dram | head -1'
        # Example
        # 0 | LPDDR4
        mem_type = utils.run(cmd).stdout.strip()
        logging.info('Ran command: `%s`', cmd)
        logging.info('Output: "%s"', mem_type)

        # key name timing_dimm_0 for backward compatibility with older test.
        keyval['timing_dimm_0'] = mem_type

        # Log memory ids
        cmd = 'mosys memory spd print id'
        # result example (1 module of memory per result line)
        # 0 | 1-45: SK Hynix (Hyundai) | 128d057e | HMT425S6CFR6A-PB
        # 1 | 1-45: SK Hynix (Hyundai) | 121d0581 | HMT425S6CFR6A-PB
        mem_ids = utils.run(cmd)
        logging.info('Ran command: `%s`', cmd)
        logging.info('Output: "%s"', mem_ids.stdout)

        mem_ids_list = [line for line in mem_ids.stdout.split('\n') if line]
        keyval['number_of_channel'] = len(mem_ids_list)

        for dimm, line in enumerate(mem_ids_list):
            keyval['memory_id_dimm_%d' % dimm] = line

        if board.startswith('rambi') or board.startswith('expresso'):
            logging.info('Skipping test on rambi and expresso, '
                         'see crbug.com/411401')
        elif errors > 0:
            # If self.error is not zero, there were errors.
            error_list_str = ', '.join(error_list)
            raise error.TestFail('Found incorrect values: %s' % error_list_str)

        keyval['cpu_name'] = utils.get_cpu_name()

        # Log memory type
        cmd = 'dmidecode -t memory'
        mem_dmi = utils.run(cmd)
        logging.info('Ran command: `%s`', cmd)
        logging.info('Output: "%s"', mem_dmi.stdout)

        pattern = r'\s*Speed: (?P<speed>\d+) MT/s'
        for line in mem_dmi.stdout.split('\n'):
            match = re.match(pattern, line)
            if match:
                keyval['speed'] = match.group('speed')
                break
        else:
            keyval['speed'] = 'N/A'

        self.write_perf_keyval(keyval)
