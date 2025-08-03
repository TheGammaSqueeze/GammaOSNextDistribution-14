#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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

"""
This scripts compiles Java files which are needed to execute run-tests.
It is intended to be used only from soong genrule.
"""

import argparse
import functools
import glob
import os
import pathlib
import re
import shlex
import shutil
import subprocess
import sys
import zipfile

from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor
from fcntl import lockf, LOCK_EX, LOCK_NB
from importlib.machinery import SourceFileLoader
from os import environ, getcwd, chdir, cpu_count, chmod
from os.path import relpath
from pathlib import Path
from pprint import pprint
from re import match
from shutil import copytree, rmtree
from subprocess import run
from tempfile import TemporaryDirectory, NamedTemporaryFile
from typing import Dict, List, Union, Set, Optional

USE_RBE = 100  # Percentage of tests that can use RBE (between 0 and 100)

lock_file = None  # Keep alive as long as this process is alive.

RBE_COMPARE = False  # Debugging: Check that RBE and local output are identical.

RBE_D8_DISABLED_FOR = {
  "952-invoke-custom",        # b/228312861: RBE uses wrong inputs.
  "979-const-method-handle",  # b/228312861: RBE uses wrong inputs.
}

# Debug option. Report commands that are taking a lot of user CPU time.
REPORT_SLOW_COMMANDS = False

class BuildTestContext:
  def __init__(self, args, android_build_top, test_dir):
    self.android_build_top = android_build_top.absolute()
    self.bootclasspath = args.bootclasspath.absolute()
    self.test_name = test_dir.name
    self.test_dir = test_dir.absolute()
    self.mode = args.mode
    self.jvm = (self.mode == "jvm")
    self.host = (self.mode == "host")
    self.target = (self.mode == "target")
    assert self.jvm or self.host or self.target

    self.java_home = Path(os.environ.get("JAVA_HOME")).absolute()
    self.java_path = self.java_home / "bin/java"
    self.javac_path = self.java_home / "bin/javac"
    self.javac_args = "-g -Xlint:-options"

    # Helper functions to execute tools.
    self.d8_path = args.d8.absolute()
    self.d8 = functools.partial(self.run, args.d8.absolute())
    self.jasmin = functools.partial(self.run, args.jasmin.absolute())
    self.javac = functools.partial(self.run, self.javac_path)
    self.smali_path = args.smali.absolute()
    self.rbe_rewrapper = args.rewrapper.absolute()
    self.smali = functools.partial(self.run, args.smali.absolute())
    self.soong_zip = functools.partial(self.run, args.soong_zip.absolute())
    self.zipalign = functools.partial(self.run, args.zipalign.absolute())
    if args.hiddenapi:
      self.hiddenapi = functools.partial(self.run, args.hiddenapi.absolute())

    # RBE wrapper for some of the tools.
    if "RBE_server_address" in os.environ and USE_RBE > (hash(self.test_name) % 100):
      self.rbe_exec_root = os.environ.get("RBE_exec_root")

      # TODO(b/307932183) Regression: RBE produces wrong output for D8 in ART
      disable_d8 = any((self.test_dir / n).exists() for n in ["classes", "src2", "src-art"])

      if self.test_name not in RBE_D8_DISABLED_FOR and not disable_d8:
        self.d8 = functools.partial(self.rbe_d8, args.d8.absolute())
      self.javac = functools.partial(self.rbe_javac, self.javac_path)
      self.smali = functools.partial(self.rbe_smali, args.smali.absolute())

    # Minimal environment needed for bash commands that we execute.
    self.bash_env = {
      "ANDROID_BUILD_TOP": self.android_build_top,
      "D8": args.d8.absolute(),
      "JAVA": self.java_path,
      "JAVAC": self.javac_path,
      "JAVAC_ARGS": self.javac_args,
      "JAVA_HOME": self.java_home,
      "PATH": os.environ["PATH"],
      "PYTHONDONTWRITEBYTECODE": "1",
      "SMALI": args.smali.absolute(),
      "SOONG_ZIP": args.soong_zip.absolute(),
      "TEST_NAME": self.test_name,
    }

  def bash(self, cmd):
    return subprocess.run(cmd,
                          shell=True,
                          cwd=self.test_dir,
                          env=self.bash_env,
                          check=True)

  def run(self, executable: pathlib.Path, args: List[Union[pathlib.Path, str]]):
    assert isinstance(executable, pathlib.Path), executable
    cmd: List[Union[pathlib.Path, str]] = []
    if REPORT_SLOW_COMMANDS:
      cmd += ["/usr/bin/time"]
    if executable.suffix == ".sh":
      cmd += ["/bin/bash"]
    cmd += [executable]
    cmd += args
    env = self.bash_env
    env.update({k: v for k, v in os.environ.items() if k.startswith("RBE_")})
    # Make paths relative as otherwise we could create too long command line.
    for i, arg in enumerate(cmd):
      if isinstance(arg, pathlib.Path):
        assert arg.absolute(), arg
        cmd[i] = relpath(arg, self.test_dir)
      elif isinstance(arg, list):
        assert all(p.absolute() for p in arg), arg
        cmd[i] = ":".join(relpath(p, self.test_dir) for p in arg)
      else:
        assert isinstance(arg, str), arg
    p = subprocess.run(cmd,
                       encoding=sys.stdout.encoding,
                       cwd=self.test_dir,
                       env=self.bash_env,
                       stderr=subprocess.STDOUT,
                       stdout=subprocess.PIPE)
    if REPORT_SLOW_COMMANDS:
      m = re.search("([0-9\.]+)user", p.stdout)
      assert m, p.stdout
      t = float(m.group(1))
      if t > 1.0:
        cmd_text = " ".join(map(str, cmd[1:]))[:100]
        print(f"[{self.test_name}] Command took {t:.2f}s: {cmd_text}")

    if p.returncode != 0:
      raise Exception("Command failed with exit code {}\n$ {}\n{}".format(
                      p.returncode, " ".join(map(str, cmd)), p.stdout))
    return p

  def rbe_wrap(self, args, inputs: Set[pathlib.Path]=None):
    with NamedTemporaryFile(mode="w+t") as input_list:
      inputs = inputs or set()
      for i in inputs:
        assert i.exists(), i
      for i, arg in enumerate(args):
        if isinstance(arg, pathlib.Path):
          assert arg.absolute(), arg
          inputs.add(arg)
        elif isinstance(arg, list):
          assert all(p.absolute() for p in arg), arg
          inputs.update(arg)
      input_list.writelines([relpath(i, self.rbe_exec_root)+"\n" for i in inputs])
      input_list.flush()
      dbg_args = ["-compare", "-num_local_reruns=1", "-num_remote_reruns=1"] if RBE_COMPARE else []
      return self.run(self.rbe_rewrapper, [
        "--platform=" + os.environ["RBE_platform"],
        "--input_list_paths=" + input_list.name,
      ] + dbg_args + args)

  def rbe_javac(self, javac_path:Path, args):
    output = relpath(Path(args[args.index("-d") + 1]), self.rbe_exec_root)
    return self.rbe_wrap(["--output_directories", output, javac_path] + args)

  def rbe_d8(self, d8_path:Path, args):
    inputs = set([d8_path.parent.parent / "framework/d8.jar"])
    output = relpath(Path(args[args.index("--output") + 1]), self.rbe_exec_root)
    return self.rbe_wrap([
      "--output_files" if output.endswith(".jar") else "--output_directories", output,
      "--toolchain_inputs=prebuilts/jdk/jdk17/linux-x86/bin/java",
      d8_path] + args, inputs)

  def rbe_smali(self, smali_path:Path, args):
    # The output of smali is non-deterministic, so create wrapper script,
    # which runs D8 on the output to normalize it.
    api = args[args.index("--api") + 1]
    output = Path(args[args.index("--output") + 1])
    wrapper = output.with_suffix(".sh")
    wrapper.write_text('''
      set -e
      {smali} $@
      mkdir dex_normalize
      {d8} --min-api {api} --output dex_normalize {output}
      cp dex_normalize/classes.dex {output}
      rm -rf dex_normalize
    '''.strip().format(
      smali=relpath(self.smali_path, self.test_dir),
      d8=relpath(self.d8_path, self.test_dir),
      api=api,
      output=relpath(output, self.test_dir),
    ))

    inputs = set([
      wrapper,
      self.smali_path,
      self.smali_path.parent.parent / "framework/android-smali.jar",
      self.d8_path,
      self.d8_path.parent.parent / "framework/d8.jar",
    ])
    res = self.rbe_wrap([
      "--output_files", relpath(output, self.rbe_exec_root),
      "--toolchain_inputs=prebuilts/jdk/jdk17/linux-x86/bin/java",
      "/bin/bash", wrapper] + args, inputs)
    wrapper.unlink()
    return res

  def build(self) -> None:
    script = self.test_dir / "build.py"
    if script.exists():
      module = SourceFileLoader("build_" + self.test_name,
                                str(script)).load_module()
      module.build(self)
    else:
      self.default_build()

  def default_build(
      self,
      use_desugar=True,
      use_hiddenapi=True,
      need_dex=None,
      zip_compression_method="deflate",
      zip_align_bytes=None,
      api_level:Union[int, str]=26,  # Can also be named alias (string).
      javac_args=[],
      javac_classpath: List[Path]=[],
      d8_flags=[],
      d8_dex_container=True,
      smali_args=[],
      use_smali=True,
      use_jasmin=True,
      javac_source_arg="1.8",
      javac_target_arg="1.8"
    ):
    javac_classpath = javac_classpath.copy()  # Do not modify default value.

    # Wrap "pathlib.Path" with our own version that ensures all paths are absolute.
    # Plain filenames are assumed to be relative to self.test_dir and made absolute.
    class Path(pathlib.Path):
      def __new__(cls, filename: str):
        path = pathlib.Path(filename)
        return path if path.is_absolute() else (self.test_dir / path)

    need_dex = (self.host or self.target) if need_dex is None else need_dex

    if self.jvm:
      # No desugaring on jvm because it supports the latest functionality.
      use_desugar = False

    # Set API level for smali and d8.
    if isinstance(api_level, str):
      API_LEVEL = {
        "default-methods": 24,
        "parameter-annotations": 25,
        "agents": 26,
        "method-handles": 26,
        "var-handles": 28,
        "const-method-type": 28,
      }
      api_level = API_LEVEL[api_level]
    assert isinstance(api_level, int), api_level

    def zip(zip_target: Path, *files: Path):
      zip_args = ["-o", zip_target, "-C", zip_target.parent]
      if zip_compression_method == "store":
        zip_args.extend(["-L", "0"])
      for f in files:
        zip_args.extend(["-f", f])
      self.soong_zip(zip_args)

      if zip_align_bytes:
        # zipalign does not operate in-place, so write results to a temp file.
        with TemporaryDirectory() as tmp_dir:
          tmp_file = Path(tmp_dir) / "aligned.zip"
          self.zipalign(["-f", str(zip_align_bytes), zip_target, tmp_file])
          # replace original zip target with our temp file.
          tmp_file.rename(zip_target)


    def make_jasmin(dst_dir: Path, src_dir: Path) -> Optional[Path]:
      if not use_jasmin or not src_dir.exists():
        return None  # No sources to compile.
      dst_dir.mkdir()
      self.jasmin(["-d", dst_dir] + sorted(src_dir.glob("**/*.j")))
      return dst_dir

    def make_smali(dst_dex: Path, src_dir: Path) -> Optional[Path]:
      if not use_smali or not src_dir.exists():
        return None  # No sources to compile.
      p = self.smali(["-JXmx512m", "assemble"] + smali_args + ["--api", str(api_level)] +
                     ["--output", dst_dex] + sorted(src_dir.glob("**/*.smali")))
      assert dst_dex.exists(), p.stdout  # NB: smali returns 0 exit code even on failure.
      return dst_dex

    def make_java(dst_dir: Path, *src_dirs: Path) -> Optional[Path]:
      if not any(src_dir.exists() for src_dir in src_dirs):
        return None  # No sources to compile.
      dst_dir.mkdir(exist_ok=True)
      args = self.javac_args.split(" ") + javac_args
      args += ["-implicit:none", "-encoding", "utf8", "-d", dst_dir]
      args += ["-source", javac_source_arg, "-target", javac_target_arg]
      if not self.jvm and float(javac_target_arg) < 17.0:
        args += ["-bootclasspath", self.bootclasspath]
      if javac_classpath:
        args += ["-classpath", javac_classpath]
      for src_dir in src_dirs:
        args += sorted(src_dir.glob("**/*.java"))
      self.javac(args)
      javac_post = Path("javac_post.sh")
      if javac_post.exists():
        self.run(javac_post, [dst_dir])
      return dst_dir


    # Make a "dex" file given a directory of classes. This will be
    # packaged in a jar file.
    def make_dex(src_dir: Path):
      dst_jar = Path(src_dir.name + ".jar")
      args = []
      if d8_dex_container:
        args += ["-JDcom.android.tools.r8.dexContainerExperiment"]
      args += d8_flags + ["--min-api", str(api_level), "--output", dst_jar]
      args += ["--lib", self.bootclasspath] if use_desugar else ["--no-desugaring"]
      args += sorted(src_dir.glob("**/*.class"))
      self.d8(args)

      # D8 outputs to JAR files today rather than DEX files as DX used
      # to. To compensate, we extract the DEX from d8's output to meet the
      # expectations of make_dex callers.
      dst_dex = Path(src_dir.name + ".dex")
      with TemporaryDirectory() as tmp_dir:
        zipfile.ZipFile(dst_jar, "r").extractall(tmp_dir)
        (Path(tmp_dir) / "classes.dex").rename(dst_dex)

    # Merge all the dex files.
    # Skip non-existing files, but at least 1 file must exist.
    def make_dexmerge(dst_dex: Path, *src_dexs: Path):
      # Include destination. Skip any non-existing files.
      srcs = [f for f in [dst_dex] + list(src_dexs) if f.exists()]

      # NB: We merge even if there is just single input.
      # It is useful to normalize non-deterministic smali output.
      tmp_dir = self.test_dir / "dexmerge"
      tmp_dir.mkdir()
      flags = []
      if d8_dex_container:
        flags += ["-JDcom.android.tools.r8.dexContainerExperiment"]
      flags += ["--min-api", str(api_level), "--output", tmp_dir]
      self.d8(flags + srcs)
      assert not (tmp_dir / "classes2.dex").exists()
      for src_file in srcs:
        src_file.unlink()
      (tmp_dir / "classes.dex").rename(dst_dex)
      tmp_dir.rmdir()


    def make_hiddenapi(*dex_files: Path):
      if not use_hiddenapi or not Path("hiddenapi-flags.csv").exists():
        return  # Nothing to do.
      args: List[Union[str, Path]] = ["encode"]
      for dex_file in dex_files:
        args.extend(["--input-dex=" + str(dex_file), "--output-dex=" + str(dex_file)])
      args.append("--api-flags=hiddenapi-flags.csv")
      args.append("--no-force-assign-all")
      self.hiddenapi(args)


    if Path("classes.dex").exists():
      zip(Path(self.test_name + ".jar"), Path("classes.dex"))
      return

    if Path("classes.dm").exists():
      zip(Path(self.test_name + ".jar"), Path("classes.dm"))
      return

    if make_jasmin(Path("jasmin_classes"), Path("jasmin")):
      javac_classpath.append(Path("jasmin_classes"))

    if make_jasmin(Path("jasmin_classes2"), Path("jasmin-multidex")):
      javac_classpath.append(Path("jasmin_classes2"))

    # To allow circular references, compile src/, src-multidex/, src-aotex/,
    # src-bcpex/, src-ex/ together and pass the output as class path argument.
    # Replacement sources in src-art/, src2/ and src-ex2/ can replace symbols
    # used by the other src-* sources we compile here but everything needed to
    # compile the other src-* sources should be present in src/ (and jasmin*/).
    extra_srcs = ["src-multidex", "src-aotex", "src-bcpex", "src-ex"]
    replacement_srcs = ["src2", "src-ex2"] + ([] if self.jvm else ["src-art"])
    if (Path("src").exists() and
        any(Path(p).exists() for p in extra_srcs + replacement_srcs)):
      make_java(Path("classes-tmp-all"), Path("src"), *map(Path, extra_srcs))
      javac_classpath.append(Path("classes-tmp-all"))

    if make_java(Path("classes-aotex"), Path("src-aotex")) and need_dex:
      make_dex(Path("classes-aotex"))
      # rename it so it shows up as "classes.dex" in the zip file.
      Path("classes-aotex.dex").rename(Path("classes.dex"))
      zip(Path(self.test_name + "-aotex.jar"), Path("classes.dex"))

    if make_java(Path("classes-bcpex"), Path("src-bcpex")) and need_dex:
      make_dex(Path("classes-bcpex"))
      # rename it so it shows up as "classes.dex" in the zip file.
      Path("classes-bcpex.dex").rename(Path("classes.dex"))
      zip(Path(self.test_name + "-bcpex.jar"), Path("classes.dex"))

    make_java(Path("classes"), Path("src"))

    if not self.jvm:
      # Do not attempt to build src-art directories on jvm,
      # since it would fail without libcore.
      make_java(Path("classes"), Path("src-art"))

    if make_java(Path("classes2"), Path("src-multidex")) and need_dex:
      make_dex(Path("classes2"))

    make_java(Path("classes"), Path("src2"))

    # If the classes directory is not-empty, package classes in a DEX file.
    # NB: some tests provide classes rather than java files.
    if any(Path("classes").glob("*")) and need_dex:
      make_dex(Path("classes"))

    if Path("jasmin_classes").exists():
      # Compile Jasmin classes as if they were part of the classes.dex file.
      if need_dex:
        make_dex(Path("jasmin_classes"))
        make_dexmerge(Path("classes.dex"), Path("jasmin_classes.dex"))
      else:
        # Move jasmin classes into classes directory so that they are picked up
        # with -cp classes.
        Path("classes").mkdir(exist_ok=True)
        copytree(Path("jasmin_classes"), Path("classes"), dirs_exist_ok=True)

    if need_dex and make_smali(Path("smali_classes.dex"), Path("smali")):
      # Merge smali files into classes.dex,
      # this takes priority over any jasmin files.
      make_dexmerge(Path("classes.dex"), Path("smali_classes.dex"))

    # Compile Jasmin classes in jasmin-multidex as if they were part of
    # the classes2.jar
    if Path("jasmin-multidex").exists():
      if need_dex:
        make_dex(Path("jasmin_classes2"))
        make_dexmerge(Path("classes2.dex"), Path("jasmin_classes2.dex"))
      else:
        # Move jasmin classes into classes2 directory so that
        # they are picked up with -cp classes2.
        Path("classes2").mkdir()
        copytree(Path("jasmin_classes2"), Path("classes2"), dirs_exist_ok=True)
        rmtree(Path("jasmin_classes2"))

    if need_dex and make_smali(Path("smali_classes2.dex"), Path("smali-multidex")):
      # Merge smali_classes2.dex into classes2.dex
      make_dexmerge(Path("classes2.dex"), Path("smali_classes2.dex"))

    make_java(Path("classes-ex"), Path("src-ex"))

    make_java(Path("classes-ex"), Path("src-ex2"))

    if Path("classes-ex").exists() and need_dex:
      make_dex(Path("classes-ex"))

    if need_dex and make_smali(Path("smali_classes-ex.dex"), Path("smali-ex")):
      # Merge smali files into classes-ex.dex.
      make_dexmerge(Path("classes-ex.dex"), Path("smali_classes-ex.dex"))

    if Path("classes-ex.dex").exists():
      # Apply hiddenapi on the dex files if the test has API list file(s).
      make_hiddenapi(Path("classes-ex.dex"))

      # quick shuffle so that the stored name is "classes.dex"
      Path("classes.dex").rename(Path("classes-1.dex"))
      Path("classes-ex.dex").rename(Path("classes.dex"))
      zip(Path(self.test_name + "-ex.jar"), Path("classes.dex"))
      Path("classes.dex").rename(Path("classes-ex.dex"))
      Path("classes-1.dex").rename(Path("classes.dex"))

    # Apply hiddenapi on the dex files if the test has API list file(s).
    if need_dex:
      if any(Path(".").glob("*-multidex")):
        make_hiddenapi(Path("classes.dex"), Path("classes2.dex"))
      else:
        make_hiddenapi(Path("classes.dex"))

    # Create a single dex jar with two dex files for multidex.
    if need_dex:
      if Path("classes2.dex").exists():
        zip(Path(self.test_name + ".jar"), Path("classes.dex"), Path("classes2.dex"))
      else:
        zip(Path(self.test_name + ".jar"), Path("classes.dex"))


# If we build just individual shard, we want to split the work among all the cores,
# but if the build system builds all shards, we don't want to overload the machine.
# We don't know which situation we are in, so as simple work-around, we use a lock
# file to allow only one shard to use multiprocessing at the same time.
def use_multiprocessing(mode: str) -> bool:
  if "RBE_server_address" in os.environ:
    return True
  global lock_file
  lock_path = Path(environ["TMPDIR"]) / ("art-test-run-test-build-py-" + mode)
  lock_file = open(lock_path, "w")
  try:
    lockf(lock_file, LOCK_EX | LOCK_NB)
    return True  # We are the only instance of this script in the build system.
  except BlockingIOError:
    return False  # Some other instance is already running.


def main() -> None:
  parser = ArgumentParser(description=__doc__)
  parser.add_argument("--out", type=Path, help="Final zip file")
  parser.add_argument("--mode", choices=["host", "jvm", "target"])
  parser.add_argument("--bootclasspath", type=Path)
  parser.add_argument("--d8", type=Path)
  parser.add_argument("--hiddenapi", type=Path)
  parser.add_argument("--jasmin", type=Path)
  parser.add_argument("--rewrapper", type=Path)
  parser.add_argument("--smali", type=Path)
  parser.add_argument("--soong_zip", type=Path)
  parser.add_argument("--zipalign", type=Path)
  parser.add_argument("--test-dir-regex")
  parser.add_argument("srcs", nargs="+", type=Path)
  args = parser.parse_args()

  android_build_top = Path(getcwd()).absolute()
  ziproot = args.out.absolute().parent / "zip"
  test_dir_regex = re.compile(args.test_dir_regex) if args.test_dir_regex else re.compile(".*")
  srcdirs = set(
    s.parents[-4].absolute()
    for s in args.srcs
    if test_dir_regex.search(str(s))
  )

  # Special hidden-api shard: If the --hiddenapi flag is provided, build only
  # hiddenapi tests. Otherwise exclude all hiddenapi tests from normal shards.
  def filter_by_hiddenapi(srcdir: Path) -> bool:
    return (args.hiddenapi != None) == ("hiddenapi" in srcdir.name)

  # Initialize the test objects.
  # We need to do this before we change the working directory below.
  tests: List[BuildTestContext] = []
  for srcdir in filter(filter_by_hiddenapi, srcdirs):
    dstdir = ziproot / args.mode / srcdir.name
    copytree(srcdir, dstdir)
    tests.append(BuildTestContext(args, android_build_top, dstdir))

  # We can not change the working directory per each thread since they all run in parallel.
  # Create invalid read-only directory to catch accidental use of current working directory.
  with TemporaryDirectory("-do-not-use-cwd") as invalid_tmpdir:
    os.chdir(invalid_tmpdir)
    os.chmod(invalid_tmpdir, 0)
    with ThreadPoolExecutor(cpu_count() if use_multiprocessing(args.mode) else 1) as pool:
      jobs = {}
      for ctx in tests:
        jobs[ctx.test_name] = pool.submit(ctx.build)
      for test_name, job in jobs.items():
        try:
          job.result()
        except Exception as e:
          raise Exception("Failed to build " + test_name) from e

  # Create the final zip file which contains the content of the temporary directory.
  proc = run([android_build_top / args.soong_zip, "-o", android_build_top / args.out,
              "-C", ziproot, "-D", ziproot], check=True)


if __name__ == "__main__":
  main()
