cmake_minimum_required(VERSION 3.10)
project(DittoSuite)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)

set(CMAKE_BUILD_TYPE Debug)

add_subdirectory(schema)

include_directories(include)
include_directories(${CMAKE_CURRENT_BINARY_DIR})

file(GLOB libditto_src
	src/*.cpp
)

add_library(libdittosuite STATIC ${libditto_src})

target_link_libraries(libdittosuite
	benchmark_proto
	${Protobuf_LIBRARIES}
	${Protobuf_PROTOC_LIBRARIES}
	pthread
)

add_executable(dittobench dittobench.cpp)
add_executable(dittotrace dittotrace.cpp)

target_link_libraries(dittobench
	libdittosuite
)

target_link_libraries(dittotrace
	libdittosuite
)

add_subdirectory(test)
