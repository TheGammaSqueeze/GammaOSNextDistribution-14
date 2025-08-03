# Lint as: python2, python3
# Copyright 2016 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Utility functions for AFE-based interactions.

NOTE: This module should only be used in the context of a running test. Any
      utilities that require accessing the AFE, should do so by creating
      their own instance of the AFE client and interact with it directly.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import traceback

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros import provision
from autotest_lib.server.cros import provisioner
from autotest_lib.server import site_utils as server_utils


def _log_image_name(image_name):
    try:
        logging.debug("_log_image_name: image (%s)", image_name)
        server_utils.ParseBuildName(name=image_name)
    except Exception:
        logging.error(traceback.format_exc())


def _format_image_name(board, version):
    return "%s-release/%s" % (board, version)


def get_stable_cros_image_name_v2(host_info):
    """Retrieve the ChromeOS stable image name for a given board.

    @param host_info: a host_info_store object.

    @returns Name of a ChromeOS image to be installed in order to
            repair the given board.
    """
    if not host_info.cros_stable_version:
        raise error.AutoservError("No cros stable_version found"
                                  " in host_info_store.")

    logging.debug("Get cros stable_version for board: %s",
                  getattr(host_info, "board", None))
    out = _format_image_name(board=host_info.board,
                             version=host_info.cros_stable_version)
    _log_image_name(out)
    return out


def get_stable_firmware_version_v2(host_info):
    """Retrieve the stable firmware version for a given model.

    @param host_info: a host_info_store object.

    @returns A version of firmware to be installed via
             `chromeos-firmwareupdate` from a repair build.
    """
    logging.debug("Get firmware stable_version for model: %s",
                  getattr(host_info, "model", None))
    return host_info.firmware_stable_version


def get_stable_faft_version_v2(host_info):
    """Retrieve the stable firmware version for FAFT DUTs.

    @param host_info: a host_info_store object.

    @returns A version of firmware to be installed in order to
            repair firmware on a DUT used for FAFT testing.
    """
    logging.debug("Get faft stable_version for model: %s",
                  getattr(host_info, "model", None))
    return host_info.faft_stable_version


def clean_provision_labels(host):
    """Clean provision-related labels.

    @param host: Host object.
    """
    info = host.host_info_store.get()
    info.clear_version_labels()
    attributes = host.get_attributes_to_clear_before_provision()
    for key in attributes:
        info.attributes.pop(key, None)

    host.host_info_store.commit(info)


def add_provision_labels(host, version_prefix, image_name,
                         provision_attributes={}):
    """Add provision labels for host.

    @param host: Host object.
    @param version_prefix: a string version prefix, e.g. "cros-version:"
    @param image_name: a string image name, e.g. peppy-release/R70-11011.0.0.
    @param provision_attributes: a map, including attributes for provisioning,
        e.g. {"job_repo_url": "http://..."}
    """
    info = host.host_info_store.get()
    info.attributes.update(provision_attributes)
    info.set_version_label(version_prefix, image_name)
    host.host_info_store.commit(info)


def machine_install_and_update_labels(host, update_url, with_cheets=False,
                                      staging_server=None):
    """Install a build and update the version labels on a host.

    @param host: Host object where the build is to be installed.
    @param update_url: URL of the build to install.
    @param with_cheets: If true, installation is for a specific, custom
        version of Android for a target running ARC.
    @param staging_server: Server where images have been staged. Typically,
        an instance of dev_server.ImageServer.
    """
    clean_provision_labels(host)

    logging.debug('Attempting to provision with quick-provision.')
    cros_provisioner = provisioner.ChromiumOSProvisioner(update_url, host=host)
    image_name, host_attributes = cros_provisioner.run_provision()

    if with_cheets:
        image_name += provision.CHEETS_SUFFIX
    add_provision_labels(host, host.VERSION_PREFIX, image_name, host_attributes)
