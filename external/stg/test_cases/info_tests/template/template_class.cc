template<typename T, int size>
struct wrapper {
  T member[size];
};
wrapper<int, 17> variable;
