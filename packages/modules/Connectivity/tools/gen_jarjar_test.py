#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import gen_jarjar
import unittest


class TestGenJarjar(unittest.TestCase):
    def test_gen_rules(self):
        args = gen_jarjar.parse_arguments([
            "jarjar-rules-generator-testjavalib.jar",
            "--prefix", "jarjar.prefix",
            "--output", "test-output-rules.txt",
            "--apistubs", "framework-connectivity.stubs.module_lib.jar",
            "--unsupportedapi", ":testdata/test-unsupportedappusage.txt",
            "--excludes", "testdata/test-jarjar-excludes.txt",
        ])
        gen_jarjar.make_jarjar_rules(args)

        with open(args.output) as out:
            lines = out.readlines()

        self.maxDiff = None
        self.assertListEqual([
            'rule android.net.IpSecTransform jarjar.prefix.@0\n',
            'rule android.net.IpSecTransformTest jarjar.prefix.@0\n',
            'rule android.net.IpSecTransformTest$* jarjar.prefix.@0\n',
            'rule test.unsupportedappusage.OtherUnsupportedUsageClass jarjar.prefix.@0\n',
            'rule test.unsupportedappusage.OtherUnsupportedUsageClassTest jarjar.prefix.@0\n',
            'rule test.unsupportedappusage.OtherUnsupportedUsageClassTest$* jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClassTest jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClassTest$* jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClass jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClassTest jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClassTest$* jarjar.prefix.@0\n'
        ], lines)

    def test_gen_rules_repeated_args(self):
        args = gen_jarjar.parse_arguments([
            "jarjar-rules-generator-testjavalib.jar",
            "--prefix", "jarjar.prefix",
            "--output", "test-output-rules.txt",
            "--apistubs", "framework-connectivity.stubs.module_lib.jar",
            "--apistubs", "framework-connectivity-t.stubs.module_lib.jar",
            "--unsupportedapi",
            "testdata/test-unsupportedappusage.txt:testdata/test-other-unsupportedappusage.txt",
            "--excludes", "testdata/test-jarjar-excludes.txt",
        ])
        gen_jarjar.make_jarjar_rules(args)

        with open(args.output) as out:
            lines = out.readlines()

        self.maxDiff = None
        self.assertListEqual([
            'rule test.utils.TestUtilClass jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClassTest jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClassTest$* jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClass jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClassTest jarjar.prefix.@0\n',
            'rule test.utils.TestUtilClass$TestInnerClassTest$* jarjar.prefix.@0\n'], lines)


if __name__ == '__main__':
    # Need verbosity=2 for the test results parser to find results
    unittest.main(verbosity=2)
