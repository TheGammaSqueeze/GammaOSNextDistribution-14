# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Provide version control for Bluetooth tests"""

import logging
import os

from autotest_lib.server import utils

CWD = os.getcwd()
BLUETOOTH_DIR = os.path.dirname(__file__)
REMOTE_NAME = 'cros'
BRANCH_NAME = 'main'
BRANCH_NAME_FULL = os.path.join(REMOTE_NAME, BRANCH_NAME)
HTTP_MIRROR_URL =\
        'http://commondatastorage.googleapis.com/chromeos-localmirror'
BUNDLE_PATH = 'distfiles/bluetooth_peer_bundle'
HTTP_BUNDLE_URL = os.path.join(HTTP_MIRROR_URL, BUNDLE_PATH)
LATEST_STABLE_AUTOTEST_COMMIT = 'LATEST_STABLE_AUTOTEST_COMMIT'
HTTP_LATEST_STABLE_AUTOTEST_COMMIT_URL = os.path.join(
        HTTP_BUNDLE_URL, LATEST_STABLE_AUTOTEST_COMMIT)


def check_git_tree_clean():
    """ Check if local directory is clear from modification

    @returns: True if success, False otherwise
    """
    output = utils.run('git status --porcelain')
    if output.stdout != '':
        logging.info(
                'The Autotest directory is not clean! To perform the AVL\n'
                'testing consistently, the AVL setup process will fetch\n'
                'a specific commit hash from the server and check out\n'
                'locally. To preserve your local changes, please commit\n'
                'or stash your changes! Changes:')
        logging.info(output.stdout)
        return False

    logging.info('Local git tree is clean.')
    return True


def fetch_target_commit():
    """ Fetch from the cloud or git to retrieve latest ToT or latest stable
    commit hash.

    @returns: current and targeted commit hash
    """
    current_commit = utils.system_output('git rev-parse HEAD')
    utils.run('git fetch ' + REMOTE_NAME)
    target_commit = utils.system_output(
            'git rev-parse {}'.format(BRANCH_NAME_FULL))

    output = utils.run('wget -O {} {}'.format(
            LATEST_STABLE_AUTOTEST_COMMIT,
            HTTP_LATEST_STABLE_AUTOTEST_COMMIT_URL),
                       ignore_status=True)

    if output.exit_status != 0:
        logging.info('Failed to fetch the latest commit from the server')
        logging.info(output.stdout)
        logging.info(output.stderr)
    else:
        with open(LATEST_STABLE_AUTOTEST_COMMIT) as commit_file:
            target_commit = commit_file.readline().strip()

    logging.info('The latest commit will be used is:\n%s', target_commit)
    return current_commit, target_commit


def checkout_commit(commit):
    """ Checkout the autotest directory to the specified commit."""
    output = utils.run('git checkout {}'.format(commit), ignore_status=True)
    if output.exit_status != 0:
        logging.info(output.stderr)
        logging.info('Failed to checkout target commit, please retry '
                     'after\nrepo sync')
    else:
        logging.info('Target (stable or ToT) autotest commit is checked out,\n'
                     'please rerun the test!')


def test_version_setup_exit_print():
    """ Exit the setup and return to the previous CWD."""
    logging.info('=======================================================\n')
    os.chdir(CWD)


def test_version_setup():
    """This and above functions hope to sync the AVL test environments
    among different vendors, partners, and developers by providing an
    automatic process to fetch a commit hash of the "released"
    (or "stabled") version of the autotest directory from the cloud and
    checkout locally. No manual interaction should be expected.

    @returns: True if current commit version satisfied requirement, the
              test shall proceed. False otherwise.
    """
    logging.info('=======================================================\n'
                 '                    AVL Test Setup\n')

    os.chdir(BLUETOOTH_DIR)
    if not check_git_tree_clean():
        test_version_setup_exit_print()
        return False

    current_commit, target_commit = fetch_target_commit()
    if current_commit == target_commit:
        logging.info('Local tree is already at target autotest commit.')
        test_version_setup_exit_print()
        return True

    checkout_commit(target_commit)
    test_version_setup_exit_print()
    return False
