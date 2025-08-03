# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import global_config

RPM_FRONTEND_URI = global_config.global_config.get_config_value(
        'CROS', 'rpm_frontend_uri', type=str, default='')

RPM_CALL_TIMEOUT_MINS = 15
POWERUNIT_HOSTNAME_KEY = 'powerunit_hostname'
POWERUNIT_OUTLET_KEY = 'powerunit_outlet'
HYDRA_HOSTNAME_KEY = 'hydra_hostname'
