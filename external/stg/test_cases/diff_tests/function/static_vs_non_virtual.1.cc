struct StaticToNormal {
  int st;
  int print();
  int m;
};
int StaticToNormal::print() { return st; }

struct NormalToStatic {
  static int st;
  static int print();
  int m;
};
int NormalToStatic::print() { return st; }

StaticToNormal static_to_normal;
NormalToStatic normal_to_static;

int NormalToStatic::st;
