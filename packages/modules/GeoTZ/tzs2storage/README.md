This directory contains files related to storage of TZ S2 data files used for offline geolocation
time zone detection.

High level file structure
=========================

  * `src/readonly/` - host + device code for reading TZ S2 data files
  * `src/write/`    - host code for writing TZ S2 data files
  * `src/test/`     - host tests for readonly/ and write/ code
  * `tools/`        - host tooling to support generation / debugging / testing of TZ S2 data
                      files.

Format overview
===============

The TZ S2 data file format builds on top of a generic "block file format" where
most of the blocks are "packed tables". See the README.md associated with the
com.android.storage classes for more information.

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

TZ S2 data file format information
==================================

The TZ S2 data file is a type of block file that uses packed tables. It overlays additional rules,
block types, etc. on top of the general block file format.

High level description
----------------------

The TZ S2 data file format is intended to be language neutral (e.g. Java or C code could easily be
written to read it with minimal dependencies), providing a good balance between file size,
extensibility and memory usage at runtime.

It is designed to be flexible and parameterized / self describing: the header block contains format
information needed to locate and interpret the data storage blocks.

The file stores time zone geolocation data at a single S2 level. Logically, the data consists of:

```
{start S2 cell ID (inclusive)}, {end S2 cell ID (exclusive)}, {time zone IDs}
```

The main usecase of the file is to lookup the time zone ID(s) (if any) for a given S2 cell ID.

General storage approach
------------------------

To keep the file size to a minimum, the format avoids storing 64-bit S2 cell IDs directly. Instead,
the logical input data is mapped to a data structure that allows the individual S2 cell IDs to be
stored using only a subset of the bits needed to store a full S2 cell ID.

Each logical S2 range data described above is subdivided into ranges with a common S2 cell ID
prefix. Any ranges in the source data that span a prefix are split into smaller ranges to preserve
the "same prefix" property. All ranges with a common prefix can be stored in one "suffix table".

The time zone ID strings are also only stored once and are referenced indirectly, avoiding repeated
storage of common strings.

TZ S2 data lookup
-----------------

Suffix table block IDs are calculated by taking the prefix of the S2 cell ID being sought and
applying a fixed offset. The block info and block data for the cell's suffix table can be accessed
directly using the block's ID.

Specifically:

The `{prefix}` is computed by extracting the first `{X}` bits of the S2 cell ID. The `{prefix}` is
used to obtain the `{block ID}` of the block used to store the suffix table. The `{block ID}` is
calculated by adding a fixed offset (obtained from the header block) to the cell ID `{prefix}`.

The `{block ID}` is first used to look lookup the block info. If the length of the block with
`{block ID}` is zero, the lookup stops at this point as it means there are no ranges for `{prefix}`.

When the `{block ID}` block is non-zero length, the block is interpreted as a packed table
(described above) which stores the suffix table's entries.

The `{suffix}`, the final `{Y}` bits of the search S2 cell ID, is used to seek for a record
containing the s2 range holding that cell ID, if any. The `{suffix}` will match either no records or
only one record in the table.

For more information on suffix table storage, see the Suffix Table Blocks section below.

The Header Block
----------------

The header block is always required to be block zero and have the expected type
ID (1).

The header contains format information needed to address the suffix table blocks
and the block format information needed to interpret those blocks. It also
contains information shared by all blocks such as the TZ ID sets.

TZ ID Sets storage
------------------

Sets of one or more time zone IDs are referenced by every range stored in the TZ S2 data file.

Individual time zone IDs are strings like "America/Los_Angeles" that should only be stored once to
conserve space.

Further, the time zone IDs are referenced as sets, e.g. one cell range may reference
"Europe/London", another may reference "Europe/Paris" and another may reference
both "Europe/London" and "Europe/Paris".

It is important to keep the number of bits used in each suffix table entry to a
minimum because there are hundreds of thousands of ranges globally and hundreds
of distinct sets. To do this we make use of "banking", which leverages
properties of the data.

For example:

1. Several ranges with S2 cell IDs close together may reference the same set - e.g. there
will be several range entries that reference "Europe/London".
2. There is unlikely to a single S2 cell that needs to reference both "America/Los_Angeles" and
"Europe/London", since the areas covered by those time zone IDs are geographically separate.

Consequently:

Every time zone ID string referenced in the file is assigned a numeric ID. The string is stored once
in the file in the header block. All references to time zone IDs are made via the numeric ID.

e.g.
```
1: Europe/London
2: Europe/Paris
3: ...
```

Every TZ S2 data file has one or more "set banks". Each bank contains an array of `{set of time zone
IDs}`.

A bank may contain many sets, which various combinations of TZ IDs:
```
1: {1}   - meaning the set {"Europe/London"}
2: {2}   - meaning the set {"Europe/Paris"}
3: {1,2} - meaning the set {"Europe/London", "Europe/Paris"}
...
```

Via this indirection and banking, each range entry can address a set of time zone ID strings using
only a numeric bank ID and a numeric set ID. The bank ID is the same for all entries in suffix
table, so this means that it can be stored once per table and only the (small) `{TZ IDs set ID}`
needs to be stored with each entry.

Suffix Table Blocks
-------------------

The suffix table block is a packed table with shared information and one or more entries.

The shared information consists of:

```
{TZ IDs set bank}       - the bank ID of the TZ IDs set bank used when looking up time zone set IDs
                          referenced by all table entries.
```

Each record in the suffix table logically holds entries consisting of:
```
{start S2 cell ID (inclusive)}, {end S2 cell ID (exclusive)}, {time zone IDs}`
```

As with any packed table, each record in the packed table has a fixed width of `{R}` bits. The first
`{M}` bits of every record are used to store the (ordered) `{key}`.

The `{key}` for an entry contains only the `{suffix}` bits from the `{start S2 cell ID
(inclusive)}`. To reconstruct the `{start S2 cell ID (inclusive)}` it's only necessary to know
the `{prefix}` for the table and the `{key}`.

The remaining (`{R-M}`) bits are used to store the ``{value}``. The ``{value}`` is further
sub-divided into two: the `{end S2 cell ID offset}` and the `{TZ IDs set ID}`.

The `{end S2 cell ID offset}` is a transformation of the `{end S2 cell ID (exclusive)}`. The `{end
S2 cell ID}` can be calculated by adding the `{end S2 cell ID offset}` to the `{start S2 cell ID
(inclusive)}`.

When searching for an S2 cell ID, the prefix is used to locate the correct suffix table. The suffix
bits from the S2 cell ID can be extracted. Since all data in the table is held at a single S2 level,
the suffix bits can be used to binary search the suffix table entries by looking for an entry
containing the suffix bits, i.e. by comparing the suffix bits against the `{key}` and `{key}` +
`{end S2 cell ID offset}` value.

If an entry is found, the `{TZ set ID}` indirectly leads to the `{time zone IDs}` for the range. For
more information see TZ ID Sets storage above.
