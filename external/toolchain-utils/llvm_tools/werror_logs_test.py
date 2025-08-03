#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for werror_logs.py."""

import io
import logging
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
from typing import Dict
import unittest
from unittest import mock

import werror_logs


class SilenceLogs:
    """Used by Test.silence_logs to ignore all logging output."""

    def filter(self, _record):
        return False


def create_warning_info(packages: Dict[str, int]) -> werror_logs.WarningInfo:
    """Constructs a WarningInfo conveniently in one line.

    Mostly useful because `WarningInfo` has a defaultdict field, and those
    don't `assertEqual` to regular dict fields.
    """
    x = werror_logs.WarningInfo()
    x.packages.update(packages)
    return x


class Test(unittest.TestCase):
    """Tests for werror_logs."""

    def silence_logs(self):
        f = SilenceLogs()
        log = logging.getLogger()
        log.addFilter(f)
        self.addCleanup(log.removeFilter, f)

    def make_tempdir(self) -> Path:
        tempdir = tempfile.mkdtemp("werror_logs_test_")
        self.addCleanup(shutil.rmtree, tempdir)
        return Path(tempdir)

    def test_clang_warning_parsing_parses_flag_errors(self):
        self.assertEqual(
            werror_logs.ClangWarning.try_parse_line(
                "clang-17: error: optimization flag -foo is not supported "
                "[-Werror,-Wfoo]"
            ),
            werror_logs.ClangWarning(
                name="-Wfoo",
                message="optimization flag -foo is not supported",
                location=None,
            ),
        )

    def test_clang_warning_parsing_doesnt_care_about_werror_order(self):
        self.assertEqual(
            werror_logs.ClangWarning.try_parse_line(
                "clang-17: error: optimization flag -foo is not supported "
                "[-Wfoo,-Werror]"
            ),
            werror_logs.ClangWarning(
                name="-Wfoo",
                message="optimization flag -foo is not supported",
                location=None,
            ),
        )

    def test_clang_warning_parsing_parses_code_errors(self):
        self.assertEqual(
            werror_logs.ClangWarning.try_parse_line(
                "/path/to/foo/bar/baz.cc:12:34: error: don't do this "
                "[-Werror,-Wbar]"
            ),
            werror_logs.ClangWarning(
                name="-Wbar",
                message="don't do this",
                location=werror_logs.ClangWarningLocation(
                    file="/path/to/foo/bar/baz.cc",
                    line=12,
                    column=34,
                ),
            ),
        )

    def test_clang_warning_parsing_parses_implicit_errors(self):
        self.assertEqual(
            werror_logs.ClangWarning.try_parse_line(
                # N.B., "-Werror" is missing in this message
                "/path/to/foo/bar/baz.cc:12:34: error: don't do this "
                "[-Wbar]"
            ),
            werror_logs.ClangWarning(
                name="-Wbar",
                message="don't do this",
                location=werror_logs.ClangWarningLocation(
                    file="/path/to/foo/bar/baz.cc",
                    line=12,
                    column=34,
                ),
            ),
        )

    def test_clang_warning_parsing_canonicalizes_correctly(self):
        canonical_forms = (
            ("/build/foo/bar/baz.cc", "/build/{board}/bar/baz.cc"),
            ("///build//foo///bar//baz.cc", "/build/{board}/bar/baz.cc"),
            ("/build/baz.cc", "/build/baz.cc"),
            ("/build.cc", "/build.cc"),
            (".", "."),
        )

        for before, after in canonical_forms:
            self.assertEqual(
                werror_logs.ClangWarning.try_parse_line(
                    f"{before}:12:34: error: don't do this [-Werror,-Wbar]",
                    canonicalize_board_root=True,
                ),
                werror_logs.ClangWarning(
                    name="-Wbar",
                    message="don't do this",
                    location=werror_logs.ClangWarningLocation(
                        file=after,
                        line=12,
                        column=34,
                    ),
                ),
            )

    def test_clang_warning_parsing_doesnt_canonicalize_if_not_asked(self):
        self.assertEqual(
            werror_logs.ClangWarning.try_parse_line(
                "/build/foo/bar/baz.cc:12:34: error: don't do this "
                "[-Werror,-Wbar]",
                canonicalize_board_root=False,
            ),
            werror_logs.ClangWarning(
                name="-Wbar",
                message="don't do this",
                location=werror_logs.ClangWarningLocation(
                    file="/build/foo/bar/baz.cc",
                    line=12,
                    column=34,
                ),
            ),
        )

    def test_clang_warning_parsing_skips_uninteresting_lines(self):
        self.silence_logs()

        pointless = (
            "",
            "foo",
            "error: something's wrong",
            "clang-14: warning: something's wrong [-Wsomething]",
            "clang-14: error: something's wrong",
        )
        for line in pointless:
            self.assertIsNone(
                werror_logs.ClangWarning.try_parse_line(line), line
            )

    def test_aggregation_correctly_scrapes_warnings(self):
        aggregated = werror_logs.AggregatedWarnings()
        aggregated.add_report_json(
            {
                "cwd": "/var/tmp/portage/sys-devel/llvm/foo/bar",
                "stdout": textwrap.dedent(
                    """\
                    Foo
                    clang-17: error: failed to blah [-Werror,-Wblah]
                    /path/to/file.cc:1:2: error: other error [-Werror,-Wother]
                    """
                ),
            }
        )
        aggregated.add_report_json(
            {
                "cwd": "/var/tmp/portage/sys-devel/llvm/foo/bar",
                "stdout": textwrap.dedent(
                    """\
                    Foo
                    clang-17: error: failed to blah [-Werror,-Wblah]
                    /path/to/file.cc:1:3: error: other error [-Werror,-Wother]
                    Bar
                    """
                ),
            }
        )

        self.assertEqual(aggregated.num_reports, 2)
        self.assertEqual(
            dict(aggregated.warnings),
            {
                werror_logs.ClangWarning(
                    name="-Wblah",
                    message="failed to blah",
                    location=None,
                ): create_warning_info(
                    packages={"sys-devel/llvm": 2},
                ),
                werror_logs.ClangWarning(
                    name="-Wother",
                    message="other error",
                    location=werror_logs.ClangWarningLocation(
                        file="/path/to/file.cc",
                        line=1,
                        column=2,
                    ),
                ): create_warning_info(
                    packages={"sys-devel/llvm": 1},
                ),
                werror_logs.ClangWarning(
                    name="-Wother",
                    message="other error",
                    location=werror_logs.ClangWarningLocation(
                        file="/path/to/file.cc",
                        line=1,
                        column=3,
                    ),
                ): create_warning_info(
                    packages={"sys-devel/llvm": 1},
                ),
            },
        )

    def test_aggregation_guesses_packages_correctly(self):
        aggregated = werror_logs.AggregatedWarnings()
        cwds = (
            "/var/tmp/portage/sys-devel/llvm/foo/bar",
            "/var/cache/portage/sys-devel/llvm/foo/bar",
            "/build/amd64-host/var/tmp/portage/sys-devel/llvm/foo/bar",
            "/build/amd64-host/var/cache/portage/sys-devel/llvm/foo/bar",
        )
        for d in cwds:
            # If the directory isn't recognized, this will raise.
            aggregated.add_report_json(
                {
                    "cwd": d,
                    "stdout": "clang-17: error: foo [-Werror,-Wfoo]",
                }
            )

        self.assertEqual(len(aggregated.warnings), 1)
        warning, warning_info = next(iter(aggregated.warnings.items()))
        self.assertEqual(warning.name, "-Wfoo")
        self.assertEqual(
            warning_info, create_warning_info({"sys-devel/llvm": len(cwds)})
        )

    def test_aggregation_raises_if_package_name_cant_be_guessed(self):
        aggregated = werror_logs.AggregatedWarnings()
        with self.assertRaises(werror_logs.UnknownPackageNameError):
            aggregated.add_report_json({})

    def test_warning_by_flag_summarization_works_in_simple_case(self):
        string_io = io.StringIO()
        werror_logs.summarize_warnings_by_flag(
            {
                werror_logs.ClangWarning(
                    name="-Wother",
                    message="other error",
                    location=werror_logs.ClangWarningLocation(
                        file="/path/to/some/file.cc",
                        line=1,
                        column=2,
                    ),
                ): create_warning_info(
                    {
                        "sys-devel/llvm": 3000,
                        "sys-devel/gcc": 1,
                    }
                ),
                werror_logs.ClangWarning(
                    name="-Wother",
                    message="other error",
                    location=werror_logs.ClangWarningLocation(
                        file="/path/to/some/file.cc",
                        line=1,
                        column=3,
                    ),
                ): create_warning_info(
                    {
                        "sys-devel/llvm": 1,
                    }
                ),
            },
            file=string_io,
        )
        result = string_io.getvalue()
        self.assertEqual(
            result,
            textwrap.dedent(
                """\
                ## Instances of each fatal warning:
                \t-Wother: 3,002
                """
            ),
        )

    def test_warning_by_package_summarization_works_in_simple_case(self):
        string_io = io.StringIO()
        werror_logs.summarize_per_package_warnings(
            (
                create_warning_info(
                    {
                        "sys-devel/llvm": 3000,
                        "sys-devel/gcc": 1,
                    }
                ),
                create_warning_info(
                    {
                        "sys-devel/llvm": 1,
                    }
                ),
            ),
            file=string_io,
        )
        result = string_io.getvalue()
        self.assertEqual(
            result,
            textwrap.dedent(
                """\
                ## Per-package warning counts:
                \tsys-devel/llvm: 3,001
                \t sys-devel/gcc:     1
                """
            ),
        )

    def test_cq_builder_determination_works(self):
        self.assertEqual(
            werror_logs.cq_builder_name_from_werror_logs_path(
                "gs://chromeos-image-archive/staryu-cq/"
                "R123-15771.0.0-94466-8756713501925941617/"
                "staryu.20240207.fatal_clang_warnings.tar.xz"
            ),
            "staryu-cq",
        )

    @mock.patch.object(subprocess, "run")
    def test_tarball_downloading_works(self, run_mock):
        tempdir = self.make_tempdir()
        unpack_dir = tempdir / "unpack"
        download_dir = tempdir / "download"

        gs_urls = [
            "gs://foo/bar-cq/build-number/123.fatal_clang_warnings.tar.xz",
            "gs://foo/baz-cq/build-number/124.fatal_clang_warnings.tar.xz",
            "gs://foo/qux-cq/build-number/125.fatal_clang_warnings.tar.xz",
        ]
        named_gs_urls = [
            (werror_logs.cq_builder_name_from_werror_logs_path(x), x)
            for x in gs_urls
        ]
        werror_logs.download_and_unpack_werror_tarballs(
            unpack_dir, download_dir, gs_urls
        )

        # Just verify that this executed the correct commands. Normally this is
        # a bit fragile, but given that this function internally is pretty
        # complex (starting up a threadpool, etc), extra checking is nice.
        want_gsutil_commands = [
            [
                "gsutil",
                "cp",
                gs_url,
                download_dir / name / os.path.basename(gs_url),
            ]
            for name, gs_url in named_gs_urls
        ]
        want_untar_commands = [
            ["tar", "xaf", gsutil_command[-1]]
            for gsutil_command in want_gsutil_commands
        ]

        cmds = []
        for call_args in run_mock.call_args_list:
            call_positional_args = call_args[0]
            cmd = call_positional_args[0]
            cmds.append(cmd)
        cmds.sort()
        self.assertEqual(
            cmds, sorted(want_gsutil_commands + want_untar_commands)
        )

    @mock.patch.object(subprocess, "run")
    def test_tarball_downloading_fails_if_exceptions_are_raised(self, run_mock):
        self.silence_logs()

        def raise_exception(*_args, check=False, **_kwargs):
            self.assertTrue(check)
            raise subprocess.CalledProcessError(returncode=1, cmd=[])

        run_mock.side_effect = raise_exception
        tempdir = self.make_tempdir()
        unpack_dir = tempdir / "unpack"
        download_dir = tempdir / "download"

        gs_urls = [
            "gs://foo/bar-cq/build-number/123.fatal_clang_warnings.tar.xz",
            "gs://foo/baz-cq/build-number/124.fatal_clang_warnings.tar.xz",
            "gs://foo/qux-cq/build-number/125.fatal_clang_warnings.tar.xz",
        ]
        with self.assertRaisesRegex(ValueError, r"3 download\(s\) failed"):
            werror_logs.download_and_unpack_werror_tarballs(
                unpack_dir, download_dir, gs_urls
            )
        self.assertEqual(run_mock.call_count, 3)


if __name__ == "__main__":
    unittest.main()
