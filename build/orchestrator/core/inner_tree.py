#!/usr/bin/python3
#
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

import enum
import json
import os
import subprocess
import sys
import textwrap

import nsjail

_INNER_BUILD = ".inner_build"


class InnerTreeKey(object):
    """Trees are identified uniquely by their root and the TARGET_PRODUCT they will use to build.
    If a single tree uses two different prdoucts, then we won't make assumptions about
    them sharing _anything_.
    TODO: This is true for soong. It's more likely that bazel could do analysis for two
    products at the same time in a single tree, so there's an optimization there to do
    eventually."""

    def __init__(self, root, product):
        if isinstance(root, list):
            self.melds = root[1:]
            root = root[0]
        else:
            self.melds = []
        self.root = root
        self.product = product

    def __str__(self):
        return (f"TreeKey(root={enquote(self.root)} "
                f"product={enquote(self.product)}")

    def __hash__(self):
        return hash((self.root, str(self.melds), self.product))

    def _cmp(self, other):
        assert isinstance(other, InnerTreeKey)
        if self.root < other.root:
            return -1
        if self.root > other.root:
            return 1
        if self.melds < other.melds:
            return -1
        if self.melds > other.melds:
            return 1
        if self.product == other.product:
            return 0
        if self.product is None:
            return -1
        if other.product is None:
            return 1
        if self.product < other.product:
            return -1
        return 1

    def __eq__(self, other):
        return self._cmp(other) == 0

    def __ne__(self, other):
        return self._cmp(other) != 0

    def __lt__(self, other):
        return self._cmp(other) < 0

    def __le__(self, other):
        return self._cmp(other) <= 0

    def __gt__(self, other):
        return self._cmp(other) > 0

    def __ge__(self, other):
        return self._cmp(other) >= 0


class InnerTree(object):
    def __init__(self, context, paths, product, variant):
        """Initialize with the inner tree root (relative to the workspace root)"""
        if not isinstance(paths, list):
            paths = [paths]
        self.root = paths[0]
        self.meld_dirs = paths[1:]
        # TODO: more complete checking (include '../' in the checks, etc.
        if any(x.startswith(os.path.sep) for x in self.meld_dirs):
            raise Exception(
                f"meld directories may not start with {os.path.sep}")
        if any(x.startswith('=') for x in self.meld_dirs[1:]):
            raise Exception('only the first meld directory can specify "="')

        self.product = product
        self.variant = variant
        self.domains = {}
        self.context = context
        self.env_used = []
        self.nsjail = context.tools.nsjail
        self.out_root_origin = context.out.inner_tree_dir(self.root, product)
        self.out = OutDirLayout(self.root, self.out_root_origin)
        self._meld_config = None

    def __str__(self):
        return (f"InnerTree(root={enquote(self.root)} "
                f"product={enquote(self.product)} "
                f"domains={enquote(list(self.domains.keys()))} "
                f"meld={enquote(self.meld_dirs)})")

    @property
    def meld_config(self):
        """Return the meld configuration for invoking inner_build."""
        if self._meld_config:
            return self._meld_config

        inner_tree_src_path = os.path.abspath(self.root)
        config = nsjail.Nsjail(inner_tree_src_path)
        inner_tree_out_path = self.out.root(base=self.out.Base.OUTER,
                                            abspath=True)
        out_root_origin = self.out.root()

        # Add TARGET_PRODUCT and TARGET_BUILD_VARIANT.
        if self.product:
            config.add_envar(name="TARGET_PRODUCT", value=self.product)
        config.add_envar(name="TARGET_BUILD_VARIANT", value=self.variant)

        # TODO: determine what other envirnoment variables need to be copied
        # into the nsjail config.

        # If the first meld dir path starts with "=", overlay the entire tree
        # with that before melding other sub manifests.
        meld_dirs = self.meld_dirs
        tree_root = inner_tree_src_path
        if meld_dirs and meld_dirs[0].startswith('='):
            tree_root = os.path.abspath(meld_dirs[0][1:])
            meld_dirs = meld_dirs[1:]
            sys.stderr.write(f'overlaying {self.root} with {tree_root}\n')

        config.add_mountpt(src=tree_root,
                           dst=inner_tree_src_path,
                           is_bind=True,
                           rw=False,
                           mandatory=True)
        # Place OUTDIR at /out
        os.makedirs(out_root_origin, exist_ok=True)
        config.add_mountpt(src=os.path.abspath(out_root_origin),
                           dst=inner_tree_out_path,
                           is_bind=True,
                           rw=True,
                           mandatory=True)

        # TODO: Once we have the lightweight tree, this mount should move to
        # platform/apisurfaces, and be mandatory.
        api_surfaces = self.context.out.api_surfaces_dir(
            base=self.context.out.Base.ORIGIN, abspath=True)
        # Always mount api_surfaces dir.
        # The mount point is out/api_surfaces -> <inner_tree>/out/api_surfaces
        # soong_finder will be speciall-cased to look for Android.bp files in
        # this dir.
        api_surfaces_inner_tree = os.path.join(inner_tree_out_path,
                                               "api_surfaces")
        os.makedirs(api_surfaces, exist_ok=True)
        os.makedirs(api_surfaces_inner_tree, exist_ok=True)
        config.add_mountpt(src=api_surfaces,
                           dst=api_surfaces_inner_tree,
                           is_bind=True,
                           rw=False,
                           mandatory=False)
        # Share the Network namespace for API export.
        # This ensures that the Bazel client can communicate with the Bazel daemon.
        # This does not preclude build systems of inner trees from setting
        # up different sandbox configs. e.g. Soong is free to run the build
        # in a sandbox that disables network access.
        # TODO: Make this more restrictive. This should only be limited to the
        # loopback device.
        config.add_option(name="clone_newnet", value="false")

        def _meld_git(shared, src):
            dst = os.path.join(self.root, src[len(shared) + 1:])
            abs_dst = os.path.join(inner_tree_src_path, src[len(shared) + 1:])
            abs_src = os.path.abspath(src)
            # Only meld if we have not already mounted something at {dst}, and
            # either the project is missing, or is an empty directory:  nsjail
            # creates empty directories when it mounts the directory.
            if abs_dst in config.mount_points:
                sys.stderr.write(f'{dst} already mounted, ignoring {src}\n')
            elif not os.path.isdir(dst) or not os.listdir(dst):
                # TODO: For repo workspaces, we need to handle <linkfile/> and
                # <copyfile/> elements from the manifest.
                sys.stderr.write(f'melding {src} into {dst}\n')
                config.add_mountpt(src=abs_src,
                                   dst=abs_dst,
                                   is_bind=True,
                                   rw=False,
                                   mandatory=True)

        for shared in meld_dirs:
            if os.path.isdir(os.path.join(shared, '.git')):
                # TODO: If this is the root of the meld_dir, process the
                # modules instead of the git project.
                print('TODO: handle git submodules case')
                continue

            # Use os.walk (which uses os.scandir), so that we get recursion
            # for free.
            for src, dirs, _ in os.walk(shared):
                # When repo syncs the workspace, .git is a symlink.
                if '.git' in dirs or os.path.isdir(os.path.join(src, '.git')):
                    _meld_git(shared, src)
                    # Stop recursing.
                    dirs[:] = []
                # TODO: determine what other source control systems we need
                # to detect and support here.

        self._meld_config = config
        return self._meld_config

    @property
    def build_domains(self):
        """The build_domains for this inner-tree."""
        return sorted(self.domains.keys())

    def set_env_used(self):
        """Record the environment used in the inner tree."""
        with open(self.out.env_used_file(), "r", encoding="iso-8859-1") as f:
            try:
                self.env_used = json.load(f)
            except json.decoder.JSONDecodeError as ex:
                sys.stderr.write(f"failed to parse {env_path}: {ex.msg}\n")
                raise ex

    def invoke(self, args):
        """Call the inner tree command for this inner tree. Exits on failure."""
        # TODO: Build time tracing

        # Validate that there is a .inner_build command to run at the root of the tree
        # so we can print a good error message
        # If we are melding the inner_build into the tree, it won't be
        # executable at this time.
        #inner_build_tool = os.path.join(self.root, _INNER_BUILD)
        #if not os.path.exists(inner_build_tool):
        #    sys.stderr.write(
        #        f"Unable to execute {inner_build_tool}. Is there an inner tree "
        #        f"or lunch combo misconfiguration?\n")
        #    sys.exit(1)

        meld_config = self.meld_config
        inner_tree_src_path = meld_config.cwd

        # Write the nsjail config
        nsjail_config_file = self.out.nsjail_config_file()
        meld_config.generate_config(nsjail_config_file)

        # Build the command
        cmd = [
            self.nsjail,
            "--config",
            nsjail_config_file,
            "--",
            os.path.join(inner_tree_src_path, _INNER_BUILD),
            "--out_dir",
            self.out.root(base=self.out.Base.INNER),
        ]
        cmd += args

        # Run the command
        print("% " + " ".join(cmd))
        process = subprocess.run(cmd, shell=False, check=False)

        # TODO: Probably want better handling of inner tree failures
        if process.returncode:
            sys.stderr.write(
                f"Build error in inner tree: {self.root}\nstopping "
                f"multitree build.\n")
            sys.exit(1)


class InnerTrees(object):
    def __init__(self, trees, domains):
        self.trees = trees
        self.domains = domains

    def __str__(self):
        """Return a debugging dump of this object"""

        def _vals(values):
            return ("\n" + " " * 16).join(sorted([str(t) for t in values]))

        return textwrap.dedent(f"""\
        InnerTrees {{
            inner-tree: [
                {self.trees.values()[0]}
                {_vals(self.trees.values()[1:])}
            ]
            domains: [
                {_vals(self.domains.values())}
            ]
        }}""")

    def __iter__(self):
        """Return a generator yielding the sorted inner tree keys."""
        for key in sorted(self.trees.keys()):
            yield key

    def for_each_tree(self, func, cookie=None):
        """Call func for each of the inner trees once for each product that will be built in it.

        The calls will be in a stable order.

        Return a map of the InnerTreeKey to the return value from func().
        """
        result = {x: func(x, self.trees[x], cookie) for x in self}
        return result

    def get(self, tree_key):
        """Get an inner tree for tree_key"""
        return self.trees.get(tree_key)

    def keys(self):
        """Get the keys for the inner trees in name order."""
        return [self.trees[k] for k in sorted(self.trees.keys())]


@enum.unique
class OutDirBase(enum.Enum):
    """The basepath to use for output paths.

    ORIGIN: Path is relative to ${OUT_DIR}. Use this when the path will be
            consumed while not nsjailed. (default)
    OUTER:  Path is relative to the outer tree root.  Use this when the path
            will be consumed while nsjailed in the outer tree.
    INNER:  Path is relative to the inner tree root.  Use this when the path
            will be consumed while nsjailed in the inner tree.
    """
    DEFAULT = 0
    ORIGIN = 1
    OUTER = 2
    INNER = 3


class OutDirLayout(object):
    """Encapsulates the logic about the layout of the inner tree out directories.
    See also context.OutDir for outer tree out dir contents."""

    # For ease of use.
    Base = OutDirBase

    def __init__(self, tree_root, out_origin, out_path="out"):
        """Initialize with the root of the OUT_DIR for the inner tree.

        Args:
          tree_root: The workspace-relative path of the inner_tree.
          out_origin: The OUT_DIR path for the inner tree.
                      Usually "${OUT_DIR}/trees/{tree_root}_{product}"
          out_path: Where the inner tree out_dir will be mapped, relative to the
                    inner tree root. Usually "out".
        """
        self._base = {}
        self._base[self.Base.ORIGIN] = out_origin
        self._base[self.Base.OUTER] = os.path.join(tree_root, out_path)
        self._base[self.Base.INNER] = out_path
        self._base[self.Base.DEFAULT] = self._base[self.Base.ORIGIN]

    def _generate_path(self,
                       *args,
                       base: OutDirBase = OutDirBase.DEFAULT,
                       abspath=False):
        """Return the path to the file.

        Args:
          relpath: The inner tree out_dir relative path to use.
          base: Which base path to use.
          abspath: return the absolute path.
        """
        ret = os.path.join(self._base[base], *args)
        if abspath:
            ret = os.path.abspath(ret)
        return ret

    def root(self, *args, **kwargs):
        return self._generate_path(*args, **kwargs)

    def api_contributions_dir(self, **kwargs):
        return self._generate_path("api_contributions", **kwargs)

    def build_targets_file(self, **kwargs):
        return self._generate_path("build_targets.json", **kwargs)

    def env_used_file(self, **kwargs):
        return self._generate_path("inner_tree.env", **kwargs)

    def main_ninja_file(self, **kwargs):
        return self._generate_path("inner_tree.ninja", **kwargs)

    def nsjail_config_file(self, **kwargs):
        return self._generate_path("nsjail.cfg", **kwargs)

    def tree_info_file(self, **kwargs):
        return self._generate_path("tree_info.json", **kwargs)

    def tree_query(self, **kwargs):
        return self._generate_path("tree_query.json", **kwargs)


def enquote(s):
    return json.dumps(s)
