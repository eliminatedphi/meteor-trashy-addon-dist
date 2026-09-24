cmake_minimum_required(VERSION 3.14.0)
project(mapman CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_INCLUDE_CURRENT_DIR ON)

find_package(Qt6 REQUIRED COMPONENTS Widgets)

find_package(ZLIB REQUIRED)
find_package(SQLite3 REQUIRED)
include_directories(${ZLIB_INCLUDE_DIRS})
include_directories(${SQLite3_INCLUDE_DIRS})

INCLUDE (CheckTypeSize)

SET(CMAKE_EXTRA_INCLUDE_FILES "filesystem")
check_type_size("std::filesystem::path::value_type" PATH_VALSIZE LANGUAGE CXX)
SET(CMAKE_EXTRA_INCLUDE_FILES)

add_compile_definitions(PATH_VALSIZE=${PATH_VALSIZE})

set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

set(mapman_SOURCES
    src/main.cpp
    src/mapdump.cpp
    src/utils.cpp
    src/library.cpp
    src/painter.cpp
    src/sliceview.cpp
    src/groupview.cpp
    src/diffview.cpp
    src/comms.cpp
    src/mainwindow.cpp
)
add_executable(mapman ${mapman_SOURCES})

target_link_libraries(mapman
    ${ZLIB_LIBRARIES}
)

target_link_libraries(mapman Qt6::Widgets ${SQLite3_LIBRARIES})
