#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for rust_watch.py."""

import logging
import pathlib
import subprocess
import time
import unittest
import unittest.mock

from cros_utils import tiny_render
import rust_watch


class Test(unittest.TestCase):
    """Tests."""

    def _silence_logs(self):
        """Silences all log output until the end of the current test."""

        def should_log(_record):
            return 0

        logger = logging.root
        logger.addFilter(should_log)
        self.addCleanup(logger.removeFilter, should_log)

    def test_release_version_parsing(self):
        self.assertEqual(
            rust_watch.RustReleaseVersion.from_string("1.2.3"),
            rust_watch.RustReleaseVersion(1, 2, 3),
        )

    def test_release_version_json_round_trips(self):
        ver = rust_watch.RustReleaseVersion(1, 2, 3)
        self.assertEqual(
            rust_watch.RustReleaseVersion.from_json(ver.to_json()), ver
        )

    def test_state_json_round_trips(self):
        state = rust_watch.State(
            last_seen_release=rust_watch.RustReleaseVersion(1, 2, 3),
            last_gentoo_sha="abc123",
        )

        self.assertEqual(rust_watch.State.from_json(state.to_json()), state)

    @unittest.mock.patch.object(subprocess, "run")
    @unittest.mock.patch.object(time, "sleep")
    def test_update_git_repo_tries_again_on_failure(self, sleep_mock, run_mock):
        self._silence_logs()

        oh_no_error = ValueError("oh no")

        def check_returncode():
            raise oh_no_error

        run_call_count = 0

        def run_sideeffect(*_args, **_kwargs):
            nonlocal run_call_count
            run_call_count += 1
            result = unittest.mock.Mock()
            result.returncode = 1
            result.check_returncode = check_returncode
            return result

        run_mock.side_effect = run_sideeffect

        with self.assertRaises(ValueError) as raised:
            rust_watch.update_git_repo(pathlib.Path("/does/not/exist/at/all"))

        self.assertIs(raised.exception, oh_no_error)
        self.assertEqual(run_call_count, 5)

        sleep_timings = [unittest.mock.call(60 * i) for i in range(1, 5)]
        self.assertEqual(sleep_mock.mock_calls, sleep_timings)

    @unittest.mock.patch.object(subprocess, "run")
    def test_get_new_gentoo_commits_functions(self, run_mock):
        returned = unittest.mock.Mock()
        returned.returncode = 0
        returned.stdout = "\n".join(
            (
                "abc123 newer commit",
                "abcdef and an older commit",
            )
        )
        run_mock.return_value = returned
        results = rust_watch.get_new_gentoo_commits(
            pathlib.Path("/does/not/exist/at/all"), "defabc"
        )
        self.assertEqual(
            results,
            [
                rust_watch.GitCommit("abcdef", "and an older commit"),
                rust_watch.GitCommit("abc123", "newer commit"),
            ],
        )

    def test_compose_email_on_a_new_gentoo_commit(self):
        sha_a = "a" * 40
        new_commit = rust_watch.maybe_compose_email(
            new_gentoo_commits=[
                rust_watch.GitCommit(
                    sha=sha_a,
                    subject="summary_a",
                ),
            ],
        )

        self.assertEqual(
            new_commit,
            (
                "[rust-watch] new rust ebuild commit detected",
                [
                    "commit:",
                    tiny_render.UnorderedList(
                        [
                            [
                                tiny_render.Link(
                                    rust_watch.gentoo_sha_to_link(sha_a),
                                    sha_a[:12],
                                ),
                                ": summary_a",
                            ],
                        ]
                    ),
                ],
            ),
        )

    def test_compose_email_composes_nothing_when_no_new_updates_exist(self):
        self.assertIsNone(rust_watch.maybe_compose_email(new_gentoo_commits=()))

    def test_compose_bug_creates_bugs_on_new_versions(self):
        bug_body_start = "A new Rust stable release has been detected;"
        title, body = rust_watch.maybe_compose_bug(
            old_state=rust_watch.State(
                last_seen_release=rust_watch.RustReleaseVersion(1, 0, 0),
                last_gentoo_sha="",
            ),
            newest_release=rust_watch.RustReleaseVersion(1, 0, 1),
        )
        self.assertEqual(title, "[Rust] Update to 1.0.1")
        self.assertTrue(body.startswith(bug_body_start))

        title, body = rust_watch.maybe_compose_bug(
            old_state=rust_watch.State(
                last_seen_release=rust_watch.RustReleaseVersion(1, 0, 0),
                last_gentoo_sha="",
            ),
            newest_release=rust_watch.RustReleaseVersion(1, 1, 0),
        )
        self.assertEqual(title, "[Rust] Update to 1.1.0")
        self.assertTrue(body.startswith(bug_body_start))

        title, body = rust_watch.maybe_compose_bug(
            old_state=rust_watch.State(
                last_seen_release=rust_watch.RustReleaseVersion(1, 0, 0),
                last_gentoo_sha="",
            ),
            newest_release=rust_watch.RustReleaseVersion(2, 0, 0),
        )
        self.assertEqual(title, "[Rust] Update to 2.0.0")
        self.assertTrue(body.startswith(bug_body_start))

    def test_compose_bug_does_nothing_when_no_new_updates_exist(self):
        self.assertIsNone(
            rust_watch.maybe_compose_bug(
                old_state=rust_watch.State(
                    last_seen_release=rust_watch.RustReleaseVersion(1, 0, 0),
                    last_gentoo_sha="",
                ),
                newest_release=rust_watch.RustReleaseVersion(1, 0, 0),
            )
        )


if __name__ == "__main__":
    unittest.main()
