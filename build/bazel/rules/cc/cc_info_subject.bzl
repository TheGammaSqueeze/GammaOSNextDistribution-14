# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a cocc of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""CcInfo testing subject."""

load("@rules_testing//lib:truth.bzl", "subjects")

def cc_info_subject(info, *, meta):
    """Creates a new `CcInfoSubject` for a CcInfo provider instance.

    Method: CcInfoSubject.new

    Args:
        info: The CcInfo object
        meta: ExpectMeta object.

    Returns:
        A `CcInfoSubject` struct
    """

    # buildifier: disable=uninitialized
    public = struct(
        # go/keep-sorted start
        headers = lambda *a, **k: _cc_info_subject_headers(self, *a, **k),
        includes = lambda *a, **k: _cc_info_subject_includes(self, *a, **k),
        system_includes = lambda *a, **k: _cc_info_subject_system_includes(self, *a, **k),
        # go/keep-sorted end
    )
    self = struct(
        actual = info,
        meta = meta,
    )
    return public

def _cc_info_subject_includes(self):
    """Returns a `CollectionSubject` for the `includes` attribute.

    Method: CcInfoSubject.includes
    """
    return subjects.collection(
        self.actual.compilation_context.includes,
        meta = self.meta.derive("includes()"),
    )

def _cc_info_subject_system_includes(self):
    """Returns a `CollectionSubject` for the `system_includes` attribute.

    Method: CcInfoSubject.system_includes
    """
    return subjects.collection(
        self.actual.compilation_context.system_includes,
        meta = self.meta.derive("system_includes()"),
    )

def _cc_info_subject_headers(self):
    """Returns a `CollectionSubject` for the `headers` attribute.

    Method: CcInfoSubject.headers
    """
    return subjects.depset_file(
        self.actual.compilation_context.headers,
        meta = self.meta.derive("headers()"),
    )
