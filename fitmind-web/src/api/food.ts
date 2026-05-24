import request from '../utils/request'

export interface FoodItem {
  id: string
  name: string
  nameEn: string
  calories: number
  protein: number
  carbs: number
  fat: number
  fiber: number
  servingSize: string
  confidence: number
  imageUrl?: string
}

export interface FoodRecognitionResult {
  success: boolean
  message: string
  foods: FoodItem[]
  totalCalories: number
  totalProtein: number
  totalCarbs: number
  totalFat: number
}

export interface FoodSearchResult {
  success: boolean
  foods: FoodItem[]
}

export interface FoodRecord {
  id: number
  userId: number
  foodId: string
  foodName: string
  calories: number
  protein: number
  carbs: number
  fat: number
  servingSize: string
  createTime: string
}

export const recognizeFood = (imageBase64: string) => {
  return request({
    url: '/food/recognize',
    method: 'post',
    timeout: 90000,
    data: { image: imageBase64 }
  })
}

export const searchFood = (keyword: string) => {
  return request({
    url: '/food/search',
    method: 'get',
    params: { keyword }
  })
}

export const getFoodDetail = (id: string) => {
  return request({
    url: `/food/${id}`,
    method: 'get'
  })
}

export const getCommonFoods = () => {
  return request({
    url: '/food/common',
    method: 'get'
  })
}

export const addFoodRecord = (data: { foodId: string; foodName: string; calories: number; protein: number; carbs: number; fat: number; servingSize: string }) => {
  return request({
    url: '/food/record',
    method: 'post',
    data
  })
}

export const getFoodRecords = () => {
  return request({
    url: '/food/records',
    method: 'get'
  })
}
