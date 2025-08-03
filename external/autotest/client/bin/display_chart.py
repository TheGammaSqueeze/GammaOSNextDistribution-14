# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

'''Login with test account and display chart file using telemetry.'''

import argparse
import contextlib
import json
import logging
import os
import select
import signal
import sys
import tempfile

# Set chart process preferred logging format before overridden by importing
# common package.
logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s - %(levelname)s - %(message)s')

# This sets up import paths for autotest.
sys.path.append('/usr/local/autotest/bin')
import common
from autotest_lib.client.bin import utils
from autotest_lib.client.cros import constants
from autotest_lib.client.cros.multimedia import display_facade as display_facade_lib
from autotest_lib.client.cros.multimedia import facade_resource
from autotest_lib.client.common_lib.cros import chrome

DEFAULT_DISPLAY_LEVEL = 96.0


class Fifo:
    """Fifo to communicate with chart service."""

    FIFO_POLL_TIMEOUT_MS = 300

    def __init__(self):
        self._ready = False

    def __enter__(self):
        # Prepare fifo file.
        self._tmpdir = tempfile.mkdtemp(prefix='chart_fifo_', dir='/tmp')
        self._path = os.path.join(self._tmpdir, 'fifo')
        os.mkfifo(self._path)

        # Hook SIGINT signal to stop fifo.
        self._original_sig_handler = signal.getsignal(signal.SIGINT)

        def handler(a, b):
            signal.signal(signal.SIGINT, self._original_sig_handler)
            self._ready = False

        signal.signal(signal.SIGINT, handler)

        self._ready = True
        return self

    def __exit__(self, exc_type, exc_value, exc_traceback):
        signal.signal(signal.SIGINT, self._original_sig_handler)
        os.unlink(self._path)
        os.rmdir(self._tmpdir)

    def get_path(self):
        return self._path

    def read(self):
        """Read json format command from fifo."""
        while self._ready:
            with os.fdopen(os.open(self._path, os.O_RDONLY | os.O_NONBLOCK),
                           'r') as fd:
                p = select.poll()
                p.register(fd, select.POLLIN)
                if p.poll(self.FIFO_POLL_TIMEOUT_MS):
                    cmd = fd.read()
                    return json.loads(cmd)
        return None


@contextlib.contextmanager
def control_brightness():
    """Help to programmatically control the brightness.

    Returns:
      A function which can set brightness between [0.0, 100.0].
    """

    def set_brightness(display_level):
        utils.system('backlight_tool --set_brightness_percent=%s' %
                     display_level)
        logging.info('Set display brightness to %r', display_level)

    original_display_level = utils.system_output(
            'backlight_tool --get_brightness_percent')
    logging.info('Save original display brightness %r', original_display_level)

    utils.system('stop powerd', ignore_status=True)
    yield set_brightness
    logging.info('Restore display brightness %r', original_display_level)
    utils.system('start powerd', ignore_status=True)
    set_brightness(original_display_level)


@contextlib.contextmanager
def control_display(cr):
    """Fix the display orientation instead of using gyro orientation."""
    board = utils.get_board()
    logging.info("Board:%s", board)
    if board == 'scarlet':
        DISPLAY_ORIENTATION = 90
    else:
        DISPLAY_ORIENTATION = 0

    logging.info('Set fullscreen.')
    facade = facade_resource.FacadeResource(cr)
    display_facade = display_facade_lib.DisplayFacadeLocal(facade)
    display_facade.set_fullscreen(True)

    logging.info('Fix screen rotation %d.', DISPLAY_ORIENTATION)
    internal_display_id = display_facade.get_internal_display_id()
    original_display_orientation = display_facade.get_display_rotation(
            internal_display_id)
    display_facade.set_display_rotation(internal_display_id,
                                        rotation=DISPLAY_ORIENTATION)
    yield
    display_facade.set_display_rotation(internal_display_id,
                                        rotation=original_display_orientation)


def display(chart_path, display_level):
    """Display chart on device by using telemetry."""
    chart_path = os.path.abspath(chart_path)
    if os.path.isfile(chart_path):
        first_chart_name = os.path.basename(chart_path)
        chart_dir_path = os.path.dirname(chart_path)
    elif os.path.isdir(chart_path):
        first_chart_name = None
        chart_dir_path = chart_path
    else:
        assert False, 'chart_path %r not found.' % chart_path

    def show_chart(name):
        """Show image on chart base on file name"""
        filepath = os.path.join(chart_dir_path, name)
        logging.info('Display chart file of path %r.', filepath)
        tab = cr.browser.tabs[0]
        tab.Navigate(cr.browser.platform.http_server.UrlOf(filepath))
        tab.WaitForDocumentReadyStateToBeComplete()

    logging.info('Setup SIGINT listener for stop displaying.')

    with chrome.Chrome(
            extension_paths=[constants.DISPLAY_TEST_EXTENSION],
            autotest_ext=True,
            init_network_controller=True) as cr, \
            control_brightness() as set_brightness, \
            control_display(cr), \
            Fifo() as fifo:
        set_brightness(display_level)

        cr.browser.platform.SetHTTPServerDirectories(chart_dir_path)
        if first_chart_name is not None:
            show_chart(first_chart_name)

        logging.info('Chart is ready. Fifo: %s', fifo.get_path())
        # Flush the 'is ready' message for server test to sync with ready state.
        sys.stdout.flush()
        sys.stderr.flush()

        while True:
            cmd = fifo.read()
            if cmd is None:
                break
            new_chart_name = cmd.get('chart_name')
            if new_chart_name is not None:
                show_chart(new_chart_name)

            new_display_level = cmd.get('display_level')
            if new_display_level is not None:
                set_brightness(new_display_level)


if __name__ == '__main__':
    argparser = argparse.ArgumentParser(
            description='Display chart file on chrome by using telemetry.'
            ' Send SIGINT or keyboard interrupt to stop displaying.')
    argparser.add_argument(
            'chart_path',
            help='Path of displayed chart file'
            ' or the directory to put chart files for displaying in fifo mode.'
    )
    argparser.add_argument(
            '--display_level',
            type=float,
            default=DEFAULT_DISPLAY_LEVEL,
            help=
            'Set brightness as linearly-calculated percent in [0.0, 100.0].')

    args = argparser.parse_args()
    display(args.chart_path, args.display_level)
