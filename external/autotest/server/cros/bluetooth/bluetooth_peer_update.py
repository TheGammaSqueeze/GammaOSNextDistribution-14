# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This file provides functions to implement bluetooth_PeerUpdate test
which downloads chameleond bundle from google cloud storage and updates
peer device associated with a DUT
"""

from __future__ import absolute_import

import logging
import os
import sys
import tempfile
import time
import yaml

from datetime import datetime

import common
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error


# The location of the package in the cloud
GS_PUBLIC = 'gs://chromeos-localmirror/distfiles/bluetooth_peer_bundle/'

# NAME of the file that stores python2 commits info in the cloud
PYTHON2_COMMITS_FILENAME = 'bluetooth_python2_commits'

# NAME of the file that stores commits info in the Google cloud storage.
COMMITS_FILENAME = 'bluetooth_commits.yaml'


# The following needs to be kept in sync with values chameleond code
BUNDLE_TEMPLATE='chameleond-0.0.2-{}.tar.gz' # Name of the chamleond package
BUNDLE_DIR = 'chameleond-0.0.2'
BUNDLE_VERSION = '9999'
CHAMELEON_BOARD = 'fpga_tio'


def run_cmd(peer, cmd):
    """A wrapper around host.run()."""
    try:
        logging.info('executing command %s on peer',cmd)
        result = peer.host.run(cmd)
        logging.info('exit_status is %s', result.exit_status)
        logging.info('stdout is %s stderr is %s', result.stdout, result.stderr)
        output = result.stderr if result.stderr else result.stdout
        if result.exit_status == 0:
            return True, output
        else:
            return False, output
    except error.AutoservRunError as e:
        logging.error('Error while running cmd %s %s', cmd, e)
        return False, None


def read_google_cloud_file(filename):
    """ Check if update is required

    Read the contents of the Googlle cloud file.

    @param filename: the filename of the Google cloud file

    @returns: the contexts of the file if successful; None otherwise.
    """
    try:
        with tempfile.NamedTemporaryFile() as tmp_file:
            tmp_filename = tmp_file.name
            cmd = 'gsutil cp {} {}'.format(filename, tmp_filename)
            result = utils.run(cmd)
            if result.exit_status != 0:
                logging.error('Downloading file %s failed with %s',
                              filename, result.exit_status)
                return None
            with open(tmp_filename) as f:
                content = f.read()
                logging.debug('content of the file %s: %s', filename, content)
                return content
    except Exception as e:
        logging.error('Error in reading %s', filename)
        return None


def is_update_needed(peer, target_commit):
    """ Check if update is required

    Update if the commit hash doesn't match

    @returns: True/False
    """
    return not is_commit_hash_equal(peer, target_commit)


def is_commit_hash_equal(peer, target_commit):
    """ Check if chameleond commit hash is the expected one"""
    try:
        commit = peer.get_bt_commit_hash()
    except:
        logging.error('Getting the commit hash failed. Updating the peer %s',
                      sys.exc_info())
        return True

    logging.debug('commit %s found on peer %s', commit, peer.host)
    return commit == target_commit


def is_chromeos_build_greater_or_equal(build1, build2):
    """ Check if build1 is greater or equal to the build2"""
    build1 = [int(key1) for key1 in build1.split('.')]
    build2 = [int(key2) for key2 in build2.split('.')]
    for key1, key2 in zip(build1, build2):
        if key1 > key2:
            return True
        elif key1 == key2:
            continue
        else:
            return False
    return True


def perform_update(force_system_packages_update, peer, target_commit,
                   latest_commit):
    """ Update the chameleond on the peer

    @param force_system_packages_update: True to update system packages of the
                                          peer.
    @param peer: btpeer to be updated
    @param target_commit: target git commit
    @param latest_commit: the latest git commit in the lab_commit_map, which
                           is defined in the bluetooth_commits.yaml

    @returns: True if the update process is success, False otherwise
    """

    # Only update the system when the target commit is the latest.
    # Since system packages are backward compatible so it's safe to keep
    # it the latest.
    needs_system_update = 'true'
    if force_system_packages_update:
        logging.info("Forced system packages update on the peer.")
    elif target_commit == latest_commit:
        logging.info(
                "Perform system packages update as the peer's "
                "target_commit is the latest one %s", target_commit)
    else:
        logging.info("Skip updating system packages on the peer.")
        needs_system_update = 'false'

    logging.info('copy the file over to the peer')
    try:
        cur_dir = '/tmp/'
        bundle = BUNDLE_TEMPLATE.format(target_commit)
        bundle_path = os.path.join(cur_dir, bundle)
        logging.debug('package location is %s', bundle_path)

        peer.host.send_file(bundle_path, '/tmp/')
    except:
        logging.error('copying the file failed %s ', sys.exc_info())
        logging.error(str(os.listdir(cur_dir)))
        return False

    # Backward compatibility for deploying the chamleeon bundle:
    # use 'PY_VERSION=python3' only when the target_commit is not in
    # the specified python2 commits. When py_version_option is empty,
    # python2 will be used in the deployment.
    python2_commits_filename = GS_PUBLIC + PYTHON2_COMMITS_FILENAME
    python2_commits = read_google_cloud_file(python2_commits_filename)
    logging.info('target_commit %s python2_commits %s ',
                 target_commit, python2_commits)
    if bool(python2_commits) and target_commit in python2_commits:
        py_version_option = ''
    else:
        py_version_option = 'PY_VERSION=python3'

    HOST_NOW = datetime.strftime(datetime.now(), '%Y-%m-%d %H:%M:%S')
    logging.info('running make on peer')
    cmd = ('cd %s && rm -rf %s && tar zxf %s &&'
           'cd %s && find -exec touch -c {} \; &&'
           'make install REMOTE_INSTALL=TRUE '
           'HOST_NOW="%s" BUNDLE_VERSION=%s '
           'CHAMELEON_BOARD=%s NEEDS_SYSTEM_UPDATE=%s '
           '%s && rm %s%s' %
           (cur_dir, BUNDLE_DIR, bundle, BUNDLE_DIR, HOST_NOW, BUNDLE_VERSION,
            CHAMELEON_BOARD, needs_system_update, py_version_option, cur_dir,
            bundle))
    logging.info(cmd)
    status, _ = run_cmd(peer, cmd)
    if not status:
        logging.info('make failed')
        return False

    logging.info('chameleond installed on peer')
    return True


def restart_check_chameleond(peer):
    """restart chameleond and make sure it is running."""

    restart_cmd = 'sudo /etc/init.d/chameleond restart'
    start_cmd = 'sudo /etc/init.d/chameleond start'
    status_cmd = 'sudo /etc/init.d/chameleond status'

    status, _ = run_cmd(peer, restart_cmd)
    if not status:
        status, _ = run_cmd(peer, start_cmd)
        if not status:
            logging.error('restarting/starting chamleond failed')
    #
    #TODO: Refactor so that we wait for all peer devices all together.
    #
    # Wait till chameleond initialization is complete
    time.sleep(5)

    status, output = run_cmd(peer, status_cmd)
    expected_output = 'chameleond is running'
    return status and expected_output in output


def update_peer(force_system_packages_update, peer, target_commit,
                latest_commit):
    """Update the chameleond on peer devices if required

    @param force_system_packages_update: True to update system packages of the
                                          peer
    @param peer: btpeer to be updated
    @param target_commit: target git commit
    @param latest_commit: the latest git commit in the lab_commit_map, which
                           is defined in the bluetooth_commits.yaml

    @returns: (True, None) if update succeeded
              (False, reason) if update failed
    """

    if peer.get_platform() != 'RASPI':
        logging.error('Unsupported peer %s',str(peer.host))
        return False, 'Unsupported peer'

    if not perform_update(force_system_packages_update, peer, target_commit,
                          latest_commit):
        return False, 'Update failed'

    if not restart_check_chameleond(peer):
        return False, 'Unable to start chameleond'

    if is_update_needed(peer, target_commit):
        return False, 'Commit not updated after upgrade'

    logging.info('updating chameleond succeded')
    return True, ''


def update_all_peers(host, raise_error=False):
    """Update the chameleond on all peer devices of the given host

    @param host: the DUT, usually a Chromebook
    @param raise_error: set this to True to raise an error if any

    @returns: True if _update_all_peers success
              False if raise_error=False and _update_all_peers failed

    @raises: error.TestFail if raise_error=True and _update_all_peers failed
    """
    fail_reason = _update_all_peers(host)

    if fail_reason:
        if raise_error:
            raise error.TestFail(fail_reason)
        logging.error(fail_reason)
        return False
    else:
        return True


def _update_all_peers(host):
    """Update the chameleond on all peer devices of an host"""
    try:
        target_commit = get_target_commit(host)
        latest_commit = get_latest_commit(host)

        if target_commit is None:
            return 'Unable to get current commit'

        if latest_commit is None:
            return 'Unable to get latest commit'

        if host.btpeer_list == []:
            return 'Bluetooth Peer not present'

        peers_to_update = [
                p for p in host.btpeer_list
                if is_update_needed(p, target_commit)
        ]

        if not peers_to_update:
            logging.info('No peer needed update')
            return
        logging.debug('At least one peer needs update')

        if not download_installation_files(host, target_commit):
            return 'Unable to download installation files'

        # TODO(b:160782273) Make this parallel
        failed_peers = []
        host_is_in_lab_next_hosts = is_in_lab_next_hosts(host)
        for peer in peers_to_update:
            updated, reason = update_peer(host_is_in_lab_next_hosts, peer,
                                          target_commit, latest_commit)
            if updated:
                logging.info('peer %s updated successfully', str(peer.host))
            else:
                failed_peers.append((str(peer.host), reason))

        if failed_peers:
            return 'peer update failed (host, reason): %s' % failed_peers

    except Exception as e:
        return 'Exception raised in _update_all_peers: %s' % e
    finally:
        if not cleanup(host, target_commit):
            return 'Update peer cleanup failed'


def get_bluetooth_commits_yaml(host, method='from_cloud'):
    """Get the bluetooth_commit.yaml file

    This function has the side effect that it will set the attribute,
    host.bluetooth_commits_yaml for caching.

    @param host: the DUT, usually a Chromebook
    @param method: from_cloud: download the YAML file from the Google Cloud
                                Storage
                    from_local: download the YAML file from local, this option
                                is convienent for testing
    @returns: bluetooth_commits.yaml file if exists

    @raises: error.TestFail if failed to get the yaml file
    """
    try:
        if not hasattr(host, 'bluetooth_commits_yaml'):
            if method == 'from_cloud':
                src = GS_PUBLIC + COMMITS_FILENAME
                host.bluetooth_commits_yaml = yaml.safe_load(
                        read_google_cloud_file(src))
            elif method == 'from_local':
                yaml_file_path = os.path.dirname(os.path.realpath(__file__))
                yaml_file_path = os.path.join(yaml_file_path,
                                              'bluetooth_commits.yaml')
                with open(yaml_file_path) as f:
                    yaml_file = f.read()
                    host.bluetooth_commits_yaml = yaml.safe_load(yaml_file)
            else:
                raise error.TestError('invalid YAML download method: %s',
                                      method)
            logging.info('content of yaml file: %s',
                         host.bluetooth_commits_yaml)
    except Exception as e:
        logging.error('Error getting bluetooth_commits.yaml: %s', e)

    return host.bluetooth_commits_yaml


def is_in_lab_next_hosts(host):
    """Check if the host is in the lab_next_hosts

    This function has the side effect that it will set the attribute,
    host.is_in_lab_next_hosts for caching.

    @param host: the DUT, usually a Chromebook

    @returns: True if the host is in the lab_next_hosts, False otherwise.
    """
    if not hasattr(host, 'is_in_lab_next_hosts'):
        host_build = host.get_release_version()
        content = get_bluetooth_commits_yaml(host)

        if (host_name(host) in content.get('lab_next_hosts')
                    and host_build == content.get('lab_next_build')):
            host.is_in_lab_next_hosts = True
        else:
            host.is_in_lab_next_hosts = False
    return host.is_in_lab_next_hosts


def get_latest_commit(host):
    """ Get the latest_commmit in the bluetooth_commits.yaml

    @param host: the DUT, usually a Chromebook

    @returns: the latest commit hash if exists
    """
    try:
        content = get_bluetooth_commits_yaml(host)
        latest_commit = content.get('lab_commit_map')[0]['chameleon_commit']
        logging.info('The latest commit is: %s', latest_commit)
    except Exception as e:
        logging.error('Exception in get_latest_commit(): ', str(e))
    return latest_commit


def host_name(host):
    """ Get the name of a host

    @param host: the DUT, usually a Chromebook

    @returns: the hostname if exists, None otherwise
    """
    if hasattr(host, 'hostname'):
        return host.hostname.rstrip('.cros')
    else:
        return None


def get_target_commit(host):
    """ Get the target commit per the DUT

    Download the yaml file containing the commits, parse its contents,
    and cleanup.

    The yaml file looks like
    ------------------------
    lab_curr_commit: d732343cf
    lab_next_build: 13721.0.0
    lab_next_commit: 71be114
    lab_next_hosts:
      - chromeos15-row8-rack5-host1
      - chromeos15-row5-rack7-host7
      - chromeos15-row5-rack1-host4
    lab_commit_map:
      - build_version: 14461.0.0
        chameleon_commit: 87bed79
      - build_version: 00000.0.0
        chameleon_commit: 881f0e0

    The lab_next_commit will be used only when 3 conditions are satisfied
    - the lab_next_commit is non-empty
    - the hostname of the DUT can be found in lab_next_hosts
    - the host_build of the DUT is the same as lab_next_build

    Tests of next build will go back to the commits in the lab_commit_map
    automatically. The purpose is that in case lab_next_commit is not stable,
    the DUTs will go back to use the supposed stable commit according to the
    lab_commit_map. Test server will choose the biggest build_version in the
    lab_commit_map which is smaller than the host_build.

    On the other hand, if lab_next_commit is stable by juding from the lab
    dashboard, someone can then copy lab_next_build to lab_commit_map manually.

    @param host: the DUT, usually a Chromebook

    @returns commit in case of success; None in case of failure
    """
    hostname = host_name(host)

    try:
        content = get_bluetooth_commits_yaml(host)

        lab_next_commit = content.get('lab_next_commit')
        if (is_in_lab_next_hosts(host) and bool(lab_next_commit)):
            commit = lab_next_commit
            logging.info(
                    'target commit of the host %s is: %s from the '
                    'lab_next_commit', hostname, commit)
        else:
            host_build = host.get_release_version()
            lab_commit_map = content.get('lab_commit_map')
            for item in lab_commit_map:
                build = item['build_version']
                if is_chromeos_build_greater_or_equal(host_build, build):
                    commit = item['chameleon_commit']
                    break
            else:
                logging.error('lab_commit_map is corrupted')
                commit = None
            logging.info(
                    'target commit of the host %s is: %s from the '
                    'lab_commit_map', hostname, commit)

    except Exception as e:
        logging.error('Exception %s in get_target_commit()', str(e))
        commit = None
    return commit


def download_installation_files(host, commit):
    """ Download the chameleond installation bundle"""
    src_path = GS_PUBLIC + BUNDLE_TEMPLATE.format(commit)
    dest_path = '/tmp/' + BUNDLE_TEMPLATE.format(commit)
    logging.debug('chamelond bundle path is %s', src_path)
    logging.debug('bundle path in DUT is %s', dest_path)

    cmd = 'gsutil cp {} {}'.format(src_path, dest_path)
    try:
        result = utils.run(cmd)
        if result.exit_status != 0:
            logging.error('Downloading the chameleond bundle failed with %d',
                          result.exit_status)
            return False
        # Send file to DUT from the test server
        host.send_file(dest_path, dest_path)
        logging.debug('file send to %s %s',host, dest_path)
        return True
    except Exception as e:
        logging.error('exception %s in download_installation_files', str(e))
        return False


def cleanup(host, commit):
    """ Cleanup the installation file from server."""

    dest_path = '/tmp/' + BUNDLE_TEMPLATE.format(commit)
    # remove file from test server
    if not os.path.exists(dest_path):
        logging.debug('File %s not found', dest_path)
        return True

    try:
        logging.debug('Remove file %s', dest_path)
        os.remove(dest_path)

        # remove file from the DUT
        result = host.run('rm {}'.format(dest_path))
        if result.exit_status != 0:
            logging.error('Unable to delete %s on dut', dest_path)
            return False
        return True
    except Exception as e:
        logging.error('Exception %s in cleanup', str(e))
        return False
