template <typename... Args> constexpr auto j = sizeof...(Args);
auto x = j<int, bool, char>;
