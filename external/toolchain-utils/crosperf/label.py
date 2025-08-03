# -*- coding: utf-8 -*-
# Copyright 2013 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""The label of benchamrks."""


import hashlib
import os

from cros_utils import misc
from cros_utils.file_utils import FileUtils
from image_checksummer import ImageChecksummer


class Label(object):
    """The label class."""

    def __init__(
        self,
        name,
        build,
        chromeos_image,
        autotest_path,
        debug_path,
        chromeos_root,
        board,
        remote,
        image_args,
        cache_dir,
        cache_only,
        log_level,
        compiler,
        crosfleet=False,
        chrome_src=None,
    ):

        self.image_type = self._GetImageType(chromeos_image)

        # Expand ~
        chromeos_root = os.path.expanduser(chromeos_root)
        if self.image_type == "local":
            chromeos_image = os.path.expanduser(chromeos_image)

        self.name = name
        self.build = build
        self.chromeos_image = chromeos_image
        self.autotest_path = autotest_path
        self.debug_path = debug_path
        self.board = board
        self.remote = remote
        self.image_args = image_args
        self.cache_dir = cache_dir
        self.cache_only = cache_only
        self.log_level = log_level
        self.chrome_version = ""
        self.compiler = compiler
        self.crosfleet = crosfleet

        if not chromeos_root:
            if self.image_type == "local":
                chromeos_root = FileUtils().ChromeOSRootFromImage(
                    chromeos_image
                )
            if not chromeos_root:
                raise RuntimeError(
                    "No ChromeOS root given for label '%s' and could "
                    "not determine one from image path: '%s'."
                    % (name, chromeos_image)
                )
        else:
            chromeos_root = FileUtils().CanonicalizeChromeOSRoot(chromeos_root)
            if not chromeos_root:
                raise RuntimeError(
                    "Invalid ChromeOS root given for label '%s': '%s'."
                    % (name, chromeos_root)
                )

        self.chromeos_root = chromeos_root
        if not chrome_src:
            # Old and new chroots may have different chrome src locations.
            # The path also depends on the chrome build flags.
            # Give priority to chrome-src-internal.
            chrome_src_rel_paths = [
                ".cache/distfiles/target/chrome-src-internal",
                ".cache/distfiles/chrome-src-internal",
                ".cache/distfiles/target/chrome-src",
                ".cache/distfiles/chrome-src",
            ]
            for chrome_src_rel_path in chrome_src_rel_paths:
                chrome_src_abs_path = os.path.join(
                    self.chromeos_root, chrome_src_rel_path
                )
                if os.path.exists(chrome_src_abs_path):
                    chrome_src = chrome_src_abs_path
                    break
            if not chrome_src:
                raise RuntimeError(
                    "Can not find location of Chrome sources.\n"
                    f"Checked paths: {chrome_src_rel_paths}"
                )
        else:
            chrome_src = misc.CanonicalizePath(chrome_src)
            # Make sure the path exists.
            if not os.path.exists(chrome_src):
                raise RuntimeError(
                    "Invalid Chrome src given for label '%s': '%s'."
                    % (name, chrome_src)
                )
        self.chrome_src = chrome_src

        self._SetupChecksum()

    def _SetupChecksum(self):
        """Compute label checksum only once."""

        self.checksum = None
        if self.image_type == "local":
            self.checksum = ImageChecksummer().Checksum(self, self.log_level)
        elif self.image_type == "trybot":
            self.checksum = hashlib.md5(
                self.chromeos_image.encode("utf-8")
            ).hexdigest()

    def _GetImageType(self, chromeos_image):
        image_type = None
        if chromeos_image.find("xbuddy://") < 0:
            image_type = "local"
        elif chromeos_image.find("trybot") >= 0:
            image_type = "trybot"
        else:
            image_type = "official"
        return image_type

    def __hash__(self):
        """Label objects are used in a map, so provide "hash" and "equal"."""

        return hash(self.name)

    def __eq__(self, other):
        """Label objects are used in a map, so provide "hash" and "equal"."""

        return isinstance(other, Label) and other.name == self.name

    def __str__(self):
        """For better debugging."""

        return 'label[name="{}"]'.format(self.name)


class MockLabel(object):
    """The mock label class."""

    def __init__(
        self,
        name,
        build,
        chromeos_image,
        autotest_path,
        debug_path,
        chromeos_root,
        board,
        remote,
        image_args,
        cache_dir,
        cache_only,
        log_level,
        compiler,
        crosfleet=False,
        chrome_src=None,
    ):
        self.name = name
        self.build = build
        self.chromeos_image = chromeos_image
        self.autotest_path = autotest_path
        self.debug_path = debug_path
        self.board = board
        self.remote = remote
        self.cache_dir = cache_dir
        self.cache_only = cache_only
        if not chromeos_root:
            self.chromeos_root = "/tmp/chromeos_root"
        else:
            self.chromeos_root = chromeos_root
        self.image_args = image_args
        self.chrome_src = chrome_src
        self.image_type = self._GetImageType(chromeos_image)
        self.checksum = ""
        self.log_level = log_level
        self.compiler = compiler
        self.crosfleet = crosfleet
        self.chrome_version = "Fake Chrome Version 50"

    def _GetImageType(self, chromeos_image):
        image_type = None
        if chromeos_image.find("xbuddy://") < 0:
            image_type = "local"
        elif chromeos_image.find("trybot") >= 0:
            image_type = "trybot"
        else:
            image_type = "official"
        return image_type
