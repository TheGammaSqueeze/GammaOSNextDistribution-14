# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import re

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error


class firmware_LockedME(test.test):
    """Validates that the Management Engine has been locked."""
    # Needed by autotest
    version = 1

    # Temporary file to read BIOS image into. We run in a tempdir anyway, so it
    # doesn't need a path.
    BIOS_FILE = 'bios.bin'
    RANDOM_FILE = 'newdata'
    FLASHED_FILE = 'flasheddata'

    def flashrom(self, ignore_status=False, args=()):
        """Run flashrom, expect it to work. Fail if it doesn't"""
        extra = ['-p', 'host'] + list(args)
        return utils.run('flashrom', ignore_status=ignore_status, args=extra)

    def determine_spi_rom_wp_status(self):
        """Determine the AP SPI-ROM's write-protection status."""
        flashrom_result = self.flashrom(args=('--wp-status',))
        logging.info('The above flashrom command returns.... %s',
                flashrom_result.stdout)
        if (("disabled" in flashrom_result.stdout) and
                ("start=0x00000000, len=0x0000000" in flashrom_result.stdout)):
            return False
        else:
            return True

    def md5sum(self, filename):
        """Run md5sum on a file

        @param filename: Filename to sum
        @return: md5sum of the file as a 32-character hex string
        """
        r = utils.run('md5sum', ignore_status=False, args=[filename])
        return r.stdout.split()[0]

    def has_ME(self):
        """See if we can detect an ME.
        FREG* is printed only when HSFS_FDV is set, which means the descriptor
        table is valid. If we're running a BIOS without a valid descriptor this
        step will fail. Unfortunately, we don't know of a simple and reliable
        way to identify systems that have ME hardware.
        """
        logging.info('See if we have an ME...')
        r = self.flashrom(args=('-V',))
        return r.stdout.find("FREG0") >= 0

    def try_to_rewrite(self, sectname):
        """If we can modify the ME section, restore it and raise an error."""
        logging.info('Try to write section %s...', sectname)
        size = os.stat(sectname).st_size
        utils.run('dd', args=('if=/dev/urandom', 'of=%s' % (self.RANDOM_FILE),
                              'count=1', 'bs=%d' % (size)))
        self.flashrom(args=('-V', '-w', self.BIOS_FILE,
                            '-i' , '%s:%s' % (sectname, self.RANDOM_FILE),
                            '--noverify-all'),
                      ignore_status=True)
        self.flashrom(args=('-r',
                            '-i', '%s:%s' % (sectname, self.FLASHED_FILE)))
        md5sum_random = self.md5sum(filename=self.RANDOM_FILE)
        md5sum_flashed = self.md5sum(filename=self.FLASHED_FILE)
        if md5sum_random == md5sum_flashed:
            logging.info('Oops, it worked! Put it back...')
            self.flashrom(args=('-w', self.BIOS_FILE,
                                '-i', '%s:%s' % (sectname, sectname),
                                '--noverify-all'),
                          ignore_status=True)
            raise error.TestFail('%s is writable, ME is unlocked' % sectname)

    def check_manufacturing_mode(self):
        """Fail if manufacturing mode is not found or enbaled."""

        # See if coreboot told us that the ME is still in Manufacturing Mode.
        # It shouldn't be. We have to look only at the last thing it reports
        # because it reports the values twice and the first one isn't always
        # reliable.
        logging.info('Check for Manufacturing Mode...')
        last = None
        with open('/sys/firmware/log') as infile:
            for line in infile:
                if re.search('ME: Manufacturing Mode', line):
                    last = line
        if last is not None and last.find("YES") >= 0:
            raise error.TestFail("The ME is still in Manufacturing Mode")

    def check_region_inaccessible(self, sectname):
        """Test and ensure a region is not accessible by host CPU."""

        self.try_to_rewrite(sectname)

    def run_once(self, expect_me_present=True):
        """Fail unless the ME is locked.

        @param expect_me_present: False means the system has no ME.
        """
        cpu_arch = utils.get_cpu_arch()
        if cpu_arch == "arm":
            raise error.TestNAError('This test is not applicable, '
                    'because an ARM device has been detected. '
                    'ARM devices do not have an ME (Management Engine)')

        cpu_family = utils.get_cpu_soc_family()
        if cpu_family == "amd":
            raise error.TestNAError('This test is not applicable, '
                    'because an AMD device has been detected. '
                    'AMD devices do not have an ME (Management Engine)')

        # If the AP SPI-ROM is blocking writes to the ME regions, and the ME
        # regions are unlocked, they won't be writable, so will appear locked
        # (i.e. this will be a false PASS).
        if self.determine_spi_rom_wp_status():
            raise error.TestFail('Software wp is enabled on the AP\'s SPI-ROM, '
                'or a protected range is set.  Please disable software wp and '
                'clear the protected range prior to running this test.')

        # See if the system even has an ME, and whether we expected that.
        if self.has_ME():
            if not expect_me_present:
                raise error.TestFail('We expected no ME, but found one anyway')
        else:
            if expect_me_present:
                raise error.TestNAError("No ME found. That's probably wrong.")
            else:
                logging.info('We expected no ME and we have no ME, so pass.')
                return

        # Make sure manufacturing mode is off.
        self.check_manufacturing_mode()

        # Read the image using flashrom.
        self.flashrom(args=('-r', self.BIOS_FILE))

        # Use 'IFWI' fmap region as a proxy for a device which doesn't
        # have a dedicated ME region in the boot media.
        r = utils.run('dump_fmap', args=('-p', self.BIOS_FILE))
        is_IFWI_platform = r.stdout.find("IFWI") >= 0

        # Get the bios image and extract the ME components
        logging.info('Pull the ME components from the BIOS...')
        dump_fmap_args = ['-x', self.BIOS_FILE, 'SI_DESC']
        inaccessible_sections = []
        if is_IFWI_platform:
            inaccessible_sections.append('DEVICE_EXTENSION')
        else:
            inaccessible_sections.append('SI_ME')
        dump_fmap_args.extend(inaccessible_sections)
        utils.run('dump_fmap', args=tuple(dump_fmap_args))

        # So far, so good, but we need to be certain. Rather than parse what
        # flashrom tells us about the ME-related registers, we'll just try to
        # change the ME components. We shouldn't be able to.
        inaccessible_sections.append('SI_DESC')
        for sectname in inaccessible_sections:
            self.check_region_inaccessible(sectname)

        # Okay, that's about all we can try. Looks like it's locked.
