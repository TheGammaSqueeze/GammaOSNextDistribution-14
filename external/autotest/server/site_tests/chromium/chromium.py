# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import logging
import os

from autotest_lib.client.common_lib.error import TestFail
from autotest_lib.server import test
from autotest_lib.server import utils


class chromium(test.test):
    """Run Chromium tests built on a Skylab DUT."""

    version = 1

    PROVISION_POINT = '/var/lib/imageloader/lacros'
    MOUNT_POINT = '/usr/local/tmp/chromium'
    CHRONOS_RES_DIR = '/home/chronos/user/results'

    def initialize(self, host=None, args=None):
        self.host = host
        assert host.path_exists(self.PROVISION_POINT), (
                'chromium test artifact is not provisioned by CTP. '
                'Please check the CTP request.')
        self._mount_runtime()
        args_dict = utils.args_to_dict(args)
        self.exe_rel_path = args_dict.get('exe_rel_path', '')
        path_to_executable = os.path.join(self.MOUNT_POINT, self.exe_rel_path)
        assert self.host.path_exists(path_to_executable), (
                'chromium test executable is not mounted at the '
                'expected path, %s' % path_to_executable)

        test_args = args_dict.get('test_args')
        if not test_args:
            test_args_b64 = args_dict.get('test_args_b64')
            if test_args_b64:
                test_args = base64.b64decode(test_args_b64)
        if isinstance(test_args, bytes):
            test_args = test_args.decode()
        self.test_args = test_args

        self.shard_number = args_dict.get('shard_number', 1)
        self.shard_index = args_dict.get('shard_index', 0)

    def _mount_runtime(self):
        try:
            self.host.run(
                    'mkdir -p {mount} && '
                    'imageloader --mount --mount_component=lacros'
                    ' --mount_point={mount}'.format(mount=self.MOUNT_POINT))
        except Exception as e:
            raise TestFail('Exception while mount test artifact: %s', e)

    def cleanup(self):
        try:
            self.host.run('imageloader --unmount --mount_point={mount};'
                          'rm -rf {mount} {chronos_res}'.format(
                                  chronos_res=self.CHRONOS_RES_DIR,
                                  mount=self.MOUNT_POINT))
        except Exception as e:
            logging.exception('Exception while clear test files: %s', e)

    def run_once(self):
        cmd = ('{mount}/{exe} '
               '--test-launcher-summary-output {chronos_res}/output.json '
               '--test-launcher-shard-index {idx} '
               '--test-launcher-total-shards {num} '.format(
                       mount=self.MOUNT_POINT,
                       exe=self.exe_rel_path,
                       chronos_res=self.CHRONOS_RES_DIR,
                       idx=self.shard_index,
                       num=self.shard_number))
        if self.test_args:
            cmd += '-- %s' % self.test_args
        try:
            self.host.run('su chronos -c -- "%s"' % cmd)
        except Exception as e:
            raise TestFail('Exception while execute test: %s', e)
        finally:
            self.host.get_file('%s/*' % self.CHRONOS_RES_DIR,
                               self.resultsdir,
                               delete_dest=True)
