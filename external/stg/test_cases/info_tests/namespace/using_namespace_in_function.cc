namespace foo {
  struct Foo { int x; };
}

int bar() {
  // This using directive should generate DW_TAG_imported_module in DWARF.
  using namespace foo;
  return Foo().x;
}
