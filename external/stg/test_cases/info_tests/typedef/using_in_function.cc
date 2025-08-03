namespace foo {
  struct Foo { int x; };
}

int bar() {
  // This using directive should generate DW_TAG_imported_declaration in DWARF.
  using foo::Foo;
  return Foo().x;
}
