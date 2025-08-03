# import mmap

import struct

LZ4_FRAME_MAGIC = b"\x04\x22\x4D\x18"


def scan_legacy_lz4_frames(data):
  LZ4_LEGACY_FRAME_MAGIC = b"\x02\x21\x4C\x18"
  index = 0
  while index < len(data):
    try:
      index = data.index(LZ4_LEGACY_FRAME_MAGIC, index)
      print("Legacy Lz4 frame at {}".format(index))
      index += 4
      while index < len(data):
        magic = data[index:index+4]
        if magic == LZ4_LEGACY_FRAME_MAGIC or magic == LZ4_FRAME_MAGIC:
          break
        (csize,) = struct.unpack("<L", magic)
        if index + 4 + csize >= len(data) or csize == 0:
          break
        print("Legacy lz4 block at {}, compressed data size {}".format(index, csize))
        index += csize

    except ValueError:
      break


def scan_lz4_frames(data):
  index = 0
  while index < len(data):
    try:
      index = data.index(LZ4_FRAME_MAGIC, index)
      frame_offset = index
      index += 4
      flag = data[index]
      block_descriptor = data[index+1]
      block_checksum_present = flag & 0x10 != 0
      content_size_present = flag & 0x8 != 0
      content_checksum_present = flag & 0x4 != 0
      dictionary_id = flag & 0x1 != 0
      index += 2
      content_size = None
      if content_size_present:
        content_size = struct.unpack("<Q", data[index:index+8])
        index += 8
      if dictionary_id:
        dictionary_id = struct.unpack("<L", data[index:index+4])
        index += 4
      header_checksum = data[index:index+1]
      index += 1
      print("Lz4 frame at {}, content size: {}".format(
          frame_offset, content_size))
      while index < len(data):
        (block_size,) = struct.unpack("<L", data[index:index+4])
        uncompressed = block_size & 0x80000000 != 0
        block_size &= 0x7FFFFFFF
        index += 4
        index += block_size
        if index >= len(data) or block_size == 0:
          break
        print("Block uncompressed: {}, size: {}".format(uncompressed, block_size))
    except ValueError:
      break


def main(argv):
  if len(argv) != 2:
    print("Usage:", argv[0], "<path to a file>")
    return 1
  path = argv[1]

  with open(path, "rb") as fp:
    data = fp.read()
    scan_legacy_lz4_frames(data)
    scan_lz4_frames(data)


if __name__ == '__main__':
  import sys
  sys.exit(main(sys.argv))
