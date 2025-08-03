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

import os
import subprocess
import unittest
import time

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
class TestHidlJava(unittest.TestCase):
    pass

def cleanup(cmd):
    binary = cmd.split()[0]
    run_cmd("adb shell killall %s >/dev/null 2>&1" % binary, ignore_error=True)

def make_test(client, server):
    def test(self):
        try:
            env = "CLASSPATH=/data/framework/hidl_test_java_java.jar"

            cleanup(client)
            cleanup(server)
            run_cmd("adb shell \"( %s %s -s ) </dev/null >/dev/null 2>&1 &\"" % (env, server))
            time.sleep(2)
            run_cmd("adb shell %s %s -c" % (env, client))
        finally:
            cleanup(client)
            cleanup(server)
    return test

def has_bitness(bitness):
    return 0 == run_cmd("echo '[[ \"$(getprop ro.product.cpu.abilist%s)\" != \"\" ]]' | adb shell sh" % bitness, ignore_error=True)

if __name__ == '__main__':
    cmds = ["app_process /data/framework com.android.commands.hidl_test_java.HidlTestJava"]

    if has_bitness(32):
        cmds += ["/data/nativetest/hidl_test_java_native/hidl_test_java_native"]

    if has_bitness(64):
        cmds += ["/data/nativetest64/hidl_test_java_native/hidl_test_java_native"]

    assert len(cmds) >= 2

    def short_name(cmd):
        if "app" in cmd:
            return "java"
        if "64" in cmd:
            return "64"
        return "32"

    for client in cmds:
        for server in cmds:
            test_name = 'test_%s_to_%s' % (short_name(client), short_name(server))
            test = make_test(client, server)
            setattr(TestHidlJava, test_name, test)

    suite = unittest.TestLoader().loadTestsFromTestCase(TestHidlJava)
    unittest.TextTestRunner(verbosity=2).run(suite)
