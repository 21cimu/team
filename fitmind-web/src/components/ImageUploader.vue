<script setup lang="ts">
import { ref } from 'vue';

const emit = defineEmits<{
  (e: 'upload', imageBase64: string): void;
}>();

const isDragging = ref(false);
const selectedFile = ref<File | null>(null);
const previewUrl = ref<string>('');
const isProcessing = ref(false);

const handleDragEnter = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = true;
};

const handleDragLeave = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = false;
};

const handleDragOver = (e: DragEvent) => {
  e.preventDefault();
};

const handleDrop = (e: DragEvent) => {
  e.preventDefault();
  isDragging.value = false;
  
  const files = e.dataTransfer?.files;
  if (files && files.length > 0) {
    processFile(files[0]);
  }
};

const handleFileSelect = (e: Event) => {
  const target = e.target as HTMLInputElement;
  const files = target.files;
  if (files && files.length > 0) {
    processFile(files[0]);
  }
};

const handleCameraClick = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    const video = document.createElement('video');
    video.srcObject = stream;
    video.play();
    
    setTimeout(() => {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(video, 0, 0);
        const imageBase64 = canvas.toDataURL('image/jpeg');
        previewUrl.value = imageBase64;
        emit('upload', imageBase64);
      }
      stream.getTracks().forEach(track => track.stop());
    }, 1000);
  } catch (error) {
    console.error('Camera access denied:', error);
  }
};

const processFile = (file: File) => {
  if (!file.type.startsWith('image/')) {
    alert('请上传图片文件');
    return;
  }

  selectedFile.value = file;
  isProcessing.value = true;

  const reader = new FileReader();
  reader.onload = (e) => {
    const result = e.target?.result as string;
    previewUrl.value = result;
    isProcessing.value = false;
    emit('upload', result);
  };
  reader.readAsDataURL(file);
};

const resetUpload = () => {
  selectedFile.value = null;
  previewUrl.value = '';
};
</script>

<template>
  <div class="relative">
    <div
      class="border-2 border-dashed rounded-xl p-8 text-center transition-all duration-300 cursor-pointer"
      :class="isDragging ? 'border-teal-500 bg-teal-500/10' : 'border-gray-600 hover:border-gray-500 bg-gray-800/50'"
      @dragenter="handleDragEnter"
      @dragleave="handleDragLeave"
      @dragover="handleDragOver"
      @drop="handleDrop"
      @click="$refs.fileInput.click()"
    >
      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        class="hidden"
        @change="handleFileSelect"
      />

      <div v-if="isProcessing" class="py-8">
        <div class="w-12 h-12 border-4 border-teal-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
        <p class="text-teal-400">正在处理图片...</p>
      </div>

      <div v-else-if="!previewUrl" class="space-y-4">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gray-700/50">
          <svg class="w-8 h-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
          </svg>
        </div>
        <div>
          <p class="text-gray-300 font-medium">点击上传图片或拖拽到此处</p>
          <p class="text-gray-500 text-sm mt-1">支持 JPG、PNG 格式</p>
        </div>
      </div>

      <div v-else class="space-y-4">
        <img :src="previewUrl" alt="预览" class="max-h-48 mx-auto rounded-lg object-contain" />
        <div class="flex gap-2 justify-center">
          <button
            @click.stop="resetUpload"
            class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm text-gray-300 transition-colors"
          >
            重新上传
          </button>
        </div>
      </div>
    </div>

    <div v-if="!previewUrl" class="mt-4 flex justify-center gap-4">
      <button
        @click.stop="handleCameraClick"
        class="flex items-center gap-2 px-4 py-2 bg-teal-600 hover:bg-teal-500 rounded-lg text-white text-sm transition-colors"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
        </svg>
        使用摄像头
      </button>
    </div>
  </div>
</template>