#!/usr/bin/env python3
# Copyright 2018 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unittest for buildbot_utils.py."""


import time
import unittest
from unittest.mock import patch

from cros_utils import buildbot_utils
from cros_utils import command_executer


class TrybotTest(unittest.TestCase):
    """Test for CommandExecuter class."""

    tryjob_out = (
        '[{"buildbucket_id": "8952721143823688176", "build_config": '
        '"cave-llvm-toolchain-tryjob", "url": '
        # pylint: disable=line-too-long
        '"http://cros-goldeneye/chromeos/healthmonitoring/buildDetails?buildbucketId=8952721143823688176"}]'
    )

    GSUTILS_LS = "\n".join(
        [
            "gs://chromeos-image-archive/{0}/R78-12421.0.0/",
            "gs://chromeos-image-archive/{0}/R78-12422.0.0/",
            "gs://chromeos-image-archive/{0}/R78-12423.0.0/",
        ]
    )

    GSUTILS_LS_RECIPE = "\n".join(
        [
            "gs://chromeos-image-archive/{0}/R83-12995.0.0-30031-8885075268947031/",
            "gs://chromeos-image-archive/{0}/R83-13003.0.0-30196-8884755532184725/",
            "gs://chromeos-image-archive/{0}/R83-13003.0.0-30218-8884712858556419/",
        ]
    )

    buildresult_out = (
        '{"8952721143823688176": {"status": "pass", "artifacts_url":'
        '"gs://chromeos-image-archive/trybot-elm-release-tryjob/R67-10468.0.0-'
        'b20789"}}'
    )

    buildbucket_id = "8952721143823688176"
    counter_1 = 10

    def testGetTrybotImage(self):
        with patch.object(buildbot_utils, "SubmitTryjob") as mock_submit:
            with patch.object(buildbot_utils, "PeekTrybotImage") as mock_peek:
                with patch.object(time, "sleep", return_value=None):

                    def peek(_chromeos_root, _buildbucket_id):
                        self.counter_1 -= 1
                        if self.counter_1 >= 0:
                            return ("running", "")
                        return (
                            "pass",
                            "gs://chromeos-image-archive/trybot-elm-release-tryjob/"
                            "R67-10468.0.0-b20789",
                        )

                    mock_peek.side_effect = peek
                    mock_submit.return_value = self.buildbucket_id

                    # sync
                    buildbucket_id, image = buildbot_utils.GetTrybotImage(
                        "/tmp", "falco-release-tryjob", []
                    )
                    self.assertEqual(buildbucket_id, self.buildbucket_id)
                    self.assertEqual(
                        "trybot-elm-release-tryjob/" "R67-10468.0.0-b20789",
                        image,
                    )

                    # async
                    buildbucket_id, image = buildbot_utils.GetTrybotImage(
                        "/tmp", "falco-release-tryjob", [], asynchronous=True
                    )
                    self.assertEqual(buildbucket_id, self.buildbucket_id)
                    self.assertEqual(" ", image)

    def testSubmitTryjob(self):
        with patch.object(
            command_executer.CommandExecuter, "RunCommandWOutput"
        ) as mocked_run:
            mocked_run.return_value = (0, self.tryjob_out, "")
            buildbucket_id = buildbot_utils.SubmitTryjob(
                "/", "falco-release-tryjob", [], []
            )
            self.assertEqual(buildbucket_id, self.buildbucket_id)

    def testPeekTrybotImage(self):
        with patch.object(
            command_executer.CommandExecuter, "RunCommandWOutput"
        ) as mocked_run:
            # pass
            mocked_run.return_value = (0, self.buildresult_out, "")
            status, image = buildbot_utils.PeekTrybotImage(
                "/", self.buildbucket_id
            )
            self.assertEqual("pass", status)
            self.assertEqual(
                "gs://chromeos-image-archive/trybot-elm-release-tryjob/"
                "R67-10468.0.0-b20789",
                image,
            )

            # running
            mocked_run.return_value = (1, "", "")
            status, image = buildbot_utils.PeekTrybotImage(
                "/", self.buildbucket_id
            )
            self.assertEqual("running", status)
            self.assertEqual(None, image)

            # fail
            buildresult_fail = self.buildresult_out.replace('"pass"', '"fail"')
            mocked_run.return_value = (0, buildresult_fail, "")
            status, image = buildbot_utils.PeekTrybotImage(
                "/", self.buildbucket_id
            )
            self.assertEqual("fail", status)
            self.assertEqual(
                "gs://chromeos-image-archive/trybot-elm-release-tryjob/"
                "R67-10468.0.0-b20789",
                image,
            )

    def testParseTryjobBuildbucketId(self):
        buildbucket_id = buildbot_utils.ParseTryjobBuildbucketId(
            self.tryjob_out
        )
        self.assertEqual(buildbucket_id, self.buildbucket_id)

    def testGetLatestImageValid(self):
        with patch.object(
            command_executer.CommandExecuter, "ChrootRunCommandWOutput"
        ) as mocked_run:
            with patch.object(
                buildbot_utils, "DoesImageExist"
            ) as mocked_imageexist:
                IMAGE_DIR = "lulu-release"
                mocked_run.return_value = (
                    0,
                    self.GSUTILS_LS.format(IMAGE_DIR),
                    "",
                )
                mocked_imageexist.return_value = True
                image = buildbot_utils.GetLatestImage("", IMAGE_DIR)
                self.assertEqual(image, f"{IMAGE_DIR}/R78-12423.0.0")

    def testGetLatestImageInvalid(self):
        with patch.object(
            command_executer.CommandExecuter, "ChrootRunCommandWOutput"
        ) as mocked_run:
            with patch.object(
                buildbot_utils, "DoesImageExist"
            ) as mocked_imageexist:
                IMAGE_DIR = "kefka-release"
                mocked_run.return_value = (
                    0,
                    self.GSUTILS_LS.format(IMAGE_DIR),
                    "",
                )
                mocked_imageexist.return_value = False
                image = buildbot_utils.GetLatestImage("", IMAGE_DIR)
                self.assertIsNone(image)

    def testGetLatestRecipeImageValid(self):
        with patch.object(
            command_executer.CommandExecuter, "ChrootRunCommandWOutput"
        ) as mocked_run:
            with patch.object(
                buildbot_utils, "DoesImageExist"
            ) as mocked_imageexist:
                IMAGE_DIR = "lulu-llvm-next-nightly"
                mocked_run.return_value = (
                    0,
                    self.GSUTILS_LS_RECIPE.format(IMAGE_DIR),
                    "",
                )
                mocked_imageexist.return_value = True
                image = buildbot_utils.GetLatestRecipeImage("", IMAGE_DIR)
                self.assertEqual(
                    image,
                    f"{IMAGE_DIR}/R83-13003.0.0-30218-8884712858556419",
                )

    def testGetLatestRecipeImageInvalid(self):
        with patch.object(
            command_executer.CommandExecuter, "ChrootRunCommandWOutput"
        ) as mocked_run:
            with patch.object(
                buildbot_utils, "DoesImageExist"
            ) as mocked_imageexist:
                IMAGE_DIR = "kefka-llvm-next-nightly"
                mocked_run.return_value = (
                    0,
                    self.GSUTILS_LS_RECIPE.format(IMAGE_DIR),
                    "",
                )
                mocked_imageexist.return_value = False
                image = buildbot_utils.GetLatestRecipeImage("", IMAGE_DIR)
                self.assertIsNone(image)

    def testGetLatestRecipeImageTwodays(self):
        with patch.object(
            command_executer.CommandExecuter, "ChrootRunCommandWOutput"
        ) as mocked_run:
            with patch.object(
                buildbot_utils, "DoesImageExist"
            ) as mocked_imageexist:
                IMAGE_DIR = "lulu-llvm-next-nightly"
                mocked_run.return_value = (
                    0,
                    self.GSUTILS_LS_RECIPE.format(IMAGE_DIR),
                    "",
                )
                mocked_imageexist.side_effect = [False, False, True]
                image = buildbot_utils.GetLatestRecipeImage("", IMAGE_DIR)
                self.assertIsNone(image)
                mocked_imageexist.side_effect = [False, True, True]
                image = buildbot_utils.GetLatestRecipeImage("", IMAGE_DIR)
                self.assertEqual(
                    image,
                    f"{IMAGE_DIR}/R83-13003.0.0-30196-8884755532184725",
                )


if __name__ == "__main__":
    unittest.main()
