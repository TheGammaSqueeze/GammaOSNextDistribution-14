# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Generates an xml manifest for the `repo` tool.

It enumerates all git submodules recursively, and
creates a `project` entry in the manifest, pointing
at the repository's url and hash.

Repository's url are remapped to point either at AOSP
or at experimental-qemu-build-internal when the AOSP
repository is not ready.

Some submodules are not used by the build and are not mapped.

Nested repositories are relocated into ./third_party and a
symlink is created to recreate the structure.
"""

import argparse
import dataclasses
import os
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET

# Host name of AOSP Git on borg repositories.
# See rpc://android.googlesource.com/{repo name}
AOSP_HOST = "android.googlesource.com"

# Submodules that are not used by the build.
REPO_TO_SKIP = {
    "https://boringssl.googlesource.com/boringssl",
    "https://chromium.googlesource.com/chromium/tools/depot_tools.git",
    "https://chromium.googlesource.com/chromiumos/platform/minigbm",
    "https://chromium.googlesource.com/chromiumos/platform/minijail",
    "https://chromium.googlesource.com/chromiumos/third_party/tpm2",
    "https://chromium.googlesource.com/crosvm/perfetto/",
    "https://github.com/akheron/jansson",
    "https://github.com/google/brotli",
    "https://github.com/google/wycheproof",
    "https://github.com/kkos/oniguruma",
    "https://github.com/krb5/krb5",
    "https://github.com/openssl/openssl",
    "https://github.com/pyca/cryptography.git",
    "https://github.com/tianocore/edk2-cmocka.git",
    "https://github.com/ucb-bar/berkeley-softfloat-3.git",
    "https://gitlab.com/libvirt/libvirt-ci.git",
    "https://github.com/gost-engine/engine",
    "https://github.com/provider-corner/libprov.git",
    "https://github.com/MIPI-Alliance/public-mipi-sys-t.git",
    "https://github.com/Zeex/subhook.git",
    "https://github.com/zeux/pugixml.git",
    "https://github.com/devicetree-org/pylibfdt.git",
    "https://gitlab.com/qemu-project/edk2.git",
    "https://gitlab.com/qemu-project/ipxe.git",
    "https://gitlab.com/qemu-project/libvfio-user.git",
    "https://gitlab.com/qemu-project/openbios.git",
    "https://gitlab.com/qemu-project/opensbi.git",
    "https://gitlab.com/qemu-project/qboot.git",
    "https://gitlab.com/qemu-project/qemu-palcode.git",
    "https://gitlab.com/qemu-project/QemuMacDrivers.git",
    "https://gitlab.com/qemu-project/seabios-hppa.git",
    "https://gitlab.com/qemu-project/seabios.git/",
    "https://gitlab.com/qemu-project/skiboot.git",
    "https://gitlab.com/qemu-project/SLOF.git",
    "https://gitlab.com/qemu-project/u-boot-sam460ex.git",
    "https://gitlab.com/qemu-project/vbootrom.git",
}

# Replaces repositories URLs.
REPO_MAPPING = {
    # Submodules remapped to android AOSP.
    "git://anongit.freedesktop.org/git/pixman.git": (
        "https://android.googlesource.com/platform/external/pixman"
    ),
    "https://boringssl.googlesource.com/boringssl": (
        "https://android.googlesource.com/platform/external/boringssl"
    ),
    "https://chromium.googlesource.com/chromiumos/third_party/virglrenderer": (
        "https://android.googlesource.com/platform/external/virglrenderer"
    ),
    "https://chromium.googlesource.com/crosvm/crosvm": (
        "https://android.googlesource.com/platform/external/crosvm"
    ),
    "https://github.com/google/googletest.git": (
        "https://android.googlesource.com/platform/external/googletest"
    ),
    "https://github.com/mesonbuild/meson.git": (
        "https://android.googlesource.com/trusty/external/qemu-meson"
    ),
    "https://gitlab.com/qemu-project/dtc.git": (
        "https://android.googlesource.com/platform/external/dtc"
    ),
    "https://gitlab.com/qemu-project/meson.git": (
        "https://android.googlesource.com/trusty/external/qemu-meson"
    ),
    "https://gitlab.com/qemu-project/u-boot.git": (  # Can probably be removed.
        "https://android.googlesource.com/platform/external/u-boot"
    ),
    "https://gitlab.freedesktop.org/slirp/libslirp.git": (
        "https://android.googlesource.com/trusty/external/qemu-libslirp"
    ),
    "https://gitlab.gnome.org/GNOME/glib.git": (
        "https://android.googlesource.com/platform/external/bluetooth/glib"
    ),
    "https://github.com/KhronosGroup/EGL-Registry.git": (
        "https://android.googlesource.com/platform/external/egl-registry"
    ),
    "https://gitlab.com/qemu-project/berkeley-softfloat-3.git": "https://android.googlesource.com/platform/external/berkeley-softfloat-3",
    "https://gitlab.com/qemu-project/berkeley-testfloat-3.git": "https://android.googlesource.com/platform/external/berkeley-testfloat-3",
    "https://salsa.debian.org/xorg-team/lib/libpciaccess.git": (
        "https://android.googlesource.com/platform/external/libpciaccess"
    ),
    "https://gitlab.com/qemu-project/keycodemapdb.git": (
        "https://android.googlesource.com/trusty/external/qemu-keycodemapdb"
    ),
    "https://gitlab.gnome.org/GNOME/gvdb.git": (
        "https://android.googlesource.com/platform/external/gvdb"
    ),
    "https://github.com/anholt/libepoxy.git": (
        "https://android.googlesource.com/platform/external/libepoxy"
    ),
    "https://gitlab.freedesktop.org/virgl/virglrenderer.git": (
        "https://android.googlesource.com/platform/external/virglrenderer"
    ),
}


@dataclasses.dataclass
class Submodule:
  """Occurrence of a module in the tree."""

  path: str
  origin_url: str
  hash: str


@dataclasses.dataclass
class Project:
  """Project in a repo manifest."""

  origin_url: str
  gob_host: str
  gob_path: str
  revision: str
  shallow: bool
  linkat: list


def GetAllSubmodules(path):
  """Yields a Submodule for each module recursively found in the specified repo."""

  yield Submodule(
      ".",
      "https://android.googlesource.com/device/google/cuttlefish_vmm",
      "main",
  )

  output = subprocess.check_output([
      "git",
      "submodule",
      "foreach",
      "--recursive",
      "-q",
      "echo ${displaypath} $(git remote get-url origin) ${sha1}",
  ])
  for line in output.decode().strip().split("\n"):
    yield Submodule(*line.split(" "))


def MatchProject(url):
  prefix_to_remote = {
      "https://android.googlesource.com/": AOSP_HOST,
      "persistent-https://android.git.corp.google.com/": AOSP_HOST,
  }
  for prefix, remote in prefix_to_remote.items():
    if url.startswith(prefix):
      return remote, url.removeprefix(prefix)
  raise ValueError(
      f"Url {url} could neither be mapped to AOSP to"
      " experimental-qemu-build-internal. If this is a new submodule, verify"
      " if it is used by the build. If it is, we should import it to AOSP. If"
      " not, add it to the REPO_TO_SKIP."
  )


def main():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("input", help="git root directory")
  parser.add_argument(
      "--repo_manifest",
      type=argparse.FileType("w"),
      help="output repo tool manifest",
  )

  args = parser.parse_args()

  submodules = list(GetAllSubmodules(args.input))

  # Build a list of rep project.
  project_by_name = {}
  for module in submodules:
    repo_url = module.origin_url
    # Skip unused code repositories.
    if repo_url in REPO_TO_SKIP:
      continue
    # Remap repository to their new location or to None.
    if repo_url in REPO_MAPPING:
      repo_url = REPO_MAPPING[repo_url]
    gob_host, gob_path = MatchProject(repo_url)
    # Add the repository instance to the list merging by gob_path
    if gob_path not in project_by_name:
      project_by_name[gob_path] = Project(
          origin_url=module.origin_url,
          gob_host=gob_host,
          gob_path=gob_path,
          revision=module.hash,
          shallow=module.path.startswith("qemu/prebuilts/"),
          linkat=[],
      )

    assert project_by_name[gob_path].gob_host == gob_host
    project_by_name[gob_path].linkat.append(module.path)

  GenerateRepoManifest(project_by_name.values(), args.repo_manifest)


def SimpleName(project):
  return project.gob_path.replace("/", "_")


def GenerateRepoManifest(projects, out):
  # Generate repo manifest xml.
  manifest = ET.Element("manifest")
  ET.SubElement(
      manifest,
      "remote",
      name="aosp",
      fetch="sso://android.googlesource.com",
      review="sso://android/",
  )
  ET.SubElement(
      manifest,
      "remote",
      name="experimental-qemu-build-internal",
      fetch="sso://experimental-qemu-build-internal",
  )
  ET.SubElement(
      manifest,
      "default",
      attrib={"sync-j": "16"},
      revision="main",
      remote="aosp",
  )

  for project in projects:
    # The project is instantiated only once.
    elem = ET.SubElement(
        manifest,
        "project",
        path=project.linkat[0],
        name=project.gob_path,
        revision=project.revision,
    )
    if project.gob_host != AOSP_HOST:
      elem.attrib["remote"] = project.gob_host
    else:
      pass  # Use the default value which is AOSP.

    if project.shallow:
      elem.attrib["clone-depth"] = "1"

    for linkat in project.linkat[1:]:
      ET.SubElement(elem, "linkfile", src=".", dest=linkat)

  tree = ET.ElementTree(manifest)
  ET.indent(tree)
  tree.write(out, encoding="unicode")


if __name__ == "__main__":
  sys.exit(main())
