This directory contains files related to storage of read-only S2 data in blocks.
The classes are generic.

The data file format is intended to be language neutral (e.g. Java or C code could easily be
written to read it with minimal dependencies), providing a good balance between file size,
extensibility and memory usage at runtime.

High level file structure
=========================

  * `src/readonly/` - host + device code for reading data files
  * `src/write/`    - host code for writing data files
  * `src/test/`     - host tests for readonly/ and write/ code
  * `tools/`        - host tooling to support generation / debugging / testing of data
                      files.

Developed for Android, the code is also intended to work with host Java for easy
testing and debugging. The code is split into "readonly" and "write" code, as
the code that writes the files is expected to only be used on host during file
generation, while the "readonly" would be used on both host and device.

Block file format information
=============================

A "block file" is a general-purpose file format containing a small amount of header information,
blocks of data, and metadata about those blocks. All types are big-endian / network byte ordered.

Blocks are assigned contiguous IDs which are numbered from zero.

1. The file header has a type-identifying magic, and a version.
2. Next are the block infos, which hold metadata about all blocks in the file such as their ID,
a type ID, (optional) arbitrary "extra" bytes, and size / offset information for the block.
3. Lastly, come the blocks of data themselves. Blocks can be zero-length, in which case they take up
no space in the file.

Packed tables
=============

Packed tables are a way of arranging block data to store tables of key-ordered key / value pairs in
a compact way. Each entry in the table takes up a fixed number of bytes.

Packed tables may contain some (optional) shared information that applies to all records in the
table. Then they contain one or more fixed length `{key}`/`{value}` records of `{R}` bits sorted by
`{key}`.  The table data is easily memory mapped and each record can be randomly accessed by
`{key}`.

