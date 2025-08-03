struct StaticOffset {
  static int st;
  static void print() {}
};

int StaticOffset::st;

int main() {
  StaticOffset::print();
  return 0;
}
