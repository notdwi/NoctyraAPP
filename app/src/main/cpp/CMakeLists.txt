cmake_minimum_required(VERSION 3.22.1)
project(noctyra_discord LANGUAGES CXX)

set(SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../discord-sdk)

add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION ${SDK_DIR}/libs/${ANDROID_ABI}/libdiscord_partner_sdk.so)

add_library(noctyra_discord SHARED discord_bridge.cpp)
target_include_directories(noctyra_discord PRIVATE ${SDK_DIR}/include)
target_compile_features(noctyra_discord PRIVATE cxx_std_17)
target_compile_options(noctyra_discord PRIVATE -Os -fvisibility=hidden)
target_link_options(noctyra_discord PRIVATE -Wl,--gc-sections -s)
target_link_libraries(noctyra_discord discord_partner_sdk log)
