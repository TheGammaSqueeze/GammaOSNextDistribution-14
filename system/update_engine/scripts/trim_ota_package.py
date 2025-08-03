#!/usr/bin/env python3

import zipfile
import struct


def readPayloadMetadata(zfp: zipfile.ZipFile, entry):
  _MAGIC = b'CrAU'
  # 8 bytes for version, 8 bytes for manifest length, 4 bytes for metadata signature length
  HEADER_STRUCT = ">4sQQL"
  HEADER_LEN = struct.calcsize(HEADER_STRUCT)
  with zfp.open(entry) as fp:
    header = fp.read(HEADER_LEN)
    (magic, version, manifest_length,
     metadata_signature_len) = struct.unpack(HEADER_STRUCT, header)
    assert magic == _MAGIC
    assert version == 2, "Unsupported major payload version " + str(version)
    print(f"{manifest_length} {metadata_signature_len}")
    return header + fp.read(manifest_length + metadata_signature_len)


def main(argv):
  if len(argv) != 3:
    print("Usage:", argv[0], "<input file> <output file>")
    return 1
  infile = argv[1]
  outfile = argv[2]
  with zipfile.ZipFile(infile, "r") as inzfp, zipfile.ZipFile(outfile, "w") as outzfp:
    for entry in inzfp.infolist():
      if entry.filename.startswith("META") or entry.filename.endswith(".map") or entry.filename.endswith(".prop"):
        outzfp.writestr(entry, inzfp.read(entry))
      elif entry.filename == "payload.bin":
        outzfp.writestr(entry, readPayloadMetadata(inzfp, entry))


if __name__ == '__main__':
  import sys
  sys.exit(main(sys.argv))
