# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file has been automatically generated. Do not edit!

AUTHOR = 'ARC++ Team'
NAME = 'cheets_CTS_R.internal.x86.CtsWindowManager.L'
ATTRIBUTES = 'suite:arc-cts-r, suite:arc-cts'
DEPENDENCIES = 'arc, cts_abi_x86'
JOB_RETRIES = 1
TEST_TYPE = 'server'
TIME = 'MEDIUM'
MAX_RESULT_SIZE_KB = 512000
PY_VERSION = 3
DOC = 'Run module CtsWindowManager.L of the Android Compatibility Test Suite (CTS) using x86 ABI in the ARC++ container.'

def run_TS(machine):
    host_list = [hosts.create_host(machine)]
    job.run_test(
        'cheets_CTS_R',
        hosts=host_list,
        iterations=1,
        tag='internal.x86.CtsWindowManager.L',
        test_name='cheets_CTS_R.internal.x86.CtsWindowManager.L',
        run_template=['run', 'commandAndExit', 'cts', '--include-filter', 'CtsWindowManagerDeviceTestCases android.server.wm.LayoutTests', '--include-filter', 'CtsWindowManagerDeviceTestCases android.server.wm.LocationInWindowTests', '--include-filter', 'CtsWindowManagerDeviceTestCases android.server.wm.LocationOnScreenTests', '--logcat-on-failure'],
        retry_template=['run', 'commandAndExit', 'retry', '--retry', '{session_id}'],
        target_module='CtsWindowManagerDeviceTestCases',
        target_plan=None,
        bundle='x86',
        uri='LATEST',
        use_jdk9=True,
        timeout=3600)

parallel_simple(run_TS, machines)
