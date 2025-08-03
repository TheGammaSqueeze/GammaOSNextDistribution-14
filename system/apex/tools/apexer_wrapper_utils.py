#!/usr/bin/env python3

import hashlib
import subprocess

def RunCommand(cmd: list[str]) -> None:
  """Construct a command line from parts and run it."""
  try:
    res = subprocess.run(
        cmd,
        check=True,
        stdout=subprocess.PIPE,
        universal_newlines=True,
        stderr=subprocess.PIPE)
  except subprocess.CalledProcessError as err:
    print(err.stderr)
    print(err.output)
    raise err

def GetDigest(file_path: str) -> str:
  """Get sha512 digest of a file """
  digester = hashlib.sha512()
  with open(file_path, 'rb') as f:
    bytes_to_digest = f.read()
    digester.update(bytes_to_digest)
    return digester.hexdigest()
