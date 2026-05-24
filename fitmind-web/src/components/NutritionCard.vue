<script setup lang="ts">
import type { FoodItem } from '../api/food';

defineProps<{
  food: FoodItem;
}>();

const emit = defineEmits<{
  (e: 'add', food: FoodItem): void;
}>();

const getCalorieColor = (calories: number) => {
  if (calories < 100) return 'text-green-400';
  if (calories < 300) return 'text-yellow-400';
  return 'text-red-400';
};
</script>

<template>
  <div class="bg-gray-800/50 border border-gray-700 rounded-xl p-4 hover:border-teal-500/50 transition-all duration-300">
    <div class="flex items-start justify-between mb-3">
      <div class="flex-1">
        <h4 class="font-semibold text-white">{{ food.name }}</h4>
        <p class="text-xs text-gray-400 font-mono">{{ food.nameEn }}</p>
      </div>
      <span
        class="px-2 py-1 rounded-full text-xs font-medium"
        :class="getCalorieColor(food.calories)"
      >
        {{ food.calories }} 千卡
      </span>
    </div>

    <div class="grid grid-cols-4 gap-2 mb-3 text-center">
      <div class="bg-gray-700/50 rounded-lg p-2">
        <p class="text-xs text-gray-400">蛋白质</p>
        <p class="text-sm font-semibold text-teal-400">{{ food.protein }}g</p>
      </div>
      <div class="bg-gray-700/50 rounded-lg p-2">
        <p class="text-xs text-gray-400">碳水</p>
        <p class="text-sm font-semibold text-yellow-400">{{ food.carbs }}g</p>
      </div>
      <div class="bg-gray-700/50 rounded-lg p-2">
        <p class="text-xs text-gray-400">脂肪</p>
        <p class="text-sm font-semibold text-red-400">{{ food.fat }}g</p>
      </div>
      <div class="bg-gray-700/50 rounded-lg p-2">
        <p class="text-xs text-gray-400">纤维</p>
        <p class="text-sm font-semibold text-green-400">{{ food.fiber }}g</p>
      </div>
    </div>

    <div class="flex items-center justify-between">
      <span class="text-xs text-gray-500">{{ food.servingSize }}</span>
      <span class="text-xs text-teal-400">置信度: {{ (food.confidence * 100).toFixed(0) }}%</span>
    </div>

    <button
      @click="emit('add', food)"
      class="mt-3 w-full py-2 bg-teal-600 hover:bg-teal-500 rounded-lg text-white text-sm font-medium transition-colors"
    >
      添加到记录
    </button>
  </div>
</template>