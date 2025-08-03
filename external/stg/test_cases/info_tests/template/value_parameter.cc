template<auto V> struct S {};

S<15> v_int;
S<'p'> v_char;
// Not supported by Clang yet.
//S<4.2> v_double;

extern const int a;
S<&a> v_int_pointer;

const int b = 4;
const int& c = b;
S<c> v_int_reference;

extern void d();
S<&d> v_function_pointer;

void e() {}
void (&f)() = e;
S<f> v_function_reference;

struct H {
  int i1;
  int i2;
  void j1();
  void j2();
};
S<&H::i2> v_pointer_to_member;
S<&H::j2> v_pointer_to_method;

enum class K { l };
S<K::l> v_enumerator;

S<nullptr> v_nullptr;

// C++20 only.
//int m[2] = { 10, 4 };
//S<&(m[1])> v_pointer_to_subobject;

// C++20 only and cannot appear in ABI anyway.
//S<[](){}> v_lambda;
