// Using "__builtin_va_list" makes "__va_list_tag" structure appear in DWARF.
// This structure is made by compiler and doesn't have DW_AT_decl_file.
void* foo(__builtin_va_list* va_list) { return va_list; }
