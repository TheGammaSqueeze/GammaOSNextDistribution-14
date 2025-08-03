# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import contextlib
import json
import logging
import os
import time

from autotest_lib.client.common_lib import error, utils


class ChartFixture:
    """Sets up chart tablet to display placeholder scene image."""
    DISPLAY_SCRIPT = '/usr/local/autotest/bin/display_chart.py'
    OUTPUT_LOG = '/tmp/chart_service.log'

    def __init__(self, chart_host, scene_uri, job=None):
        self.host = chart_host
        self.scene_uri = scene_uri
        self.job = job
        self.display_pid = None
        self.host.run(['rm', '-f', self.OUTPUT_LOG], ignore_status=True)

    def initialize(self):
        """Prepare scene file and display it on chart host."""
        logging.info('Prepare scene file')
        chart_version = self.host.run(
                'cat /etc/lsb-release | grep CHROMEOS_RELEASE_BUILDER_PATH')
        logging.info('Chart version: %s', chart_version)
        if utils.is_in_container():
            # Reboot chart to clean the dirty state from last test. See
            # b/201032899.
            version = self.host.get_release_builder_path()
            self.job.run_test('provision_QuickProvision',
                              host=self.host,
                              value=version,
                              force_update_engine=True)

        tmpdir = self.host.get_tmp_dir()
        scene_path = os.path.join(
                tmpdir, self.scene_uri[self.scene_uri.rfind('/') + 1:])
        self.host.run('wget', args=('-O', scene_path, self.scene_uri))

        logging.info('Display scene file')
        self.display_pid = self.host.run_background(
                'python {script} {scene} >{log} 2>&1'.format(
                        script=self.DISPLAY_SCRIPT,
                        scene=scene_path,
                        log=self.OUTPUT_LOG))

        logging.info(
                'Poll for "is ready" message for ensuring chart is ready.')
        timeout = 60
        poll_time_step = 0.1
        while timeout > 0:
            if self.host.run(
                    'grep',
                    args=('-q', 'Chart is ready.', self.OUTPUT_LOG),
                    ignore_status=True).exit_status == 0:
                break
            time.sleep(poll_time_step)
            timeout -= poll_time_step
        else:
            raise error.TestError('Timeout waiting for chart ready')

    def cleanup(self):
        """Cleanup display script."""
        if self.display_pid is not None:
            self.host.run(
                    'kill',
                    args=('-2', str(self.display_pid)),
                    ignore_status=True)
            self.host.get_file(self.OUTPUT_LOG, '.')


def get_chart_address(host_address, args):
    """Get address of chart tablet from commandline args or mapping logic in
    test lab.

    @param host_address: a list of hostname strings.
    @param args: a dict parse from commandline args.
    @return:
        A list of strings for chart tablet addresses.
    """
    address = utils.args_to_dict(args).get('chart')
    if address is not None:
        return address.split(',')
    elif utils.is_in_container():
        return [utils.get_lab_chart_address(host) for host in host_address]
    else:
        return None


class DUTFixture:
    """Sets up camera filter for target camera facing on DUT."""
    TEST_CONFIG_PATH = '/var/cache/camera/test_config.json'
    CAMERA_SCENE_LOG = '/tmp/scene.jpg'

    def __init__(self, test, host, facing):
        self.test = test
        self.host = host
        self.facing = facing

    @contextlib.contextmanager
    def _set_selinux_permissive(self):
        selinux_mode = self.host.run_output('getenforce')
        self.host.run('setenforce 0')
        yield
        self.host.run('setenforce', args=(selinux_mode, ))

    def _write_file(self, filepath, content, permission=None, owner=None):
        """Write content to filepath on remote host.
        @param permission: set permission to 0xxx octal number of remote file.
        @param owner: set owner of remote file.
        """
        tmp_path = os.path.join(self.test.tmpdir, os.path.basename(filepath))
        with open(tmp_path, 'w') as f:
            f.write(content)
        if permission is not None:
            os.chmod(tmp_path, permission)
        self.host.send_file(tmp_path, filepath, delete_dest=True)
        if owner is not None:
            self.host.run('chown', args=(owner, filepath))

    def initialize(self):
        """Filter out camera other than target facing on DUT."""
        self._write_file(
                self.TEST_CONFIG_PATH,
                json.dumps({
                        'enable_back_camera': self.facing == 'back',
                        'enable_front_camera': self.facing == 'front',
                        'enable_external_camera': False
                }),
                owner='arc-camera')

        # cros_camera_service will reference the test config to filter out
        # undesired cameras.
        logging.info('Restart camera service with filter option')
        self.host.upstart_restart('cros-camera')

        # arc_setup will reference the test config to filter out the media
        # profile of undesired cameras.
        logging.info('Restart ARC++ container with camera test config')
        self.host.run('restart ui')

    @contextlib.contextmanager
    def _stop_camera_service(self):
        # Ensure camera service is running or the
        # upstart_stop()/upstart_restart() may failed due to in
        # "start|post-stop" sleep for respawning state. See b/183904344 for
        # detail.
        logging.info('Wait for presence of camera service')
        self.host.wait_for_service('cros-camera')

        self.host.upstart_stop('cros-camera')
        yield
        self.host.upstart_restart('cros-camera')

    def log_camera_scene(self):
        """Capture an image from camera as the log for debugging scene related
        problem."""

        gtest_filter = (
                'Camera3StillCaptureTest/'
                'Camera3DumpSimpleStillCaptureTest.DumpCaptureResult/0')
        with self._stop_camera_service():
            self.host.run(
                    'sudo',
                    args=('--user=arc-camera', 'cros_camera_test',
                          '--gtest_filter=' + gtest_filter,
                          '--camera_facing=' + self.facing,
                          '--dump_still_capture_path=' +
                          self.CAMERA_SCENE_LOG))

        self.host.get_file(self.CAMERA_SCENE_LOG, '.')

    def cleanup(self):
        """Cleanup camera filter."""
        logging.info('Remove filter option and restore camera service')
        with self._stop_camera_service():
            self.host.run('rm', args=('-f', self.TEST_CONFIG_PATH))

        logging.info('Restore camera profile in ARC++ container')
        self.host.run('restart ui')
