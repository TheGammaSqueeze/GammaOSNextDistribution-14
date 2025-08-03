#!/usr/bin/env python
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""XML RPC server for multimedia testing."""

import argparse
import code
import logging
import os
import six.moves.xmlrpc_client
import sys
import traceback

import common   # pylint: disable=unused-import
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import logging_config
from autotest_lib.client.cros import constants
from autotest_lib.client.cros import upstart
from autotest_lib.client.cros import xmlrpc_server
from autotest_lib.client.cros.multimedia import assistant_facade
from autotest_lib.client.cros.multimedia import audio_facade
from autotest_lib.client.cros.multimedia import browser_facade
from autotest_lib.client.cros.multimedia import cfm_facade
from autotest_lib.client.cros.multimedia import display_facade
from autotest_lib.client.cros.multimedia import facade_resource
from autotest_lib.client.cros.multimedia import graphics_facade
from autotest_lib.client.cros.multimedia import input_facade
from autotest_lib.client.cros.multimedia import kiosk_facade
from autotest_lib.client.cros.multimedia import system_facade
from autotest_lib.client.cros.multimedia import usb_facade
from autotest_lib.client.cros.multimedia import video_facade

# Python3 required for the following:
if sys.version_info[0] >= 3:
    from autotest_lib.client.cros.multimedia import bluetooth_facade


class MultimediaXmlRpcDelegate(xmlrpc_server.XmlRpcDelegate):
    """XML RPC delegate for multimedia testing."""

    def __init__(self, resource):
        """Initializes the facade objects."""

        # TODO: (crbug.com/618111) Add test driven switch for
        # supporting arc_mode enabled or disabled. At this time
        # if ARC build is tested, arc_mode is always enabled.
        arc_res = None
        if utils.get_board_property('CHROMEOS_ARC_VERSION'):
            logging.info('Using ARC resource on ARC enabled board.')
            from autotest_lib.client.cros.multimedia import arc_resource
            arc_res = arc_resource.ArcResource()

        self._facades = {
                'assistant':
                assistant_facade.AssistantFacadeLocal(resource),
                'audio':
                audio_facade.AudioFacadeLocal(resource,
                                                      arc_resource=arc_res),
                'video':
                video_facade.VideoFacadeLocal(resource,
                                                      arc_resource=arc_res),
                'display':
                display_facade.DisplayFacadeLocal(resource),
                'system':
                system_facade.SystemFacadeLocal(),
                'usb':
                usb_facade.USBFacadeLocal(),
                'browser':
                browser_facade.BrowserFacadeLocal(resource),
                'input':
                input_facade.InputFacadeLocal(),
                'cfm_main_screen':
                cfm_facade.CFMFacadeLocal(resource, 'hotrod'),
                'cfm_mimo_screen':
                cfm_facade.CFMFacadeLocal(resource, 'control'),
                'kiosk':
                kiosk_facade.KioskFacadeLocal(resource),
                'graphics':
                graphics_facade.GraphicsFacadeLocal()
        }

        # Limit some facades to python3
        if sys.version_info[0] >= 3:
            self._facades[
                    'bluetooth'] = bluetooth_facade.BluezFacadeLocal()
            self._facades['floss'] = bluetooth_facade.FlossFacadeLocal(
            )

    def __exit__(self, exception, value, traceback):
        """Clean up the resources."""
        self._facades['audio'].cleanup()

        if 'floss' in self._facades:
            self._facades['floss'].cleanup()

    def _dispatch(self, method, params):
        """Dispatches the method to the proper facade.

        We turn off allow_dotted_names option. The method handles the dot
        and dispatches the method to the proper native facade, like
        DisplayFacadeLocal.

        """
        try:
            try:
                if '.' not in method:
                    func = getattr(self, method)
                else:
                    facade_name, method_name = method.split('.', 1)
                    if facade_name in self._facades:
                        func = getattr(self._facades[facade_name], method_name)
                    else:
                        raise Exception('unknown facade: %s' % facade_name)
            except AttributeError:
                raise Exception('method %s not supported' % method)

            logging.info('Dispatching method %s with args %s',
                         str(func), str(params))
            return func(*params)
        except:
            # TODO(ihf): Try to return meaningful stacktraces from the client.
            return traceback.format_exc()


def config_logging():
    """Configs logging to be verbose and use console handler."""
    config = logging_config.LoggingConfig()
    config.configure_logging(use_console=True, verbose=True)


def main():
    """The main function, to run the XMLRPC server."""
    parser = argparse.ArgumentParser()
    parser.add_argument('-d', '--debug', action='store_true', required=False,
                        help=('create a debug console with a ServerProxy "s" '
                              'connecting to the XML RPC sever at localhost'))
    args = parser.parse_args()
    pid = os.getpid()

    if args.debug:
        s = six.moves.xmlrpc_client.ServerProxy('http://localhost:%d' %
                                  constants.MULTIMEDIA_XMLRPC_SERVER_PORT,
                                  allow_none=True)
        code.interact(local=locals())
    else:
        config_logging()
        logging.debug('multimedia_xmlrpc_server[%s] main...', pid)
        xmlrpc_server.terminate_old(__file__)

        # bind before full setup, so the error is the last thing in the log
        server = xmlrpc_server.XmlRpcServer(
                'localhost', constants.MULTIMEDIA_XMLRPC_SERVER_PORT)

        # Restart Cras to clean up any audio activities.
        upstart.restart_job('cras')

        with facade_resource.FacadeResource() as res:
            server.register_delegate(MultimediaXmlRpcDelegate(res))
            server.run()
            logging.debug('multimedia_xmlrpc_server[%s] exiting...', pid)
        logging.debug('multimedia_xmlrpc_server[%s] done.\n', pid)


if __name__ == '__main__':
    main()
