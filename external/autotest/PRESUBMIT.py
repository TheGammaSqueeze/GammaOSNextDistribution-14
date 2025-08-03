# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


def CommonChecks(input_api, output_api):
    """Check common to presubmit / preupload."""
    results = []

    # Python formatting issues are errors.
    results += input_api.canned_checks.CheckPatchFormatted(
            input_api,
            output_api,
            # Setting check_python=None ensures that only files with a
            # .style.yapf in a parent directory are checked. This effectively
            # skips most of the legacy autotest codebase that is very poorly
            # formatted.
            check_python=None,
            check_clang_format=False,
            result_factory=output_api.PresubmitError)
    return results


CheckChangeOnUpload = CommonChecks
CheckChangeOnCommit = CommonChecks
