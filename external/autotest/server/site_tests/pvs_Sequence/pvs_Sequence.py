# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server.cros.pvs import sequence


class pvs_Sequence(sequence.test_sequence):
    """
    pvs_Sequence implements a test_sequence (wrapper to test.test), instrumenting
    a series of tests and surfacing their results independently
    """

    version = 1

    def initialize(self, **args_dict):
        """
        initialize implements the initialize call in test.test, and is called before
        execution of the test.
        """
        super(pvs_Sequence, self).initialize(sequence=args_dict['sequence'])
