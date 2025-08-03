#!/usr/bin/python3
#
# Copyright (C) 2022 The Android Open Source Project
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

import json
import os
import sys
import textwrap

import common

_TEST_DIR = "build/orchestrator/test_workspace/inner_tree_1"
_DEMO_RULES_TEMPLATE = """\
rule compile_c
    command = mkdir -p ${out_dir} && g++ -c ${cflags} -o ${out} ${in}
rule link_so
    command = mkdir -p ${out_dir} && gcc -shared -o ${out} ${in}
build %(OUT_DIR)s/libhello1/hello1.o: compile_c %(TEST_DIR)s/libhello1/hello1.c
    out_dir = %(OUT_DIR)s/libhello1
    cflags = -I%(TEST_DIR)s/libhello1/include
build %(OUT_DIR)s/libhello1/libhello1.so: link_so %(OUT_DIR)s/libhello1/hello1.o
    out_dir = %(OUT_DIR)s/libhello1
build system: phony %(OUT_DIR)s/libhello1/libhello1.so
"""


class InnerBuildDemo(common.Commands):
    def export_api_contributions(self, args):
        contributions_dir = os.path.join(args.out_dir, "api_contributions")
        os.makedirs(contributions_dir, exist_ok=True)

        if "system" in args.api_domain:
            reply = dict(name="publicapi",
                         api_domain="system",
                         version=1,
                         cc_libraries=[
                             dict(
                                 name="libhello1",
                                 api=f"{_TEST_DIR}/libhello1.map.txt",
                                 api_surfaces=["publicapi"],
                                 headers=[
                                     dict(
                                         arch="",
                                         headers=[f"{_TEST_DIR}/hello1.h"],
                                         root=f"{_TEST_DIR}",
                                         name="libhello1_headers.contribution",
                                         system=False)
                                 ],
                             )
                         ])
            with open(os.path.join(contributions_dir, "api_a-1.json"),
                      "w",
                      encoding='iso-8859-1') as f:
                json.dump(reply, f, indent=4)
                return

    def analyze(self, args):
        if "system" in args.api_domain:
            # Nothing to export in this demo
            # Write a fake inner_tree.ninja; what the inner tree would have
            # generated.
            with open(os.path.join(args.out_dir, "inner_tree.ninja"),
                      "w",
                      encoding='iso-8859-1') as f:
                f.write(
                    textwrap.dedent(_DEMO_RULES_TEMPLATE % {
                        "OUT_DIR": args.out_dir,
                        "TEST_DIR": _TEST_DIR
                    }))
            with open(os.path.join(args.out_dir, "build_targets.json"),
                      "w",
                      encoding='iso-8859-1') as f:
                f.write(
                    textwrap.dedent("""\
                {
                    "staging": [
                        {
                            "dest": "staging/system/lib/libhello1.so",
                            "obj": "libhello1/libhello1.so"
                        }
                    ]
                }"""))


def main(argv):
    return InnerBuildDemo().Run(argv)


if __name__ == "__main__":
    sys.exit(main(sys.argv))

# vim: sts=4:ts=4:sw=4
