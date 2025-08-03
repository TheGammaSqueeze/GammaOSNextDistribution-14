namespace Scope {
typedef class {
  long x;
} AnonClass;

typedef struct {
  long x;
} AnonStruct;

typedef union {
  long x;
} AnonUnion;

typedef enum {
  X = 2
} AnonEnum;
};

Scope::AnonClass anon_class;
Scope::AnonStruct anon_struct;
Scope::AnonUnion anon_union;
Scope::AnonEnum anon_enum;
