# Volcano

A barely working example of running a compute shader using vulkan from clojure.

This example mostly follows the c++ example from https://github.com/mcleary/VulkanHpp-Compute-Sample.

## Usage

The code has only been tested on an M1 mac, although it should (in theory) be portable to other operating systems and architectures where vulkan is available with minor changes. Both the API and shared library are derived from the system property `volcano.vulkan.sdk.path` (see `deps.edn`). Some minor changes to the shared library and header path will be required for other OS's, but hopefully everything else should work as-is (fingers crossed).

The vulkan SDK can be downloaded from https://vulkan.lunarg.com/sdk/home.

Additionally, libclang must be available on the native library path in order to parse the vulkan headers and generate the vulkan API foreign interface.

Before running, the compute shader, `Square.hlsl` must be compiled to `Square.hsv` (see `compile_shaders.sh`).

The code is mostly boilerplate. The main idea of the program is to setup a buffer of 10 int32's, square them using the computer shader `Square.hlsl`, and then retrieve the result.


## License

Copyright © 2022 Adrian Smith

Distributed under the Eclipse Public License version 1.0.
