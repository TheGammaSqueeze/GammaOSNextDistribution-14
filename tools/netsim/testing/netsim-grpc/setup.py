"""Setup file for netsim-grpc.

Use setup.cfg to configure your project.

This file was generated with PyScaffold 4.3.
PyScaffold helps you to put up the scaffold of your new Python project.
Learn more under: https://pyscaffold.org/
"""

import os
from os import path
import subprocess
import sys

from setuptools import setup
from setuptools.command.build_py import build_py


class ProtoBuild(build_py):
  """This command automatically compiles all netsim .proto files with `protoc` compiler

  and places generated files under src/netsim_grpc/proto/
  """

  def run(self):
    here = path.abspath(path.dirname(__file__))
    root_dir = path.dirname(path.dirname(here))
    aosp_dir = path.dirname(path.dirname(root_dir))
    proto_root_dir = path.join(root_dir, "proto")
    proto_dir = path.join(proto_root_dir, "netsim")
    rootcanal_proto_root_dir = path.join(
        aosp_dir,
        "packages",
        "modules",
        "Bluetooth",
        "tools",
        "rootcanal",
        "proto",
    )
    rootcanal_proto_dir = path.join(rootcanal_proto_root_dir, "rootcanal")
    out_dir = path.join(here, "src", "netsim_grpc", "proto")

    # Rootcanal Protobufs
    for proto_file in filter(
        lambda x: x.endswith(".proto"), os.listdir(rootcanal_proto_dir)
    ):
      source = path.join(rootcanal_proto_dir, proto_file)
      output = path.join(out_dir, "rootcanal", proto_file).replace(
          ".proto", "_pb2.py"
      )

      if not path.exists(output) or (
          path.getmtime(source) > path.getmtime(output)
      ):
        sys.stderr.write(f"Protobuf-compiling {source}\n")

        subprocess.check_call([
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"-I{proto_root_dir}",
            f"-I{rootcanal_proto_root_dir}",
            f"--python_out={out_dir}",
            f"--grpc_python_out={out_dir}",
            source,
        ])

    # Netsim Protobufs
    for proto_file in filter(
        lambda x: x.endswith(".proto"), os.listdir(proto_dir)
    ):
      source = path.join(proto_dir, proto_file)
      output = path.join(out_dir, "netsim", proto_file).replace(
          ".proto", "_pb2.py"
      )

      if not path.exists(output) or (
          path.getmtime(source) > path.getmtime(output)
      ):
        sys.stderr.write(f"Protobuf-compiling {source}\n")

        subprocess.check_call([
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"-I{proto_root_dir}",
            f"-I{rootcanal_proto_root_dir}",
            f"--python_out={out_dir}",
            f"--grpc_python_out={out_dir}",
            source,
        ])

    super().run()


if __name__ == "__main__":
  try:
    setup(
        # use_scm_version={"version_scheme": "no-guess-dev", "root": "../../../"},
        cmdclass={"build_py": ProtoBuild},
    )
  except:  # noqa
    print(
        "\n\nAn error occurred while building the project, "
        "please ensure you have the most updated version of setuptools, "
        "setuptools_scm and wheel with:\n"
        "   pip install -U setuptools setuptools_scm wheel\n\n"
    )
    raise
