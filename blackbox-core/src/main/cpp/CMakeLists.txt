cmake_minimum_required(VERSION 3.22.1)
project("blackbox-core")

add_library(blackbox SHARED
    blackbox_core.cpp
)

target_include_directories(blackbox PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

find_library(log-lib log)
target_link_libraries(blackbox ${log-lib} dl)
