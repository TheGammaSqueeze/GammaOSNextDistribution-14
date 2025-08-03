# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time
import re

from autotest_lib.client.common_lib import error

# Storage types supported
STORAGE_TYPE_SSD = 'ssd'
STORAGE_TYPE_NVME = 'nvme'
STORAGE_TYPE_MMC = 'mmc'

# Storage states supported
STORAGE_STATE_NORMAL = 'normal'
STORAGE_STATE_WARNING = 'warning'
STORAGE_STATE_CRITICAL = 'critical'

BADBLOCK_CHECK_RO = 'RO'
BADBLOCK_CHECK_RW = 'RW'


class StorageError(error.TestFail):
    """Custom error class to indicate is unsupported or unavailable
    detect storage info.
    """
    pass


class ConsoleError(error.TestFail):
    """Common error class for servod console-back control failures."""
    pass


class StorageStateValidator(object):
    """Class to detect types and state of the DUT storage.

    The class supporting SSD, NVME and MMC storage types.
    The state detection and set state as:
    - normal - drive in a good shape
    - warning - drive close to the worn out state by any metrics
    - critical - drive is worn out and has errors
    """

    def __init__(self, host):
        """Initialize the storage validator.

        @param host: cros_host object providing console access
                     for reading the target info.

        @raises ConsoleError: if cannot read info
        @raises StorageError: if info is not preset
        """
        self._host = host
        self._storage_type = None
        self._storage_state = None
        self._info = []

        if not self._host:
            raise StorageError('Host is not provided')

        self._read_storage_info()

    def _read_storage_info(self):
        """Reading the storage info from SMART

        The info will be located as collection of lines
        @raises StorageError: if no info provided or data unavailable
        """
        logging.info('Extraction storage info')
        command = '. /usr/share/misc/storage-info-common.sh; get_storage_info'
        cmd_result = self._host.run(command, ignore_status=True)
        if cmd_result.exit_status != 0:
            raise StorageError('receive error: %s;', cmd_result.stderr)

        if cmd_result.stdout:
            self._info = cmd_result.stdout.splitlines()
        if len(self._info) == 0:
            raise StorageError('Storage info is empty')

    def get_type(self):
        """Determine the type of the storage on the host.

        @returns storage type (SSD, NVME, MMC)

        @raises StorageError: if type not supported or not determine
        """
        if not self._storage_type:
            self._storage_type = self._get_storage_type()
        return self._storage_type

    def get_state(self, run_badblocks=None):
        """Determine the type of the storage on the host.

        @param run_badblocks: string key to run badblock check.
                                None - check if we can run it
                                "NOT" - do not run check
                                "RW" - run read-write if booted from USB
                                "RO"  - run read-only check
        @returns storage state (normal|warning|critical)

        @raises StorageError: if type not supported or state cannot
                            be determine
        """
        if not self._storage_state:
            storage_type = self.get_type()
            if storage_type == STORAGE_TYPE_SSD:
                self._storage_state = self._get_state_for_ssd()
            elif storage_type == STORAGE_TYPE_MMC:
                self._storage_state = self._get_state_for_mms()
            elif storage_type == STORAGE_TYPE_NVME:
                self._storage_state = self._get_state_for_nvme()
        if (run_badblocks != 'NOT'
                    and self._storage_state != STORAGE_STATE_CRITICAL
                    and self._support_health_profile()):
            # run badblocks if storage not in critical state
            # if bad block found then mark storage as bad
            logging.info('Trying run badblocks on device')
            dhp = self._host.health_profile
            usb_boot = self._host.is_boot_from_external_device()
            if run_badblocks is None:
                if _is_time_to_run_badblocks_ro(dhp):
                    run_badblocks = BADBLOCK_CHECK_RO
                if usb_boot and _is_time_to_run_badblocks_rw(dhp):
                    run_badblocks = BADBLOCK_CHECK_RW
            logging.debug('run_badblocks=%s', run_badblocks)
            if usb_boot and run_badblocks == BADBLOCK_CHECK_RW:
                self._run_read_write_badblocks_check()
                dhp.refresh_badblocks_rw_run_time()
                # RO is subclass of RW so update it too
                dhp.refresh_badblocks_ro_run_time()
            if run_badblocks == BADBLOCK_CHECK_RO:
                # SMART stats sometimes is not giving issue if blocks
                # bad for reading. So we run RO check.
                self._run_readonly_badblocks_check()
                dhp.refresh_badblocks_ro_run_time()
        return self._storage_state

    def _get_storage_type(self):
        """Read the info to detect type of the storage by patterns"""
        logging.info('Extraction storage type')
        # Example "SATA Version is: SATA 3.1, 6.0 Gb/s (current: 6.0 Gb/s)"
        sata_detect = r"SATA Version is:.*"

        # Example "   Extended CSD rev 1.7 (MMC 5.0)"
        mmc_detect = r"\s*Extended CSD rev.*MMC (?P<version>\d+.\d+)"

        # Example "SMART/Health Information (NVMe Log 0x02, NSID 0xffffffff)"
        nvme_detect = r".*NVMe Log .*"

        for line in self._info:
            if re.match(sata_detect, line):
                logging.info('Found SATA device')
                logging.debug('Found line => ' + line)
                return STORAGE_TYPE_SSD

            m = re.match(mmc_detect, line)
            if m:
                version = m.group('version')
                logging.info('Found eMMC device, version: %s', version)
                logging.debug('Found line => ' + line)
                return STORAGE_TYPE_MMC

            if re.match(nvme_detect, line):
                logging.info('Found NVMe device')
                logging.debug('Found line => ' + line)
                return STORAGE_TYPE_NVME
        raise StorageError('Storage type cannot be detect')

    def _get_state_for_ssd(self):
        """Read the info to detect state for SSD storage"""
        logging.info('Extraction metrics for SSD storage')
        # Field meaning and example line that have failing attribute
        # https://en.wikipedia.org/wiki/S.M.A.R.T.
        # ID# ATTRIBUTE_NAME     FLAGS    VALUE WORST THRESH FAIL RAW_VALUE
        # 184 End-to-End_Error   PO--CK   001   001   097    NOW  135
        ssd_fail = r"""\s*(?P<param>\S+\s\S+)      # ID and attribute name
                    \s+[P-][O-][S-][R-][C-][K-] # flags
                    (\s+\d{3}){3}               # three 3-digits numbers
                    \s+NOW                      # fail indicator"""

        ssd_relocate_sectors = r"""\s*\d\sReallocated_Sector_Ct
                    \s*[P-][O-][S-][R-][C-][K-] # flags
                    \s*(?P<value>\d{3}) # VALUE
                    \s*(?P<worst>\d{3}) # WORST
                    \s*(?P<thresh>\d{3})# THRESH
                    """
        # future optimizations: read GPL and determine persentage
        for line in self._info:
            if re.match(ssd_fail, line):
                logging.debug('Found fail line => ' + line)
                return STORAGE_STATE_CRITICAL

            m = re.match(ssd_relocate_sectors, line)
            if m:
                logging.info('Found critical line => ' + line)
                value = int(m.group('value'))
                # manufacture set default value 100,
                # if number started to grow then it is time to mark it
                if value > 100:
                    return STORAGE_STATE_WARNING
        return STORAGE_STATE_NORMAL

    def _get_state_for_mms(self):
        """Read the info to detect state for MMC storage"""
        logging.debug('Extraction metrics for MMC storage')
        # Ex:
        # Device life time type A [DEVICE_LIFE_TIME_EST_TYP_A: 0x01]
        # 0x00~9 means 0-90% band
        # 0x0a means 90-100% band
        # 0x0b means over 100% band
        mmc_fail_lev = r""".*(?P<param>DEVICE_LIFE_TIME_EST_TYP_.)]?:
                        0x0(?P<val>\S)""" #life time persentage

        # Ex "Pre EOL information [PRE_EOL_INFO: 0x01]"
        # 0x00 - not defined
        # 0x01 - Normal
        # 0x02 - Warning, consumed 80% of the reserved blocks
        # 0x03 - Urgent, consumed 90% of the reserved blocks
        mmc_fail_eol = r".*(?P<param>PRE_EOL_INFO.)]?: 0x0(?P<val>\d)"

        eol_value = 0
        lev_value = -1
        for line in self._info:
            m = re.match(mmc_fail_lev, line)
            if m:
                param = m.group('val')
                logging.debug('Found line for lifetime estimate => ' + line)
                if 'a' == param:
                    val = 100
                elif 'b' == param:
                    val = 101
                else:
                    val = int(param)*10
                if val > lev_value:
                    lev_value = val
                continue

            m = re.match(mmc_fail_eol, line)
            if m:
                param = m.group('val')
                logging.debug('Found line for end-of-life => ' + line)
                eol_value = int(param)
                break

        # set state based on end-of-life
        if eol_value == 3:
            return STORAGE_STATE_CRITICAL
        elif eol_value == 2:
            return STORAGE_STATE_WARNING
        elif eol_value == 1:
            return STORAGE_STATE_NORMAL

        # set state based on life of estimates
        elif lev_value < 90:
            return STORAGE_STATE_NORMAL
        elif lev_value < 100:
            return STORAGE_STATE_WARNING
        return STORAGE_STATE_CRITICAL

    def _get_state_for_nvme(self):
        """Read the info to detect state for NVMe storage"""
        logging.debug('Extraction metrics for NVMe storage')
        # Ex "Percentage Used:         100%"
        nvme_fail = r"Percentage Used:\s+(?P<param>(\d{1,3}))%"
        used_value = -1
        for line in self._info:
            m = re.match(nvme_fail, line)
            if m:
                param = m.group('param')
                logging.debug('Found line for usage => ' + line)
                try:
                    val = int(param)
                    used_value = val
                except ValueError as e:
                    logging.info('Could not cast: %s to int ', param)
                break

        if used_value < 91:
            return STORAGE_STATE_NORMAL
        # Stop mark device as bad when they reached 100% usage
        # TODO(otabek) crbug.com/1140507 re-evaluate the max usage
        return STORAGE_STATE_WARNING

    def _get_device_storage_path(self):
        """Find and return the path to the device storage.

        Method support detection even when the device booted from USB.

        @returns path to the main device like '/dev/XXXX'
        """
        # find the name of device storage
        cmd = ('. /usr/sbin/write_gpt.sh;'
               ' . /usr/share/misc/chromeos-common.sh;'
               ' load_base_vars; get_fixed_dst_drive')
        cmd_result = self._host.run(cmd,
                                    ignore_status=True,
                                    timeout=60)
        if cmd_result.exit_status != 0:
            logging.debug('Failed to detect path to the device storage')
            return None
        return cmd_result.stdout.strip()

    def _run_readonly_badblocks_check(self):
        """Run backblocks readonly verification on device storage.

        The blocksize set as 512 based.
        """
        path = self._get_device_storage_path()
        if not path:
            # cannot continue if storage was not detected
            return
        logging.info("Running readonly badblocks check; path=%s", path)
        cmd = 'badblocks -e 1 -s -b 512 %s' % path
        try:
            # set limit in 1 hour but expecting to finish it up 30 minutes
            cmd_result = self._host.run(cmd, ignore_status=True, timeout=3600)
            if cmd_result.exit_status != 0:
                logging.debug('Failed to detect path to the device storage')
                return
            result = cmd_result.stdout.strip()
            if result:
                logging.debug("Check result: '%s'", result)
                # So has result is Bad and empty is Good.
                self._storage_state = STORAGE_STATE_CRITICAL
        except Exception as e:
            if 'Timeout encountered:' in str(e):
                logging.info('Timeout during running action')
            logging.debug(str(e))

    def _run_read_write_badblocks_check(self):
        """Run non-destructive read-write check on device storage.

        The blocksize set as 512 based.
        We can run this test only when DUT booted from USB.
        """
        path = self._get_device_storage_path()
        if not path:
            # cannot continue if storage was not detected
            return
        logging.info("Running read-write badblocks check; path=%s", path)
        cmd = 'badblocks -e 1 -nsv -b 4096 %s' % path
        try:
            # set limit in 90 minutes but expecting to finish it up 50 minutes
            cmd_result = self._host.run(cmd, ignore_status=True, timeout=5400)
            if cmd_result.exit_status != 0:
                logging.debug('Failed to detect path to the device storage')
                return
            result = cmd_result.stdout.strip()
            if result:
                logging.debug("Check result: '%s'", result)
                # So has result is Bad and empty is Good.
                self._storage_state = STORAGE_STATE_CRITICAL
        except Exception as e:
            if 'Timeout encountered:' in str(e):
                logging.info('Timeout during running action')
            logging.info('(Not critical) %s', e)

    def _support_health_profile(self):
        return (hasattr(self._host, 'health_profile')
                and self._host.health_profile)


def _is_time_to_run_badblocks_ro(dhp):
    """Verify that device can proceed to run read-only badblocks check.
    The RO check can be executed not often then one per 6 days.

    @returns True if can proceed, False if not
    """
    today_time = int(time.time())
    last_check = dhp.get_badblocks_ro_run_time_epoch()
    can_run = today_time > (last_check + (6 * 24 * 60 * 60))
    if not can_run:
        logging.info(
                'Run RO badblocks not allowed because we have run it recently,'
                ' last run %s. RO check allowed to run only once per 6 days',
                dhp.get_badblocks_ro_run_time())
    return can_run


def _is_time_to_run_badblocks_rw(dhp):
    """Verify that device can proceed to run read-write badblocks check.
    The RW check can be executed not often then one per 60 days.

    @returns True if can proceed, False if not
    """
    today_time = int(time.time())
    last_check = dhp.get_badblocks_rw_run_time_epoch()
    can_run = today_time > (last_check + (60 * 24 * 60 * 60))
    if not can_run:
        logging.info(
                'Run RW badblocks not allowed because we have run it recently,'
                ' last run %s. RW check allowed to run only once per 60 days',
                dhp.get_badblocks_rw_run_time())
    return can_run
