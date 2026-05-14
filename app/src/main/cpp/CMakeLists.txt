cmake_minimum_required(VERSION 3.22.1)
project("ggfix")

# Source files
add_library(ggfix SHARED
    hook/xhook.c
    hook/gg_bypass.c
    main.cpp
)

# Include directories
target_include_directories(ggfix PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/hook
)

# Find libraries
find_library(log-lib log)
find_library(dl-lib dl)

# Link libraries
target_link_libraries(ggfix
    ${log-lib}
    ${dl-lib}
)

# Compiler flags
target_compile_options(ggfix PRIVATE
    -Wall -Wextra -Werror
    -O2
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

# Strip in release
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")
