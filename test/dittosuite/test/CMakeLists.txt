enable_testing()

include(FetchContent)
FetchContent_Declare(
	GoogleTest
	URL https://github.com/google/googletest/archive/e2239ee6043f73722e7aa812a459f54a28552929.zip
)
FetchContent_MakeAvailable(GoogleTest)
include(GoogleTest)

include_directories(include)

function(add_gtest test_src)
	get_filename_component(test_name ${test_src} NAME_WE)

	add_executable(${test_name} ${test_src})
	target_link_libraries(${test_name}
		libdittosuite
		gtest
		gtest_main
		gmock
		gmock_main
	)

	gtest_discover_tests(${test_name})
endfunction()

file(GLOB ditto_test_srcs
	*.cpp
)
file(COPY ../example DESTINATION ${CMAKE_CURRENT_BINARY_DIR})

foreach(test_src IN ITEMS ${ditto_test_srcs})
	add_gtest(${test_src})
endforeach()
