(ns com.phronemophobic.volcano
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.rpl.specter :as specter]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.phronemophobic.clong.clang :as clong]
            [com.phronemophobic.clong.gen.jna :as gen])
  (:import
   java.io.PushbackReader
   com.sun.jna.Memory
      com.sun.jna.Native
   com.sun.jna.Pointer
   com.sun.jna.ptr.ByteByReference
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.ptr.LongByReference
   com.sun.jna.ptr.IntByReference
   com.sun.jna.ptr.FloatByReference
   com.sun.jna.CallbackReference
   com.sun.jna.Structure)
  (:gen-class))


(def vulkan-sdk-path (System/getProperty "volcano.vulkan.sdk.path"))

(def ^:no-doc libvulkan
  (com.sun.jna.NativeLibrary/getInstance
   (str vulkan-sdk-path "/macOS/Frameworks/vulkan.framework/vulkan")))

(def api
  (clong/easy-api (str vulkan-sdk-path "/macOS/include/vulkan/vulkan.h")))

(gen/def-api libvulkan api)
(gen/import-structs! api)


;;     VkApplicationInfo appInfo{};
;;     appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
;;     appInfo.pApplicationName = "Hello Triangle";
;;     appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
;;     appInfo.pEngineName = "No Engine";
;;     appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
;;     appInfo.apiVersion = VK_API_VERSION_1_0;
;; }

(defn write-string [struct field s]
  (let [bytes (.getBytes s "utf-8")
        mem (doto (Memory. (inc (alength bytes)))
                   (.write 0 bytes 0 (alength bytes))
                   (.setByte (alength bytes) 0))
        bbr (doto (ByteByReference.)
               (.setPointer mem))]
    (doto struct
      (.writeField field bbr))))

(defn write-string-array [struct field strs]
  (let [arr (Memory. (* Native/POINTER_SIZE
                        (count strs)))]
    (doseq [[i s] (map-indexed vector strs)]
      (let [offset (* i Native/POINTER_SIZE)
            str-bytes (.getBytes s "utf-8")
            mem (doto (Memory. (inc (alength str-bytes)))
                  (.write 0 str-bytes 0 (alength str-bytes))
                  (.setByte (alength str-bytes) 0))]
        (.setPointer arr offset mem)))
    (.writeField struct field arr)))

;; #define VK_MAKE_API_VERSION(variant, major, minor, patch) \
;;     ((((uint32_t)(variant)) << 29U) | (((uint32_t)(major)) << 22U) | (((uint32_t)(minor)) << 12U) | ((uint32_t)(patch)))
(defn vk-make-version
  ([major minor patch]
   (vk-make-version 0 major minor patch))
  ([variant major minor patch]
   (int
    (bit-or
     (bit-shift-left variant 29)
     (bit-shift-left major 22)
     (bit-shift-left minor 12)
     patch))))

(def VK_API_VERSION_1_0 (vk-make-version 0 1 0 0))

(def app-info (VkApplicationInfoByReference.))
(.writeField app-info "sType" VK_STRUCTURE_TYPE_APPLICATION_INFO)
(write-string app-info "pApplicationName" "Hello Triangle")
(.writeField app-info "applicationVersion" (vk-make-version 1 0 0))
(write-string app-info "pEngineName" "No Engine")
(.writeField app-info "engineVersion" (vk-make-version 1, 0, 0))
(.writeField app-info "apiVersion" VK_API_VERSION_1_0)

;; VkInstanceCreateInfo createInfo{};
;; createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
;; createInfo.pApplicationInfo = &appInfo;
(def create-info (VkInstanceCreateInfoByReference.))
(.writeField create-info "sType" VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
(.writeField create-info "pApplicationInfo" app-info)

;; createInfo.enabledExtensionCount = glfwExtensionCount;
;; createInfo.ppEnabledExtensionNames = glfwExtensions;
;; createInfo.enabledLayerCount = 0;
;; (.writeField create-info "enabledExtensionCount" (int 0))
;; (.writeField create-info "ppEnabledExtensionNames" nil)
;; (.writeField create-info "enabledLayerCount" (int 0))



;; std::vector<const char*> requiredExtensions;
;; for(uint32_t i = 0; i < glfwExtensionCount; i++) {
;;     requiredExtensions.emplace_back(glfwExtensions[i]);
;; }
;; #define VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME "VK_KHR_portability_enumeration"
(def VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME "VK_KHR_portability_enumeration")
;; requiredExtensions.emplace_back(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
(def extensions [VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME
                 "VK_KHR_get_physical_device_properties2"
                 "VK_EXT_debug_utils"


                 ])
(write-string-array create-info "ppEnabledExtensionNames" extensions)
(.writeField create-info "enabledExtensionCount" (int (count extensions)))

;; createInfo.flags |= VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
(.writeField create-info "flags"
             VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)

(def validation-layers ["VK_LAYER_KHRONOS_validation"])
(.writeField create-info "enabledLayerCount" (int (count validation-layers)))

(write-string-array create-info "ppEnabledLayerNames"
                    validation-layers)




;; if (vkCreateInstance(&createInfo, nullptr, &instance) != VK_SUCCESS) {
;;     throw std::runtime_error("failed to create instance!");
;; }
(def instance* (PointerByReference.))
(def result
  (doto (vkCreateInstance create-info nil instance*)
    prn))


(def instance (.getValue instance*))


(def ^:private main-class-loader @clojure.lang.Compiler/LOADER)
(deftype DebugCallback [f]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class [Integer/TYPE
                       Integer/TYPE
                       Pointer
                       Pointer]))
  (getReturnType [_]
    Integer/TYPE)
  (callback ^Integer [_ args]
    (.setContextClassLoader (Thread/currentThread) main-class-loader)

    (int 0)))

(defn debug-callback* [severity message-type info user-data]
  (prn "got callback!" severity message-type info user-data))
(def debug-callback
  (CallbackReference/getFunctionPointer
   (DebugCallback.
    #'debug-callback*)))

(def debug-utils-messenger-create-info
  (doto (VkDebugUtilsMessengerCreateInfoEXTByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
    (.writeField "messageSeverity"
                 (int (bit-or
                       VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                       VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                       VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)))
    (.writeField "messageType"
                 (int (bit-or
                       VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                       VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                       VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)))
    (.writeField "pfnUserCallback"
                 debug-callback)))

(def messenger* (PointerByReference.))

;; (vkCreateDebugUtilsMessengerEXT instance debug-utils-messenger-create-info nil messenger* )


(def physical-device* (PointerByReference.))
(def device-count* (IntByReference.))

;; vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
(vkEnumeratePhysicalDevices instance device-count* nil)

(vkEnumeratePhysicalDevices instance device-count* physical-device*)
(def physical-device (.getValue physical-device*))

(def device-properties (VkPhysicalDevicePropertiesByReference.))
(vkGetPhysicalDeviceProperties physical-device device-properties)


;; based on
;; https://github.com/mcleary/VulkanHpp-Compute-Sample

;; std::cout << "Hello Vulkan Compute" << std::endl;
;; 		vk::ApplicationInfo AppInfo{
;; 			"VulkanCompute",	// Application Name
;; 			1,					// Application Version
;; 			nullptr,			// Engine Name or nullptr
;; 			0,					// Engine Version
;; 			VK_API_VERSION_1_1  // Vulkan API version
;; 		};

;; 		const std::vector<const char*> Layers = { "VK_LAYER_KHRONOS_validation" };
;; 		vk::InstanceCreateInfo InstanceCreateInfo(vk::InstanceCreateFlags(),	// Flags
;; 												  &AppInfo,						// Application Info
;; 												  Layers.size(),				// Layers count
;; 												  Layers.data());				// Layers
;; 		vk::Instance Instance = vk::createInstance(InstanceCreateInfo);

;; vk::PhysicalDevice PhysicalDevice = Instance.enumeratePhysicalDevices().front();
;; 		vk::PhysicalDeviceProperties DeviceProps = PhysicalDevice.getProperties();
;; 		std::cout << "Device Name    : " << DeviceProps.deviceName << std::endl;
;; 		const uint32_t ApiVersion = DeviceProps.apiVersion;
;; 		std::cout << "Vulkan Version : " << VK_VERSION_MAJOR(ApiVersion) << "." << VK_VERSION_MINOR(ApiVersion) << "." << VK_VERSION_PATCH(ApiVersion) << std::endl;
;; 		vk::PhysicalDeviceLimits DeviceLimits = DeviceProps.limits;
;; 		std::cout << "Max Compute Shared Memory Size: " << DeviceLimits.maxComputeSharedMemorySize / 1024 << " KB" << std::endl;


(def queue-count* (IntByReference.))
(vkGetPhysicalDeviceQueueFamilyProperties physical-device queue-count* nil)

(def qfp-size (.size (VkQueueFamilyProperties.)))
(def queue-arr (Memory. (* (.size (VkQueueFamilyProperties.))
                           (.getValue queue-count*))))
(vkGetPhysicalDeviceQueueFamilyProperties physical-device queue-count* queue-arr)


(def queue-family-props
  (into []
        (comp (map (fn [i]
                     (.share queue-arr
                             (* i qfp-size)
                             qfp-size)))
              (map (fn [p]
                     (doto (Structure/newInstance VkQueueFamilyProperties p)
                       (.read)))))
        (range (.getValue queue-count*))))

(def VK_QUEUE_COMPUTE_BIT 0x00000002)

(def compute-queue-index
  (->> queue-family-props
       (map-indexed vector)
       (keep (fn [[i struct]]
               (when (not
                      (zero? (bit-and VK_QUEUE_COMPUTE_BIT
                                      (.readField struct
                                                  "queueFlags"))))
                 i)))
       first))




;; std::vector<vk::QueueFamilyProperties> QueueFamilyProps = PhysicalDevice.getQueueFamilyProperties();
;; 		auto PropIt = std::find_if(QueueFamilyProps.begin(), QueueFamilyProps.end(), [](const vk::QueueFamilyProperties& Prop)
;; 		{
;; 			return Prop.queueFlags & vk::QueueFlagBits::eCompute;
;; 		});

;; 		const uint32_t ComputeQueueFamilyIndex = std::distance(QueueFamilyProps.begin(), PropIt);
;; 		std::cout << "Compute Queue Family Index: " << ComputeQueueFamilyIndex << std::endl;




;; // Just to avoid a warning from the Vulkan Validation Layer
;; const float QueuePriority = 1.0f;
;; vk::DeviceQueueCreateInfo DeviceQueueCreateInfo(vk::DeviceQueueCreateFlags(),	// Flags
;; 									  ComputeQueueFamilyIndex,		// Queue Family Index
;; 									  1,								// Number of Queues
;; 									  &QueuePriority);

(def queue-priority (float 1))
(def device-queue-create-info (VkDeviceQueueCreateInfoByReference.))
(.writeField device-queue-create-info "sType" VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
(.writeField device-queue-create-info "queueFamilyIndex" (int compute-queue-index))
(.writeField device-queue-create-info "queueCount" (int 1))
(.writeField device-queue-create-info "pQueuePriorities"
             (FloatByReference. 1.0))



;; vk::DeviceCreateInfo DeviceCreateInfo(vk::DeviceCreateFlags(), // Flags
;; 							   DeviceQueueCreateInfo) ;  // Device Queue Create Info struct
;; 		vk::Device Device = PhysicalDevice.createDevice(DeviceCreateInfo);

(def device-create-info (VkDeviceCreateInfoByReference.))
(.writeField device-create-info "sType" VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
(.writeField device-create-info "pQueueCreateInfos" device-queue-create-info)
(.writeField device-create-info "queueCreateInfoCount" (int 1))

(def enabled-device-extensions ["VK_KHR_portability_subset"])
(write-string-array device-create-info
                    "ppEnabledExtensionNames"
                    enabled-device-extensions)
(.writeField device-create-info "enabledExtensionCount"
             (count enabled-device-extensions))

(def device* (PointerByReference.))
(assert (zero? (vkCreateDevice physical-device device-create-info nil device*)))
(def device (.getValue device*))



;; 		const uint32_t NumElements = 10;
;; 		const uint32_t BufferSize = NumElements * sizeof(int32_t);

;; 		vk::BufferCreateInfo BufferCreateInfo{
;; 			vk::BufferCreateFlags(),					// Flags
;; 			BufferSize,									// Size
;; 			vk::BufferUsageFlagBits::eStorageBuffer,	// Usage
;; 			vk::SharingMode::eExclusive,				// Sharing mode
;; 			1,											// Number of queue family indices
;; 			&ComputeQueueFamilyIndex					// List of queue family indices
;; 		};
;; 10 int32s
(def NUM-ELEMENTS 10)
(def BUFFER-SIZE (* NUM-ELEMENTS 4))
(def buffer-create-info (VkBufferCreateInfoByReference.))
(.writeField buffer-create-info "sType" VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)

(.writeField buffer-create-info "size" BUFFER-SIZE)
(.writeField buffer-create-info "usage"
             VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
(.writeField buffer-create-info "sharingMode" VK_SHARING_MODE_EXCLUSIVE)
(.writeField buffer-create-info "queueFamilyIndexCount" (int 1))
(.writeField buffer-create-info "pQueueFamilyIndices" (IntByReference. compute-queue-index))






;; 		vk::Buffer InBuffer = Device.createBuffer(BufferCreateInfo);
;; 		vk::Buffer OutBuffer = Device.createBuffer(BufferCreateInfo);

(def in-buffer* (PointerByReference.))
(assert (zero? (vkCreateBuffer device buffer-create-info nil in-buffer*)))
(def in-buffer (.getValue in-buffer*))
(def out-buffer* (PointerByReference.))
(assert (zero? (vkCreateBuffer device buffer-create-info nil out-buffer*)))
(def out-buffer (.getValue out-buffer*))



;; 		vk::MemoryRequirements InBufferMemoryRequirements = Device.getBufferMemoryRequirements(InBuffer);
;; 		vk::MemoryRequirements OutBufferMemoryRequirements = Device.getBufferMemoryRequirements(OutBuffer);

(def in-memory-requirements (VkMemoryRequirementsByReference.))
(vkGetBufferMemoryRequirements device in-buffer in-memory-requirements)
(def out-memory-requirements (VkMemoryRequirementsByReference.))
(vkGetBufferMemoryRequirements device out-buffer out-memory-requirements)


;; 		vk::PhysicalDeviceMemoryProperties MemoryProperties = PhysicalDevice.getMemoryProperties();
(def physical-device-memory-properties (VkPhysicalDeviceMemoryPropertiesByReference.))
(vkGetPhysicalDeviceMemoryProperties physical-device physical-device-memory-properties)

(defmacro with-retries [n & body]
  `(loop [i# 0]
     (if (< i# ~n)
       (let [result# (try
                       ~@body
                       (catch Exception e#
                         nil))]
         (if result#
           result#
           (recur (inc i#))))
       (do
         ~@body))))

(def memory-type-index
  (with-retries 3
    (let [memory-types (.readField physical-device-memory-properties "memoryTypes")
          memory-heaps (.readField physical-device-memory-properties "memoryHeaps")]
      (->> (range (.readField physical-device-memory-properties "memoryTypeCount"))
           (some (fn [i]
                   (let [memory-type (nth memory-types i)
                         flags (.readField memory-type "propertyFlags")]
                     (prn flags
                          (bit-and flags
                                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
                          (bit-and flags
                                   VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
                          )
                     (when (not (zero? (bit-and flags
                                                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT

                                                ;; doesn't seem to be available
                                                ;; VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                                                )))
                       (let [heap-index (.readField memory-type "heapIndex")
                             memory-heap (nth memory-heaps heap-index)
                             heap-size (.readField memory-heap "size")]
                         {:heap-size heap-size
                          :memory-type-index i})))
                   ))))))



;; 		uint32_t MemoryTypeIndex = uint32_t(~0);
;; 		vk::DeviceSize MemoryHeapSize = uint32_t(~0);
;; 		for (uint32_t CurrentMemoryTypeIndex = 0; CurrentMemoryTypeIndex < MemoryProperties.memoryTypeCount; ++CurrentMemoryTypeIndex)
;; 		{
;; 			vk::MemoryType MemoryType = MemoryProperties.memoryTypes[CurrentMemoryTypeIndex];
;; 			if ((vk::MemoryPropertyFlagBits::eHostVisible & MemoryType.propertyFlags) &&
;; 				(vk::MemoryPropertyFlagBits::eHostCoherent & MemoryType.propertyFlags))
;; 			{
;; 				MemoryHeapSize = MemoryProperties.memoryHeaps[MemoryType.heapIndex].size;
;; 				MemoryTypeIndex = CurrentMemoryTypeIndex;
;; 				break;
;; 			}
;; 		}

;; 		std::cout << "Memory Type Index: " << MemoryTypeIndex << std::endl;
;; 		std::cout << "Memory Heap Size : " << MemoryHeapSize / 1024 / 1024 / 1024 << " GB" << std::endl;
(println "heap-size" (/ (:heap-size memory-type-index)
                        1024
                        1024
                        1024)
         "gb")

;; 		vk::MemoryAllocateInfo InBufferMemoryAllocateInfo(InBufferMemoryRequirements.size, MemoryTypeIndex);
;; 		vk::MemoryAllocateInfo OutBufferMemoryAllocateInfo(OutBufferMemoryRequirements.size, MemoryTypeIndex);
;; 		vk::DeviceMemory InBufferMemory = Device.allocateMemory(InBufferMemoryAllocateInfo);
;; 		vk::DeviceMemory OutBufferMemory = Device.allocateMemory(InBufferMemoryAllocateInfo);
(def in-buffer-memory-allocate-info
  (VkMemoryAllocateInfoByReference.))
(.writeField in-buffer-memory-allocate-info "sType" VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
(.writeField in-buffer-memory-allocate-info "allocationSize" (.readField in-memory-requirements "size"))
(.writeField in-buffer-memory-allocate-info "memoryTypeIndex" (int (:memory-type-index memory-type-index)))

(def out-buffer-memory-allocate-info
  (VkMemoryAllocateInfoByReference.))
(.writeField out-buffer-memory-allocate-info "sType" VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
(.writeField out-buffer-memory-allocate-info "allocationSize" (.readField out-memory-requirements "size"))
(.writeField out-buffer-memory-allocate-info "memoryTypeIndex" (int (:memory-type-index memory-type-index)))

(def in-buffer-memory* (PointerByReference.))
(assert (zero? (vkAllocateMemory device in-buffer-memory-allocate-info nil in-buffer-memory*)))
(def in-buffer-memory (.getValue in-buffer-memory*))

(def out-buffer-memory* (PointerByReference.))
(assert (zero? (vkAllocateMemory device out-buffer-memory-allocate-info nil out-buffer-memory*)))
(def out-buffer-memory (.getValue out-buffer-memory*))


;; 		int32_t* InBufferPtr = static_cast<int32_t*>(Device.mapMemory(InBufferMemory, 0, BufferSize));
;; 		for (int32_t I = 0; I < NumElements; ++I)
;; 		{
;; 			InBufferPtr[I] = I;
;; 		}
;; 		Device.unmapMemory(InBufferMemory);

;; 		Device.bindBufferMemory(InBuffer, InBufferMemory, 0);
;; 		Device.bindBufferMemory(OutBuffer, OutBufferMemory, 0);

(def in-buf* (PointerByReference.))
(assert (zero? (vkMapMemory device in-buffer-memory 0 BUFFER-SIZE 0 in-buf*)))
(def in-buf (.getValue in-buf*))

(doseq [i (range NUM-ELEMENTS)]
  (.setInt in-buf (* i 4) i))


(vkUnmapMemory device in-buffer-memory)
(vkBindBufferMemory device in-buffer in-buffer-memory 0)
(vkBindBufferMemory device out-buffer out-buffer-memory 0)


;; std::vector<char> ShaderContents;
;; if (std::ifstream ShaderFile{ "Square.spv", std::ios::binary | std::ios::ate })
;; {
;; 	const size_t FileSize = ShaderFile.tellg();
;; 	ShaderFile.seekg(0);
;; 	ShaderContents.resize(FileSize, '\0');
;; 	ShaderFile.read(ShaderContents.data(), FileSize);
;; }

;; vk::ShaderModuleCreateInfo ShaderModuleCreateInfo(vk::ShaderModuleCreateFlags(),
;; 									     ShaderContents.size(), // Code size
;;                                                                              reinterpret_cast<const uint32_t*>(ShaderContents.data()));	// Code
(defn- slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy (clojure.java.io/input-stream x) out)
    (.toByteArray out)))

(def shader-module-create-info (VkShaderModuleCreateInfoByReference.))
(.writeField shader-module-create-info "sType" VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)

(def shader-file-bytes (slurp-bytes "Square.spv"))
(def shader-file-mem (Memory. (alength shader-file-bytes)))
(.write shader-file-mem 0 shader-file-bytes 0 (alength shader-file-bytes))

(def shader-pcode (IntByReference.))
(.setPointer shader-pcode shader-file-mem)
(.writeField shader-module-create-info "pCode" shader-pcode)
(.writeField shader-module-create-info "codeSize" (long (alength shader-file-bytes)))





;; 		vk::ShaderModule ShaderModule = Device.createShaderModule(ShaderModuleCreateInfo);
(def shader-module* (PointerByReference.))
(assert (zero? (vkCreateShaderModule device shader-module-create-info nil shader-module*)))
(def shader-module (.getValue shader-module*))



;; 		const std::vector<vk::DescriptorSetLayoutBinding> DescriptorSetLayoutBinding = {
;; 			{0, vk::DescriptorType::eStorageBuffer, 1, vk::ShaderStageFlagBits::eCompute},
;; 			{1, vk::DescriptorType::eStorageBuffer, 1, vk::ShaderStageFlagBits::eCompute}
;; 		};

(defn struct-array [class structs]
  (let [cnt (count structs)
        mem (Memory. (* (.size (Structure/newInstance class ))
                        cnt))]
    (loop [offset 0
           structs (seq structs)]
      (when structs
        (let [m (first structs)
              p (.share mem offset)
              struct (Structure/newInstance class p)]
          (doseq [[k v] m]
            (.writeField struct k v))

          (recur (+ offset (.size struct))
                 (next structs)))
        ))
    (doto (Structure/newInstance class mem)
      (.read))))

(def descriptor-set-layout-bindings
  (struct-array VkDescriptorSetLayoutBindingByReference
                [{"binding" (int 0)
                  "descriptorType" VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                  "descriptorCount" (int 1)
                  "stageFlags" VK_SHADER_STAGE_COMPUTE_BIT}
                 {"binding" (int 1)
                  "descriptorType" VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                  "descriptorCount" (int 1)
                  "stageFlags" VK_SHADER_STAGE_COMPUTE_BIT}]))


;; 		vk::DescriptorSetLayoutCreateInfo DescriptorSetLayoutCreateInfo(vk::DescriptorSetLayoutCreateFlags(),
;;                       DescriptorSetLayoutBinding);
;; 		vk::DescriptorSetLayout DescriptorSetLayout = Device.createDescriptorSetLayout(DescriptorSetLayoutCreateInfo);
(def descriptor-set-layout-create-info (VkDescriptorSetLayoutCreateInfoByReference.))
(.writeField descriptor-set-layout-create-info "sType" VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
(.writeField descriptor-set-layout-create-info "bindingCount" (int 2))
(.writeField descriptor-set-layout-create-info "pBindings" descriptor-set-layout-bindings)

(def set-layout* (PointerByReference.))
(assert (zero? (vkCreateDescriptorSetLayout device descriptor-set-layout-create-info nil set-layout*)))



;; 		vk::PipelineLayoutCreateInfo PipelineLayoutCreateInfo(vk::PipelineLayoutCreateFlags(), DescriptorSetLayout);
;; 		vk::PipelineLayout PipelineLayout = Device.createPipelineLayout(PipelineLayoutCreateInfo);
;; 		vk::PipelineCache PipelineCache = Device.createPipelineCache(vk::PipelineCacheCreateInfo());

(def pipeline-layout-create-info
  (doto (VkPipelineLayoutCreateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
    (.writeField "setLayoutCount" (int 1))
    (.writeField "pSetLayouts" set-layout*)))

(def pipeline-layout* (PointerByReference.))
(assert (zero? (vkCreatePipelineLayout device pipeline-layout-create-info nil pipeline-layout*)))
(def pipeline-layout (.getValue pipeline-layout*))
(def pipeline-cache* (PointerByReference.))
(assert (zero? (vkCreatePipelineCache device (doto (VkPipelineCacheCreateInfoByReference.)
                                               (.writeField "sType" VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO))
                                      nil
                                      pipeline-cache*)))
(def pipeline-cache (.getValue pipeline-cache*))

;; 		vk::PipelineShaderStageCreateInfo PipelineShaderCreateInfo(vk::PipelineShaderStageCreateFlags(),  // Flags
;; 																   vk::ShaderStageFlagBits::eCompute,     // Stage
;; 																   ShaderModule,					      // Shader Module
;; 																   "Main");								  // Shader Entry Point

(def pipeline-shader-stage-create-info
  (doto (VkPipelineShaderStageCreateInfo.)
    (.writeField "sType" VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
    (.writeField "stage" VK_SHADER_STAGE_COMPUTE_BIT)
    (.writeField "module" shader-module)
    (write-string "pName" "Main")))

;; 		vk::ComputePipelineCreateInfo ComputePipelineCreateInfo(vk::PipelineCreateFlags(),	// Flags
;; 																PipelineShaderCreateInfo,	// Shader Create Info struct
;; 																PipelineLayout);			// Pipeline Layout

(def compute-pipeline-create-info
  (doto (VkComputePipelineCreateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
    (.writeField "stage" pipeline-shader-stage-create-info)
    (.writeField "layout" pipeline-layout)
      ))

;; 		vk::Pipeline ComputePipeline = Device.createComputePipeline(PipelineCache, ComputePipelineCreateInfo);

(def pipeline* (PointerByReference.))
(assert (zero? (vkCreateComputePipelines device pipeline-cache 1 compute-pipeline-create-info nil pipeline*)))
(def compute-pipeline (.getValue pipeline*))

;; 		vk::DescriptorPoolSize DescriptorPoolSize(vk::DescriptorType::eStorageBuffer, 2);
;; 		vk::DescriptorPoolCreateInfo DescriptorPoolCreateInfo(vk::DescriptorPoolCreateFlags(), 1, DescriptorPoolSize);
;; 		vk::DescriptorPool DescriptorPool = Device.createDescriptorPool(DescriptorPoolCreateInfo);
(def descriptor-pool-create-info
  (doto (VkDescriptorPoolCreateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
    (.writeField "poolSizeCount" (int 2))
    
    (.writeField "maxSets" (int 1))
    (.writeField "pPoolSizes"
                 (struct-array VkDescriptorPoolSizeByReference
                               [{"type" VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                                 "descriptorCount" (int 2)}]))))
(def descriptor-pool* (PointerByReference.))
(assert (zero? (vkCreateDescriptorPool device descriptor-pool-create-info nil descriptor-pool*)))
(def descriptor-pool (.getValue descriptor-pool*))

;; 		vk::DescriptorSetAllocateInfo DescriptorSetAllocInfo(DescriptorPool, 1, &DescriptorSetLayout);
;; 		const std::vector<vk::DescriptorSet> DescriptorSets = Device.allocateDescriptorSets(DescriptorSetAllocInfo);
;; 		vk::DescriptorSet DescriptorSet = DescriptorSets.front();



(def descriptor-set-allocate-info
  (doto (VkDescriptorSetAllocateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
    (.writeField "descriptorPool" descriptor-pool)
    (.writeField "descriptorSetCount" (int 1))
    (.writeField "pSetLayouts" set-layout*)
    ))


(def descriptor-sets* (PointerByReference.))
(assert (zero? (vkAllocateDescriptorSets device descriptor-set-allocate-info descriptor-sets*)))
(def descriptor-set (.getValue descriptor-sets*))


;; 		vk::DescriptorBufferInfo InBufferInfo(InBuffer, 0, NumElements * sizeof(int32_t));
;; 		vk::DescriptorBufferInfo OutBufferInfo(OutBuffer, 0, NumElements * sizeof(int32_t));

;; 		const std::vector<vk::WriteDescriptorSet> WriteDescriptorSets = {
;; 			{DescriptorSet, 0, 0, 1, vk::DescriptorType::eStorageBuffer, nullptr, &InBufferInfo},
;; 			{DescriptorSet, 1, 0, 1, vk::DescriptorType::eStorageBuffer, nullptr, &OutBufferInfo},
;; 		};


(def descriptor-writes
  (struct-array VkWriteDescriptorSetByReference
                [{"sType" VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
                  "dstSet" descriptor-set
                  "dstBinding" (int 0)
                  "dstArrayElement" (int 0)
                  "descriptorCount" (int 1)
                  "descriptorType" VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                  "pBufferInfo" (doto (VkDescriptorBufferInfoByReference.)
                                  (.writeField "buffer" in-buffer)
                                  (.writeField "offset" 0)
                                  (.writeField "range" BUFFER-SIZE))}
                 {"sType" VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
                  "dstSet" descriptor-set
                  "dstBinding" (int 1)
                  "dstArrayElement" (int 0)
                  "descriptorCount" (int 1)
                  "descriptorType" VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                  "pBufferInfo" (doto (VkDescriptorBufferInfoByReference.)
                                  (.writeField "buffer" out-buffer)
                                  (.writeField "offset" 0)
                                  (.writeField "range" BUFFER-SIZE))}
                 ]))


;; 		Device.updateDescriptorSets(WriteDescriptorSets, {});
(vkUpdateDescriptorSets device 2 descriptor-writes 0 nil)


;; 		vk::CommandPoolCreateInfo CommandPoolCreateInfo(vk::CommandPoolCreateFlags(), ComputeQueueFamilyIndex);
(def command-pool-create-info
  (doto (VkCommandPoolCreateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
    (.writeField "queueFamilyIndex" (int compute-queue-index))))

;; 		vk::CommandPool CommandPool = Device.createCommandPool(CommandPoolCreateInfo);
(def command-pool* (PointerByReference.))
(assert (zero? (vkCreateCommandPool device command-pool-create-info nil command-pool*)))
(def command-pool (.getValue command-pool*))
;; 		vk::CommandBufferAllocateInfo CommandBufferAllocInfo(CommandPool,						// Command Pool
;; 															 vk::CommandBufferLevel::ePrimary,	// Level
;; 															 1);							    // Num Command Buffers

(def command-buffer-allocate-info
  (doto (VkCommandBufferAllocateInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
    (.writeField "level" VK_COMMAND_BUFFER_LEVEL_PRIMARY)
    (.writeField "commandPool" command-pool)
    (.writeField "commandBufferCount" (int 1))
    ))
;; 		const std::vector<vk::CommandBuffer> CmdBuffers = Device.allocateCommandBuffers(CommandBufferAllocInfo);
(def command-buffers (PointerByReference.) )
(assert (zero? (vkAllocateCommandBuffers device command-buffer-allocate-info command-buffers)))


;; 		vk::CommandBuffer CmdBuffer = CmdBuffers.front();

;; 		vk::CommandBufferBeginInfo CmdBufferBeginInfo(vk::CommandBufferUsageFlagBits::eOneTimeSubmit);
(def command-buffer (.getValue command-buffers))
(def command-buffer-begin-info
  (doto (VkCommandBufferBeginInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
    (.writeField "flags" VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)))



;; 		CmdBuffer.begin(CmdBufferBeginInfo);
;; 		CmdBuffer.bindPipeline(vk::PipelineBindPoint::eCompute, ComputePipeline);

(assert (zero? (vkBeginCommandBuffer command-buffer command-buffer-begin-info)))
(vkCmdBindPipeline command-buffer VK_PIPELINE_BIND_POINT_COMPUTE compute-pipeline)



;; 		CmdBuffer.bindDescriptorSets(vk::PipelineBindPoint::eCompute,	// Bind point
;; 									 PipelineLayout,				    // Pipeline Layout
;; 									 0,								    // First descriptor set
;; 									 { DescriptorSet },					// List of descriptor sets
;; 									 {});								// Dynamic offsets
(vkCmdBindDescriptorSets command-buffer VK_PIPELINE_BIND_POINT_COMPUTE
                         pipeline-layout
                         0
                         1
                         descriptor-sets*
                         0
                         nil
                         )
;; 		CmdBuffer.dispatch(NumElements, 1, 1);
(vkCmdDispatch command-buffer NUM-ELEMENTS 1 1)
;; 		CmdBuffer.end();
(assert (zero? (vkEndCommandBuffer command-buffer)))

;; 		vk::Queue Queue = Device.getQueue(ComputeQueueFamilyIndex, 0);
;; 		vk::Fence Fence = Device.createFence(vk::FenceCreateInfo());
(def queue* (PointerByReference.))
(vkGetDeviceQueue device compute-queue-index 0 queue*)
(def queue (.getValue queue*))

(def fence* (PointerByReference.))
(assert (zero? (vkCreateFence device
                              (doto (VkFenceCreateInfoByReference.)
                                (.writeField "sType" VK_STRUCTURE_TYPE_FENCE_CREATE_INFO))
                              nil
                              fence*)))
(def fence (.getValue fence*))


;; 		vk::SubmitInfo SubmitInfo(0,			// Num Wait Semaphores
;; 								  nullptr,		// Wait Semaphores
;; 								  nullptr,		// Pipeline Stage Flags
;; 								  1,			// Num Command Buffers
;; 								  &CmdBuffer);  // List of command buffers
;; 		Queue.submit({ SubmitInfo }, Fence);

(def submit-info
  (doto (VkSubmitInfoByReference.)
    (.writeField "sType" VK_STRUCTURE_TYPE_SUBMIT_INFO)
    (.writeField "commandBufferCount" (int 1))
    (.writeField "pCommandBuffers" command-buffers)))
(assert (zero? (vkQueueSubmit queue 1 submit-info fence )))


;; 		Device.waitForFences({ Fence },			// List of fences
;; 							 true,				// Wait All
;; 							 uint64_t(-1));		// Timeout
(assert (zero? (vkWaitForFences device 1 fence* 1 -1)))

;; 		InBufferPtr = static_cast<int32_t*>(Device.mapMemory(InBufferMemory, 0, BufferSize));
;; 		for (uint32_t I = 0; I < NumElements; ++I)
;; 		{
;; 			std::cout << InBufferPtr[I] << " ";
;; 		}
;; 		std::cout << std::endl;
;; 		Device.unmapMemory(InBufferMemory);
(def in-buf* (PointerByReference.))
(assert (zero? (vkMapMemory device in-buffer-memory 0 BUFFER-SIZE 0 in-buf*)))
(def in-buf (.getValue in-buf*))
(clojure.pprint/pprint
 (seq
  (.getIntArray in-buf 0 NUM-ELEMENTS)))
(vkUnmapMemory device in-buffer-memory)

;; 		int32_t* OutBufferPtr = static_cast<int32_t*>(Device.mapMemory(OutBufferMemory, 0, BufferSize));
;; 		for (uint32_t I = 0; I < NumElements; ++I)
;; 		{
;; 			std::cout << OutBufferPtr[I] << " ";
;; 		}
;; 		std::cout << std::endl;
;; 		Device.unmapMemory(OutBufferMemory);

(def out-buf* (PointerByReference.))
(assert (zero? (vkMapMemory device out-buffer-memory 0 BUFFER-SIZE 0 out-buf*)))
(def out-buf (.getValue out-buf*))
(clojure.pprint/pprint
 (seq
  (.getIntArray out-buf 0 NUM-ELEMENTS)))
(vkUnmapMemory device out-buffer-memory)

;; 		Device.freeMemory(InBufferMemory);
;; 		Device.freeMemory(OutBufferMemory);
;; 		Device.destroyBuffer(InBuffer);
;; 		Device.destroyBuffer(OutBuffer);

;; 		Device.resetCommandPool(CommandPool, vk::CommandPoolResetFlags());
;; 		Device.destroyFence(Fence);
;; 		Device.destroyDescriptorSetLayout(DescriptorSetLayout);
;; 		Device.destroyPipelineLayout(PipelineLayout);
;; 		Device.destroyPipelineCache(PipelineCache);
;; 		Device.destroyShaderModule(ShaderModule);
;; 		Device.destroyPipeline(ComputePipeline);
;; 		Device.destroyDescriptorPool(DescriptorPool);
;; 		Device.destroyCommandPool(CommandPool);
;; 		Device.destroy();
;; 		Instance.destroy();
;; }
(defn -main []
  (println "done!"))
