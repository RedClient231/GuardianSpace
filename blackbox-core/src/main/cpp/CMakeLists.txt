cmake_minimum_required(VERSION 3.22.1)
project("blackbox-core")

add_library(blackbox SHARED
    src/main/cpp/blackbox_core.cpp
)

target_include_directories(blackbox PRIVATE
    src/main/cpp/include
)

find_library(log-lib log)
target_link_libraries(blackbox ${log-lib} dl)
