struct M {
  int head;
  struct M * tail;
};

int loop(struct M * m) {
  return m && m == m->tail;
}
