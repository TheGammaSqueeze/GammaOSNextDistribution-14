int f01(int a, int b, int c) { return a + b + c + 1; }
int f02(int a, int b, int c) { return a + b + c + 2; }
int f03(int a, int b, int c) { return a + b + c + 3; }
int f04(int a, int b, int c) { return a + b + c + 4; }
int f05(int a, int b, int c) { return a + b + c + 5; }
int f06(int a, int b, int c) { return a + b + c + 6; }
int f07(int a, int b, int c) { return a + b + c + 7; }
int f08(int a, int b, int c) { return a + b + c + 8; }
int f09(int a, int b, int c) { return a + b + c + 9; }
int f10(int a, int b, int c) { return a + b + c + 10; }
int f11(int a, int b, int c) { return a + b + c + 11; }
int f12(int a, int b, int c) { return a + b + c + 12; }

struct S {
  int (*f01)(int a, int b, int c);
  int (*f02)(int a, int b, int c);
  int (*f03)(int a, int b, int c);
  int (*f04)(int a, int b, int c);
  int (*f05)(int a, int b, int c);
  int (*f06)(int a, int b, int c);
  int (*f07)(int a, int b, int c);
  int (*f08)(int a, int b, int c);
  int (*f09)(int a, int b, int c);
  int (*f10)(int a, int b, int c);
  int (*f11)(int a, int b, int c);
  int (*f12)(int a, int b, int c);
};

struct S s;
