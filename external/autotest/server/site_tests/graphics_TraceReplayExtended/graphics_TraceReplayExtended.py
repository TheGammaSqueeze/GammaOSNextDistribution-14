# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Implementation of the graphics_TraceReplayExtended server test."""

from autotest_lib.server.cros.graphics.graphics_tracereplayextended import (
    GraphicsTraceReplayExtendedBase)


class graphics_TraceReplayExtended(GraphicsTraceReplayExtendedBase):
    """Autotest server test for running repeated trace replays in Crostini."""
    version = 1

    def run_once(self, *args, **kwargs):
        kwargs['client_tast_test'] = 'graphics.TraceReplayExtended.' + kwargs[
            'client_tast_test']
        kwargs.setdefault('pdash_note', 'vm:crostini')
        super(graphics_TraceReplayExtended, self).run_once(*args, **kwargs)
