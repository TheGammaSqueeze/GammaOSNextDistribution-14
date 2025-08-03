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

import os


def analyze_trees(context, inner_trees):
    inner_trees.for_each_tree(run_analysis)


def run_analysis(tree_key, inner_tree, cookie):
    """Call inner_build analyze."""
    context = inner_tree.context
    cmd = ["analyze"]

    # Pass the abspath of the inner_tree.  The nsjail config will change
    # directory to this path at invocation.
    cmd.extend(["--inner_tree", os.path.join(os.getcwd(), inner_tree.root)])

    # Pass the api_surfaces directory.
    cmd.extend(["--api_surfaces_dir",
                context.out.api_surfaces_dir(base=context.out.Base.ORIGIN)])

    inner_tree.invoke(cmd)

    # Save the environment variables used for the inner-tree build.
    inner_tree.set_env_used()
