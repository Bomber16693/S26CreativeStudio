#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
file = root / "app/src/main/cpp/sdcpp/CMakeLists.txt"
text = file.read_text()
assert "set(SD_LIB stable-diffusion)" in text, "Unexpected stable-diffusion.cpp version"
patch = """
# S26 Creative Studio JNI bridge for CPU-only on-device images
if(ANDROID)
  add_library(s26sd SHARED ../s26_sd_android.cpp)
  target_include_directories(s26sd PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
  target_link_libraries(s26sd PRIVATE stable-diffusion log android)
endif()
"""
if "add_library(s26sd SHARED" not in text:
    file.write_text(text + patch)
