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

import json
import os
import textwrap


def _bool(value):
    """Convert string to one of None, True, False."""
    if isinstance(value, bool):
        return value
    if value is None:
        return None
    if value.to_lower() in ('', 'none', 'null'):
        return None
    if value in ('False', 'false'):
        return False
    return bool(value)


class Envar():
    """Environment variables."""

    def __init__(self, *args, name=None, value=None):
        assert not args, "Envar only accepts kwargs"
        self.name = name
        self.value = value

    def __str__(self):
        if self.value is None:
            # Use the current value of the variable.
            return f'envar: "{self.name}"\n'
        return f'envar: "{self.name}={self.value}"\n'


class MountPt(object):
    def __init__(self,
                 _kw_only=(),
                 src="",
                 prefix_src_env="",
                 src_content="",
                 dst="",
                 prefix_dst_env="",
                 fstype="",
                 options="",
                 is_bind=None,
                 rw=None,
                 is_dir=None,
                 mandatory=None,
                 is_symlink=None,
                 nosuid=None,
                 nodev=None,
                 noexec=None):
        assert _kw_only == (), "MountPt only accepts kwargs"
        # These asserts may need to be revisited if we ever use prefix_src_env
        # or prefix_dst_env.
        assert not src or os.path.abspath(src) == src, "Paths must be absolute"
        assert not dst or os.path.abspath(dst) == dst, "Paths must be absolute"
        self.src = src
        self.prefix_src_env = prefix_src_env
        self.src_content = src_content
        self.dst = dst
        self.prefix_dst_env = prefix_dst_env
        self.fstype = fstype
        self.options = options
        self.is_bind = _bool(is_bind)
        self.rw = _bool(rw)
        self.is_dir = _bool(is_dir)
        self.mandatory = _bool(mandatory)
        self.is_symlink = _bool(is_symlink)
        self.nosuid = _bool(nosuid)
        self.nodev = _bool(nodev)
        self.noexec = _bool(noexec)

    def __str__(self):
        ret = "mount {\n"
        if self.src:
            ret += f"  src: {json.dumps(self.src)}\n"
        if self.prefix_src_env:
            ret += f"  prefix_src_env: {json.dumps(self.prefix_src_env)}\n"
        if self.src_content:
            ret += f"  src_content: {json.dumps(self.src_content)}\n"
        if self.dst:
            ret += f"  dst: {json.dumps(self.dst)}\n"
        if self.prefix_dst_env:
            ret += f"  prefix_dst_env: {json.dumps(self.prefix_dst_env)}\n"
        if self.fstype:
            ret += f"  fstype: {json.dumps(self.fstype)}\n"
        if self.options:
            ret += f"  options: {json.dumps(self.options)}\n"
        if self.is_bind is not None:
            ret += f"  is_bind: {json.dumps(self.is_bind)}\n"
        if self.rw is not None:
            ret += f"  rw: {json.dumps(self.rw)}\n"
        if self.is_dir is not None:
            ret += f"  is_dir: {json.dumps(self.is_dir)}\n"
        if self.mandatory is not None:
            ret += f"  mandatory: {json.dumps(self.mandatory)}\n"
        if self.is_symlink is not None:
            ret += f"  is_symlink: {json.dumps(self.is_symlink)}\n"
        if self.nosuid is not None:
            ret += f"  nosuid: {json.dumps(self.nosuid)}\n"
        if self.nodev is not None:
            ret += f"  nodev: {json.dumps(self.nodev)}\n"
        if self.noexec is not None:
            ret += f"  noexec: {json.dumps(self.noexec)}\n"
        ret += "}\n\n"
        return ret

    def __eq__(self, other):
        return (isinstance(other, MountPt) and self.src == other.src
                and self.prefix_src_env == other.prefix_src_env
                and self.src_content == other.src_content
                and self.dst == other.dst
                and self.prefix_dst_env == other.prefix_dst_env
                and self.fstype == other.fstype
                and self.options == other.options
                and self.is_bind == other.is_bind and self.rw == other.rw
                and self.is_dir == other.is_dir
                and self.mandatory == other.mandatory
                and self.is_symlink == other.is_symlink
                and self.nosuid == other.nosuid and self.nodev == other.nodev
                and self.noexec == other.noexec)

    def copy(self):
        return MountPt(src=self.src,
                       prefix_src_env=self.prefix_src_env,
                       src_content=self.src_content,
                       dst=self.dst,
                       prefix_dst_env=self.prefix_dst_env,
                       fstype=self.fstype,
                       options=self.options,
                       is_bind=self.is_bind,
                       rw=self.rw,
                       is_dir=self.is_dir,
                       mandatory=self.mandatory,
                       is_symlink=self.is_symlink,
                       nosuid=self.nosuid,
                       nodev=self.nodev,
                       noexec=self.noexec)


class NsjailConfigOption(object):
    """Options for nsjail configuration."""

    def __init__(self, name, value, comment=""):
        self.name = name
        self.value = value
        self.comment = comment

    def __str__(self):
        return f"{self.comment}\n{self.name}: {self.value}"


class Nsjail(object):
    def __init__(self, cwd, verbose=False):
        self.cwd = cwd
        self.verbose = verbose
        # Add the mount points that we always need.
        self.mounts = [
            # Mount proc so that the PID namespace can be shared between the
            # parent and child process.
            MountPt(src="/proc",
                    dst="/proc",
                    is_bind=True,
                    rw=True,
                    mandatory=True),
            # Some commands may need /etc/alternatives to reach the correct
            # binary.
            MountPt(src="/etc/alternatives",
                    dst="/etc/alternatives",
                    is_bind=True,
                    rw=False,
                    mandatory=False),

            # TODO: we may need to use something other than tmpfs for this,
            # because of some tests, etc.
            MountPt(dst="/tmp",
                    fstype="tmpfs",
                    rw=True,
                    is_bind=False,
                    noexec=False,
                    nodev=True,
                    nosuid=True),

            # Some tools need /dev/shm to created a named semaphore. Use a new
            # tmpfs to limit access to the external environment.
            MountPt(dst="/dev/shm", fstype="tmpfs", rw=True, is_bind=False),

            # Add the expected tty devices.
            MountPt(src="/dev/tty", dst="/dev/tty", rw=True, is_bind=True),
            # These are symlinks to /proc/self/fd/{0,1,2}.
            MountPt(src="/proc/self/fd/0", dst="/dev/stdin", is_symlink=True),
            MountPt(src="/proc/self/fd/1", dst="/dev/stdout", is_symlink=True),
            MountPt(src="/proc/self/fd/2", dst="/dev/stderr", is_symlink=True),

            # Map the working User ID to a username
            # Some tools like Java need a valid username
            # Inner trees building with Soong also expect the nobody UID to be
            # available to setup its own nsjail.
            MountPt(
                src_content="user:x:999999:65533:user:/tmp:/bin/bash\n"
                "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin\n",
                dst="/etc/passwd",
                mandatory=False),

            # Define default group
            MountPt(src_content="group::65533:user\n"
                    "nogroup::65534:nobody\n",
                    dst="/etc/group",
                    mandatory=False),

            # Empty mtab file needed for some build scripts that check for
            # images being mounted
            MountPt(src_content="\n", dst="/etc/mtab", mandatory=False),

            # Explicitly mount required device file nodes
            #
            # This will enable a chroot based NsJail sandbox. A chroot does not
            # provide device file nodes. So just mount the required device file
            # nodes directly from the host.
            #
            # Note that this has no effect in a docker container, since in that
            # case NsJail will just mount the container device nodes. When we
            # use NsJail in a docker container we mount the full file system
            # root. So the container device nodes were already mounted in the
            # NsJail.

            # Some tools (like llvm-link) look for file descriptors in /dev/fd
            MountPt(src="/proc/self/fd",
                    dst="/dev/fd",
                    is_symlink=True,
                    mandatory=False),

            # /dev/null is a very commonly used for silencing output
            MountPt(src="/dev/null", dst="/dev/null", rw=True, is_bind=True),

            # /dev/urandom used during the creation of system.img
            MountPt(src="/dev/urandom",
                    dst="/dev/urandom",
                    rw=False,
                    is_bind=True),

            # /dev/random used by test scripts
            MountPt(src="/dev/random",
                    dst="/dev/random",
                    rw=False,
                    is_bind=True),

            # /dev/zero is required to make vendor-qemu.img
            MountPt(src="/dev/zero", dst="/dev/zero", is_bind=True),
            MountPt(src="/lib", dst="/lib", is_bind=True, rw=False),
            MountPt(src="/bin", dst="/bin", is_bind=True, rw=False),
            MountPt(src="/sbin", dst="/sbin", is_bind=True, rw=False),
            MountPt(src="/usr", dst="/usr", is_bind=True, rw=False),
            MountPt(src="/lib64",
                    dst="/lib64",
                    is_bind=True,
                    rw=False,
                    mandatory=False),
            MountPt(src="/lib32",
                    dst="/lib32",
                    is_bind=True,
                    rw=False,
                    mandatory=False),
        ]

        self.envars = [
            # Some tools in the build toolchain expect a $HOME to be set Point
            # $HOME to /tmp in case the toolchain needs to write something out
            # there
            Envar(name="HOME", value="/tmp"),
            # By default nsjail does not propagate the environment into the
            # jail. We need the path to be set up. There are a few ways to solve
            # this problem, but to avoid an undocumented dependency we are
            # explicit about the path we inject.
            Envar(name="PATH", value="/usr/bin:/usr/sbin:/bin:/sbin"),
        ]
        self.options = []

    def make_cwd_writable(self):
        """Mark things under cwd writable."""
        for mount in self.mounts:
            if mount.dst.startswith(self.cwd) and not mount.rw:
                if self.verbose:
                    print(f"Marking {mount.dst} r/w")
                mount.rw = True

    def add_mountpt(self, **kwargs):
        """Add a mountpoint to the config."""
        self.mounts.append(MountPt(**kwargs))

    def add_envar(self, **kwargs):
        """Add an envar to the config."""
        self.envars.append(Envar(**kwargs))

    def add_option(self, **kwargs):
        """Add an option to the njsail config."""
        self.options.append(NsjailConfigOption(**kwargs))

    def copy(self):
        """Return a copy of ourselves."""
        ret = Nsjail(self.cwd, verbose=self.verbose)
        ret.mounts = [x.copy() for x in self.mounts()]
        return ret

    @property
    def mount_points(self):
        """Return the list of mount points.

      Returns a list of mount points (destinations) for the nsjail.
      """
        return (x.dst for x in self.mounts)

    def add_nsjail(self, other):
        """Add another Nsjail object to this one."""
        # WARNING: clone_newnet option of inner tree should not be merged into
        # the combined nsjsail.cfg.
        assert other.cwd.startswith(self.cwd), "Must be a subdir"
        our_mounts = {x.dst: x for x in self.mounts}
        for mount in other.mounts:
            if mount.dst not in our_mounts:
                self.mounts.append(mount)
            else:
                assert mount == our_mounts[mount.dst]

    def generate_config(self, fn):
        """Generate the nsjail config file.

        Args:
          fn: (str) The name of the file to write, or None to not create.

        Returns:
          (str) The configuration written.
        """
        data = textwrap.dedent(f"""\
            name: "android-build-sandbox"
            description: "Sandboxed Android Platform Build."
            description: "No network access and a limited access to local host resources."

            log_level: {"INFO" if self.verbose else "WARNING"}
            # All configuration options are described in
            # https://github.com/google/nsjail/blob/master/config.proto

            # Run once then exit
            mode: ONCE

            # No time limit
            time_limit: 0

            # Limits memory usage
            rlimit_as_type: SOFT
            # Maximum size of core dump files
            rlimit_core_type: SOFT
            # Limits use of CPU time
            rlimit_cpu_type: SOFT
            # Maximum file size
            rlimit_fsize_type: SOFT
            # Maximum number of file descriptors opened
            rlimit_nofile_type: SOFT
            # Maximum stack size
            rlimit_stack_type: SOFT
            # Maximum number of threads
            rlimit_nproc_type: SOFT

            # Allow terminal control
            # This let's users cancel jobs with CTRL-C without exiting the
            # jail.
            skip_setsid: true

            # Below are all the host paths that shall be mounted
            # to the sandbox

            # TODO: Determine if we need to have /proc from outside of the jail.
            mount_proc: false

            # The user must mount the source to /src using --bindmount
            # It will be set as the initial working directory
            cwd: "{self.cwd}"

            # The sandbox User ID was chosen arbitrarily
            uidmap {{
              inside_id: "999999"
              outside_id: ""
              count: 1
            }}

            # The sandbox Group ID was chosen arbitrarily
            gidmap {{
              inside_id: "65533"
              outside_id: ""
              count: 1
            }}

            # Share PID namespace between parent and child process.
            # Sharing the PID namespace ensures that the Bazel daemon does not
            # get killed after every invocation.
            clone_newpid: false

            """)
        for envar in self.envars:
            data += f'{envar}'
        data += '\n'
        for mount in self.mounts:
            data += f'{mount}'
        data += '\n'
        for option in self.options:
            data += f"{option}"

        if fn:
            os.makedirs(os.path.dirname(fn), exist_ok=True)
            with open(fn, "w", encoding="iso-8859-1") as f:
                f.write(data)
        return data
