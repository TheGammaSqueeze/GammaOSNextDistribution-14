# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This module provides some tools to interact with LXC containers, for example:
  1. Download base container from given GS location, setup the base container.
  2. Create a snapshot as test container from base container.
  3. Mount a directory in drone to the test container.
  4. Run a command in the container and return the output.
  5. Cleanup, e.g., destroy the container.
"""
try:
    from autotest_lib.site_utils.lxc.constants import *
    from autotest_lib.site_utils.lxc.container import Container
    from autotest_lib.site_utils.lxc.container import ContainerId
    from autotest_lib.site_utils.lxc.container_bucket import ContainerBucket
    from autotest_lib.site_utils.lxc.container_factory import ContainerFactory
    from autotest_lib.site_utils.lxc.lxc import install_packages
except ImportError:
    from constants import *
    from container import Container
    from container import ContainerId
    from container_bucket import ContainerBucket
    from container_factory import ContainerFactory
    from lxc import install_packages
