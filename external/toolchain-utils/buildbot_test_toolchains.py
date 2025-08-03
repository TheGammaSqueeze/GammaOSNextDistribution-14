#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Copyright 2016 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script for running nightly compiler tests on ChromeOS.

This script launches a buildbot to build ChromeOS with the latest compiler on
a particular board; then it finds and downloads the trybot image and the
corresponding official image, and runs crosperf performance tests comparing
the two.  It then generates a report, emails it to the c-compiler-chrome, as
well as copying the images into the seven-day reports directory.
"""

# Script to test different toolchains against ChromeOS benchmarks.


import argparse
import datetime
import os
import re
import shutil
import sys
import time

from cros_utils import buildbot_utils
from cros_utils import command_executer
from cros_utils import logger


CROSTC_ROOT = "/usr/local/google/crostc"
NIGHTLY_TESTS_DIR = os.path.join(CROSTC_ROOT, "nightly-tests")
ROLE_ACCOUNT = "mobiletc-prebuild"
TOOLCHAIN_DIR = os.path.dirname(os.path.realpath(__file__))
TMP_TOOLCHAIN_TEST = "/tmp/toolchain-tests"
MAIL_PROGRAM = "~/var/bin/mail-detective"
PENDING_ARCHIVES_DIR = os.path.join(CROSTC_ROOT, "pending_archives")
NIGHTLY_TESTS_RESULTS = os.path.join(CROSTC_ROOT, "nightly_test_reports")

IMAGE_DIR = "{board}-{image_type}"
IMAGE_VERSION_STR = r"{chrome_version}-{tip}\.{branch}\.{branch_branch}"
IMAGE_FS = IMAGE_DIR + "/" + IMAGE_VERSION_STR
TRYBOT_IMAGE_FS = IMAGE_FS + "-{build_id}"
IMAGE_RE_GROUPS = {
    "board": r"(?P<board>\S+)",
    "image_type": r"(?P<image_type>\S+)",
    "chrome_version": r"(?P<chrome_version>R\d+)",
    "tip": r"(?P<tip>\d+)",
    "branch": r"(?P<branch>\d+)",
    "branch_branch": r"(?P<branch_branch>\d+)",
    "build_id": r"(?P<build_id>b\d+)",
}
TRYBOT_IMAGE_RE = TRYBOT_IMAGE_FS.format(**IMAGE_RE_GROUPS)

RECIPE_IMAGE_FS = IMAGE_FS + "-{build_id}-{buildbucket_id}"
RECIPE_IMAGE_RE_GROUPS = {
    "board": r"(?P<board>\S+)",
    "image_type": r"(?P<image_type>\S+)",
    "chrome_version": r"(?P<chrome_version>R\d+)",
    "tip": r"(?P<tip>\d+)",
    "branch": r"(?P<branch>\d+)",
    "branch_branch": r"(?P<branch_branch>\d+)",
    "build_id": r"(?P<build_id>\d+)",
    "buildbucket_id": r"(?P<buildbucket_id>\d+)",
}
RECIPE_IMAGE_RE = RECIPE_IMAGE_FS.format(**RECIPE_IMAGE_RE_GROUPS)

# CL that uses LLVM-Next to build the images (includes chrome).
USE_LLVM_NEXT_PATCH = "513590"


class ToolchainComparator(object):
    """Class for doing the nightly tests work."""

    def __init__(
        self,
        board,
        remotes,
        chromeos_root,
        weekday,
        patches,
        recipe=False,
        test=False,
        noschedv2=False,
        chrome_src="",
    ):
        self._board = board
        self._remotes = remotes
        self._chromeos_root = chromeos_root
        self._chrome_src = chrome_src
        self._base_dir = os.getcwd()
        self._ce = command_executer.GetCommandExecuter()
        self._l = logger.GetLogger()
        self._build = "%s-release-tryjob" % board
        self._patches = patches.split(",") if patches else []
        self._patches_string = "_".join(str(p) for p in self._patches)
        self._recipe = recipe
        self._test = test
        self._noschedv2 = noschedv2

        if not weekday:
            self._weekday = time.strftime("%a")
        else:
            self._weekday = weekday
        self._date = datetime.date.today().strftime("%Y/%m/%d")
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H:%M:%S")
        self._reports_dir = os.path.join(
            TMP_TOOLCHAIN_TEST if self._test else NIGHTLY_TESTS_RESULTS,
            "%s.%s" % (timestamp, board),
        )

    def _GetVanillaImageName(self, trybot_image):
        """Given a trybot artifact name, get latest vanilla image name.

        Args:
          trybot_image: artifact name such as
            'daisy-release-tryjob/R40-6394.0.0-b1389'
            for recipe images, name is in this format:
            'lulu-llvm-next-nightly/R84-13037.0.0-31011-8883172717979984032/'

        Returns:
          Latest official image name, e.g. 'daisy-release/R57-9089.0.0'.
        """
        # For board names with underscores, we need to fix the trybot image name
        # to replace the hyphen (for the recipe builder) with the underscore.
        # Currently the only such board we use is 'veyron_tiger'.
        if trybot_image.find("veyron-tiger") != -1:
            trybot_image = trybot_image.replace("veyron-tiger", "veyron_tiger")
        # We need to filter out -tryjob in the trybot_image.
        if self._recipe:
            trybot = re.sub("-llvm-next-nightly", "-release", trybot_image)
            mo = re.search(RECIPE_IMAGE_RE, trybot)
        else:
            trybot = re.sub("-tryjob", "", trybot_image)
            mo = re.search(TRYBOT_IMAGE_RE, trybot)
        assert mo
        dirname = IMAGE_DIR.replace("\\", "").format(**mo.groupdict())
        return buildbot_utils.GetLatestImage(self._chromeos_root, dirname)

    def _TestImages(self, trybot_image, vanilla_image):
        """Create crosperf experiment file.

        Given the names of the trybot, vanilla and non-AFDO images, create the
        appropriate crosperf experiment file and launch crosperf on it.
        """
        if self._test:
            experiment_file_dir = TMP_TOOLCHAIN_TEST
        else:
            experiment_file_dir = os.path.join(NIGHTLY_TESTS_DIR, self._weekday)
        experiment_file_name = "%s_toolchain_experiment.txt" % self._board

        compiler_string = "llvm"
        if USE_LLVM_NEXT_PATCH in self._patches_string:
            experiment_file_name = "%s_llvm_next_experiment.txt" % self._board
            compiler_string = "llvm_next"

        experiment_file = os.path.join(
            experiment_file_dir, experiment_file_name
        )
        experiment_header = """
    board: %s
    remote: %s
    retries: 1
    """ % (
            self._board,
            self._remotes,
        )
        # TODO(b/244607231): Add graphic benchmarks removed in crrev.com/c/3869851.
        experiment_tests = """
    benchmark: all_toolchain_perf {
      suite: telemetry_Crosperf
      iterations: 5
      run_local: False
    }

    benchmark: loading.desktop {
      suite: telemetry_Crosperf
      test_args: --story-tag-filter=typical
      iterations: 3
      run_local: False
    }

    benchmark: platform.ReportDiskUsage {
      suite: tast
      iterations: 1
      run_local: False
    }
    """

        with open(experiment_file, "w", encoding="utf-8") as f:
            f.write(experiment_header)
            f.write(experiment_tests)

            # Now add vanilla to test file.
            official_image = """
      vanilla_image {
        chromeos_root: %s
        chrome_src: %s
        build: %s
        compiler: llvm
      }
      """ % (
                self._chromeos_root,
                self._chrome_src,
                vanilla_image,
            )
            f.write(official_image)

            label_string = "%s_trybot_image" % compiler_string

            # Reuse autotest files from vanilla image for trybot images
            autotest_files = os.path.join(
                "/tmp", vanilla_image, "autotest_files"
            )
            experiment_image = """
      %s {
        chromeos_root: %s
        chrome_src: %s
        build: %s
        autotest_path: %s
        compiler: %s
      }
      """ % (
                label_string,
                self._chromeos_root,
                self._chrome_src,
                trybot_image,
                autotest_files,
                compiler_string,
            )
            f.write(experiment_image)

        crosperf = os.path.join(TOOLCHAIN_DIR, "crosperf", "crosperf")
        noschedv2_opts = "--noschedv2" if self._noschedv2 else ""
        no_email = not self._test
        command = (
            f"{crosperf} --no_email={no_email} "
            f"--results_dir={self._reports_dir} --logging_level=verbose "
            f"--json_report=True {noschedv2_opts} {experiment_file}"
        )

        return self._ce.RunCommand(command)

    def _SendEmail(self):
        """Find email message generated by crosperf and send it."""
        filename = os.path.join(self._reports_dir, "msg_body.html")
        if os.path.exists(filename) and os.path.exists(
            os.path.expanduser(MAIL_PROGRAM)
        ):
            email_title = "buildbot llvm test results"
            if USE_LLVM_NEXT_PATCH in self._patches_string:
                email_title = "buildbot llvm_next test results"
            command = 'cat %s | %s -s "%s, %s %s" -team -html' % (
                filename,
                MAIL_PROGRAM,
                email_title,
                self._board,
                self._date,
            )
            self._ce.RunCommand(command)

    def _CopyJson(self):
        # Make sure a destination directory exists.
        os.makedirs(PENDING_ARCHIVES_DIR, exist_ok=True)
        # Copy json report to pending archives directory.
        command = "cp %s/*.json %s/." % (
            self._reports_dir,
            PENDING_ARCHIVES_DIR,
        )
        ret = self._ce.RunCommand(command)
        # Failing to access json report means that crosperf terminated or all tests
        # failed, raise an error.
        if ret != 0:
            raise RuntimeError(
                "Crosperf failed to run tests, cannot copy json report!"
            )

    def DoAll(self):
        """Main function inside ToolchainComparator class.

        Launch trybot, get image names, create crosperf experiment file, run
        crosperf, and copy images into seven-day report directories.
        """
        if self._recipe:
            print("Using recipe buckets to get latest image.")
            # crbug.com/1077313: Some boards are not consistently
            # spelled, having underscores in some places and dashes in others.
            # The image directories consistenly use dashes, so convert underscores
            # to dashes to work around this.
            trybot_image = buildbot_utils.GetLatestRecipeImage(
                self._chromeos_root,
                "%s-llvm-next-nightly" % self._board.replace("_", "-"),
            )
        else:
            # Launch tryjob and wait to get image location.
            buildbucket_id, trybot_image = buildbot_utils.GetTrybotImage(
                self._chromeos_root,
                self._build,
                self._patches,
                tryjob_flags=["--notests"],
                build_toolchain=True,
            )
            print(
                "trybot_url: \
            http://cros-goldeneye/chromeos/healthmonitoring/buildDetails?buildbucketId=%s"
                % buildbucket_id
            )

        if not trybot_image:
            self._l.LogError("Unable to find trybot_image!")
            return 2

        vanilla_image = self._GetVanillaImageName(trybot_image)

        print("trybot_image: %s" % trybot_image)
        print("vanilla_image: %s" % vanilla_image)

        ret = self._TestImages(trybot_image, vanilla_image)
        # Always try to send report email as crosperf will generate report when
        # tests partially succeeded.
        if not self._test:
            self._SendEmail()
            self._CopyJson()
        # Non-zero ret here means crosperf tests partially failed, raise error here
        # so that toolchain summary report can catch it.
        if ret != 0:
            raise RuntimeError("Crosperf tests partially failed!")

        return 0


def Main(argv):
    """The main function."""

    # Common initializations
    command_executer.InitCommandExecuter()
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--remote", dest="remote", help="Remote machines to run tests on."
    )
    parser.add_argument(
        "--board", dest="board", default="x86-zgb", help="The target board."
    )
    parser.add_argument(
        "--chromeos_root",
        dest="chromeos_root",
        help="The chromeos root from which to run tests.",
    )
    parser.add_argument(
        "--chrome_src",
        dest="chrome_src",
        default="",
        help="The path to the source of chrome. "
        "This is used to run telemetry benchmarks. "
        "The default one is the src inside chroot.",
    )
    parser.add_argument(
        "--weekday",
        default="",
        dest="weekday",
        help="The day of the week for which to run tests.",
    )
    parser.add_argument(
        "--patch",
        dest="patches",
        help="The patches to use for the testing, "
        "seprate the patch numbers with ',' "
        "for more than one patches.",
    )
    parser.add_argument(
        "--noschedv2",
        dest="noschedv2",
        action="store_true",
        default=False,
        help="Pass --noschedv2 to crosperf.",
    )
    parser.add_argument(
        "--recipe",
        dest="recipe",
        default=True,
        help="Use images generated from recipe rather than"
        "launching tryjob to get images.",
    )
    parser.add_argument(
        "--test",
        dest="test",
        default=False,
        help="Test this script on local desktop, "
        "disabling mobiletc checking and email sending."
        "Artifacts stored in /tmp/toolchain-tests",
    )

    options = parser.parse_args(argv[1:])
    if not options.board:
        print("Please give a board.")
        return 1
    if not options.remote:
        print("Please give at least one remote machine.")
        return 1
    if not options.chromeos_root:
        print("Please specify the ChromeOS root directory.")
        return 1
    if options.test:
        print("Cleaning local test directory for this script.")
        if os.path.exists(TMP_TOOLCHAIN_TEST):
            shutil.rmtree(TMP_TOOLCHAIN_TEST)
        os.mkdir(TMP_TOOLCHAIN_TEST)

    fc = ToolchainComparator(
        options.board,
        options.remote,
        options.chromeos_root,
        options.weekday,
        options.patches,
        options.recipe,
        options.test,
        options.noschedv2,
        chrome_src=options.chrome_src,
    )
    return fc.DoAll()


if __name__ == "__main__":
    retval = Main(sys.argv)
    sys.exit(retval)
