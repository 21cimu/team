<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { recognizeFood, getCommonFoods, searchFood, addFoodRecord, type FoodItem, type FoodRecognitionResult } from '../api/food';
import { ElMessage } from 'element-plus';
import { readImageAsOptimizedDataUrl } from '../utils/imageUpload';

const status = ref<'idle' | 'analyzing' | 'success' | 'error'>('idle');
const image = ref<string | null>(null);
const recognitionResult = ref<FoodRecognitionResult | null>(null);
const commonFoods = ref<FoodItem[]>([]);
const allCommonFoods = ref<FoodItem[]>([]);
const searchKeyword = ref('');
const errorMessage = ref('');
const aiFeedback = ref('等待今日饮食打卡数据同步，AI 教练将为您生成针对性指导...');
const loadingProgress = ref(0);
const addingFoodId = ref<string | null>(null);
const searchingFoods = ref(false);

const basePlan = ref([
  { id: 1, name: '杠铃深蹲', sets: '4组 x 10次', type: '下肢力量', icon: '⌖' },
  { id: 2, name: '罗马尼亚硬拉', sets: '4组 x 12次', type: '下肢力量', icon: '⌖' },
  { id: 3, name: '坐姿腿屈伸', sets: '3组 x 15次', type: '孤立刺激', icon: '⌖' }
]);
const currentPlan = ref([...basePlan.value]);

const FOOD_NAME_MAP: Record<string, string> = {
  apple: '苹果',
  banana: '香蕉',
  orange: '橙子',
  grape: '葡萄',
  strawberry: '草莓',
  blueberry: '蓝莓',
  watermelon: '西瓜',
  pineapple: '菠萝',
  mango: '芒果',
  pear: '梨',
  peach: '桃子',
  kiwi: '猕猴桃',
  avocado: '牛油果',
  broccoli: '西兰花',
  spinach: '菠菜',
  lettuce: '生菜',
  tomato: '番茄',
  cucumber: '黄瓜',
  carrot: '胡萝卜',
  potato: '土豆',
  'sweet potato': '红薯',
  pumpkin: '南瓜',
  corn: '玉米',
  rice: '米饭',
  'brown rice': '糙米饭',
  'fried rice': '炒饭',
  noodles: '面条',
  bread: '面包',
  toast: '吐司',
  oatmeal: '燕麦',
  oats: '燕麦',
  cereal: '谷物麦片',
  yogurt: '酸奶',
  milk: '牛奶',
  cheese: '奶酪',
  egg: '鸡蛋',
  'boiled egg': '水煮蛋',
  'fried egg': '煎蛋',
  'chicken breast': '鸡胸肉',
  chicken: '鸡肉',
  beef: '牛肉',
  steak: '牛排',
  pork: '猪肉',
  salmon: '三文鱼',
  fish: '鱼肉',
  tuna: '金枪鱼',
  shrimp: '虾',
  tofu: '豆腐',
  salad: '沙拉',
  soup: '汤',
  pizza: '披萨',
  hamburger: '汉堡',
  burger: '汉堡',
  sandwich: '三明治',
  sushi: '寿司',
  dumplings: '饺子',
  baozi: '包子',
  congee: '粥',
  porridge: '粥',
  coffee: '咖啡',
  'green tea': '绿茶',
  tea: '茶',
  nuts: '坚果'
};
/*
const FOOD_NAME_MAP: Record<string, string> = {
  apple: '苹果',
  banana: '香蕉',
  orange: '橙子',
  grape: '葡萄',
  strawberry: '草莓',
  blueberry: '蓝莓',
  watermelon: '西瓜',
  pineapple: '菠萝',
  mango: '芒果',
  pear: '梨',
  peach: '桃子',
  kiwi: '猕猴桃',
  avocado: '牛油果',
  broccoli: '西兰花',
  spinach: '菠菜',
  lettuce: '生菜',
  tomato: '番茄',
  cucumber: '黄瓜',
  carrot: '胡萝卜',
  potato: '土豆',
  sweet potato: '红薯',
  pumpkin: '南瓜',
  corn: '玉米',
  rice: '米饭',
  brown rice: '糙米饭',
  fried rice: '炒饭',
  noodles: '面条',
  bread: '面包',
  toast: '吐司',
  oatmeal: '燕麦',
  oats: '燕麦',
  cereal: '谷物麦片',
  yogurt: '酸奶',
  milk: '牛奶',
  cheese: '奶酪',
  egg: '鸡蛋',
  boiled egg: '水煮蛋',
  fried egg: '煎蛋',
  chicken breast: '鸡胸肉',
  chicken: '鸡肉',
  beef: '牛肉',
  steak: '牛排',
  pork: '猪肉',
  salmon: '三文鱼',
  fish: '鱼肉',
  tuna: '金枪鱼',
  shrimp: '虾',
  tofu: '豆腐',
  salad: '沙拉',
  soup: '汤',
  pizza: '披萨',
  hamburger: '汉堡',
  burger: '汉堡',
  sandwich: '三明治',
  sushi: '寿司',
  dumplings: '饺子',
  baozi: '包子',
  congee: '粥',
  porridge: '粥',
  coffee: '咖啡',
  green tea: '绿茶',
  tea: '茶',
  nuts: '坚果'
};

*/
const containsChinese = (value?: string | null) => /[\u4e00-\u9fff]/.test(value || '');
const normalizeFoodKey = (value?: string | null) =>
  (value || '')
    .trim()
    .toLowerCase()
    .replace(/[()]/g, '')
    .replace(/\s+/g, ' ');

const getFoodDisplayName = (food: Pick<FoodItem, 'name' | 'nameEn'> | string) => {
  const primaryName = typeof food === 'string' ? food : food.name;
  const secondaryName = typeof food === 'string' ? '' : food.nameEn;

  if (containsChinese(primaryName)) return primaryName;
  if (containsChinese(secondaryName)) return secondaryName;

  const mappedName =
    FOOD_NAME_MAP[normalizeFoodKey(primaryName)] ||
    FOOD_NAME_MAP[normalizeFoodKey(secondaryName)];

  return mappedName || primaryName || secondaryName || '未知食物';
};

const getFoodEnglishName = (food: Pick<FoodItem, 'name' | 'nameEn'>) => {
  if (food.nameEn && !containsChinese(food.nameEn)) return food.nameEn;
  if (food.name && !containsChinese(food.name) && normalizeFoodKey(food.name) !== normalizeFoodKey(food.nameEn)) {
    return food.name;
  }
  return '';
};

const matchesFoodKeyword = (food: FoodItem, keyword: string) => {
  const normalizedKeyword = normalizeFoodKey(keyword);
  if (!normalizedKeyword) return true;

  const searchableFields = [
    food.name,
    food.nameEn,
    getFoodDisplayName(food),
    getFoodEnglishName(food)
  ];

  return searchableFields.some((field) => normalizeFoodKey(field).includes(normalizedKeyword));
};

const visibleFoods = computed(() => {
  const keyword = searchKeyword.value.trim();
  if (!keyword) return commonFoods.value;
  return commonFoods.value.filter((food) => matchesFoodKeyword(food, keyword));
});

const loadCommonFoods = async () => {
  const result: any = await getCommonFoods();
  const foods = Array.isArray(result?.foods) ? result.foods : [];
  allCommonFoods.value = foods;
  commonFoods.value = foods;
};

const handleKeywordInput = () => {
  const keyword = searchKeyword.value.trim();
  if (!keyword) {
    commonFoods.value = allCommonFoods.value;
  }
};

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
const fetchWithRetry = async <T>(fn: () => Promise<T>, retries = 5): Promise<T> => {
  const delays = [1000, 2000, 4000, 8000, 16000];
  for (let i = 0; i < retries; i++) {
    try {
      return await fn();
    } catch (error) {
      if (i === retries - 1) throw error;
      await sleep(delays[i]);
    }
  }
  throw new Error('Max retries exceeded');
};

const getCalorieClass = (calories: number) => {
  if (calories < 100) return 'low';
  if (calories < 300) return 'medium';
  return 'high';
};

onMounted(async () => {
  try {
    await loadCommonFoods();
  } catch (error) {
    console.error('Failed to load common foods:', error);
  }
});

const handleImageUpload = async (imageBase64: string) => {
  status.value = 'analyzing';
  errorMessage.value = '';
  recognitionResult.value = null;
  currentPlan.value = [...basePlan.value];
  aiFeedback.value = 'AI 视觉引擎正在解析食物成分与热量...';
  loadingProgress.value = 0;

  const progressInterval = setInterval(() => {
    loadingProgress.value += Math.random() * 15;
    if (loadingProgress.value >= 90) loadingProgress.value = 90;
  }, 300);

  try {
    const result: any = await fetchWithRetry(() => recognizeFood(imageBase64));
    loadingProgress.value = 100;
    clearInterval(progressInterval);

    recognitionResult.value = result;
    image.value = imageBase64;
    status.value = 'success';
    adjustTrainingPlan(result);
  } catch (error) {
    clearInterval(progressInterval);
    console.error('Food recognition failed:', error);
    status.value = 'error';
    errorMessage.value = '识别失败，请重试或上传其他图片';
    aiFeedback.value = '获取打卡数据失败，维持原计划。';
  }
};

const adjustTrainingPlan = (data: any) => {
  let newPlan = [...basePlan.value];
  let feedback = `分析完成：`;

  if (data.totalCalories > 800 || data.totalCarbs > 80) {
    feedback += `检测到本餐热量（${data.totalCalories} 千卡）与碳水偏高。为防止脂肪囤积，我已在今晚训练计划尾部动态增加「高强度间歇有氧(HIIT)」，请务必完成。`;
    newPlan.push({
      id: 4,
      name: '动感单车 HIIT',
      sets: '15分钟 (冲刺20s/慢骑40s)',
      type: '燃脂急救 (动态新增)',
      icon: '⌖',
      isNew: true
    });
  } else if (data.totalProtein > 35 && data.totalCalories > 400) {
    feedback += `本餐蛋白质充足（${data.totalProtein}g），能量储备极佳！是突破极限的好时机，我已将「杠铃深蹲」的容量上调至 5 组，去冲击大重量吧！`;
    newPlan[0] = { ...newPlan[0], sets: '5组 x 8次 (重量突破)', isModified: true };
  } else if (data.totalCalories < 300) {
    feedback += `本餐摄入偏低（仅 ${data.totalCalories} 千卡），存在训练中低血糖风险。我已将今日计划整体降阶，减少容量，请注意安全，训练后务必补充快碳！`;
    newPlan = newPlan.map(p => ({ ...p, sets: p.sets.replace('4组', '3组').replace('3组', '2组'), isModified: true }));
  } else {
    feedback += `本餐宏量营养素比例均衡，完美契合您的身体档案。训练计划无需调整，按部就班执行，保持状态！`;
  }

  aiFeedback.value = feedback;
  currentPlan.value = newPlan;
};

const handleReset = () => {
  status.value = 'idle';
  image.value = null;
  recognitionResult.value = null;
  currentPlan.value = [...basePlan.value];
  aiFeedback.value = '等待今日饮食打卡数据同步，AI 教练将为您生成针对性指导...';
};

const handleAddFood = async (food: FoodItem) => {
  addingFoodId.value = food.id;
  try {
    await addFoodRecord({
      foodId: food.id,
      foodName: food.name,
      calories: food.calories,
      protein: food.protein,
      carbs: food.carbs,
      fat: food.fat,
      servingSize: food.servingSize
    });
    ElMessage.success(`已添加 ${getFoodDisplayName(food)} 到饮食记录`);
  } catch (error) {
    console.error('Failed to add food record:', error);
    ElMessage.error('添加失败，请重试');
  } finally {
    addingFoodId.value = null;
  }
};

const handleSearch = async () => {
  const keyword = searchKeyword.value.trim();

  if (!keyword) {
    commonFoods.value = allCommonFoods.value;
    return;
  }

  searchingFoods.value = true;
  try {
    const result: any = await searchFood(keyword);
    const foods = Array.isArray(result?.foods) ? result.foods : [];

    if (foods.length > 0) {
      commonFoods.value = foods;
      return;
    }

    commonFoods.value = allCommonFoods.value.filter((food) => matchesFoodKeyword(food, keyword));
  } catch (error) {
    console.error('Failed to search foods:', error);
    commonFoods.value = allCommonFoods.value.filter((food) => matchesFoodKeyword(food, keyword));
    ElMessage.error('搜索失败，已切换为本地筛选结果');
  } finally {
    searchingFoods.value = false;
  }
};
</script>

<template>
  <div class="food-recognition-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">饮食识别</span>
          <strong class="topbar-page-title">拍照打卡</strong>
          <span class="topbar-page-meta">上传食物图片，AI 自动识别营养成分</span>
        </div>
        <div class="topbar-page-actions header-status">
          <span class="status-indicator"></span>
          <span class="text-caption">视觉引擎在线</span>
        </div>
      </div>
    </Teleport>

    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">[ 智能饮食 ]</div>
        <h1 class="text-display-md text-primary">饮食打卡</h1>
        <p class="text-secondary mt-xs">上传食物图片 · AI 智能识别营养成分</p>
      </div>
      <div class="header-status">
        <span class="status-indicator"></span>
        <span class="text-caption">视觉引擎在线</span>
      </div>
    </div>

    <div class="main-grid mb-3xl">
      <section class="analysis-col">
        <div class="nd-card analysis-card">
          <div class="card-top flex-between mb-lg">
            <div class="text-label">[ 视觉分析 ]</div>
            <button v-if="status === 'success'" @click="handleReset" class="nd-btn" style="font-size:12px;padding:6px 14px;">
              ↺ 重新上传
            </button>
          </div>

          <div class="upload-zone" @click="($refs.fileInput as HTMLInputElement)?.click()">
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              style="display:none;"
              @change="(e: Event) => {
                const target = e.target as HTMLInputElement;
                const files = target.files;
                if (files && files.length > 0) {
                  readImageAsOptimizedDataUrl(files[0])
                    .then(handleImageUpload)
                    .catch(() => {
                      ElMessage.error('图片处理失败，请重试');
                    });
                }
              }"
            />

            <template v-if="image">
              <div class="image-frame">
                <img :src="image" alt="上传的食物" class="preview-img" />
                <div v-if="status === 'analyzing'" class="scan-overlay">
                  <div class="scan-content">
                    <div class="scan-ring"></div>
                    <div class="scan-label">AI 深度扫描中</div>
                    <div class="scan-bar">
                      <div class="scan-bar-fill" :style="{ width: loadingProgress + '%' }"></div>
                    </div>
                    <div class="scan-pct">{{ Math.round(loadingProgress) }}%</div>
                  </div>
                  <div class="scan-line"></div>
                </div>
              </div>
            </template>

            <template v-else>
              <div class="upload-prompt">
                <div class="upload-icon-box">⇧</div>
                <div class="upload-title">点击上传餐食图片</div>
                <div class="upload-hint text-caption">支持 JPG / PNG 格式</div>
              </div>
            </template>
          </div>

          <div v-if="status === 'idle'" class="idle-hint">
            <div class="idle-icon">◈</div>
            <p class="text-caption">等待上传图片开始分析</p>
          </div>

          <div v-else-if="status === 'error'" class="error-block">
            <div class="error-icon">⚠</div>
            <p>{{ errorMessage }}</p>
          </div>

          <div v-else-if="status === 'success' && recognitionResult" class="result-block">
            <div class="food-tags-row mb-lg">
              <span class="text-caption text-primary" style="letter-spacing:2px;">识别内容</span>
              <div class="tag-list">
                <span v-for="(food, index) in recognitionResult.foods" :key="index" class="food-tag">
                  {{ getFoodDisplayName(food) }}
                </span>
              </div>
            </div>

            <div class="nutrition-row">
              <div class="nutri-box calories-box">
                <div class="nutri-emoji">🔥</div>
                <div class="nutri-val">{{ recognitionResult.totalCalories }}</div>
                <div class="nutri-unit">千卡</div>
                <div class="nutri-label">热量</div>
              </div>
              <div class="nutri-box protein-box">
                <div class="nutri-emoji">🥩</div>
                <div class="nutri-val">{{ recognitionResult.totalProtein }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">蛋白质</div>
              </div>
              <div class="nutri-box carbs-box">
                <div class="nutri-emoji">🍞</div>
                <div class="nutri-val">{{ recognitionResult.totalCarbs }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">碳水</div>
              </div>
              <div class="nutri-box fat-box">
                <div class="nutri-emoji">🧈</div>
                <div class="nutri-val">{{ recognitionResult.totalFat }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">脂肪</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="coach-col">
        <div class="nd-card coach-msg-card" style="box-shadow: 8px 8px 0px #22C55E;">
          <div class="coach-top mb-md">
            <div class="coach-avatar">◈</div>
            <div>
              <div class="text-subheading">FITMIND AI 教练</div>
              <div v-if="status === 'analyzing'" class="thinking-badge">
                <span class="blink-dot"></span> 思考中
              </div>
            </div>
          </div>
          <div class="coach-bubble">
            <p>{{ aiFeedback }}</p>
          </div>
        </div>

        <div class="nd-card training-card mt-xl">
          <div class="card-top flex-between mb-lg">
            <div class="text-label">[ 今日训练 ]</div>
            <span class="sync-tag">实时同步</span>
          </div>

          <div class="training-list">
            <div
              v-for="(item, index) in currentPlan"
              :key="item.id"
              class="training-row"
              :class="{ 'is-new': item.isNew, 'is-modified': item.isModified }"
            >
              <div class="training-idx">{{ index + 1 }}</div>
              <div class="training-body">
                <div class="training-name-line">
                  <span class="training-name">{{ item.name }}</span>
                  <span v-if="item.isNew" class="badge badge-new">AI 新增</span>
                  <span v-else-if="item.isModified" class="badge badge-mod">AI 调整</span>
                </div>
                <span class="text-caption">{{ item.type }}</span>
              </div>
              <div class="training-sets">{{ item.sets }}</div>
            </div>
          </div>

          <button v-if="status === 'success'" class="nd-btn primary w-full mt-lg" style="font-size:14px;">
            接受调整并开始训练 →
          </button>
        </div>
      </section>
    </div>

    <section class="common-foods-section">
      <div class="nd-card foods-card">
        <div class="card-top flex-between mb-lg">
          <div class="text-label">[ 常见食物 ]</div>
          <div class="search-row">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索食物..."
              class="nd-input"
              style="width:180px;padding:8px 12px;font-size:13px;"
              @keyup.enter="handleSearch"
              @input="handleKeywordInput"
            />
            <button @click="handleSearch" class="nd-btn" style="font-size:12px;padding:8px 14px;" :disabled="searchingFoods">
              {{ searchingFoods ? '搜索中...' : '搜索' }}
            </button>
          </div>
        </div>

        <div class="foods-grid">
          <div
            v-for="food in visibleFoods"
            :key="food.id"
            class="food-item"
          >
            <div class="food-item-top">
              <div>
                <div class="food-item-name">{{ getFoodDisplayName(food) }}</div>
                <div v-if="getFoodEnglishName(food)" class="food-item-en text-caption">{{ getFoodEnglishName(food) }}</div>
              </div>
              <span class="cal-tag" :class="getCalorieClass(food.calories)">
                {{ food.calories }} 千卡
              </span>
            </div>

            <div class="food-macros">
              <div class="macro-cell">
                <span class="macro-label">蛋白</span>
                <span class="macro-val">{{ food.protein }}g</span>
              </div>
              <div class="macro-cell">
                <span class="macro-label">碳水</span>
                <span class="macro-val">{{ food.carbs }}g</span>
              </div>
              <div class="macro-cell">
                <span class="macro-label">脂肪</span>
                <span class="macro-val">{{ food.fat }}g</span>
              </div>
              <div class="macro-cell">
                <span class="macro-label">纤维</span>
                <span class="macro-val">{{ food.fiber }}g</span>
              </div>
            </div>

            <div class="food-item-footer">
              <span class="text-caption">{{ food.servingSize }}</span>
            </div>

            <button @click="handleAddFood(food)" class="add-btn" :disabled="addingFoodId === food.id">
              {{ addingFoodId === food.id ? '添加中...' : '+ 添加到记录' }}
            </button>
          </div>
        </div>

        <div v-if="!visibleFoods.length" class="food-empty-state">
          <div class="text-label">[ 无匹配结果 ]</div>
          <p class="text-caption">没有找到相关食物，试试更短的关键词或中英文名称。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.food-recognition-page {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 2px solid var(--cta);
  background: rgba(34, 197, 94, 0.08);
}

.status-indicator {
  width: 8px;
  height: 8px;
  background: var(--cta);
  border-radius: 50%;
  animation: blink 2s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.2; }
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-xl);
}

@media (min-width: 1024px) {
  .main-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.analysis-col,
.coach-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.analysis-card {
  display: flex;
  flex-direction: column;
  padding: 28px 26px 24px;
}

.upload-zone {
  position: relative;
  aspect-ratio: 4 / 3;
  border: 3px dashed var(--border-visible);
  background: rgba(248, 250, 252, 0.02);
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}

.upload-zone:hover {
  border-color: var(--primary);
  background: rgba(194, 169, 120, 0.06);
}

.image-frame {
  width: 100%;
  height: 100%;
  position: relative;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-prompt {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
}

.upload-icon-box {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--primary);
  font-size: 28px;
  color: var(--primary);
  font-family: var(--font-heading);
  font-weight: 900;
  transition: transform 0.2s ease;
}

.upload-zone:hover .upload-icon-box {
  transform: scale(1.1);
}

.upload-title {
  font-family: var(--font-heading);
  font-size: var(--subheading);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--text-main);
}

.upload-hint {
  text-align: center;
}

.scan-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(250, 245, 237, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
}

.scan-content {
  text-align: center;
}

.scan-ring {
  width: 56px;
  height: 56px;
  border: 4px solid rgba(194, 169, 120, 0.22);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto var(--space-md);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.scan-label {
  font-family: var(--font-heading);
  font-size: var(--body);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 2px;
  color: var(--text-main);
  margin-bottom: var(--space-sm);
}

.scan-bar {
  width: 160px;
  height: 6px;
  background: rgba(255, 255, 255, 0.82);
  border: 2px solid var(--border-visible);
  margin: 0 auto var(--space-xs);
  border-radius: 999px;
}

.scan-bar-fill {
  height: 100%;
  background: var(--primary);
  transition: width 0.3s ease;
}

.scan-pct {
  font-family: var(--font-heading);
  font-size: var(--caption);
  color: var(--primary);
  font-weight: 700;
}

.scan-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--primary);
  box-shadow: 0 0 20px rgba(127, 157, 135, 0.34);
  animation: scanMove 2s linear infinite;
}

@keyframes scanMove {
  0% { top: 0%; }
  50% { top: 100%; }
  100% { top: 0%; }
}

.idle-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xl);
  border: 2px dashed var(--border-visible);
  margin-top: var(--space-lg);
}

.idle-icon {
  font-size: 32px;
  color: var(--text-secondary);
}

.error-block {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 1px solid rgba(201, 124, 122, 0.24);
  background: rgba(201, 124, 122, 0.08);
  margin-top: var(--space-lg);
  border-radius: 14px;
}

.error-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.error-block p {
  color: #b56f6c;
  margin: 0;
  font-size: var(--body-sm);
}

.result-block {
  margin-top: var(--space-lg);
}

.food-tags-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.food-tag {
  padding: 4px 12px;
  border: 2px solid var(--primary);
  font-family: var(--font-heading);
  font-size: var(--caption);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--primary);
}

.nutrition-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-md);
}

@media (min-width: 640px) {
  .nutrition-row {
    grid-template-columns: repeat(4, 1fr);
  }
}

.nutri-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-md);
  border: 2px solid var(--border-visible);
  background: var(--surface);
  transition: transform 0.2s ease;
}

.nutri-box:hover {
  transform: translate(-2px, -2px);
}

.nutri-emoji {
  font-size: 24px;
  margin-bottom: var(--space-xs);
}

.nutri-val {
  font-family: var(--font-heading);
  font-size: var(--display-md);
  font-weight: 900;
  line-height: 1;
  color: var(--text-main);
}

.nutri-unit {
  font-size: var(--caption);
  color: var(--text-secondary);
  margin-top: 2px;
}

.nutri-label {
  font-family: var(--font-heading);
  font-size: var(--label);
  text-transform: uppercase;
  letter-spacing: 2px;
  color: var(--text-secondary);
  margin-top: var(--space-xs);
}

.calories-box { border-color: var(--primary); }
.calories-box .nutri-val { color: var(--primary); }
.protein-box { border-color: #ef4444; }
.protein-box .nutri-val { color: #ef4444; }
.carbs-box { border-color: var(--warning); }
.carbs-box .nutri-val { color: var(--warning); }
.fat-box { border-color: var(--cta); }
.fat-box .nutri-val { color: var(--cta); }

.coach-msg-card {
  position: relative;
  padding: 24px 24px 22px;
}

.coach-top {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.coach-avatar {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--cta);
  font-size: 20px;
  color: var(--cta);
  font-weight: 900;
  background: rgba(34, 197, 94, 0.08);
}

.thinking-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--label);
  color: var(--cta);
  font-weight: 700;
  letter-spacing: 1px;
}

.blink-dot {
  width: 6px;
  height: 6px;
  background: var(--cta);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.3); }
}

.coach-bubble {
  border: 2px solid var(--border-visible);
  background: var(--surface);
  padding: var(--space-md);
}

.coach-bubble p {
  margin: 0;
  font-size: var(--body-sm);
  line-height: 1.7;
  color: var(--text-main);
}

.training-card {
  flex: 1;
  padding: 24px 24px 22px;
}

.sync-tag {
  padding: 4px 10px;
  border: 2px solid var(--cta);
  font-family: var(--font-heading);
  font-size: var(--label);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--cta);
}

.training-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.training-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 2px solid var(--border-visible);
  background: rgba(248, 250, 252, 0.02);
  transition: all 0.2s ease;
}

.training-row:hover {
  background: rgba(248, 250, 252, 0.05);
}

.training-row.is-new {
  border-color: var(--primary);
  background: rgba(249, 115, 22, 0.06);
}

.training-row.is-modified {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.06);
}

.training-idx {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--border-visible);
  font-family: var(--font-heading);
  font-size: var(--body-sm);
  font-weight: 900;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.training-body {
  flex: 1;
  min-width: 0;
}

.training-name-line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.training-name {
  font-family: var(--font-heading);
  font-size: var(--body);
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-main);
}

.badge {
  font-family: var(--font-heading);
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 1px;
  padding: 2px 8px;
  border: 2px solid;
}

.badge-new {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(249, 115, 22, 0.1);
}

.badge-mod {
  border-color: #3b82f6;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
}

.training-sets {
  font-family: var(--font-heading);
  font-size: var(--body-sm);
  font-weight: 700;
  color: var(--text-main);
  padding: 6px 12px;
  border: 2px solid var(--border-visible);
  background: var(--surface);
  white-space: nowrap;
}

.common-foods-section {
  margin-top: var(--space-2xl);
}

.foods-card {
  padding: 26px 24px 24px;
}

.search-row {
  display: flex;
  gap: var(--space-sm);
}

.foods-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.food-empty-state {
  margin-top: var(--space-lg);
  padding: var(--space-xl);
  border: 2px dashed var(--border-visible);
  text-align: center;
}

@media (min-width: 640px) {
  .foods-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .foods-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 1280px) {
  .foods-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.food-item {
  border: 2px solid var(--border-visible);
  background: rgba(248, 250, 252, 0.02);
  padding: 18px 18px 16px;
  transition: all 0.2s ease;
}

.food-item:hover {
  border-color: var(--primary);
  background: rgba(249, 115, 22, 0.04);
  transform: translate(-2px, -2px);
}

.food-item-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: var(--space-md);
}

.food-item-name {
  font-family: var(--font-heading);
  font-size: var(--body-sm);
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.35;
  padding-right: 6px;
}

.food-item-en {
  font-family: var(--font-mono);
  font-size: 10px;
}

.cal-tag {
  padding: 3px 8px;
  border: 2px solid;
  font-family: var(--font-heading);
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.cal-tag.low {
  border-color: var(--cta);
  color: var(--cta);
  background: rgba(34, 197, 94, 0.08);
}

.cal-tag.medium {
  border-color: var(--warning);
  color: var(--warning);
  background: rgba(251, 191, 36, 0.08);
}

.cal-tag.high {
  border-color: #ef4444;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

.food-macros {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
}

.macro-cell {
  text-align: center;
  padding: var(--space-xs);
  border: 1px solid var(--border-visible);
  background: var(--surface);
}

.macro-label {
  display: block;
  font-size: 10px;
  color: var(--text-secondary);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.macro-val {
  font-family: var(--font-heading);
  font-size: var(--body-sm);
  font-weight: 700;
  color: var(--text-main);
}

.food-item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: var(--space-md);
}

.add-btn {
  width: 100%;
  padding: 8px;
  border: 2px solid var(--primary);
  background: rgba(249, 115, 22, 0.08);
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: var(--caption);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-btn:hover:not(:disabled) {
  background: var(--primary);
  color: var(--bg-main);
}

.add-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
