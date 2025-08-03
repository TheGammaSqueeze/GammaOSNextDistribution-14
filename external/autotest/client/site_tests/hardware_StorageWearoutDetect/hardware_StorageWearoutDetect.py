# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging, os, re
from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error


class hardware_StorageWearoutDetect(test.test):
    """
    Check wear out status for storage device available in SMART for SSD and
    in ext_csd for eMMC version 5.0 or later. For previous version of eMMC,
    it will be treat as data not available.

    The test will be failed if:
    - At least one SMART variable has value under its threshold
      or
    - Percentage Used reported by SMART for NVMe or SATA is above 90
      or
    - eMMC wear out status variable is in 90-100% band or higher (
      DEVICE_LIFE_TIME_EST_TYP_A). Seeing this consistently means the lab
      device may have to be replaced.
    """

    version = 1
    STORAGE_INFO_PATH = '/var/log/storage_info.txt'
    STORAGE_INFO_COMMON_PATH = '/usr/share/misc/storage-info-common.sh'

    # Example     "SATA Version is: SATA 3.1, 6.0 Gb/s (current: 6.0 Gb/s)"
    SATA_DETECT = r"SATA Version is:.*"

    # Example     "   Extended CSD rev 1.7 (MMC 5.0)"
    MMC_DETECT = r"\s*Extended CSD rev.*MMC (?P<version>\d+.\d+)"

    # Example     "SMART/Health Information (NVMe Log 0x02, NSID 0xffffffff)"
    NVME_DETECT = r".*NVMe Log .*"

    # Field meaning and example line that have failing attribute
    # ID# ATTRIBUTE_NAME          FLAGS    VALUE WORST THRESH FAIL RAW_VALUE
    # 184 End-to-End_Error        PO--CK   001   001   097    NOW  135
    SSD_FAIL = r"""\s*(?P<param>\S+\s\S+)      # ID and attribute name
                   \s+[P-][O-][S-][R-][C-][K-] # flags
                   (\s+\d{3}){3}               # three 3-digits numbers
                   \s+NOW                      # fail indicator"""

    # We want to detect and fail if we see a non-zero value for either
    # attribute 160 Uncorrectable_Error_Cnt or attribute 187 Reported_Uncorrect
    # ID# ATTRIBUTE_NAME          FLAGS    VALUE WORST THRESH FAIL RAW_VALUE
    # 160 Uncorrectable_Error_Cnt -----    100   100   100     -   10
    SATA_FAIL = r"""\s*(?P<param>(160\s+Uncorrectable_Error_Cnt|
                    187\s+Reported_Uncorrect))
                    \s+[P-][O-][S-][R-][C-][K-]
                    (\s+\d{1,3}){3}
                    \s+(NOW|[-])
                    \s+[1-9][0-9]*"""

    # Ex "Pre EOL information [PRE_EOL_INFO: 0x02]"
    # 0x02 means Warning, consumed 80% of reserved blocks
    # 0x03 means Urgent
    MMC_FAIL = r".*(?P<param>PRE_EOL_INFO]?: 0x0[23])"

    # Ex Available Spare:                    100%
    # We want to fail when the available spare is below the
    # available spare threshold.
    NVME_SPARE = r"Available Spare:\s+(?P<param>\d{1,3})%"

    #Available Spare Threshold:          10%
    NVME_THRESH = r"Available Spare Threshold:\s+(?P<param>\d{1,3})%"

    def run_once(self, use_cached_result=True):
        """
        Run the test

        @param use_cached_result: Use the result that generated when machine
                                  booted or generate new one.
        """

        if not use_cached_result:
            if not os.path.exists(self.STORAGE_INFO_COMMON_PATH):
                msg = str('Test failed with error: %s not exist'
                          % self.STORAGE_INFO_COMMON_PATH)
                raise error.TestFail(msg)
            cmd = ' '.join(['. %s;' % (self.STORAGE_INFO_COMMON_PATH, ),
                            'get_storage_info'])
            utils.run(cmd, stdout_tee=open(self.STORAGE_INFO_PATH, 'w'),
                      stderr_tee=utils.TEE_TO_LOGS)

        # Check that storage_info file exist.
        if not os.path.exists(self.STORAGE_INFO_PATH):
            msg = str('Test failed with error: %s not exist'
                      % self.STORAGE_INFO_PATH)
            raise error.TestFail(msg)

        mmc_detect = False
        sata_detect = False
        legacy_mmc = False
        nvme_detect = False
        fail_msg = ''

        with open(self.STORAGE_INFO_PATH) as f:
            for line in f:
                m = re.match(self.SATA_DETECT, line)
                if m:
                    sata_detect = True
                    logging.info('Found SATA device')

                m = re.match(self.MMC_DETECT, line)
                if m:
                    version = m.group('version')
                    if float(version) < 5.0:
                        legacy_mmc = True
                    mmc_detect = True
                    logging.info('Found eMMC version %s', version)

                m = re.match(self.NVME_DETECT, line)
                if m:
                    nvme_detect = True
                    logging.info('Found NVMe device')

                m = re.match(self.SSD_FAIL, line, re.X)
                if m:
                    param = m.group('param')
                    fail_msg += 'SSD failure ' + param

                m = re.match(self.MMC_FAIL, line)
                if m:
                    param = m.group('param')
                    fail_msg += 'MMC failure ' + param

                m = re.match(self.SATA_FAIL, line, re.X)
                if m:
                    param = m.group('param')
                    fail_msg += 'SATA failure, attribute ' + param

                m = re.match(self.NVME_SPARE, line)
                if m:
                    # Check the next line for the available spare threshold.
                    # Fail if available spare is below the threshold.
                    spare = m.group('param')
                    nextLine = next(f)
                    nm = re.match(self.NVME_THRESH, nextLine)
                    if nm:
                        thresh = nm.group('param')
                        if int(spare) < int(thresh):
                            fail_msg += 'NVMe failure, Available Spare ' + \
                                        spare + '% below threshold ' + \
                                        thresh + '%'

        if not sata_detect and not mmc_detect and not nvme_detect:
            raise error.TestFail('Can not detect storage device.')

        if fail_msg:
            msg = 'Detected wearout parameter:%s' % fail_msg
            raise error.TestFail(msg)

        if legacy_mmc:
            msg = 'eMMC version %s detected. ' % version
            msg += 'Wearout attributes are supported in eMMC 5.0 and later.'
            logging.info(msg)
