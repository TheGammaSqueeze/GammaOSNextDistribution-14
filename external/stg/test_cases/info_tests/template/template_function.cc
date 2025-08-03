template <typename T, int size>
void func(T&) {}
void trigger() {
  int b;
  func<int, 17>(b);
}
