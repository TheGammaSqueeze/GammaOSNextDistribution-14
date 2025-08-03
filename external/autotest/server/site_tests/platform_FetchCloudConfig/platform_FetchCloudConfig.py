# Lint as: python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import common
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.utils import labellib
from autotest_lib.server import test


class platform_FetchCloudConfig(test.test):
    """Reload fresh performance CUJ cloud configuration from cloud."""
    version = 1

    def run_once(self, host):
        devservers = dev_server.ImageServer.get_available_devservers()
        devserver_url = devservers[0][0]
        if devserver_url:
            logging.info('Using devserver: %s', devserver_url)
            labels = host.host_info_store.get().labels
            build = labellib.LabelsMapping(labels).get(
                    labellib.Key.CROS_VERSION)
            if not build:
                # Not able to detect build, means not running on Moblab.
                raise error.TestFail('Unable to stage config on devserver %s, '
                                     'probably not running in Moblab.' %
                                     devserver_url)
            ds = dev_server.ImageServer(devserver_url)
            gs_bucket = dev_server._get_image_storage_server()
            if gs_bucket:
                config_path = 'config/perf_cuj/'
                config_file = 'perf_cuj.config'
                archive_url = gs_bucket + config_path
                logging.info('Staging configuration from %s.', gs_bucket)
                kwargs = {'clean': True}
                ds.stage_artifacts(build,
                                   archive_url=archive_url,
                                   files=[config_file],
                                   **kwargs)
            else:
                raise error.TestFail(
                        'Invalid GS bucket %s for devserver %s.' % gs_bucket,
                        devserver_url)
