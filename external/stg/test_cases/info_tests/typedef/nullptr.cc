// This should produce DWARF like this:
//
// DW_TAG_unspecified_type
//   DW_AT_name ("decltype(nullptr)")
//
typedef decltype(nullptr) nullptr_t;

void foo(nullptr_t) {}
