#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""Unit tests for mainline_modules_sdks.py."""
import dataclasses
import re
import typing
from pathlib import Path
import os
import shutil
import tempfile
import unittest
import zipfile
import json
from unittest import mock

import mainline_modules_sdks as mm

MAINLINE_MODULES_BY_APEX = dict(
    (m.apex, m) for m in (mm.MAINLINE_MODULES + mm.BUNDLED_MAINLINE_MODULES +
                          mm.PLATFORM_SDKS_FOR_MAINLINE))


@dataclasses.dataclass()
class FakeSnapshotBuilder(mm.SnapshotBuilder):
    """A fake snapshot builder that does not run the build.

    This skips the whole build process and just creates some fake sdk
    modules.
    """

    snapshots: typing.List[typing.Any] = dataclasses.field(default_factory=list)

    @staticmethod
    def create_sdk_library_files(z, name):
        z.writestr(f"sdk_library/public/{name}-removed.txt", "")
        z.writestr(f"sdk_library/public/{name}.srcjar", "")
        z.writestr(f"sdk_library/public/{name}-stubs.jar", "")
        z.writestr(f"sdk_library/public/{name}.txt",
                   "method public int testMethod(int);")

    def create_snapshot_file(self, out_dir, name, for_r_build):
        zip_file = Path(mm.sdk_snapshot_zip_file(out_dir, name))
        with zipfile.ZipFile(zip_file, "w") as z:
            z.writestr("Android.bp", "")
            if name.endswith("-sdk"):
                if for_r_build:
                    for library in for_r_build.sdk_libraries:
                        self.create_sdk_library_files(z, library.name)
                else:
                    self.create_sdk_library_files(z, re.sub(r"-.*$", "", name))

    def build_snapshots(self, build_release, modules):
        self.snapshots.append((build_release.name, build_release.soong_env,
                               [m.apex for m in modules]))
        # Create input file structure.
        sdks_out_dir = Path(self.mainline_sdks_dir).joinpath("test")
        sdks_out_dir.mkdir(parents=True, exist_ok=True)
        # Create a fake sdk zip file for each module.
        for module in modules:
            for sdk in module.sdks:
                self.create_snapshot_file(sdks_out_dir, sdk, module.for_r_build)
        return sdks_out_dir

    def get_art_module_info_file_data(self, sdk):
        info_file_data = f"""[
  {{
    "@type": "java_sdk_library",
    "@name": "art.module.public.api",
    "@deps": [
      "libcore_license"
    ],
    "dist_stem": "art",
    "scopes": {{
      "public": {{
        "current_api": "sdk_library/public/{re.sub(r"-.*$", "", sdk)}.txt",
        "latest_api": "{Path(self.mainline_sdks_dir).joinpath("test")}/prebuilts/sdk/art.api.public.latest/gen/art.api.public.latest",
        "latest_removed_api": "{Path(self.mainline_sdks_dir).joinpath("test")}/prebuilts/sdk/art-removed.api.public.latest/gen/art-removed.api.public.latest",
        "removed_api": "sdk_library/public/{re.sub(r"-.*$", "", sdk)}-removed.txt"
      }}
    }}
  }}
]
"""
        return info_file_data

    @staticmethod
    def write_data_to_file(file, data):
        with open(file, "w", encoding="utf8") as fd:
            fd.write(data)

    def create_snapshot_info_file(self, module, sdk_info_file, sdk):
        if module == MAINLINE_MODULES_BY_APEX["com.android.art"]:
            self.write_data_to_file(sdk_info_file,
                                    self.get_art_module_info_file_data(sdk))
        else:
            # For rest of the modules, generate an empty .info file.
            self.write_data_to_file(sdk_info_file, "[]")

    def get_module_extension_version(self):
        # Return any integer value indicating the module extension version for testing.
        return 5

    def build_sdk_scope_targets(self, build_release, modules):
        target_paths = []
        target_dict = {}
        for module in modules:
            for sdk in module.sdks:
                if "host-exports" in sdk or "test-exports" in sdk:
                    continue

                sdk_info_file = mm.sdk_snapshot_info_file(
                    Path(self.mainline_sdks_dir).joinpath("test"), sdk)
                self.create_snapshot_info_file(module, sdk_info_file, sdk)
                paths, dict_item = self.latest_api_file_targets(sdk_info_file)
                target_paths.extend(paths)
                target_dict[sdk_info_file] = dict_item

        for target_path in target_paths:
            os.makedirs(os.path.split(target_path)[0])
            if ".latest.extension_version" in target_path:
                self.write_data_to_file(
                    target_path, str(self.get_module_extension_version()))
            else:
               self.write_data_to_file(target_path, "")

        return target_dict


class TestProduceDist(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.tmp_out_dir = os.path.join(self.tmp_dir, "out")
        os.mkdir(self.tmp_out_dir)
        self.tmp_dist_dir = os.path.join(self.tmp_dir, "dist")
        os.mkdir(self.tmp_dist_dir)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def produce_dist(self, modules, build_releases):
        subprocess_runner = mm.SubprocessRunner()
        snapshot_builder = FakeSnapshotBuilder(
            tool_path="path/to/mainline_modules_sdks.sh",
            subprocess_runner=subprocess_runner,
            out_dir=self.tmp_out_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )
        producer.produce_dist(modules, build_releases)

    def list_files_in_dir(self, tmp_dist_dir):
        files = []
        for abs_dir, _, filenames in os.walk(tmp_dist_dir):
            rel_dir = os.path.relpath(abs_dir, tmp_dist_dir)
            if rel_dir == ".":
                rel_dir = ""
            for f in filenames:
                files.append(os.path.join(rel_dir, f))
        return files

    def test_unbundled_modules(self):
        # Create the out/soong/build_number.txt file that is copied into the
        # snapshots.
        self.create_build_number_file()

        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],
            MAINLINE_MODULES_BY_APEX["com.android.ipsec"],
            MAINLINE_MODULES_BY_APEX["com.android.tethering"],
            # Create a google specific module.
            mm.aosp_to_google(MAINLINE_MODULES_BY_APEX["com.android.wifi"]),
        ]
        build_releases = [
            mm.Q,
            mm.R,
            mm.S,
            mm.LATEST,
        ]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual(
            [
                # Build specific snapshots.
                "mainline-sdks/for-R-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-R-build/current/com.android.tethering/sdk/tethering-module-sdk-current.zip",
                "mainline-sdks/for-R-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                "mainline-sdks/for-S-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.android.tethering/sdk/tethering-module-sdk-current.zip",
                "mainline-sdks/for-S-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/gantry-metadata.json",
                "mainline-sdks/for-latest-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current-api-diff.txt",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.ipsec/gantry-metadata.json",
                "mainline-sdks/for-latest-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current-api-diff.txt",
                "mainline-sdks/for-latest-build/current/com.android.ipsec/sdk/ipsec-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.tethering/gantry-metadata.json",
                "mainline-sdks/for-latest-build/current/com.android.tethering/sdk/tethering-module-sdk-current-api-diff.txt",
                "mainline-sdks/for-latest-build/current/com.android.tethering/sdk/tethering-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.google.android.wifi/gantry-metadata.json",
                "mainline-sdks/for-latest-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current-api-diff.txt",
                "mainline-sdks/for-latest-build/current/com.google.android.wifi/sdk/wifi-module-sdk-current.zip",
            ],
            sorted(self.list_files_in_dir(self.tmp_dist_dir)))

        r_snaphot_dir = os.path.join(self.tmp_out_dir,
                                     "soong/mainline-sdks/test/for-R-build")
        aosp_ipsec_r_bp_file = "com.android.ipsec/sdk_library/Android.bp"
        aosp_tethering_r_bp_file = "com.android.tethering/sdk_library/Android.bp"
        google_wifi_android_bp = "com.google.android.wifi/sdk_library/Android.bp"
        self.assertEqual([
            aosp_ipsec_r_bp_file,
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike-removed.txt",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike-stubs.jar",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike.srcjar",
            "com.android.ipsec/sdk_library/public/android.net.ipsec.ike.txt",
            "com.android.ipsec/snapshot-creation-build-number.txt",
            aosp_tethering_r_bp_file,
            "com.android.tethering/sdk_library/public/framework-tethering-removed.txt",
            "com.android.tethering/sdk_library/public/framework-tethering-stubs.jar",
            "com.android.tethering/sdk_library/public/framework-tethering.srcjar",
            "com.android.tethering/sdk_library/public/framework-tethering.txt",
            "com.android.tethering/snapshot-creation-build-number.txt",
            google_wifi_android_bp,
            "com.google.android.wifi/sdk_library/public/framework-wifi-removed.txt",
            "com.google.android.wifi/sdk_library/public/framework-wifi-stubs.jar",
            "com.google.android.wifi/sdk_library/public/framework-wifi.srcjar",
            "com.google.android.wifi/sdk_library/public/framework-wifi.txt",
            "com.google.android.wifi/snapshot-creation-build-number.txt",
            "ipsec-module-sdk-current.zip",
            "tethering-module-sdk-current.zip",
            "wifi-module-sdk-current.zip",
        ], sorted(self.list_files_in_dir(r_snaphot_dir)))

        def read_r_snapshot_contents(path):
            abs_path = os.path.join(r_snaphot_dir, path)
            with open(abs_path, "r", encoding="utf8") as file:
                return file.read()

        # Check the contents of the AOSP ipsec module
        ipsec_contents = read_r_snapshot_contents(aosp_ipsec_r_bp_file)
        expected = read_test_data("ipsec_for_r_Android.bp")
        self.assertEqual(expected, ipsec_contents)

        # Check the contents of the AOSP tethering module
        tethering_contents = read_r_snapshot_contents(aosp_tethering_r_bp_file)
        expected = read_test_data("tethering_for_r_Android.bp")
        self.assertEqual(expected, tethering_contents)

        # Check the contents of the Google ipsec module
        wifi_contents = read_r_snapshot_contents(google_wifi_android_bp)
        expected = read_test_data("google_wifi_for_r_Android.bp")
        self.assertEqual(expected, wifi_contents)

    def test_old_release(self):
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],  # An unnbundled module
            MAINLINE_MODULES_BY_APEX["com.android.runtime"],  # A bundled module
            MAINLINE_MODULES_BY_APEX["platform-mainline"],  # Platform SDK
        ]
        build_releases = [mm.S]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual([
            "mainline-sdks/for-S-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
            "mainline-sdks/for-S-build/current/com.android.art/sdk/art-module-sdk-current.zip",
            "mainline-sdks/for-S-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
        ], sorted(self.list_files_in_dir(self.tmp_dist_dir)))

    def test_latest_release(self):
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],  # An unnbundled module
            MAINLINE_MODULES_BY_APEX["com.android.runtime"],  # A bundled module
            MAINLINE_MODULES_BY_APEX["platform-mainline"],  # Platform SDK
        ]
        build_releases = [mm.LATEST]
        self.produce_dist(modules, build_releases)

        # pylint: disable=line-too-long
        self.assertEqual(
            [
                # Bundled modules and platform SDKs.
                "bundled-mainline-sdks/com.android.runtime/host-exports/runtime-module-host-exports-current.zip",
                "bundled-mainline-sdks/com.android.runtime/sdk/runtime-module-sdk-current.zip",
                "bundled-mainline-sdks/platform-mainline/sdk/platform-mainline-sdk-current.zip",
                "bundled-mainline-sdks/platform-mainline/test-exports/platform-mainline-test-exports-current.zip",
                # Unbundled (normal) modules.
                "mainline-sdks/for-latest-build/current/com.android.art/gantry-metadata.json",
                "mainline-sdks/for-latest-build/current/com.android.art/host-exports/art-module-host-exports-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current-api-diff.txt",
                "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current.zip",
                "mainline-sdks/for-latest-build/current/com.android.art/test-exports/art-module-test-exports-current.zip",
            ],
            sorted(self.list_files_in_dir(self.tmp_dist_dir)))

        art_api_diff_file = os.path.join(
            self.tmp_dist_dir,
            "mainline-sdks/for-latest-build/current/com.android.art/sdk/art-module-sdk-current-api-diff.txt"
        )
        self.assertNotEqual(
            os.path.getsize(art_api_diff_file),
            0,
            msg="Api diff file should not be empty for the art module")

        art_gantry_metadata_json_file = os.path.join(
            self.tmp_dist_dir,
            "mainline-sdks/for-latest-build/current/com.android.art/gantry-metadata.json"
        )

        with open(art_gantry_metadata_json_file, "r",
                  encoding="utf8") as gantry_metadata_json_file_object:
            json_data = json.load(gantry_metadata_json_file_object)

        self.assertEqual(
            json_data["api_diff_file"],
            "art-module-sdk-current-api-diff.txt",
            msg="Incorrect api-diff file name.")
        self.assertEqual(
            json_data["api_diff_file_size"],
            267,
            msg="Incorrect api-diff file size.")
        self.assertEqual(
            json_data["module_extension_version"],
            5,
            msg="The module extension version does not match the expected value."
        )
        self.assertEqual(
            json_data["last_finalized_version"],
            5,
            msg="The last finalized version does not match the expected value."
        )

    def create_build_number_file(self):
        soong_dir = os.path.join(self.tmp_out_dir, "soong")
        os.makedirs(soong_dir, exist_ok=True)
        build_number_file = os.path.join(soong_dir, "build_number.txt")
        with open(build_number_file, "w", encoding="utf8") as f:
            f.write("build-number")

    def test_snapshot_build_order(self):
        # Create the out/soong/build_number.txt file that is copied into the
        # snapshots.
        self.create_build_number_file()

        subprocess_runner = unittest.mock.Mock(mm.SubprocessRunner)
        snapshot_builder = FakeSnapshotBuilder(
            tool_path="path/to/mainline_modules_sdks.sh",
            subprocess_runner=subprocess_runner,
            out_dir=self.tmp_out_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )

        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.art"],
            MAINLINE_MODULES_BY_APEX["com.android.ipsec"],
            # Create a google specific module.
            mm.aosp_to_google(MAINLINE_MODULES_BY_APEX["com.android.wifi"]),
        ]
        build_releases = [
            mm.Q,
            mm.R,
            mm.S,
            mm.LATEST,
        ]

        producer.produce_dist(modules, build_releases)

        # Check the order in which the snapshots are built.
        self.assertEqual([
            (
                "R",
                {},
                ["com.android.ipsec", "com.google.android.wifi"],
            ),
            (
                "latest",
                {},
                [
                    "com.android.art", "com.android.ipsec",
                    "com.google.android.wifi"
                ],
            ),
            (
                "S",
                {
                    "SOONG_SDK_SNAPSHOT_TARGET_BUILD_RELEASE": "S"
                },
                [
                    "com.android.art", "com.android.ipsec",
                    "com.google.android.wifi"
                ],
            ),
        ], snapshot_builder.snapshots)

    def test_generate_sdk_supported_modules_file(self):
        subprocess_runner = mm.SubprocessRunner()
        snapshot_builder = FakeSnapshotBuilder(
            tool_path="path/to/mainline_modules_sdks.sh",
            subprocess_runner=subprocess_runner,
            out_dir=self.tmp_out_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )
        producer = mm.SdkDistProducer(
            subprocess_runner=subprocess_runner,
            snapshot_builder=snapshot_builder,
            dist_dir=self.tmp_dist_dir,
        )

        # Contains only sdk modules.
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.adservices"],
            MAINLINE_MODULES_BY_APEX["com.android.art"],
            MAINLINE_MODULES_BY_APEX["com.android.mediaprovider"],
        ]
        producer.dist_generate_sdk_supported_modules_file(modules)
        with open(os.path.join(self.tmp_dist_dir, "sdk-modules.txt"), "r",
                  encoding="utf8") as sdk_modules_file:
            sdk_modules = sdk_modules_file.readlines()

        self.assertTrue("com.google.android.adservices\n" in sdk_modules)
        self.assertTrue("com.google.android.art\n" in sdk_modules)
        self.assertTrue("com.google.android.mediaprovider\n" in sdk_modules)

        # Contains only non-sdk modules.
        modules = [
            mm.MainlineModule(
                apex="com.android.adbd",
                sdks=[],
                first_release="",
            ),
            mm.MainlineModule(
                apex="com.android.adbd",
                sdks=[],
                first_release="",
            ),
        ]
        producer.dist_generate_sdk_supported_modules_file(modules)
        with open(os.path.join(self.tmp_dist_dir, "sdk-modules.txt"), "r",
                  encoding="utf8") as sdk_modules_file:
            sdk_modules = sdk_modules_file.readlines()

        self.assertEqual(len(sdk_modules), 0)

        # Contains mixture of sdk and non-sdk modules.
        modules = [
            MAINLINE_MODULES_BY_APEX["com.android.adservices"],
            MAINLINE_MODULES_BY_APEX["com.android.mediaprovider"],
            mm.MainlineModule(
                apex="com.android.adbd",
                sdks=[],
                first_release="",
            ),
            mm.MainlineModule(
                apex="com.android.adbd",
                sdks=[],
                first_release="",
            ),
        ]
        producer.dist_generate_sdk_supported_modules_file(modules)
        with open(os.path.join(self.tmp_dist_dir, "sdk-modules.txt"), "r",
                  encoding="utf8") as sdk_modules_file:
            sdk_modules = sdk_modules_file.readlines()

        self.assertTrue("com.google.android.adservices\n" in sdk_modules)
        self.assertTrue("com.google.android.mediaprovider\n" in sdk_modules)
        self.assertFalse("com.google.android.adbd\n" in sdk_modules)
        self.assertFalse("com.google.android.extservices\n" in sdk_modules)


def path_to_test_data(relative_path):
    """Construct a path to a test data file.

    The relative_path is relative to the location of this file.
    """
    this_file = __file__
    # When running as a python_test_host (name=<x>) with an embedded launcher
    # the __file__ points to .../<x>/<x>.py but the .../<x> is not a directory
    # it is a binary with the launcher and the python file embedded inside. In
    # that case a test data file <rel> is at .../<x>_data/<rel>, not
    # .../<x>/<x>_data/<rel> so it is necessary to trim the base name (<x>.py)
    # from the file.
    if not os.path.isfile(this_file):
        this_file = os.path.dirname(this_file)
    # When the python file is at .../<x>.py (or in the case of an embedded
    # launcher at .../<x>/<x>.py) then the test data is at .../<x>_data/<rel>.
    this_file_without_ext, _ = os.path.splitext(this_file)
    return os.path.join(this_file_without_ext + "_data", relative_path)


def read_test_data(relative_path):
    with open(path_to_test_data(relative_path), "r", encoding="utf8") as f:
        return f.read()


class TestAndroidBpTransformations(unittest.TestCase):

    def apply_transformations(self, src, transformations, build_release, expected):
        producer = mm.SdkDistProducer(
            subprocess_runner=mock.Mock(mm.SubprocessRunner),
            snapshot_builder=mock.Mock(mm.SnapshotBuilder),
            script=self._testMethodName,
        )

        with tempfile.TemporaryDirectory() as tmp_dir:
            path = os.path.join(tmp_dir, "Android.bp")
            with open(path, "w", encoding="utf8") as f:
                f.write(src)

            mm.apply_transformations(
                producer, tmp_dir, transformations, build_release)

            with open(path, "r", encoding="utf8") as f:
                result = f.read()

        self.maxDiff = None
        self.assertEqual(expected, result)

    def test_common_mainline_module(self):
        """Tests the transformations applied to a common mainline sdk on S.

        This uses ipsec as an example of a common mainline sdk. This checks
        that the general Soong config module types and variables are used.
        """
        src = read_test_data("ipsec_Android.bp.input")

        expected = read_test_data("ipsec_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        transformations = module.transformations(mm.S, mm.Sdk)

        self.apply_transformations(src, transformations, mm.S, expected)

    def test_common_mainline_module_tiramisu(self):
        """Tests the transformations applied to a common mainline sdk on T.

        This uses ipsec as an example of a common mainline sdk. This checks
        that the use_source_config_var property is inserted.
        """
        src = read_test_data("ipsec_Android.bp.input")

        expected = read_test_data("ipsec_tiramisu_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        transformations = module.transformations(mm.Tiramisu, mm.Sdk)

        self.apply_transformations(src, transformations, mm.Tiramisu, expected)

    def test_optional_mainline_module(self):
        """Tests the transformations applied to an optional mainline sdk on S.

        This uses wifi as an example of a optional mainline sdk. This checks
        that the module specific Soong config module types and variables are
        used.
        """
        src = read_test_data("wifi_Android.bp.input")

        expected = read_test_data("wifi_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.wifi"]
        transformations = module.transformations(mm.S, mm.Sdk)

        self.apply_transformations(src, transformations, mm.S, expected)

    def test_optional_mainline_module_tiramisu(self):
        """Tests the transformations applied to an optional mainline sdk on T.

        This uses wifi as an example of a optional mainline sdk. This checks
        that the use_source_config_var property is inserted.
        """
        src = read_test_data("wifi_Android.bp.input")

        expected = read_test_data("wifi_tiramisu_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.wifi"]
        transformations = module.transformations(mm.Tiramisu, mm.Sdk)

        self.apply_transformations(src, transformations, mm.Tiramisu, expected)

    def test_optional_mainline_module_latest(self):
        """Tests the transformations applied to an optional mainline sdk LATEST.

        This uses wifi as an example of a optional mainline sdk. This checks
        that the use_source_config_var property is inserted.
        """
        src = read_test_data("wifi_Android.bp.input")

        expected = read_test_data("wifi_latest_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.wifi"]
        transformations = module.transformations(mm.LATEST, mm.Sdk)

        self.apply_transformations(src, transformations, mm.LATEST, expected)

    def test_art(self):
        """Tests the transformations applied to a the ART mainline module.

        The ART mainline module uses a different Soong config setup to the
        common mainline modules. This checks that the ART specific Soong config
        module types, and variables are used.
        """
        src = read_test_data("art_Android.bp.input")

        expected = read_test_data("art_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.art"]
        transformations = module.transformations(mm.S, mm.Sdk)

        self.apply_transformations(src, transformations, mm.S, expected)

    def test_art_module_exports(self):
        """Tests the transformations applied to a the ART mainline module.

        The ART mainline module uses a different Soong config setup to the
        common mainline modules. This checks that the ART specific Soong config
        module types, and variables are used.
        """
        src = read_test_data("art_Android.bp.input")

        expected = read_test_data("art_host_exports_Android.bp.expected")

        module = MAINLINE_MODULES_BY_APEX["com.android.art"]
        transformations = module.transformations(mm.S, mm.HostExports)

        self.apply_transformations(src, transformations, mm.S, expected)

    def test_r_build(self):
        """Tests the transformations that are applied for the R build.

        This uses ipsec as an example of a common mainline module. That would
        usually apply the mm.SoongConfigBoilerplateInserter transformation but
        because this is being run for build R that transformation should not be
        applied.
        """
        src = read_test_data("ipsec_for_r_Android.bp")

        # There should be no changes made.
        expected = src

        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        transformations = module.transformations(mm.R, mm.Sdk)

        self.apply_transformations(src, transformations, mm.R, expected)

    def test_additional_transformation(self):
        """Tests additional transformation.

        This uses ipsec as an example of a common case for adding information
        in Android.bp file.
        This checks will append the information in Android.bp for a regular module.
        """

        @dataclasses.dataclass(frozen=True)
        class TestTransformation(mm.FileTransformation):
            """Transforms an Android.bp file by appending testing message."""

            test_content: str = ""

            def apply(self, producer, path, build_release):
                with open(path, "a+", encoding="utf8") as file:
                    self._apply_transformation(producer, file, build_release)

            def _apply_transformation(self, producer, file, build_release):
                if build_release >= mm.Tiramisu:
                    file.write(self.test_content)

        src = read_test_data("ipsec_Android.bp.input")

        expected = read_test_data(
            "ipsec_tiramisu_Android.bp.additional.expected")
        test_transformation = TestTransformation(
            "Android.bp", test_content="\n// Adding by test")
        module = MAINLINE_MODULES_BY_APEX["com.android.ipsec"]
        module = dataclasses.replace(
            module, apex=module.apex,
            first_release=module.first_release,
            additional_transformations=[test_transformation])
        transformations = module.transformations(mm.Tiramisu, mm.Sdk)
        self.apply_transformations(src, transformations, mm.Tiramisu, expected)


class TestFilterModules(unittest.TestCase):

    def test_no_filter(self):
        all_modules = mm.MAINLINE_MODULES + mm.BUNDLED_MAINLINE_MODULES
        modules = mm.filter_modules(all_modules, None)
        self.assertEqual(modules, all_modules)

    def test_with_filter(self):
        modules = mm.filter_modules(mm.MAINLINE_MODULES, "com.android.art")
        expected = MAINLINE_MODULES_BY_APEX["com.android.art"]
        self.assertEqual(modules, [expected])


class TestModuleProperties(unittest.TestCase):

    def test_unbundled(self):
        for module in mm.MAINLINE_MODULES:
            with self.subTest(module=module):
                self.assertFalse(module.is_bundled())

    def test_bundled(self):
        for module in (mm.BUNDLED_MAINLINE_MODULES +
                       mm.PLATFORM_SDKS_FOR_MAINLINE):
            with self.subTest(module=module):
                self.assertTrue(module.is_bundled())
                self.assertEqual(module.first_release, mm.LATEST)


if __name__ == "__main__":
    unittest.main(verbosity=2)
