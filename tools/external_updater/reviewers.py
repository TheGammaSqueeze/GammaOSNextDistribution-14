# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Find main reviewers for git push commands."""

from collections.abc import MutableMapping
import math
import random
from typing import List, Set, Union

# To randomly pick one of multiple reviewers, we put them in a List[str]
# to work with random.choice efficiently.
# To pick all of multiple reviewers, we use a Set[str].

# A ProjMapping maps a project path string to
# (1) a single reviewer email address as a string, or
# (2) a List of multiple reviewers to be randomly picked, or
# (3) a Set of multiple reviewers to be all added.
ProjMapping = MutableMapping[str, Union[str, List[str], Set[str]]]

# Rust crate owners (reviewers).
RUST_CRATE_OWNERS: ProjMapping = {
    'rust/crates/anyhow': 'mmaurer@google.com',
    # more rust crate owners could be added later
    # if so, consider modifying the quotas in RUST_REVIEWERS
}

PROJ_REVIEWERS: ProjMapping = {
    # define non-rust project reviewers here
}

# Combine all roject reviewers.
PROJ_REVIEWERS.update(RUST_CRATE_OWNERS)

# Reviewers for external/rust/crates projects not found in PROJ_REVIEWER.
# Each person has a quota, the number of projects to review.
# The sum of these quotas should ideally be at least the number of Rust
# projects, but this only matters if we have many entries in RUST_CRATE_OWNERS,
# as we subtract a person's owned crates from their quota.
RUST_REVIEWERS: dict[str, float] = {
    'ivanlozano@google.com': 20,
    'jeffv@google.com': 20,
    'mmaurer@google.com': 20,
    'srhines@google.com': 20,
    'tweek@google.com': 20,
    # If a Rust reviewer needs to take a vacation, comment out the line,
    # and distribute the quota to other reviewers.
}


# pylint: disable=invalid-name
def add_proj_count(projects: MutableMapping[str, float], reviewer: str, n: float) -> None:
    """Add n to the number of projects owned by the reviewer."""
    if reviewer in projects:
        projects[reviewer] += n
    else:
        projects[reviewer] = n


# Random Rust reviewers are selected from RUST_REVIEWER_LIST,
# which is created from RUST_REVIEWERS and PROJ_REVIEWERS.
# A person P in RUST_REVIEWERS will occur in the RUST_REVIEWER_LIST N times,
# if N = RUST_REVIEWERS[P] - (number of projects owned by P in PROJ_REVIEWERS)
# is greater than 0. N is rounded up by math.ceil.
def create_rust_reviewer_list() -> List[str]:
    """Create a list of duplicated reviewers for weighted random selection."""
    # Count number of projects owned by each reviewer.
    rust_reviewers = set(RUST_REVIEWERS.keys())
    projects: dict[str, float] = {}  # map from owner to number of owned projects
    for value in PROJ_REVIEWERS.values():
        if isinstance(value, str):  # single reviewer for a project
            add_proj_count(projects, value, 1)
            continue
        # multiple reviewers share one project, count only rust_reviewers
        reviewers = set(filter(lambda x: x in rust_reviewers, value))
        if reviewers:
            count = 1.0 / len(reviewers)  # shared among all reviewers
            for name in reviewers:
                add_proj_count(projects, name, count)
    result = []
    for (x, n) in RUST_REVIEWERS.items():
        if x in projects:  # reduce x's quota by the number of assigned ones
            n = n - projects[x]
        if n > 0:
            result.extend([x] * math.ceil(n))
    if result:
        return result
    # Something was wrong or quotas were too small so that nobody
    # was selected from the RUST_REVIEWERS. Select everyone!!
    return list(RUST_REVIEWERS.keys())


RUST_REVIEWER_LIST: List[str] = create_rust_reviewer_list()


def find_reviewers(proj_path: str) -> str:
    """Returns an empty string or a reviewer parameter(s) for git push."""
    index = proj_path.find('/external/')
    if index >= 0:  # full path
        proj_path = proj_path[(index + len('/external/')):]
    elif proj_path.startswith('external/'):  # relative path
        proj_path = proj_path[len('external/'):]
    if proj_path in PROJ_REVIEWERS:
        reviewers = PROJ_REVIEWERS[proj_path]
        # pylint: disable=isinstance-second-argument-not-valid-type
        if isinstance(reviewers, List):  # pick any one reviewer
            return 'r=' + random.choice(reviewers)
        if isinstance(reviewers, Set):  # add all reviewers in sorted order
            return ','.join(map(lambda x: 'r=' + x, sorted(reviewers)))
        # reviewers must be a string
        return 'r=' + reviewers
    if proj_path.startswith('rust/crates/'):
        return 'r=' + random.choice(RUST_REVIEWER_LIST)
    return ''
