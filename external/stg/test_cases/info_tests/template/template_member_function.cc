struct A {
  template <typename T, int size>
  void func(T&) {}
};
void trigger(A& abc) {
  int b;
  abc.func<int, 17>(b);
}
