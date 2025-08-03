#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import subprocess
import unittest

def run_cmd(cmd, ignore_error=False):
    print("Running command:", cmd)
    p = subprocess.Popen(cmd, shell=True)
    p.communicate()
    if not ignore_error and p.returncode:
        raise subprocess.CalledProcessError(p.returncode, cmd)
    return p.returncode

def has_hwservicemanager():
    # if the property is set, or hwservicemanager is missing, then we don't have
    # hwservicemanger running.
    return 0 != run_cmd("echo '[[ \"$(getprop hwservicemanager.disabled)\" == \"true\" ]] || " +
                        "[[ ! -f /system/bin/hwservicemanager ]]' | adb shell sh", ignore_error=True)

@unittest.skipUnless(has_hwservicemanager(), "no hwservicemanager")
class TestHidl(unittest.TestCase):
    pass

def make_test(client, server):
    def test(self):
        try:
            run_cmd("adb shell killall %s >/dev/null 2>&1" % client, ignore_error=True)
            run_cmd("adb shell killall %s >/dev/null 2>&1" % server, ignore_error=True)
            run_cmd("adb shell \"( %s ) </dev/null >/dev/null 2>&1 &\"" % server)
            run_cmd("adb shell %s" % client)
        finally:
            run_cmd("adb shell killall %s >/dev/null 2>&1" % client, ignore_error=True)
            run_cmd("adb shell killall %s >/dev/null 2>&1" % server, ignore_error=True)
    return test

def has_bitness(bitness):
    return 0 == run_cmd("echo '[[ \"$(getprop ro.product.cpu.abilist%s)\" != \"\" ]]' | adb shell sh" % bitness, ignore_error=True)

if __name__ == '__main__':
    clients = []
    servers = []

    if has_bitness(32):
        clients += ["/data/nativetest/hidl_test_client/hidl_test_client32"]
        servers += ["/data/nativetest/hidl_test_servers/hidl_test_servers32"]

    if has_bitness(64):
        clients += ["/data/nativetest64/hidl_test_client/hidl_test_client64"]
        servers += ["/data/nativetest64/hidl_test_servers/hidl_test_servers64"]

    assert len(clients) > 0
    assert len(servers) > 0

    def short_name(binary):
        if "64" in binary:
            return "64"
        return "32"

    for client in clients:
        for server in servers:
            test_name = 'test_%s_to_%s' % (short_name(client), short_name(server))
            test = make_test(client, server)
            setattr(TestHidl, test_name, test)

    suite = unittest.TestLoader().loadTestsFromTestCase(TestHidl)
    unittest.TextTestRunner(verbosity=2).run(suite)
