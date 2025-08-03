struct Struct {
  long x;
};

union Union {
  long y;
};

class Class {
  long z;
};

enum Enum {
  ENUM_ZERO  = 0,
  ENUM_ONE   = 1,
};

enum class EnumClass {
  ZERO  = 0,
  ONE   = 1,
};

typedef void* Typedef;

Struct foo_struct;
Union foo_union;
Class foo_class;
Enum foo_enum;
EnumClass foo_enum_class;
Typedef foo_typedef;
