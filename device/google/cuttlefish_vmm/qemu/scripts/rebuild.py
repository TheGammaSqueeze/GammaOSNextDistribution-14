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

"""A script to rebuild QEMU from scratch on Linux."""

import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
from typing import Any
from typing import Callable
from typing import Dict
from typing import List
from typing import Sequence


def copy_file(src: Path, dst: Path) -> None:
  log("  COPY_FILE %s --> %s" % (src, dst))
  os.makedirs(dst.parent, exist_ok=True)
  shutil.copy2(src, dst)


def log(msg: str) -> None:
  print(msg)


def create_dev_environment(
    build_dir: Path, install_dir: Path, prebuilts_dir: Path, clang_dir: Path
) -> Dict[str, str]:
  sysroot = str(build_dir / "sysroot")
  binprefix = "%s/" % (clang_dir / "bin")
  env = os.environ.copy()
  path = env["PATH"]
  ld_library_path = env.get("LD_LIBRARY_PATH", "")
  env.update({
      "CC": f"{binprefix}clang --sysroot={sysroot}",
      "CXX": f"{binprefix}clang++ --sysroot={sysroot} -stdlib=libc++",
      # FIXME: this file does not exist.
      "LD": f"{binprefix}llvm-ld --sysroot={sysroot}",
      "AR": f"{binprefix}llvm-ar",
      "NM": f"{binprefix}llvm-nm",
      "PKG_CONFIG_PATH": ":".join([
          f"{install_dir}/usr/lib/x86_64-linux-gnu/pkgconfig",
          f"{install_dir}/usr/lib/pkgconfig",
          f"{sysroot}/usr/lib/pkgconfig",
      ]),
      "PATH": f"{install_dir}/usr/bin:{path}",
      "LD_LIBRARY_PATH": ":".join([
          f"{install_dir}/usr/lib/x86_64-linux-gnu",
          f"{install_dir}/usr/lib",
          f"{clang_dir}/lib:{ld_library_path}",
      ]),
      # Required to ensure that configure scripts that do not rely
      # on pkg-config find their dependencies properly.
      "CFLAGS": f"-I{install_dir}/usr/include",
      "LDFLAGS": f"-Wl,-L{install_dir}/usr/lib",
  })
  return env


_CLANG_VERSION = "r487747"


def generate_shell_command(
    cmd_args: Sequence[str],
    build_dir: Path | None,
    env: Dict[str, str] | None = None,
    base_env: Dict[str, str] | None = None,
) -> str:
  """Generate a shell command that can be printed or written to a script.

  Arguments:
    cmd_args: A list of strings for the command arguments.
    build_dir: An optional path to a build directory. None if the command must
      run in the current one.
    env: An optional dictionary of environment variable definitions
    base_env: An optional base environment. Values in env will be compared to
      it, and only differences will appear in the result. If None then
      os.environ will be used.

  Returns:
    A single string that can be printed or written to a script.
    All command arguments and variable values will be properly quoted.
  """
  if base_env is None:
    base_env = dict(os.environ)
  environ = []
  if env:
    environ = [
        "%s=%s" % (k, shlex.quote(v))
        for k, v in sorted(env.items())
        if k not in base_env or base_env[k] != v
    ]
  result = ""
  result_wrap = False
  if build_dir:
    result += f"cd {build_dir} && \\\n"
    result_wrap = True
  if environ:
    result += " \\\n".join(environ) + " \\\n"

  result += " ".join(shlex.quote(c) for c in cmd_args)
  if result_wrap:
    result = f"({result})"
  return result


def run_command(
    cmd_args: Sequence[Any],
    build_dir: Path | None = None,
    env: Dict[str, str] | None = None,
) -> None:
  # Convert arguments to strings, to support Path items directly.
  cmd_args = [str(c) for c in cmd_args]

  # Log a copy pastable command to help with iteration.
  log(generate_shell_command(cmd_args, build_dir, env) + "\n")
  subprocess.run(cmd_args, cwd=build_dir, env=env).check_returncode()


##########################################################################
##########################################################################
#####
#####  B U I L D   C O N F I G
#####
##########################################################################
##########################################################################


class BuildConfig(object):
  """Global build configuration object that is passed to all functions

  that implement a specific task build instructions below.

  This provides readonly directory paths, a default development
  environment used to launch all action commands, and ways to augment it
  with custom modifications (e.g. to add specific compiler flags).

  Usage is the following:

     1) Create instance, passing the path of the build directory, and
        the path to the read-only top directory project.

     2) Later, the instance is passed to each build function, which will
        be able to call its various methods to perform the operations
        required for its task, e.g. unpacking archives, applying patches
        or running commands.
  """

  def __init__(self, build_dir: Path, top_dir: Path):
    self._build_dir = build_dir
    self._sysroot_dir = build_dir / "sysroot"
    self._install_dir = build_dir / "dest-install"
    self._prebuilts_dir = top_dir / "prebuilts"
    self._clang_dir = self._prebuilts_dir / "clang" / f"clang-{_CLANG_VERSION}"
    self._third_party_dir = top_dir / "third_party"
    self._env = create_dev_environment(
        self._build_dir,
        self._install_dir,
        self._prebuilts_dir,
        self._clang_dir,
    )

    # By default, run commands directly. Subclasses can override
    # this value to record the commands instead, for example
    # to write them to a script or into a Makefile or Ninja build plan.
    self._runner = run_command

  def enable_ccache(self) -> None:
    for varname in ("CC", "CXX"):
      self._env[varname] = "ccache " + self._env[varname]

  @property
  def build_dir(self) -> Path:
    return self._build_dir

  @property
  def install_dir(self) -> Path:
    return self._install_dir

  @property
  def prebuilts_dir(self) -> Path:
    return self._prebuilts_dir

  @property
  def clang_dir(self) -> Path:
    return self._clang_dir

  @property
  def sysroot_dir(self) -> Path:
    return self._sysroot_dir

  @property
  def third_party_dir(self) -> Path:
    return self._third_party_dir

  def env_copy(self):
    """Return a copy of the current environment dictionary."""
    return self._env.copy()

  def env_copy_with(self, new_values: Dict[str, str]) -> Dict[str, str]:
    """Return a copy of the current environment, updated with new variables."""
    env = self._env.copy()
    env.update(new_values)
    return env

  def env_with_DESTDIR(self, dest_dir: Path | None = None) -> Dict[str, str]:
    """Return a copy of the current environment, with DESTDIR set to

    the installation directory.
    """
    return self.env_copy_with({"DESTDIR": str(dest_dir or self._install_dir)})

  def make_subdir(self, subdir: Path) -> Path:
    path = self.build_dir / subdir
    self._runner(["rm", "-rf", path], self.build_dir, None)
    self._runner(["mkdir", "-p", path], self.build_dir, None)
    return path

  def run(
      self,
      args: Sequence[Path | str],
      sub_build_dir: Path | None = None,
      env: Dict[str, str] | None = None,
  ) -> None:
    """Run a command in |sub_build_dir|, with optional |env|."""
    cur_dir = self.build_dir
    if sub_build_dir:
      cur_dir = cur_dir / sub_build_dir
    if env is None:
      env = self.env_copy()
    self._runner(args, cur_dir, env)

  def run_make_build(
      self, sub_build_dir: Path | None = None, extra_args: List[str] = []
  ) -> None:
    """Run `make -j<numcpus>` in |sub_build_dir|."""
    self.run(["make", f"-j{os.cpu_count()}"] + extra_args, sub_build_dir)

  def run_make_install(
      self,
      sub_build_dir: Path | None = None,
      use_DESTDIR: bool = False,
      dest_dir: Path | None = None,
  ) -> None:
    """Run `make install` in |sub_build_dir|.

    If use_DESTDIR is True, set DESTDIR env variable.
    """
    env = None
    if use_DESTDIR:
      env = self.env_with_DESTDIR(dest_dir=dest_dir)
    self.run(["make", "install"], sub_build_dir, env)

  def copy_file(self, src_path: Path, dst_path: Path):
    if dst_path.is_dir():
      raise ValueError(
          f"Misuse: dst_path ({dst_path}) points at an existing directory."
      )
    self._runner(["mkdir", "-p", dst_path.parent], None, None)
    self._runner(["cp", "-f", src_path, dst_path], None, None)

  def copy_dir(self, src_dir: Path, dst_dir: Path):
    self._runner(["mkdir", "-p", dst_dir.parent], None, None)
    self._runner(
        ["cp", "-rfL", "--no-target-directory", src_dir, dst_dir], None, None
    )


##########################################################################
##########################################################################
#####
#####  B U I L D   S E Q U E N C  E R
#####
#####  A |Project| can register build tasks and their dependencies.
#####  Then it can return a build plan to be executed in sequence.
#####
##########################################################################
##########################################################################

BuildTaskFn = Callable[[BuildConfig], None]


class Project:
  # Type of a build task function that takes a single |BuildConfig| argument.

  def __init__(self):
    self.tasks = {}

  def task(self, deps: List[BuildTaskFn]):
    """Decorator that registers a |BuildTaskFn| and its dependencies."""

    def decorator(fn: BuildTaskFn) -> BuildTaskFn:
      for dep in deps:
        if dep not in self.tasks:
          raise ValueError(
              f"Task {fn} depends on {dep}, but {dep} is was not yet defined."
              " Did you forgot to annotate it?"
          )
      if fn in self.tasks:
        raise ValueError(f"Task {fn} already defined.")
      self.tasks[fn] = deps
      return fn

    return decorator

  def get_build_task_list(
      self, task_function: BuildTaskFn
  ) -> List[BuildTaskFn]:
    """Returns the transitive dependency list of the current task."""
    # Rely on the fact that:
    # a - function are registered in topological order
    # b - python dictionaries are iterated in insertion order.
    task_list = list(self.tasks.keys())
    return task_list[: task_list.index(task_function) + 1]


project = Project()

##########################################################################
##########################################################################
#####
#####  I N D I V I D U A L   T A S K S
#####
#####  Each build_task_for_xxx() function below should only access a single
#####  BuildConfig argument, be decorated with `project.task` and enumerate
#####  the tasks it depends on.
#####
#####  These functions should also only use the BuildConfig methods
#####  to do their work, i.e. they shall not directly modify the
#####  filesystem or environment in any way.
#####
##########################################################################
##########################################################################


@project.task([])
def build_task_for_sysroot(build: BuildConfig):
  # populate_sysroot(build.build_dir / 'sysroot', build.prebuilts_dir),
  dst_sysroot = build.build_dir / "sysroot"
  dst_sysroot_lib_dir = dst_sysroot / "usr" / "lib"

  # Copy the content of the sysroot first.
  src_sysroot_dir = build.prebuilts_dir / "gcc/sysroot"
  build.copy_dir(src_sysroot_dir / "usr", dst_sysroot / "usr")

  # Add the static gcc runtime libraries and C runtime objects.
  static_libgcc_dir = build.prebuilts_dir / "gcc/lib/gcc/x86_64-linux/4.8.3"
  for lib in [
      "libgcc.a",
      "libgcc_eh.a",
      "crtbegin.o",
      "crtbeginS.o",
      "crtbeginT.o",
      "crtend.o",
      "crtendS.o",
  ]:
    build.copy_file(static_libgcc_dir / lib, dst_sysroot_lib_dir / lib)

  # Add the shared gcc runtime libraries.
  # Do we need libatomic.so and others?
  shared_libgcc_dir = build.prebuilts_dir / "gcc/x86_64-linux/lib64"
  for lib in ["libgcc_s.so", "libgcc_s.so.1", "libstdc++.a", "libstdc++.so"]:
    build.copy_file(shared_libgcc_dir / lib, dst_sysroot_lib_dir / lib)


@project.task([build_task_for_sysroot])
def build_task_for_ninja(build: BuildConfig):
  build.copy_file(
      build.prebuilts_dir / "ninja" / "ninja",
      build.install_dir / "usr" / "bin" / "ninja",
  )


@project.task([])
def build_task_for_python(build: BuildConfig):
  src_python_dir = build.third_party_dir / "python"
  dst_python_dir = build.install_dir / "usr"
  for d in ("bin", "lib", "share"):
    build.copy_dir(src_python_dir / d, dst_python_dir / d)


@project.task(
    [build_task_for_sysroot, build_task_for_ninja, build_task_for_python]
)
def build_task_for_meson(build: BuildConfig):
  meson_packager = (
      build.third_party_dir / "meson" / "packaging" / "create_zipapp.py"
  )
  build.run([
      "python3",
      "-S",
      meson_packager,
      "--outfile=%s" % (build.install_dir / "usr" / "bin" / "meson"),
      "--interpreter",
      "/usr/bin/env python3",
      build.third_party_dir / "meson",
  ])


@project.task([])
def build_task_for_rust(build: BuildConfig):
  log("Install prebuilt rust.")
  src_rust_dir = build.prebuilts_dir / "rust" / "linux-x86" / "1.73.0"
  dst_rust_dir = build.install_dir / "usr"
  for d in ("bin", "lib", "lib64", "share"):
    src_dir = src_rust_dir / d
    dst_dir = dst_rust_dir / d
    build.copy_dir(src_dir, dst_dir)
  build.run(
      ["ln", "-sf", "libc++.so", "libc++.so.1"],
      sub_build_dir=dst_rust_dir / "lib64",
      env={},
  )


@project.task([build_task_for_sysroot])
def build_task_for_make(build: BuildConfig) -> None:
  build.copy_file(
      build.prebuilts_dir / "build-tools" / "linux-x86" / "bin" / "make",
      build.install_dir / "usr" / "bin" / "make",
  )


@project.task([])
def build_task_for_cmake(build: BuildConfig):
  log("Install Cmake prebuilt.")
  build.copy_file(
      build.prebuilts_dir / "cmake" / "bin" / "cmake",
      build.install_dir / "usr" / "bin" / "cmake",
  )
  build.copy_dir(
      build.prebuilts_dir / "cmake" / "share",
      build.install_dir / "usr" / "share",
  )


@project.task([build_task_for_make])
def build_task_for_bzip2(build: BuildConfig):
  build_dir = build.make_subdir(Path("bzip2"))
  build.copy_dir(build.third_party_dir / "bzip2", build_dir)
  env = build.env_copy()
  build.run(
      [
          "make",
          f"-j{os.cpu_count()}",
          "CC=%s" % env["CC"],
          "AR=%s" % env["AR"],
          "CFLAGS=%s -O2 -D_FILE_OFFSET_BITS=64" % env["CFLAGS"],
          "LDFLAGS=%s" % env["LDFLAGS"],
      ],
      build_dir,
  )
  build.run(
      [
          "make",
          "install",
          f"PREFIX={build.install_dir}/usr",
      ],
      build_dir,
  )


@project.task([build_task_for_make])
def build_task_for_pkg_config(build: BuildConfig):
  build_dir = build.make_subdir(Path("pkg-config"))
  build.copy_dir(build.third_party_dir / "pkg-config", build_dir)
  build.run(
      [
          "sed",
          "-i",
          "s/m4_copy(/m4_copy_force(/g",
          "glib/m4macros/glib-gettext.m4",
      ],
      build_dir,
  )
  # Run configure separately so that we can pass "--with-internal-glib".
  build.run(["./autogen.sh", "--no-configure"], build_dir)

  cmd_env = build.env_copy()
  cmd_env["CFLAGS"] += " -Wno-int-conversion"
  cmd_args = [
      "./configure",
      "--prefix=%s/usr" % build.install_dir,
      "--disable-shared",
      "--with-internal-glib",
  ]
  build.run(cmd_args, build_dir, cmd_env)
  build.run_make_build(build_dir)
  build.run_make_install(build_dir)


@project.task([build_task_for_make])
def build_task_for_patchelf(build: BuildConfig):
  build_dir = build.make_subdir(Path("patchelf"))
  build.copy_dir(build.third_party_dir / "patchelf", build_dir)
  # Run configure separately so that we can pass "--with-internal-glib".
  build.run(["./bootstrap.sh"], build_dir)
  build.run(
      [
          "./configure",
          "--prefix=%s/usr" % build.install_dir,
      ],
      build_dir,
  )
  build.run_make_build(build_dir)
  build.run_make_install(build_dir)


@project.task([build_task_for_make, build_task_for_cmake])
def build_task_for_zlib(build: BuildConfig):
  lib_name = "zlib"
  src_dir = build.third_party_dir / lib_name
  build_dir = build.make_subdir(Path(lib_name))

  # `--undefined-version` workaround the pickiness of lld.
  # Some symbols of the link script are not found which
  # is an error for lld and not for ld.

  # `-Wunused-command-line-argument` remove annoying warnings
  # introduces by adding a linker flag to all the clang
  # invocations.

  # `--no-deprecated-non-prototype` removes warning due
  # to the use of deprecated C features.
  env = build.env_copy()
  env["CC"] += " -Wl,--undefined-version -Wno-unused-command-line-argument"
  env["CC"] += " -Wno-deprecated-non-prototype"

  cmd_args = [
      "cmake",
      f"-DCMAKE_INSTALL_PREFIX={build.install_dir}/usr",
      src_dir,
  ]
  build.run(cmd_args, build_dir, env=env)
  build.run_make_build(build_dir)
  build.run_make_install(build_dir, use_DESTDIR=False)


@project.task([build_task_for_make, build_task_for_bzip2])
def build_task_for_libpcre2(build: BuildConfig):
  build_dir = build.make_subdir(Path("pcre"))
  build.copy_dir(build.third_party_dir / "pcre", build_dir)

  cmd_args = [
      "./configure",
      "--prefix=/usr",
      "--disable-shared",
  ]
  build.run(cmd_args, build_dir)
  build.run_make_build(build_dir)
  build.run_make_install(build_dir, use_DESTDIR=True)


@project.task([build_task_for_make])
def build_task_for_libffi(build: BuildConfig):
  build_dir = build.make_subdir(Path("libffi"))
  build.copy_dir(build.third_party_dir / "libffi", build_dir)

  build.run(["./autogen.sh"], build_dir)

  cmd_args = [
      "./configure",
      "--prefix=/usr",
      "--disable-shared",
  ]
  build.run(cmd_args, build_dir)
  build.run_make_build(build_dir)
  build.run_make_install(build_dir, use_DESTDIR=True)


@project.task([
    build_task_for_make,
    build_task_for_meson,
    build_task_for_libffi,
    build_task_for_libpcre2,
    build_task_for_zlib,
    build_task_for_pkg_config,
])
def build_task_for_glib(build: BuildConfig):
  src_dir = build.third_party_dir / "glib"
  build_dir = build.make_subdir(Path("glib"))

  # --prefix=$DESTDIR is required to ensure the pkg-config .pc files contain
  #     the right absolute path.
  #
  # --includedir=$DESTDIR/include is required to avoid installs to
  #     /out/dest-install/out/dest-install/usr/include!
  #
  build.run(
      [
          "meson",
          "setup",
          "--default-library=static",
          "--prefix=%s/usr" % build.install_dir,
          "--includedir=%s/usr/include" % build.install_dir,
          "--libdir=%s/usr/lib" % build.install_dir,
          "--buildtype=release",
          "--wrap-mode=nofallback",
          build_dir,
          src_dir,
      ],
  )

  build.run(["ninja", "install"], build_dir)


@project.task([
    build_task_for_make,
    build_task_for_meson,
    build_task_for_pkg_config,
])
def build_task_for_pixman(build: BuildConfig):
  src_dir = build.third_party_dir / "pixman"
  build_dir = build.make_subdir(Path("pixman"))
  cmd_args = [
      "meson",
      "setup",
      "--prefix=%s/usr" % build.install_dir,
      "--includedir=%s/usr/include" % build.install_dir,
      "--libdir=%s/usr/lib" % build.install_dir,
      "--default-library=static",
      "-Dtests=disabled",
      "--buildtype=release",
      build_dir,
      src_dir,
  ]
  env = build.env_copy()
  env["CC"] += " -ldl -Wno-implicit-function-declaration"
  build.run(cmd_args, env=env)
  build.run(
      [
          "meson",
          "compile",
      ],
      build_dir,
  )
  build.run(
      [
          "meson",
          "install",
      ],
      build_dir,
  )


@project.task([
    build_task_for_make,
    build_task_for_glib,
])
def build_task_for_libslirp(build: BuildConfig):
  src_dir = build.third_party_dir / "libslirp"
  build_dir = build.make_subdir(Path("libslirp"))

  cmd_args = [
      "meson",
      "setup",
      "--prefix=%s/usr" % build.install_dir,
      "--includedir=%s/usr/include" % build.install_dir,
      "--libdir=%s/usr/lib" % build.install_dir,
      "--default-library=static",
      "--buildtype=release",
      build_dir,
      src_dir,
  ]
  build.run(cmd_args, src_dir)
  build.run(["ninja", "install"], build_dir)


@project.task([
    build_task_for_make,
    build_task_for_cmake,
])
def build_task_for_googletest(build: BuildConfig):
  dir_name = Path("googletest")
  build.make_subdir(dir_name)
  cmd_args = [
      "cmake",
      f"-DCMAKE_INSTALL_PREFIX={build.install_dir}/usr",
      build.third_party_dir / dir_name,
  ]
  build.run(cmd_args, dir_name)
  build.run_make_build(dir_name)
  build.run_make_install(dir_name, use_DESTDIR=False)


@project.task([
    build_task_for_make,
    build_task_for_cmake,
    build_task_for_googletest,
])
def build_task_for_aemu_base(build: BuildConfig):
  dir_name = Path("aemu")
  build.make_subdir(dir_name)
  # Options from third_party/aemu/rebuild.sh
  cmd_args = [
      "cmake",
      "-DAEMU_COMMON_GEN_PKGCONFIG=ON",
      "-DAEMU_COMMON_BUILD_CONFIG=gfxstream",
      "-DENABLE_VKCEREAL_TESTS=ON",  # `ON` for `aemu-base-testing-support`.
      f"-DCMAKE_INSTALL_PREFIX={build.install_dir}/usr",
      build.third_party_dir / dir_name,
  ]
  build.run(cmd_args, dir_name)
  build.run_make_build(dir_name)
  build.run_make_install(dir_name, use_DESTDIR=False)


@project.task([
    build_task_for_make,
    build_task_for_cmake,
])
def build_task_for_flatbuffers(build: BuildConfig):
  dir_name = Path("flatbuffers")
  build.make_subdir(dir_name)
  cmd_args = [
      "cmake",
      f"-DCMAKE_INSTALL_PREFIX={build.install_dir}/usr",
      build.third_party_dir / dir_name,
  ]
  build.run(cmd_args, dir_name)
  build.run_make_build(dir_name)
  build.run_make_install(dir_name, use_DESTDIR=False)


@project.task([
    build_task_for_make,
    build_task_for_meson,
])
def build_task_for_libpciaccess(build: BuildConfig):
  dir_name = Path("libpciaccess")
  src_dir = build.third_party_dir / dir_name
  build_dir = build.make_subdir(dir_name)

  build.run(
      [
          "meson",
          "setup",
          "--prefix=%s/usr" % build.install_dir,
          build_dir,
          src_dir,
      ],
  )
  build.run(
      [
          "meson",
          "compile",
      ],
      build_dir,
  )
  build.run(
      [
          "meson",
          "install",
      ],
      build_dir,
  )


@project.task([
    build_task_for_make,
    build_task_for_meson,
    build_task_for_libpciaccess,
])
def build_task_for_libdrm(build: BuildConfig):
  dir_name = Path("libdrm")
  src_dir = build.third_party_dir / dir_name
  build_dir = build.make_subdir(dir_name)

  build.run(
      [
          "meson",
          "setup",
          f"--prefix={build.install_dir}/usr",
          build_dir,
          src_dir,
      ],
  )
  build.run(
      [
          "meson",
          "compile",
      ],
      build_dir,
  )
  build.run(
      [
          "meson",
          "install",
      ],
      build_dir,
  )


@project.task([])
def build_task_for_egl(build: BuildConfig):
  build.copy_dir(
      build.third_party_dir / "egl" / "api" / "KHR",
      build.sysroot_dir / "usr" / "include" / "KHR",
  )
  build.copy_dir(
      build.third_party_dir / "egl" / "api" / "EGL",
      build.sysroot_dir / "usr" / "include" / "EGL",
  )


@project.task([
    build_task_for_meson,
    build_task_for_aemu_base,
    build_task_for_flatbuffers,
    build_task_for_egl,
    build_task_for_libdrm,
])
def build_task_for_gfxstream(build: BuildConfig):
  dir_name = Path("gfxstream")
  src_dir = build.third_party_dir / dir_name
  build_dir = build.make_subdir(dir_name)
  build.run(
      [
          "meson",
          "setup",
          f"--prefix={build.install_dir}/usr",
          build_dir,
          src_dir,
      ],
  )
  build.run(
      [
          "meson",
          "compile",
      ],
      build_dir,
  )
  build.run(
      [
          "meson",
          "install",
      ],
      build_dir,
  )


@project.task([
    build_task_for_make,
    build_task_for_rust,
    build_task_for_gfxstream,
])
def build_task_for_rutabaga(build: BuildConfig):
  out_dir = build.make_subdir(Path("rutabaga"))
  cmd_args = [
      build.install_dir / "usr/bin/cargo",
      "build",
      "--offline",
      "--features=gfxstream",
      "--release",
  ]
  env = {
      "CARGO_TARGET_DIR": str(out_dir),
      "GFXSTREAM_PATH": str(build.build_dir / "gfxstream" / "host"),
      "PATH": f"{build.install_dir}/usr/bin:{os.environ['PATH']}",
      "CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER": (
          f"{build.clang_dir}/bin/clang"
      ),
      "RUSTFLAGS": (
          f"-Clink-arg=--sysroot={build.sysroot_dir} -Clink-arg=-Wl,-rpath,$ORIGIN"
      ),
  }
  rutabaga_src_dir = build.third_party_dir / "crosvm" / "rutabaga_gfx" / "ffi"
  build.run(cmd_args, rutabaga_src_dir, env)

  build.copy_file(
      out_dir / "release" / "librutabaga_gfx_ffi.so",
      build.install_dir / "usr" / "lib" / "librutabaga_gfx_ffi.so",
  )
  build.run(
      ["ln", "-sf", "librutabaga_gfx_ffi.so", "librutabaga_gfx_ffi.so.0"],
      build.install_dir / "usr" / "lib",
      env={}
  )
  build.copy_file(
      out_dir / "release" / "rutabaga_gfx_ffi.pc",
      build.install_dir / "usr" / "lib" / "pkgconfig" / "rutabaga_gfx_ffi.pc",
  )
  build.copy_file(
      rutabaga_src_dir / "src" / "include" / "rutabaga_gfx_ffi.h",
      build.install_dir
      / "usr"
      / "include"
      / "rutabaga_gfx"
      / "rutabaga_gfx_ffi.h",
  )


@project.task([])
def build_task_for_libgbm(build: BuildConfig):
  # gbm is part of mesa which is a large project.
  # The dependency is taken fron the system.
  build.copy_file(
      "/usr/lib/x86_64-linux-gnu/libgbm.so.1",
      build.install_dir / "usr/lib/libgbm.so.1",
  )
  build.copy_file(
      "/usr/lib/x86_64-linux-gnu/libgbm.so",
      build.install_dir / "usr/lib/libgbm.so",
  )
  build.copy_file(
      "/usr/lib/x86_64-linux-gnu/libgbm.so.1.0.0",
      build.install_dir / "usr/lib/libgbm.so.1.0.0",
  )
  build.copy_file(
      "/usr/lib/x86_64-linux-gnu/pkgconfig/gbm.pc",
      build.install_dir / "usr/lib/pkgconfig/gbm.pc",
  )
  build.copy_file("/usr/include/gbm.h", build.install_dir / "usr/include/gbm.h")


@project.task([
    build_task_for_egl,
    build_task_for_libgbm,
    build_task_for_meson,
    build_task_for_ninja,
])
def build_task_for_libepoxy(build: BuildConfig):
  src_dir = build.third_party_dir / "libepoxy"
  build_dir = build.make_subdir(Path("libepoxy"))
  build.run(
      [
          "meson",
          "setup",
          "--prefix=%s/usr" % build.install_dir,
          "--libdir=%s/usr/lib" % build.install_dir,
          "-Dtests=false",
          build_dir,
          src_dir,
      ],
  )

  build.run(["ninja", "install"], build_dir)
  # There is a bug in`qemu/third_party/libepoxy/src/meson.build`
  # that result in a corrupted line `Requires.private: x11,` in `epoxy.pc`.
  # This is not valid and causes the failure:
  # `Empty package name in Requires or Conflicts in file '[...]epoxy.pc'`
  # This is because 'x11' is found as an implicit dependency and the
  # pkgconfig specification in the meson file adds an empty element.
  # Until a better solution is found, remove the dependency.
  build.run([
      "sed",
      "-i",
      "s/Requires.private: x11, $//g",
      build.install_dir / "usr/lib/pkgconfig/epoxy.pc",
  ])


@project.task([
    build_task_for_egl,
    build_task_for_libdrm,
    build_task_for_libepoxy,
    build_task_for_libgbm,
    build_task_for_meson,
    build_task_for_ninja,
])
def build_task_for_virglrenderer(build: BuildConfig):
  src_dir = build.third_party_dir / "virglrenderer"
  build_dir = build.make_subdir(Path("virglrenderer"))
  build.run(
      [
          "meson",
          "setup",
          "--prefix=%s/usr" % build.install_dir,
          "--libdir=%s/usr/lib" % build.install_dir,
          "-Dplatforms=egl",
          "-Dtests=false",
          build_dir,
          src_dir,
      ],
  )

  build.run(["ninja", "install"], build_dir)


@project.task([
    build_task_for_make,
    build_task_for_libslirp,
    build_task_for_glib,
    build_task_for_pixman,
    build_task_for_zlib,
    build_task_for_pkg_config,
    build_task_for_rutabaga,
    build_task_for_gfxstream,
    build_task_for_virglrenderer,
])
def build_task_for_qemu(build: BuildConfig):
  target_list = [
      "aarch64-softmmu",
      "riscv64-softmmu",
      "x86_64-softmmu",
  ]
  src_dir = build.third_party_dir / "qemu"
  build_dir = build.make_subdir(Path("qemu"))
  cmd_args: List[str | Path] = [
      src_dir.resolve() / "configure",
      "--prefix=/usr",
      "--target-list=%s" % ",".join(target_list),
      "--disable-plugins",
      "--enable-virglrenderer",
      # Cuttlefish is packaged in host archives that are assembled in
      # `$ANDROID_BUILD_TOP/out/host/linux-x86`.
      # Binaries are in `./bin` and resources are in `./usr/share` which is
      # different from QEMU default expectations. Details in b/296286524.
      # Move the binary directory up by one. This path is relative to
      # `--prefix` above.
      "-Dbindir=../bin",
      # Because the canonicalized `bindir` is `/bin` and does not start
      # with the `--prefix` the `qemu_firmwarepath` is interpreted differently.
      # Hence we have to rewrite it to work as expected.
      "-Dqemu_firmwarepath=../usr/share/qemu",
      # `gfxstream` is is only capable to output a dynamic library for now
      # `libgfxstream_backend.so`
      # "--static",
      # "--with-git-submodules=ignore",
  ]
  build.run(cmd_args, build_dir)
  build.run_make_build(build_dir)


@project.task([
    build_task_for_qemu,
    build_task_for_patchelf,
])
def build_task_for_qemu_portable(build: BuildConfig):
  package_dir = build.make_subdir(Path("qemu-portable"))
  # Install to a new directory rather than to the common taks install dir.
  build.run_make_install(
      build.build_dir / "qemu", use_DESTDIR=True, dest_dir=package_dir
  )
  bin_dir = package_dir / "bin"
  files = [
      "dest-install/usr/lib/libgbm.so.1",
      "dest-install/usr/lib/libz.so.1",
      "dest-install/usr/lib/libepoxy.so.0",
      "dest-install/usr/lib/libvirglrenderer.so.1",
      "dest-install/usr/lib/librutabaga_gfx_ffi.so.0",
      "dest-install/usr/lib64/libc++.so.1",
  ]
  # Meson install directory depends on the system and differs between podman and
  # the developer's workstation. Probe the file system to pick the right location.
  either_or = [
      "dest-install/usr/lib/x86_64-linux-gnu/libgfxstream_backend.so.0",
      "dest-install/usr/lib/libgfxstream_backend.so.0",
  ]
  try:
    files.append(
        next(
            path for path in either_or if os.path.isfile(build.build_dir / path)
        )
    )
  except StopIteration:
    raise FileNotFoundError(f"None of the paths exist: {either_or}")

  build.run(["cp", "-t", bin_dir] + files)
  build.run(["chmod", "a+rx"] + list(bin_dir.glob("*")))
  build.run(["patchelf", "--set-rpath", "$ORIGIN"] + list(bin_dir.glob("*")))
  build.run(
      [
          "tar",
          "-czvf",
          "qemu-portable.tar.gz",
          "--directory",
          "qemu-portable",
          ".",
      ],
      build.build_dir,
  )


@project.task([
    build_task_for_qemu_portable,
])
def build_task_for_qemu_test(build: BuildConfig):
  build.run(["make", "test"], build.build_dir / "qemu")


##########################################################################
##########################################################################
#####
#####  B U I L D   T A S K S
#####
##########################################################################
##########################################################################


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument("--build-dir", required=True, help="Build directory.")
  parser.add_argument("--ccache", action="store_true", help="Enable ccache.")
  parser.add_argument(
      "--run-tests",
      action="store_true",
      help="Run QEMU test suite after the build.",
  )
  parser.add_argument(
      "tasks",
      metavar="T",
      type=str,
      nargs="*",
      help="run task by names in the specified order",
  )
  args = parser.parse_args()

  build_dir = Path(args.build_dir)

  top_dir = Path(os.path.dirname(__file__)).parent
  build_config = BuildConfig(build_dir, top_dir)

  if args.ccache:
    build_config.enable_ccache()

  if args.tasks:
    for task in args.tasks:
      globals()[task](build_config)
  else:
    if build_dir.exists():
      print("Cleaning up build directory...")
      for f in os.listdir(build_dir):
        path = build_dir / f
        if os.path.isfile(path):
          os.remove(path)
        else:
          shutil.rmtree(path)
    else:
      os.makedirs(build_dir)

    # Compute the build plan to get 'qemu'
    build_tasks = project.get_build_task_list(
        build_task_for_qemu_test
        if args.run_tests
        else build_task_for_qemu_portable
    )

    print("BUILD PLAN: %s" % ", ".join([t.__name__ for t in build_tasks]))

    for task in build_tasks:
      task(build_config)

  return 0


if __name__ == "__main__":
  sys.exit(main())
