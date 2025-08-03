# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

_BUILDS_BUCKET = 'gs://chromeos-arc-images/builds'

_ABI_MAP = {
        'armeabi-v7a': 'arm',
        'arm64-v8a': 'arm64',
        'x86': 'x86',
        'x86_64': 'x86_64'
}

# This version and beyond contains logic in push_to_device.py that supports
# HOST:PORT format for specifying the remote machine.
_PTD_MIN_VERSION_MAP = {
        'pi-arc': 7740639,
        'rvc-arc': 7741959,
        'sc-arc-dev': 7743996,
}


def push_userdebug_image(host, branch_prefix, lunch_target, download_func,
                         install_bundle_func, run_func):
    """This pushes a userdebug android image to the host.

    This downloads the userdebug android image and push_to_device.py tool
    from a google storage bucket.
    This uses the push_to_device.py tool to push the image onto the host.

    @param host: target host to push the image to.
    @param branch_prefix: the branch name prefix of where the image is
                          (e.g. rvc-arc, pi-arc). This does not have to be
                          the exact branch name for a particular release
                          (e.g. rvc-arc-m91).
    @param lunch_target: the target lunch name (e.g. cheets, bertha)
    @param download_func: function for downloading an object. This shall be
                          self._download_to_cache when invoking from TradefedTest class.
    @param install_bundle_func: function for downloading and unarchiving files.
                                This shall be self._install_bundle when invoking
                                from TradefedTest class.
    @param run_func: function for running a command. This shall be
                     self._run when invoking from TradefedTest class.

    @returns True on success, False otherwise.
    """
    arc_version = host.get_arc_version()
    if not arc_version:
        logging.error('Failed to determine ARC version.')
        return False

    # The split is necessary because push_to_device.py puts the whole image name
    # in CHROMEOS_ARC_VERSION, e.g. bertha_x86_64-img-7759413.
    # The split won't hurt even if it is just a number e.g.
    # CHROMEOS_ARC_VERSION=7759413.
    arc_version = int(arc_version.split('-')[-1])

    abi = _ABI_MAP[host.get_arc_primary_abi()]

    # Using '*' here to let gsutil figure out the release branch name.
    # arc_version is unique and will not show multiple branches.
    image_base_uri = '{}/git_{}-*linux-{}_{}-userdebug'.format(
            _BUILDS_BUCKET, branch_prefix, lunch_target, abi)

    image_uri = '{}/{}/{}_{}-img-{}.zip'.format(image_base_uri, arc_version,
                                                lunch_target, abi, arc_version)
    se_policy_uri = '{}/{}/sepolicy.zip'.format(image_base_uri, arc_version)

    image_file = download_func(image_uri)
    se_policy_file = download_func(se_policy_uri)

    if branch_prefix in _PTD_MIN_VERSION_MAP:
        ptd_version = max(arc_version, _PTD_MIN_VERSION_MAP[branch_prefix])
    else:
        logging.warning(
                '{} is not in _PTD_MIN_VERSION_MAP. This might fail to fetch '
                'the push_to_device tool.'.format(branch_prefix))
        ptd_version = arc_version

    push_to_device_uri = '{}/{}/push_to_device.zip'.format(
            image_base_uri, ptd_version)

    push_to_device_dir = install_bundle_func(push_to_device_uri)
    push_to_device_tool = os.path.join(push_to_device_dir, 'push_to_device.py')

    # This file on the device tells the infrastructure
    # that the device has to be reprovisioned before running other tasks.
    host.run('touch /mnt/stateful_partition/.force_provision', )
    logging.info('Pushing ARC userdebug image {} to {}.'.format(
            arc_version, host.host_port))
    run_func(
            push_to_device_tool,
            args=[
                    '--use-prebuilt-file',
                    image_file,
                    '--sepolicy-artifacts-path',
                    se_policy_file,
                    '--force',
                    host.host_port,
            ],
            ignore_status=False,
            verbose=True,
            nickname='Push userdebug image.',
    )
    return True
