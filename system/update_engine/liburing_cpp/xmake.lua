add_requires("liburing", "gtest")

target("liburing_cpp")
  set_kind("static")
  add_files("src/*.cpp")
  add_packages("liburing")
  set_languages("c++17")
  add_includedirs("include", {public = true})
  add_cxflags("-g")


target("liburing_cpp_tests")
  set_kind("binary")
  add_files("tests/*.cpp")
  set_languages("c++17")
  add_deps("liburing_cpp")
  add_packages("gtest", "liburing")
  add_cxflags("-g")

