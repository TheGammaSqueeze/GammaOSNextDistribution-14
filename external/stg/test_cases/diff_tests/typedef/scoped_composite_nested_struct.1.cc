struct Scope {
  typedef class {
    struct Nested {
      long x;
    } nested;
  } UnnamedClass;

  typedef struct {
    struct Nested {
      long x;
    } nested;
  } UnnamedStruct;

  typedef union {
    struct Nested {
      long x;
    } nested;
  } UnnamedUnion;
};

Scope::UnnamedClass unnamed_class;
Scope::UnnamedStruct unnamed_struct;
Scope::UnnamedUnion unnamed_union;
