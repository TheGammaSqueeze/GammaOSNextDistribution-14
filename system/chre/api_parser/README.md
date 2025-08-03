# CHRE API Parser + Code Generator for CHPP

Since one of the main use cases of CHPP is to support remoting a CHRE PAL
implementation to a peripheral component, we leverage the CHRE PAL API
definitions for the platform-specific implementation of those services, such
as the WWAN service. However, the structures used in the CHRE PAL APIs are the
same as those used in the CHRE API for nanoapps, which are not inherently
serializable, as they include things like pointers to other structures. So we
must create slightly updated versions of these to send over the wire with CHPP.

The `chre_api_to_chpp.py` script in this directory parses CHRE APIs according
to instructions given in `chre_api_annotations.json` and generates structures
and conversion code used for serialization and deserialization.

## Serialization format

_TL;DR: The CHPP format is just a packed and flattened version of the CHRE
structures, replacing pointers with offsets._

The serialized output is meant to match the source CHRE structure as closely as
possible, however some special handling is needed to be able to send them over
the wire to a different processor in the system. The general process for
 generating a serializable CHPP version of a CHRE structure is as follows:

 1. Rewrite the name to start with "Chpp" instead of "chre" and mark it packed,
    to ensure there's no padding between fields (which is compiler-dependent)
 1. Flatten the structure by replacing pointers to nested structures/arrays with
    a ChppOffset which describes the location in the payload where the nested
    data resides.
 1. A CHPP application layer header is allocated for convenience when used in
    CHPP. The header values need to be set by CHPP and they are not zeroed out.
    The header length is not included in the offset calculation.

ChppOffset is a collection of a 16-bit offset from the beginning of the payload,
and a length. While we could implicitly derive the offset, the length cannot
exceed 16 bits due to limitations in the encapsulating CHPP protocol
(and we don't expect to bump up against this limit anyways), and including the
offset field helps simplify decoding.

This approach allows for a highly optimized, in-place encoding and decoding that
only requires converting between pointer and ChppOffset types, if the following
conditions are met:

 1. The size of the CHRE and CHPP structures are the same, and the offsets to
    all contained fields are the same, and the same property holds for all
    nested structures/unions
 1. (Encoding only) All nested structures appear in contiguous memory - for
    example, if a structure has a pointer to an array, then the array appears
    immediately after the structure in memory, with no padding
 1. (Decoding only) Processor alignment constraints are met for all fields, such
    that they can be safely accessed through normal means at the location in
    memory at which they reside, when accounting for any headers, etc.

If conditions 2 or 3 are not met, then optimized encode/decode is still possible
after copying the memory to a separate allocation. So in-place conversion can't
be done, but the only translation needed is between pointer and ChppOffset. If
the first condition is not met, then conversion is done using field-wise
assignment, which allows the compiler to generate the necessary instructions to
handle differences in alignment and packing.

All generated code currently assumes that it's running on a little endian CPU,
as the wire format requires little endian byte order.

TODO: The script currently only generates code for the pessimistic case, where
none of the constraints are met. By generating code that checks sizeof/offsetof
on all fields and placing the optimized path in an if/else condition, the
compiler can evaluate condition 1 and prune away code for the less-optimal path.

## Annotations

As the C API does not have enough information for the script to know how to
handle things like variable-length arrays (VLAs), we use the
`chre_api_annotations.json` file to provide this detail. The supported fields
 are explained in the illustrated example below:

```json
[// A list of entries for each input file
{
  // Path starting at <android_root>/system/chre to the input file
  "filename": "chre_api/include/chre_api/chre/wwan.h",
  // List of #includes that also need to be parsed, e.g. because they provide
  // struct definitions that are used in the file
  "includes": [
    "chre_api/include/chre_api/chre/common.h"
  ],
  // Which files the output header should pull in to satisfy dependencies
  "output_includes": [
    "chpp/common/common_types.h",
    "chre_api/chre/wwan.h"
  ],
  // A list of entries providing additional information for structs/unions that
  // appear in the input
  "struct_info": [
    {
      "name": "chreWwanCellInfoResult",
      // List of annotations for fields within the struct
      "annotations": [
        // Instead of copying the input, always force setting a specific value
        // for a field. The value is whatever should appear in the code for the
        // assignment statement, or the value to memset it to for an array type.
        {
          // Which field within the struct this annotation applies to
          "field": "version",
          // The type of annotation we're supplying
          "annotation": "fixed_value",
          // Additional information is dependent upon the annotation type
          "value": "CHRE_WWAN_CELL_INFO_RESULT_VERSION"
        },
        // Since the 'cookie' field here is a void*, we're rewriting to a
        // uint32_t to keep the same structure size + field offsets (on 32-bit
        // architectures) - in practice we'll also force the value to 0
        {
          "field": "cookie",
          "annotation": "rewrite_type",
          "type_override": "uint32_t"
        },
        // Indicates a variable length array field, with the number of elements
        // given by another field in the same structure called cellInfoCount
        {
          "field": "cells",
          "annotation": "var_len_array",
          "length_field": "cellInfoCount"
        }
      ]
    },
    {
      "name": "chreWwanCellInfo",
      "annotations": [
        // In this case, we have a union field, where only one of the members is
        // used, according to the provided mapping on the adjacent discriminator
        // field
        {
          "field": "CellInfo",
          "annotation": "union_variant",
          "discriminator": "cellInfoType",
          "mapping": [
            ["CHRE_WWAN_CELL_INFO_TYPE_GSM", "gsm"],
            ["CHRE_WWAN_CELL_INFO_TYPE_CDMA", "cdma"],
            ["CHRE_WWAN_CELL_INFO_TYPE_LTE", "lte"],
            ["CHRE_WWAN_CELL_INFO_TYPE_WCDMA", "wcdma"],
            ["CHRE_WWAN_CELL_INFO_TYPE_TD_SCDMA", "tdscdma"],
            ["CHRE_WWAN_CELL_INFO_TYPE_NR", "nr"]
          ]
        }
      ]
    }
  ],
  // The list of top-level structures appearing in the input that we should
  // create conversion routines for
  "root_structs": [
    "chreWwanCellInfoResult"
  ]
}]
```

## Requirements

Tested with Python 3.7, but most versions of Python 3 should work.

Requires pyclibrary - see requirements.txt.
