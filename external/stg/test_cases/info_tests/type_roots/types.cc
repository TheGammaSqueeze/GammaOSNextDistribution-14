enum E1 {};
enum class E2;
struct S1 {};
struct S2;
typedef unsigned char T1;
typedef struct {} T2;
union U1 {};
union U2;

void func(E1, E2*, S1, S2*, T1, T2, U1, U2*) {}
