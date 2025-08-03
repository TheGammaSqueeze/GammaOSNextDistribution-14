struct Scope {
  struct StructDecl {
    long x;
  };
  class ClassDecl {
    long x;
  };
  union UnionDecl {
    long x;
  };
  enum EnumDecl { X = 2 };
  typedef long TypedefDecl;
};

Scope::StructDecl struct_decl;
Scope::ClassDecl class_decl;
Scope::UnionDecl union_decl;
Scope::EnumDecl enum_decl;
Scope::TypedefDecl typedef_decl;
