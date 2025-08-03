namespace Scope {
typedef class {
  int x;
} AnonClass;

typedef struct {
  int x;
} AnonStruct;

typedef union {
  int x;
} AnonUnion;

typedef enum {
  X = 1
} AnonEnum;
};

Scope::AnonClass anon_class;
Scope::AnonStruct anon_struct;
Scope::AnonUnion anon_union;
Scope::AnonEnum anon_enum;
