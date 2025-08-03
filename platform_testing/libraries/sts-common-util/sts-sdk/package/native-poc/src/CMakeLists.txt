cmake_minimum_required(VERSION 3.18.1)

project("nativepoc")

add_executable(
        nativepoc
        native-sample.cpp
)

find_library( # Sets the name of the path variable.
        log-lib
        log)
