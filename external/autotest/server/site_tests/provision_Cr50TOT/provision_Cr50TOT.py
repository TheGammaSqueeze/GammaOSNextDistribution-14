# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


""" The autotest performing Cr50 update to the TOT image."""


import logging
import os

from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.client.common_lib import error
from autotest_lib.server import utils
from autotest_lib.server.cros import gsutil_wrapper
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


# TOT cr50 images are built as part of the reef image builder.
BUILDER = 'reef'
GS_URL = 'gs://chromeos-releases/dev-channel/' + BUILDER
# Firmware artifacts are stored in files like this.
#   ChromeOS-firmware-R79-12519.0.0-reef.tar.bz2
FIRMWARE_NAME = 'ChromeOS-firmware-%s-%s.tar.bz2'
REMOTE_TMPDIR = '/tmp/cr50_tot_update'
CR50_IMAGE_PATH = 'cr50/ec.bin'
# Wait 10 seconds for the update to take effect.
class provision_Cr50TOT(FirmwareTest):
    """Update cr50 to TOT.

    The reef builder builds cr50. Fetch the image from the latest reef build
    and update cr50 to that image. This expects that the DUT is running node
    locked RO.
    """
    version = 1

    def get_latest_builds(self, board='reef-release',
                          bucket='chromeos-image-archive',
                          num_builds=5):
        """Gets the latest build for the given board.

        Args:
          board: The board for which the latest build needs to be fetched.
          bucket: The GS bucket name.
          num_builds: Number of builds to return.

        Raises:
          error.TestFail() if the List() method is unable to retrieve the
              contents of the path gs://<bucket>/<board> for any reason.
        """
        path = 'gs://%s/%s' % (bucket, board)
        cmd = 'gsutil ls -- %s' % path
        try:
            contents = utils.system_output(cmd).splitlines()
            latest_contents = contents[(num_builds * -1):]
            latest_builds = []
            for content in latest_contents:
                latest_builds.append(content.strip(path).strip('/'))
            latest_builds.reverse()
            logging.info('Checking latest builds %s', latest_builds)
            return latest_builds
        except Exception as e:
            raise error.TestFail('Could not determine the latest build due '
                                 'to exception: %s' % e)

    def get_cr50_build(self, latest_ver, remote_dir):
        """Download the TOT cr50 image from the reef artifacts."""
        bucket = os.path.join(GS_URL, latest_ver.split('-')[-1])
        filename = FIRMWARE_NAME % (latest_ver, BUILDER)
        logging.info('Using cr50 image from %s', latest_ver)

        # Download the firmware artifacts from google storage.
        gsutil_wrapper.copy_private_bucket(host=self.host,
                                           bucket=bucket,
                                           filename=filename,
                                           destination=remote_dir)

        # Extract the cr50 image.
        dut_path = os.path.join(remote_dir, filename)
        result = self.host.run('tar xfv %s -C %s' % (dut_path, remote_dir))
        return os.path.join(remote_dir, CR50_IMAGE_PATH)


    def get_latest_cr50_build(self):
        self.host.run('mkdir -p %s' % (REMOTE_TMPDIR))
        latest_builds = self.get_latest_builds()
        for latest_build in latest_builds:
            try:
                return self.get_cr50_build(latest_build, REMOTE_TMPDIR)
            except Exception as e:
                logging.warning('Unable to find %s cr50 image %s',
                                latest_build, e)
        raise error.TestFail('Unable to find latest cr50 image in %s' %
                             latest_builds)


    def run_once(self, host, force=False):
        """Update cr50 to the TOT image from the reef builder."""
        # TODO(mruthven): remove once the test is successfully scheduled.
        logging.info('SUCCESSFULLY SCHEDULED PROVISION CR50 TOT UPDATE')
        if not force:
            logging.info('skipping update')
            return
        logging.info('cr50 version %s', host.servo.get('cr50_version'))
        self.host = host
        cr50_path = self.get_latest_cr50_build()
        logging.info('cr50 image is at %s', cr50_path)
        local_path = os.path.join(self.resultsdir, 'cr50.bin.tot')
        self.host.get_file(cr50_path, local_path)

        cr50_utils.GSCTool(self.host, ['-a', cr50_path])

        self.cr50.wait_for_reboot(
                timeout=self.faft_config.gsc_update_wait_for_reboot)
        cr50_version = self.cr50.get_active_version_info()[3].split('/')[-1]
        logging.info('Cr50 running %s after update', cr50_version)
        self.make_rootfs_writable()
        cr50_utils.InstallImage(self.host, local_path, cr50_utils.CR50_PREPVT)
