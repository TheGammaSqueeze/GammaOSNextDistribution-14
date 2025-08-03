struct Scope {
  struct StructDecl {
    int x;
  };
  class ClassDecl {
    int x;
  };
  union UnionDecl {
    int x;
  };
  enum EnumDecl { X = 1 };
  typedef int TypedefDecl;
};

Scope::StructDecl struct_decl;
Scope::ClassDecl class_decl;
Scope::UnionDecl union_decl;
Scope::EnumDecl enum_decl;
Scope::TypedefDecl typedef_decl;
