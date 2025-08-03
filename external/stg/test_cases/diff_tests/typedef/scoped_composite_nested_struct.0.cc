// TODO: abidw gives the two nested members the same id but they
// should be different.
struct Scope {
  typedef class {
    struct Nested {
      int x;
    } nested;
  } UnnamedClass;

  typedef struct {
    struct Nested {
      int x;
    } nested;
  } UnnamedStruct;

  typedef union {
    struct Nested {
      int x;
    } nested;
  } UnnamedUnion;
};

Scope::UnnamedClass unnamed_class;
Scope::UnnamedStruct unnamed_struct;
Scope::UnnamedUnion unnamed_union;
