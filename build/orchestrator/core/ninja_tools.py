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

import collections
import os
import sys

# Workaround for python include path
# TODO: restructure things so that we do not need this.
# pylint: disable=wrong-import-position,import-error
_ninja_dir = os.path.realpath(
    os.path.join(os.path.dirname(__file__), "..", "ninja"))
if _ninja_dir not in sys.path:
    sys.path.append(_ninja_dir)
import ninja_writer
from ninja_syntax import BuildAction, Rule
# pylint: enable=wrong-import-position,import-error


class Ninja(ninja_writer.Writer):
    """Some higher level constructs on top of raw ninja writing.

    TODO: Not sure where these should be.
    """

    def __init__(self, context, file, **kwargs):
        super().__init__(
            file,
            builddir=context.out.root(base=context.out.Base.OUTER),
            **kwargs)
        self._context = context
        self._did_copy_file = False
        self._write_rule = None
        self._phonies = collections.defaultdict(set)
        self._acp = self._context.tools.acp()

    def add_copy_file(self, copy_to, copy_from):
        self.add_rule(
            Rule("copy_file", [
                ("command",
                 f"mkdir -p ${{out_dir}} && {self._acp} -f ${{in}} ${{out}}")
            ]))
        self.add_build_action(
            BuildAction(output=copy_to,
                        rule="copy_file",
                        inputs=[copy_from],
                        implicits=[self._acp],
                        variables=[("out_dir", os.path.dirname(copy_to))]))

    def add_global_phony(self, name, deps):
        """Add a global phony target.

        This should be used when there are multiple places that will want to add
        to the same phony, with possibly different dependencies. The resulting
        dependency list will be the combined dependency list.

        If you can, to save memory, use add_phony instead of this function.
        """
        assert isinstance(deps, (set, list, tuple)), f"bad type: {type(deps)}"
        self._phonies[name] |= set(deps)

    def write(self, *args, **kwargs):
        """Write the file, including our global phonies."""
        for phony, deps in self._phonies.items():
            self.add_phony(phony, deps)
        super().write(*args, **kwargs)

    def add_write_file(self, filepath: str, content: str):
        """Writes the content as a string to filepath

        The content is written as-is, special characters are not escaped
        """
        self.add_rule(
            Rule("write_file", [("description", "Writes content to out"),
                                ("command", "printf '${content}' > ${out}")]))

        self.add_build_action(
            BuildAction(output=filepath,
                        rule="write_file",
                        variables=[("content", content)]))
