# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import logging
import os
import re
import six
import sys
import six.moves.urllib.parse

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server import autotest
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.cros.dynamic_suite import tools

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock


def _metric_name(base_name):
    return 'chromeos/autotest/provision/' + base_name


_QUICK_PROVISION_SCRIPT = 'quick-provision'

# PROVISION_FAILED - A flag file to indicate provision failures.  The
# file is created at the start of any AU procedure (see
# `ChromiumOSProvisioner._prepare_host()`).  The file's location in
# stateful means that on successul update it will be removed.  Thus, if
# this file exists, it indicates that we've tried and failed in a
# previous attempt to update.
PROVISION_FAILED = '/var/tmp/provision_failed'

# A flag file used to enable special handling in lab DUTs.  Some
# parts of the system in Chromium OS test images will behave in ways
# convenient to the test lab when this file is present.  Generally,
# we create this immediately after any update completes.
LAB_MACHINE_FILE = '/mnt/stateful_partition/.labmachine'

# _TARGET_VERSION - A file containing the new version to which we plan
# to update.  This file is used by the CrOS shutdown code to detect and
# handle certain version downgrade cases.  Specifically:  Downgrading
# may trigger an unwanted powerwash in the target build when the
# following conditions are met:
#  * Source build is a v4.4 kernel with R69-10756.0.0 or later.
#  * Target build predates the R69-10756.0.0 cutoff.
# When this file is present and indicates a downgrade, the OS shutdown
# code on the DUT knows how to prevent the powerwash.
_TARGET_VERSION = '/run/update_target_version'

# _REBOOT_FAILURE_MESSAGE - This is the standard message text returned
# when the Host.reboot() method fails.  The source of this text comes
# from `wait_for_restart()` in client/common_lib/hosts/base_classes.py.

_REBOOT_FAILURE_MESSAGE = 'Host did not return from reboot'

DEVSERVER_PORT = '8082'
GS_CACHE_PORT = '8888'


class _AttributedUpdateError(error.TestFail):
    """Update failure with an attributed cause."""

    def __init__(self, attribution, msg):
        super(_AttributedUpdateError,
              self).__init__('%s: %s' % (attribution, msg))
        self._message = msg

    def _classify(self):
        for err_pattern, classification in self._CLASSIFIERS:
            if re.match(err_pattern, self._message):
                return classification
        return None

    @property
    def failure_summary(self):
        """Summarize this error for metrics reporting."""
        classification = self._classify()
        if classification:
            return '%s: %s' % (self._SUMMARY, classification)
        else:
            return self._SUMMARY


class HostUpdateError(_AttributedUpdateError):
    """Failure updating a DUT attributable to the DUT.

    This class of exception should be raised when the most likely cause
    of failure was a condition existing on the DUT prior to the update,
    such as a hardware problem, or a bug in the software on the DUT.
    """

    DUT_DOWN = 'No answer to ssh'

    _SUMMARY = 'DUT failed prior to update'
    _CLASSIFIERS = [
            (DUT_DOWN, DUT_DOWN),
            (_REBOOT_FAILURE_MESSAGE, 'Reboot failed'),
    ]

    def __init__(self, hostname, msg):
        super(HostUpdateError,
              self).__init__('Error on %s prior to update' % hostname, msg)


class ImageInstallError(_AttributedUpdateError):
    """Failure updating a DUT when installing from the devserver.

    This class of exception should be raised when the target DUT fails
    to download and install the target image from the devserver, and
    either the devserver or the DUT might be at fault.
    """

    _SUMMARY = 'Image failed to download and install'
    _CLASSIFIERS = []

    def __init__(self, hostname, devserver, msg):
        super(ImageInstallError, self).__init__(
                'Download and install failed from %s onto %s' %
                (devserver, hostname), msg)


class NewBuildUpdateError(_AttributedUpdateError):
    """Failure updating a DUT attributable to the target build.

    This class of exception should be raised when updating to a new
    build fails, and the most likely cause of the failure is a bug in
    the newly installed target build.
    """

    CHROME_FAILURE = 'Chrome failed to reach login screen'
    ROLLBACK_FAILURE = 'System rolled back to previous build'

    _SUMMARY = 'New build failed'
    _CLASSIFIERS = [
            (CHROME_FAILURE, 'Chrome did not start'),
            (ROLLBACK_FAILURE, ROLLBACK_FAILURE),
    ]

    def __init__(self, update_version, msg):
        super(NewBuildUpdateError,
              self).__init__('Failure in build %s' % update_version, msg)

    @property
    def failure_summary(self):
        #pylint: disable=missing-docstring
        return 'Build failed to work after installing'


def _url_to_version(update_url):
    """Return the version based on update_url.

    @param update_url: url to the image to update to.

    """
    # The ChromeOS version is generally the last element in the URL. The only
    # exception is delta update URLs, which are rooted under the version; e.g.,
    # http://.../update/.../0.14.755.0/au/0.14.754.0. In this case we want to
    # strip off the au section of the path before reading the version.
    return re.sub('/au/.*', '',
                  six.moves.urllib.parse.urlparse(update_url).path).split(
                          '/')[-1].strip()


def url_to_image_name(update_url):
    """Return the image name based on update_url.

    From a URL like:
        http://172.22.50.205:8082/update/lumpy-release/R27-3837.0.0
    return lumpy-release/R27-3837.0.0

    @param update_url: url to the image to update to.
    @returns a string representing the image name in the update_url.

    """
    return six.moves.urllib.parse.urlparse(update_url).path[len('/update/'):]


def get_update_failure_reason(exception):
    """Convert an exception into a failure reason for metrics.

    The passed in `exception` should be one raised by failure of
    `ChromiumOSProvisioner.run_provision`.  The returned string will describe
    the failure.  If the input exception value is not a truish value
    the return value will be `None`.

    The number of possible return strings is restricted to a limited
    enumeration of values so that the string may be safely used in
    Monarch metrics without worrying about cardinality of the range of
    string values.

    @param exception  Exception to be converted to a failure reason.

    @return A string suitable for use in Monarch metrics, or `None`.
    """
    if exception:
        if isinstance(exception, _AttributedUpdateError):
            return exception.failure_summary
        else:
            return 'Unknown Error: %s' % type(exception).__name__
    return None


class ChromiumOSProvisioner(object):
    """Chromium OS specific DUT update functionality."""

    def __init__(self,
                 update_url,
                 host=None,
                 interactive=True,
                 is_release_bucket=None,
                 is_servohost=False,
                 public_bucket=False):
        """Initializes the object.

        @param update_url: The URL we want the update to use.
        @param host: A client.common_lib.hosts.Host implementation.
        @param interactive: Bool whether we are doing an interactive update.
        @param is_release_bucket: If True, use release bucket
            gs://chromeos-releases.
        @param is_servohost: Bool whether the update target is a servohost.
        @param public_bucket: True to copy payloads to a public throwaway GS
            bucket. This avoids using a lab cache server, so local test runs
            can provision without any special setup.
        """
        self.update_url = update_url
        self.host = host
        self.interactive = interactive
        self.update_version = _url_to_version(update_url)
        self._is_release_bucket = is_release_bucket
        self._is_servohost = is_servohost
        self._public_bucket = public_bucket

    def _run(self, cmd, *args, **kwargs):
        """Abbreviated form of self.host.run(...)"""
        return self.host.run(cmd, *args, **kwargs)

    def _rootdev(self, options=''):
        """Returns the stripped output of rootdev <options>.

        @param options: options to run rootdev.

        """
        return self._run('rootdev %s' % options).stdout.strip()

    def _reset_update_engine(self):
        """Resets the host to prepare for a clean update regardless of state."""
        self._run('stop ui || true')
        self._run('stop update-engine || true; start update-engine')

    def _reset_stateful_partition(self):
        """Clear any pending stateful update request."""
        cmd = ['rm', '-rf']
        for f in ('var_new', 'dev_image_new', '.update_available'):
            cmd += [os.path.join('/mnt/stateful_partition', f)]
        # TODO(b/165024723): This is a temporary measure until we figure out the
        # root cause of this bug.
        for f in ('dev_image/share/tast/data', 'dev_image/libexec/tast',
                  'dev_image/tmp/tast'):
            cmd += [os.path.join('/mnt/stateful_partition', f)]
        cmd += [_TARGET_VERSION, '2>&1']
        self._run(cmd)

    def _set_target_version(self):
        """Set the "target version" for the update."""
        # Version strings that come from release buckets do not have RXX- at the
        # beginning. So remove this prefix only if the version has it.
        version_number = (self.update_version.split('-')[1] if
                          '-' in self.update_version else self.update_version)
        self._run('echo %s > %s' % (version_number, _TARGET_VERSION))

    def _revert_boot_partition(self):
        """Revert the boot partition."""
        part = self._rootdev('-s')
        logging.warning('Reverting update; Boot partition will be %s', part)
        return self._run('/postinst %s 2>&1' % part)

    def _get_remote_script(self, script_name):
        """Ensure that `script_name` is present on the DUT.

        The given script (e.g. `quick-provision`) may be present in the
        stateful partition under /usr/local/bin, or we may have to
        download it from the devserver.

        Determine whether the script is present or must be downloaded
        and download if necessary.  Then, return a command fragment
        sufficient to run the script from whereever it now lives on the
        DUT.

        @param script_name  The name of the script as expected in
                            /usr/local/bin and on the devserver.
        @return A string with the command (minus arguments) that will
                run the target script.
        """
        remote_script = '/usr/local/bin/%s' % script_name
        if self.host.path_exists(remote_script):
            return remote_script
        self.host.run('mkdir -p -m 1777 /usr/local/tmp')
        remote_tmp_script = '/usr/local/tmp/%s' % script_name
        server_name = six.moves.urllib.parse.urlparse(self.update_url)[1]
        script_url = 'http://%s/static/%s' % (server_name, script_name)
        fetch_script = 'curl -Ss -o %s %s && head -1 %s' % (
                remote_tmp_script, script_url, remote_tmp_script)

        first_line = self._run(fetch_script).stdout.strip()

        if first_line and first_line.startswith('#!'):
            script_interpreter = first_line.lstrip('#!')
            if script_interpreter:
                return '%s %s' % (script_interpreter, remote_tmp_script)
        return None

    def _prepare_host(self):
        """Make sure the target DUT is working and ready for update.

        Initially, the target DUT's state is unknown.  The DUT is
        expected to be online, but we strive to be forgiving if Chrome
        and/or the update engine aren't fully functional.
        """
        # Summary of work, and the rationale:
        #  1. Reboot, because it's a good way to clear out problems.
        #  2. Touch the PROVISION_FAILED file, to allow repair to detect
        #     failure later.
        #  3. Run the hook for host class specific preparation.
        #  4. Stop Chrome, because the system is designed to eventually
        #     reboot if Chrome is stuck in a crash loop.
        #  5. Force `update-engine` to start, because if Chrome failed
        #     to start properly, the status of the `update-engine` job
        #     will be uncertain.
        if not self.host.is_up():
            raise HostUpdateError(self.host.hostname, HostUpdateError.DUT_DOWN)
        self._reset_stateful_partition()
        # Servohost reboot logic is handled by themselves.
        if not self._is_servohost:
            self.host.reboot(timeout=self.host.REBOOT_TIMEOUT)
            self._run('touch %s' % PROVISION_FAILED)
        self.host.prepare_for_update()
        # Servohost will only update via quick provision.
        if not self._is_servohost:
            self._reset_update_engine()
        logging.info('Updating from version %s to %s.',
                     self.host.get_release_version(), self.update_version)

    def _quick_provision_with_gs_cache(self, provision_command, devserver_name,
                                       image_name):
        """Run quick_provision using GsCache server.

        @param provision_command: The path of quick_provision command.
        @param devserver_name: The devserver name and port (optional).
        @param image_name: The image to be installed.
        """
        logging.info('Try quick provision with gs_cache.')
        # If enabled, GsCache server listion on different port on the
        # devserver.
        gs_cache_server = devserver_name.replace(DEVSERVER_PORT, GS_CACHE_PORT)
        gs_cache_url = (
                'http://%s/download/%s' %
                (gs_cache_server, 'chromeos-releases'
                 if self._is_release_bucket else 'chromeos-image-archive'))

        # Check if GS_Cache server is enabled on the server.
        self._run('curl -s -o /dev/null %s' % gs_cache_url)

        command = '%s --noreboot %s %s' % (provision_command, image_name,
                                           gs_cache_url)
        self._run(command)
        metrics.Counter(
                _metric_name('quick_provision')).increment(fields={
                        'devserver': devserver_name,
                        'gs_cache': True
                })

    def _quick_provision_with_devserver(self, provision_command,
                                        devserver_name, image_name):
        """Run quick_provision using legacy devserver.

        @param provision_command: The path of quick_provision command.
        @param devserver_name: The devserver name and port (optional).
        @param image_name: The image to be installed.
        """
        logging.info('Try quick provision with devserver.')
        ds = dev_server.ImageServer('http://%s' % devserver_name)
        archive_url = ('gs://chromeos-releases/%s' %
                       image_name if self._is_release_bucket else None)
        try:
            ds.stage_artifacts(
                    image_name,
                    ['quick_provision', 'stateful', 'autotest_packages'],
                    archive_url=archive_url)
        except dev_server.DevServerException as e:
            six.reraise(error.TestFail, str(e), sys.exc_info()[2])

        static_url = 'http://%s/static' % devserver_name
        command = '%s --noreboot %s %s' % (provision_command, image_name,
                                           static_url)
        self._run(command)
        metrics.Counter(
                _metric_name('quick_provision')).increment(fields={
                        'devserver': devserver_name,
                        'gs_cache': False
                })

    def _quick_provision_with_public_bucket(self, provision_command,
                                            image_name):
        """Run quick_provision using public GS bucket.

        @param provision_command: The path of quick_provision command.
        @param image_name: The image to be installed.
        """
        logging.info('Try quick provision with public bucket.')

        bucket_url = self.update_url[:self.update_url.find(image_name) - 1]
        command = '%s --noreboot %s %s' % (provision_command, image_name,
                                           bucket_url)
        self._run(command)

    def _install_update(self):
        """Install an updating using the `quick-provision` script.

        This uses the `quick-provision` script to download and install
        a root FS, kernel and stateful filesystem content.

        @return The kernel expected to be booted next.
        """
        logging.info('Installing image at %s onto %s', self.update_url,
                     self.host.hostname)
        server_name = six.moves.urllib.parse.urlparse(self.update_url)[1]
        if self._public_bucket:
            image_name = self.update_url.partition('provision/')[2]
        else:
            image_name = url_to_image_name(self.update_url)

        logging.info('Installing image using quick-provision.')
        provision_command = self._get_remote_script(_QUICK_PROVISION_SCRIPT)
        try:
            if self._public_bucket:
                self._quick_provision_with_public_bucket(
                        provision_command, image_name)
            else:
                try:
                    self._quick_provision_with_gs_cache(
                            provision_command, server_name, image_name)
                except Exception as e:
                    logging.error(
                            'Failed to quick-provision with gscache with '
                            'error %s', e)
                    self._quick_provision_with_devserver(
                            provision_command, server_name, image_name)

            self._set_target_version()
            return kernel_utils.verify_kernel_state_after_update(self.host)
        except Exception:
            # N.B.  We handle only `Exception` here.  Non-Exception
            # classes (such as KeyboardInterrupt) are handled by our
            # caller.
            logging.exception('quick-provision script failed;')
            self._revert_boot_partition()
            self._reset_stateful_partition()
            self._reset_update_engine()
            return None

    def _complete_update(self, expected_kernel):
        """Finish the update, and confirm that it succeeded.

        Initial condition is that the target build has been downloaded
        and installed on the DUT, but has not yet been booted.  This
        function is responsible for rebooting the DUT, and checking that
        the new build is running successfully.

        @param expected_kernel: kernel expected to be active after reboot.
        """
        # Regarding the 'crossystem' command below: In some cases,
        # the update flow puts the TPM into a state such that it
        # fails verification.  We don't know why.  However, this
        # call papers over the problem by clearing the TPM during
        # the reboot.
        #
        # We ignore failures from 'crossystem'.  Although failure
        # here is unexpected, and could signal a bug, the point of
        # the exercise is to paper over problems; allowing this to
        # fail would defeat the purpose.
        self._run('crossystem clear_tpm_owner_request=1', ignore_status=True)
        self.host.reboot(timeout=self.host.REBOOT_TIMEOUT)

        # Touch the lab machine file to leave a marker that
        # distinguishes this image from other test images.
        # Afterwards, we must re-run the autoreboot script because
        # it depends on the LAB_MACHINE_FILE.
        autoreboot_cmd = ('FILE="%s" ; [ -f "$FILE" ] || '
                          '( touch "$FILE" ; start autoreboot )')
        self._run(autoreboot_cmd % LAB_MACHINE_FILE)
        try:
            kernel_utils.verify_boot_expectations(
                    expected_kernel, NewBuildUpdateError.ROLLBACK_FAILURE,
                    self.host)
        except Exception:
            # When the system is rolled back, the provision_failed file is
            # removed. So add it back here and re-raise the exception.
            self._run('touch %s' % PROVISION_FAILED)
            raise

        logging.debug('Cleaning up old autotest directories.')
        try:
            installed_autodir = autotest.Autotest.get_installed_autodir(
                    self.host)
            self._run('rm -rf ' + installed_autodir)
        except autotest.AutodirNotFoundError:
            logging.debug('No autotest installed directory found.')

    def run_provision(self):
        """Perform a full provision of a DUT in the test lab.

        This downloads and installs the root FS and stateful partition
        content needed for the update specified in `self.host` and
        `self.update_url`.  The provision is performed according to the
        requirements for provisioning a DUT for testing the requested
        build.

        At the end of the procedure, metrics are reported describing the
        outcome of the operation.

        @returns A tuple of the form `(image_name, attributes)`, where
            `image_name` is the name of the image installed, and
            `attributes` is new attributes to be applied to the DUT.
        """
        server_name = ""
        if not self._public_bucket:
            server_name = dev_server.get_resolved_hostname(self.update_url)
            metrics.Counter(_metric_name('install')).increment(
                    fields={'devserver': server_name})

        try:
            self._prepare_host()
        except _AttributedUpdateError:
            raise
        except Exception as e:
            logging.exception('Failure preparing host prior to update.')
            raise HostUpdateError(self.host.hostname, str(e))

        try:
            expected_kernel = self._install_update()
        except _AttributedUpdateError:
            raise
        except Exception as e:
            logging.exception('Failure during download and install.')
            raise ImageInstallError(self.host.hostname, server_name, str(e))

        # Servohost will handle post update process themselves.
        if not self._is_servohost:
            try:
                self._complete_update(expected_kernel)
            except _AttributedUpdateError:
                raise
            except Exception as e:
                logging.exception('Failure from build after update.')
                raise NewBuildUpdateError(self.update_version, str(e))

        image_name = url_to_image_name(self.update_url)
        # update_url is different from devserver url needed to stage autotest
        # packages, therefore, resolve a new devserver url here.
        devserver_url = dev_server.ImageServer.resolve(
                image_name, self.host.hostname).url()
        repo_url = tools.get_package_url(devserver_url, image_name)
        return image_name, {ds_constants.JOB_REPO_URL: repo_url}
