#!/bin/bash

set -e
set -x

VK_SDK_PATH=/Users/adrian/VulkanSDK/1.3.250.0/

${VK_SDK_PATH}/macOS/bin/dxc -T cs_6_0 -E "Main" -spirv -fvk-use-dx-layout -fspv-target-env=vulkan1.0 -Fo "Square.spv" "Square.hlsl"
