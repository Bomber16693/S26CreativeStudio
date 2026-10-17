#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
cmake=root/"app/src/main/cpp/yue2/CMakeLists.txt"
data=cmake.read_text()
assert "link_ggml_backends" in data and "yue-server" in data, "Unexpected upstream yue2.cpp CMake"
patch="""\n# Android native wrapper added by S26 Creative Studio
if(ANDROID)
    add_library(yue2mobile SHARED
        ../yue2_android.cpp
        src/request.cpp src/model-store.cpp
        ${WEBUI_OUTPUT}
    )
    target_include_directories(yue2mobile PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
    target_link_libraries(yue2mobile PRIVATE yyjson httplib log android)
    link_ggml_backends(yue2mobile)
endif()
"""
if "# Android native wrapper added by S26 Creative Studio" not in data:
    cmake.write_text(data+patch)
