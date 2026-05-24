<template>
  <div class="diet-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">饮食计划</span>
          <strong class="topbar-page-title">营养方案</strong>
          <span class="topbar-page-meta">{{ todayDate }}</span>
        </div>
        <div class="topbar-page-actions">
          <button class="nd-btn" @click="plan = null; isEditing = false">返回初始页</button>
          <button v-if="plan && !isEditing" class="nd-btn" @click="enterEditMode" :disabled="loading">
            编辑计划
          </button>
          <button v-if="plan && !isEditing" class="nd-btn" @click="plan = null" :disabled="loading">
            重置计划
          </button>
          <template v-if="isEditing">
            <button class="nd-btn primary" @click="saveEdit" :disabled="editLoading">
              {{ editLoading ? '[ 保存中... ]' : '保存修改' }}
            </button>
            <button class="nd-btn" @click="cancelEdit">
              取消
            </button>
          </template>
        </div>
      </div>
    </Teleport>

    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">[ 营养协议 ]</div>
        <h1 class="text-display-md text-primary">营养饮食</h1>
        <p class="text-secondary mt-xs">{{ todayDate }}</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 8px;">
        <button class="nd-btn" @click="plan = null; isEditing = false">返回初始页</button>
        <button v-if="plan && !isEditing" class="nd-btn" @click="enterEditMode" :disabled="loading">
          编辑计划
        </button>
        <button v-if="plan && !isEditing" class="nd-btn" @click="plan = null" :disabled="loading">
          重置计划
        </button>
        <template v-if="isEditing">
          <button class="nd-btn primary" @click="saveEdit" :disabled="editLoading">
            {{ editLoading ? '[ 保存中... ]' : '保存修改' }}
          </button>
          <button class="nd-btn" @click="cancelEdit">取消</button>
        </template>
      </div>
    </div>

    <!-- Plan View -->
    <div v-if="plan" class="plan-layout" :class="{ editing: isEditing }">
      <div class="plan-view">
        <div v-if="!isEditing" class="diet-plan-head">
          <div class="diet-plan-copy">
            <div class="text-label mb-xs">[ 今日饮食 ]</div>
            <h2 class="diet-plan-title">AI 营养计划</h2>
            <p class="diet-plan-desc">
              {{ planData.calorieStrategy || planData.hydration || '\u6839\u636E\u4F60\u7684\u76EE\u6807\u548C\u8EAB\u4F53\u72B6\u6001\u751F\u6210\u7684\u4ECA\u65E5\u9910\u996E\u5B89\u6392\u3002' }}
            </p>
          </div>
          <div class="diet-plan-actions">
            <button class="nd-btn" @click="returnToInitialView">返回初始页</button>
          </div>
        </div>
        <!-- Macro Summary -->
        <div class="nd-card summary-card mb-2xl">
          <div v-if="!isEditing" class="summary-grid">
            <div class="summary-item">
              <div class="text-caption text-secondary">总热量</div>
              <div class="text-display-md mt-xs text-primary">
                {{ plan.totalCalories || planData.totalCalories || 0 }}
                <span class="text-label ml-xs">千卡</span>
              </div>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-item">
              <div class="text-caption text-secondary">蛋白质</div>
              <div class="text-heading mt-xs text-success">
                {{ plan.protein || planData.protein || 0 }}<span class="text-caption ml-xs">g</span>
              </div>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-item">
              <div class="text-caption text-secondary">碳水</div>
              <div class="text-heading mt-xs text-warning">
                {{ plan.carbs || planData.carbs || 0 }}<span class="text-caption ml-xs">g</span>
              </div>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-item">
              <div class="text-caption text-secondary">脂肪</div>
              <div class="text-heading mt-xs">
                {{ plan.fat || planData.fat || 0 }}<span class="text-caption ml-xs">g</span>
              </div>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-item">
              <div class="text-caption text-secondary">状态</div>
              <div class="text-heading mt-xs" :class="plan.status === 1 ? 'text-success' : 'text-secondary'">
                {{ plan.status === 1 ? '\u2713 \u5DF2\u5B8C\u6210' : '\u25CB \u5F85\u6267\u884C' }}
              </div>
            </div>
          </div>
          <div v-else class="edit-summary-grid">
            <div class="edit-field">
              <label class="text-caption text-secondary">总热量（千卡）</label>
              <input v-model.number="editForm.totalCalories" type="number" class="nd-input" placeholder="2000" min="1" />
            </div>
            <div class="edit-field">
              <label class="text-caption text-secondary">蛋白质（g）</label>
              <input v-model.number="editForm.protein" type="number" class="nd-input" placeholder="150" min="0" />
            </div>
            <div class="edit-field">
              <label class="text-caption text-secondary">碳水（g）</label>
              <input v-model.number="editForm.carbs" type="number" class="nd-input" placeholder="200" min="0" />
            </div>
            <div class="edit-field">
              <label class="text-caption text-secondary">脂肪（g）</label>
              <input v-model.number="editForm.fat" type="number" class="nd-input" placeholder="65" min="0" />
            </div>
          </div>
        </div>

        <!-- Meals List -->
        <div class="meal-section-head mb-lg">
          <span v-if="!isEditing">[ 餐饮安排 ]</span>
          <span v-else>[ 餐饮编辑 ]</span>
        </div>
        <div v-if="!isEditing" class="meals-list mb-xl">
          <div
            v-for="meal in mealPlans"
            :key="meal.recordId"
            class="nd-card meal-card"
            :class="{ 'green-shadow': meal.index % 2 === 1, completed: meal.completed }"
          >
            <div class="meal-header mb-md">
              <h3 class="text-heading">{{ meal.mealName }}</h3>
              <span class="text-caption text-secondary">{{ meal.calories }} 千卡</span>
            </div>
            <div class="meal-items">
              <div v-for="(item, iIndex) in meal.items" :key="iIndex" class="meal-item-row">
                <span class="text-body-sm">{{ item.name }}</span>
                <span class="dot-leader"></span>
                <span class="text-caption text-secondary">{{ item.amount }}</span>
                <span class="text-caption ml-md cal-col">{{ item.calories }} 千卡</span>
              </div>
            </div>
            <div class="meal-card-footer">
              <span class="meal-status-chip" :class="{ completed: meal.completed }">
                {{ meal.completed ? '✓ 本餐已确认' : '○ 待确认' }}
              </span>
              <button
                class="nd-btn meal-checkin-btn"
                :class="{ primary: !meal.completed }"
                @click="handleMealCheckIn(meal)"
                :disabled="meal.completed || !!mealCheckinLoading[meal.index]"
              >
                {{
                  meal.completed
                    ? '已记录'
                    : mealCheckinLoading[meal.index]
                      ? '[ 记录中... ]'
                      : '确认本餐'
                }}
              </button>
            </div>
          </div>
        </div>
        <div v-if="!isEditing && planPanels.length" class="plan-panels mb-2xl">
          <div
            v-for="(panel, index) in planPanels"
            :key="`${panel.title}-${index}`"
            class="nd-card plan-panel-card"
          >
            <div class="text-caption text-secondary">{{ panel.title }}</div>
            <div class="plan-panel-value mt-sm">{{ panel.value }}</div>
          </div>
        </div>

        <!-- Edit Mode: Meal Editor -->
        <div v-else class="meals-edit-list mb-2xl">
          <div
            v-for="(meal, mIdx) in editForm.meals"
            :key="mIdx"
            class="nd-card edit-meal-card mb-md"
          >
            <div class="edit-meal-header">
              <input v-model="meal.mealName" type="text" class="nd-input" placeholder="\u9910\u6B21\u540D\u79F0\uFF08\u4F8B\u5982 \u65E9\u9910\uFF09" />
              <button class="remove-btn" @click="removeEditMeal(mIdx)">\u2715</button>
            </div>
            <div v-for="(item, iIdx) in meal.items" :key="iIdx" class="edit-item-row mb-xs">
              <input v-model="item.name" type="text" class="nd-input small" placeholder="食物名称" />
              <input v-model="item.amount" type="text" class="nd-input small" placeholder="分量" />
              <input v-model.number="item.calories" type="number" class="nd-input small" placeholder="千卡" />
              <button class="remove-icon-btn" @click="removeEditItem(mIdx, iIdx)">\u2715</button>
            </div>
            <button class="add-item-btn" @click="addEditItem(mIdx)">+ 添加食物</button>
          </div>
          <button class="nd-btn w-full" @click="addEditMeal">
            + 添加餐次
          </button>
        </div>

      </div>

      <aside v-if="!isEditing" class="plan-aside">
        <div class="nd-card aside-card aside-quote-card">
          <div class="text-label mb-sm">[ 今日箴言 ]</div>
          <p class="aside-quote">“{{ nutritionQuote.quote }}”</p>
          <p class="aside-quote-author">{{ nutritionQuote.author }}</p>
        </div>

        <div class="nd-card aside-card">
          <div class="text-label mb-sm">[ 执行重点 ]</div>
          <div
            v-for="(item, index) in executionHighlights"
            :key="`${item}-${index}`"
            class="focus-row"
          >
            <span class="focus-index">0{{ index + 1 }}</span>
            <span class="focus-copy">{{ item }}</span>
          </div>
        </div>

        <div class="nd-card aside-card aside-metrics-card">
          <div class="text-label mb-sm">[ 快速读数 ]</div>
          <div
            v-for="metric in planMetrics"
            :key="metric.label"
            class="metric-row"
          >
            <span class="text-caption text-secondary">{{ metric.label }}</span>
            <strong class="metric-value">{{ metric.value }}</strong>
          </div>
        </div>
      </aside>

      <div v-if="!isEditing && plan.status === 0" class="action-bar">
        <div class="action-bar-copy">
          <div class="text-label mb-xs">[ 今日执行 ]</div>
          <p class="action-bar-text">现在支持按早餐、午餐、晚餐分别确认。每确认一餐会立即记录该餐热量，全部餐次确认后会自动完成今日饮食计划。</p>
        </div>
        <div class="action-bar-progress">
          <strong class="action-bar-progress-value">{{ completedMealCount }}/{{ mealPlans.length }}</strong>
          <span class="text-caption text-secondary">餐已确认</span>
        </div>
      </div>
      <div v-else-if="!isEditing" class="completed-banner">
        <span class="text-label text-success">✓ 今日饮食已完成</span>
        <p class="completed-banner-text">这份营养计划已经归档，保持这种节奏即可。</p>
        <button class="nd-btn primary mt-md" @click="plan = null">返回初始页</button>
      </div>
    </div>

    <!-- Creation View -->
    <div v-else>
      <!-- Nutrition Knowledge Section -->
      <div class="knowledge-section mb-2xl">
        <div class="text-label mb-lg">[ 营养基础 ]</div>
        <div class="knowledge-grid">
          <div class="nd-card knowledge-card">
            <div class="knowledge-icon">🔥</div>
            <h3 class="text-heading mt-sm">热量</h3>
            <p class="text-caption text-secondary mt-xs">每日所需热量 = 基础代谢率（BMR）乘以活动系数。减脂通常建议制造 300-500 千卡缺口，增肌通常建议增加 200-400 千卡盈余。</p>
            <div class="knowledge-detail mt-md">
              <div class="detail-row"><span class="text-caption">久坐人群</span><span class="text-caption text-primary">BMR × 1.2</span></div>
              <div class="detail-row"><span class="text-caption">轻度运动</span><span class="text-caption text-primary">BMR × 1.375</span></div>
              <div class="detail-row"><span class="text-caption">中度运动</span><span class="text-caption text-primary">BMR × 1.55</span></div>
              <div class="detail-row"><span class="text-caption">高强度训练</span><span class="text-caption text-primary">BMR × 1.725</span></div>
            </div>
          </div>
          <div class="nd-card knowledge-card">
            <div class="knowledge-icon">🥚</div>
            <h3 class="text-heading mt-sm">蛋白质</h3>
            <p class="text-caption text-secondary mt-xs">蛋白质是肌肉修复与生长的关键营养素。每克蛋白质可提供 4 千卡热量。</p>
            <div class="knowledge-detail mt-md">
              <div class="detail-row"><span class="text-caption">普通成人</span><span class="text-caption text-success">0.8-1.0 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">力量训练者</span><span class="text-caption text-success">1.6-2.2 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">减脂期</span><span class="text-caption text-success">2.0-2.4 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">优质来源</span><span class="text-caption text-primary">鸡胸、鸡蛋、鱼类、牛肉</span></div>
            </div>
          </div>
          <div class="nd-card knowledge-card">
            <div class="knowledge-icon">🍚</div>
            <h3 class="text-heading mt-sm">碳水化合物</h3>
            <p class="text-caption text-secondary mt-xs">碳水是身体优先使用的能量来源，每克碳水可提供 4 千卡。训练前后合理补充尤其关键。</p>
            <div class="knowledge-detail mt-md">
              <div class="detail-row"><span class="text-caption">低强度日</span><span class="text-caption text-warning">3-5 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">中强度训练</span><span class="text-caption text-warning">5-7 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">高强度训练</span><span class="text-caption text-warning">7-10 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">优质来源</span><span class="text-caption text-primary">糙米、燕麦、红薯</span></div>
            </div>
          </div>
          <div class="nd-card knowledge-card">
            <div class="knowledge-icon">🥑</div>
            <h3 class="text-heading mt-sm">脂肪</h3>
            <p class="text-caption text-secondary mt-xs">脂肪参与激素合成与营养吸收，每克脂肪可提供 9 千卡。健康脂肪同样不可忽视。</p>
            <div class="knowledge-detail mt-md">
              <div class="detail-row"><span class="text-caption">推荐占比</span><span class="text-caption">总热量的 20-35%</span></div>
              <div class="detail-row"><span class="text-caption">最低摄入</span><span class="text-caption">0.5-0.8 g/kg</span></div>
              <div class="detail-row"><span class="text-caption">饱和脂肪</span><span class="text-caption text-warning">&lt; 总热量的 10%</span></div>
              <div class="detail-row"><span class="text-caption">优质来源</span><span class="text-caption text-primary">坚果、牛油果、橄榄油</span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Diet Recommendations -->
      <div class="recommendations-section mb-2xl">
        <div class="text-label mb-lg">[ 饮食推荐 ]</div>
        <div class="recommendations-grid">
          <div class="nd-card rec-card">
            <div class="rec-header">
              <span class="rec-badge badge-cut">减脂期</span>
              <h3 class="text-heading">减脂饮食方案</h3>
            </div>
            <div class="rec-macros mt-md">
              <div class="macro-item"><span class="text-caption text-secondary">热量</span><span class="text-body">1800-2200 千卡</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">蛋白质</span><span class="text-body text-success">30-35%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">碳水</span><span class="text-body text-warning">35-40%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">脂肪</span><span class="text-body">25-30%</span></div>
            </div>
            <div class="rec-tips mt-md">
              <p class="text-caption text-secondary">饮食优先保证高蛋白，增强饱腹感。</p>
              <p class="text-caption text-secondary">主食尽量选择低 GI 碳水，稳定能量与食欲。</p>
              <p class="text-caption text-secondary">每日热量缺口控制在 300-500 千卡更稳妥。</p>
            </div>
          </div>
          <div class="nd-card rec-card">
            <div class="rec-header">
              <span class="rec-badge badge-bulk">增肌期</span>
              <h3 class="text-heading">增肌饮食方案</h3>
            </div>
            <div class="rec-macros mt-md">
              <div class="macro-item"><span class="text-caption text-secondary">热量</span><span class="text-body">2500-3200 千卡</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">蛋白质</span><span class="text-body text-success">25-30%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">碳水</span><span class="text-body text-warning">45-55%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">脂肪</span><span class="text-body">20-25%</span></div>
            </div>
            <div class="rec-tips mt-md">
              <p class="text-caption text-secondary">训练后 30 分钟内补充蛋白质和碳水更有效。</p>
              <p class="text-caption text-secondary">每日维持 200-400 千卡盈余更利于稳定增肌。</p>
              <p class="text-caption text-secondary">碳水可以更多放在训练前后时段摄入。</p>
            </div>
          </div>
          <div class="nd-card rec-card">
            <div class="rec-header">
              <span class="rec-badge badge-maintain">维持期</span>
              <h3 class="text-heading">体态维持方案</h3>
            </div>
            <div class="rec-macros mt-md">
              <div class="macro-item"><span class="text-caption text-secondary">热量</span><span class="text-body">2200-2600 千卡</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">蛋白质</span><span class="text-body text-success">25-30%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">碳水</span><span class="text-body text-warning">40-45%</span></div>
              <div class="macro-item"><span class="text-caption text-secondary">脂肪</span><span class="text-body">25-30%</span></div>
            </div>
            <div class="rec-tips mt-md">
              <p class="text-caption text-secondary">保持热量摄入与消耗大体平衡。</p>
              <p class="text-caption text-secondary">均衡摄入三大营养素，稳定三餐节奏。</p>
              <p class="text-caption text-secondary">注意微量元素与膳食纤维的补充。</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Plan Creation -->
      <div class="creation-layout" :class="{ 'has-preview': creationPreviewMeals.length > 0, 'has-analysis-ready': hasRecordedAnalysis }">
        <div v-if="!creationPreviewMeals.length" class="nd-card creation-card creation-card-ai" :class="{ 'analysis-ready': hasRecordedAnalysis }">
          <div class="creation-header">
            <span class="text-caption" :class="hasRecordedAnalysis ? 'text-primary' : 'text-warning'">
              {{ hasRecordedAnalysis ? '[ 联动生成 ]' : '[ AI 生成 ]' }}
            </span>
            <h2 class="text-heading mt-sm">{{ hasRecordedAnalysis ? '基于已记录餐次生成饮食' : '智能生成饮食' }}</h2>
            <p class="text-caption text-secondary mt-xs">
              {{ hasRecordedAnalysis
                ? `已将本次识别结果记录到${recordedAnalysisSnapshot?.mealLabel}，AI 会按这餐已经吃过来重排今天剩余饮食。`
                : (hasAnalysisReady
                  ? '右侧识别完成后，还需要先记录到具体餐次，左侧才会按这餐已吃过来联动生成。'
                  : 'AI 将分析您的身体指标并生成个性化营养计划。') }}
            </p>
            <div v-if="hasRecordedAnalysis && recordedAnalysisSnapshot" class="analysis-bridge mt-lg">
              <div class="analysis-bridge-head">
                <span class="text-caption text-primary">已记录到 {{ recordedAnalysisSnapshot.mealLabel }}</span>
                <strong class="analysis-bridge-calories">{{ recordedAnalysisSnapshot.result.totalCalories }} 千卡</strong>
              </div>
              <div class="analysis-bridge-tags">
                <span v-for="tag in analysisSummaryTags" :key="tag" class="analysis-bridge-tag">{{ tag }}</span>
              </div>
              <div class="analysis-bridge-macros">
                <span>蛋白质 {{ recordedAnalysisSnapshot.result.totalProtein }}g</span>
                <span>碳水 {{ recordedAnalysisSnapshot.result.totalCarbs }}g</span>
                <span>脂肪 {{ recordedAnalysisSnapshot.result.totalFat }}g</span>
              </div>
            </div>
          </div>
          <div class="ai-visual mt-2xl mb-2xl" :class="{ 'ai-visual-compact': hasRecordedAnalysis, 'ai-visual-loading': loading }">
            <div class="ai-rings">
              <div class="ring r1"></div>
              <div class="ring r2" style="border-color: rgba(34,197,94,0.3);"></div>
              <div class="ring r3" style="border-color: rgba(34,197,94,0.15);"></div>
              <div class="ai-center-text" style="color: #22C55E;">&#x1F957;</div>
            </div>
            <div v-if="loading" class="creation-inline-processing">
              <div class="processing-ring"></div>
              <span class="text-caption text-warning">AI 正在生成营养计划...</span>
            </div>
            <p v-if="hasRecordedAnalysis" class="ai-visual-caption text-caption text-secondary">
              已记录的餐次会被视为今天已完成摄入，生成后左侧会直接切换为剩余饮食安排预览。
            </p>
            <p v-else-if="hasAnalysisReady" class="ai-visual-caption text-caption text-secondary">
              先在右侧把这次识别结果记录到早餐、午餐、晚餐或加餐，再进行联动生成。
            </p>
          </div>
          <button class="nd-btn primary w-full" @click="handleGenerate" :disabled="loading">
            {{ loading ? '[ 生成中... ]' : (hasRecordedAnalysis ? '⚡ 按已记录餐次生成计划' : '⚡ 开启 AI 生成') }}
          </button>
        </div>
        <div v-else class="nd-card creation-card creation-card-preview">
          <div class="creation-header">
            <span class="text-caption text-primary">[ &#x9910;&#x996E;&#x5B89;&#x6392; ]</span>
            <h2 class="text-heading mt-sm">AI &#x751F;&#x6210;&#x8BA1;&#x5212;&#x9884;&#x89C8;</h2>
            <p class="text-caption text-secondary mt-xs">&#x751F;&#x6210;&#x540E;&#x8FD9;&#x5F20;&#x5361;&#x7247;&#x4F1A;&#x76F4;&#x63A5;&#x5207;&#x6362;&#x4E3A;&#x9910;&#x996E;&#x5B89;&#x6392;&#xFF0C;&#x65B9;&#x4FBF;&#x5148;&#x9884;&#x89C8;&#x518D;&#x8FDB;&#x5165;&#x8BE6;&#x60C5;&#x9875;&#x3002;</p>
          </div>
          <div class="preview-meals mt-xl">
            <div
              v-for="meal in creationPreviewMeals"
              :key="meal.mealName"
              class="preview-meal-card"
            >
              <div class="preview-meal-head">
                <strong class="text-heading">{{ meal.mealName }}</strong>
                <span class="text-caption text-secondary">{{ meal.calories }} &#x5343;&#x5361;</span>
              </div>
              <div class="preview-meal-items">
                <div
                  v-for="item in meal.items"
                  :key="item.name"
                  class="preview-item-row"
                >
                  <span class="text-body-sm">{{ item.name }}</span>
                  <span class="dot-leader"></span>
                  <span class="text-caption text-secondary">{{ item.amount }}</span>
                  <span class="text-caption ml-md cal-col">{{ item.calories }} &#x5343;&#x5361;</span>
                </div>
              </div>
            </div>
          </div>
          <div class="preview-actions mt-lg">
            <button class="nd-btn primary w-full" @click="openPreviewPlanDetail">&#x8FDB;&#x5165;&#x8BE6;&#x60C5;&#x8BA1;&#x5212;&#x9875;</button>
            <button class="nd-btn w-full" @click="handleGenerate" :disabled="loading">
              {{ loading ? '[ \u91CD\u65B0\u751F\u6210\u4E2D... ]' : (hasRecordedAnalysis ? '\u6309\u5DF2\u8BB0\u5F55\u9910\u6B21\u91CD\u65B0\u751F\u6210' : '\u91CD\u65B0\u751F\u6210') }}
            </button>
          </div>
          <p class="preview-tip text-caption text-secondary mt-lg">[ AI &#x751F;&#x6210; ] &#x4E0E; [ &#x9910;&#x996E;&#x5B89;&#x6392; ] &#x73B0;&#x5728;&#x5171;&#x7528;&#x540C;&#x4E00;&#x5F20;&#x5361;&#x7247;&#xFF0C;&#x751F;&#x6210;&#x540E;&#x4F1A;&#x5728;&#x539F;&#x4F4D;&#x5207;&#x6362;&#x3002;</p>
        </div>
        <div
          class="nd-card creation-card creation-card-analysis"
          :class="{ 'analysis-success': analysisStatus === 'success' && !!analysisResult }"
        >
          <div class="card-top flex-between mb-lg">
            <div class="text-label">[ &#x89C6;&#x89C9;&#x5206;&#x6790; ]</div>
            <button v-if="analysisStatus === 'success'" @click="handleAnalysisReset" class="nd-btn" style="font-size:12px;padding:6px 14px;">
              &#x2192; &#x91CD;&#x65B0;&#x4E0A;&#x4F20;
            </button>
          </div>
          <div class="upload-zone diet-upload-zone" @click="openAnalysisUpload">
            <input
              ref="analysisFileInput"
              type="file"
              accept="image/*"
              style="display:none;"
              @change="handleAnalysisFileChange"
            />
            <template v-if="analysisImage">
              <div class="image-frame">
                <img :src="analysisImage" alt="uploaded food" class="preview-img" />
                <div v-if="analysisStatus === 'analyzing'" class="scan-overlay">
                  <div class="scan-content">
                    <div class="scan-ring"></div>
                    <div class="scan-label">AI &#x6DF1;&#x5EA6;&#x626B;&#x63CF;&#x4E2D;</div>
                    <div class="scan-bar">
                      <div class="scan-bar-fill" :style="{ width: analysisProgress + '%' }"></div>
                    </div>
                    <div class="scan-pct">{{ Math.round(analysisProgress) }}%</div>
                  </div>
                  <div class="scan-line"></div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="upload-prompt">
                <div class="upload-icon-box">&#x2B06;</div>
                <div class="upload-title">&#x70B9;&#x51FB;&#x4E0A;&#x4F20;&#x9910;&#x98DF;&#x56FE;&#x7247;</div>
                <div class="upload-hint text-caption">&#x652F;&#x6301; JPG / PNG &#x683C;&#x5F0F;</div>
              </div>
            </template>
          </div>
          <div v-if="analysisStatus === 'idle'" class="idle-hint">
            <div class="idle-icon">&#x25CC;</div>
            <p class="text-caption">&#x8BC6;&#x522B;&#x540E;&#x53EF;&#x76F4;&#x63A5;&#x8BB0;&#x5F55;&#x672C;&#x6B21;&#x996E;&#x98DF;&#x5343;&#x5361;</p>
          </div>
          <div v-else-if="analysisStatus === 'error'" class="error-block">
            <div class="error-icon">&#x26A0;</div>
            <p>{{ analysisErrorMessage }}</p>
          </div>
          <div v-else-if="analysisStatus === 'success' && analysisResult" class="result-block">
            <div class="analysis-top-grid">
              <div class="food-tags-row">
                <span class="text-caption text-primary" style="letter-spacing:2px;">&#x8BC6;&#x522B;&#x5185;&#x5BB9;</span>
                <div class="tag-list">
                  <span v-for="(food, index) in analysisResult.foods" :key="food.name + '-' + index" class="food-tag">
                    {{ food.name }}
                  </span>
                </div>
              </div>
              <div class="analysis-meal-target">
                <div class="text-caption text-primary analysis-meal-target-label">计入餐次</div>
                <div class="analysis-meal-options">
                  <button
                    v-for="option in analysisMealOptions"
                    :key="option.id"
                    type="button"
                    class="analysis-meal-option"
                    :class="{ active: analysisMealTarget === option.id }"
                    @click="analysisMealTarget = option.id"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="nutrition-row">
              <div class="nutri-box calories-box">
                <div class="nutri-emoji">&#x1F525;</div>
                <div class="nutri-val">{{ analysisResult.totalCalories }}</div>
                <div class="nutri-unit">&#x5343;&#x5361;</div>
                <div class="nutri-label">&#x70ED;&#x91CF;</div>
              </div>
              <div class="nutri-box protein-box">
                <div class="nutri-emoji">&#x1F357;</div>
                <div class="nutri-val">{{ analysisResult.totalProtein }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">&#x86CB;&#x767D;&#x8D28;</div>
              </div>
              <div class="nutri-box carbs-box">
                <div class="nutri-emoji">&#x1F35A;</div>
                <div class="nutri-val">{{ analysisResult.totalCarbs }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">&#x78B3;&#x6C34;</div>
              </div>
              <div class="nutri-box fat-box">
                <div class="nutri-emoji">&#x1F951;</div>
                <div class="nutri-val">{{ analysisResult.totalFat }}</div>
                <div class="nutri-unit">g</div>
                <div class="nutri-label">&#x8102;&#x80AA;</div>
              </div>
            </div>
            <button class="nd-btn primary w-full mt-lg" @click="handleRecordAnalysis" :disabled="recordingAnalysis">
              {{ recordingAnalysis ? '[ \u8BB0\u5F55\u4E2D... ]' : '\u8BB0\u5F55\u672C\u6B21\u996E\u98DF ' + analysisResult.totalCalories + ' \u5343\u5361' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed, watch } from 'vue'
import { getTodayDietPlan, generateDietPlan, checkInDietPlan, updateDietPlan } from '../../api/ai'
import { recognizeFood, addFoodRecord, getFoodRecords, type FoodRecognitionResult, type FoodRecord } from '../../api/food'
import { ElMessage } from 'element-plus'
import { useWeather } from '../../composables/useWeather'
import { toWeatherContextPayload } from '../../utils/aiContext'
import { readImageAsOptimizedDataUrl } from '../../utils/imageUpload'

const plan = ref<any>(null)
const previewPlan = ref<any>(null)
const loading = ref(false)
const checkinLoading = ref(false)
const mealCheckinLoading = reactive<Record<number, boolean>>({})
const isEditing = ref(false)
const editLoading = ref(false)
const completedMealRecordIds = ref<string[]>([])
const editForm = reactive({
  totalCalories: 2000,
  protein: 150,
  carbs: 200,
  fat: 65,
  meals: [] as { mealName: string; items: { name: string; amount: string; calories: number }[] }[]
})

const analysisFileInput = ref<HTMLInputElement | null>(null)
const analysisStatus = ref<'idle' | 'analyzing' | 'success' | 'error'>('idle')
const analysisImage = ref<string | null>(null)
const analysisResult = ref<FoodRecognitionResult | null>(null)
const analysisErrorMessage = ref('')
const analysisProgress = ref(0)
const recordingAnalysis = ref(false)
const analysisMealTarget = ref('')
const recordedAnalysisSnapshot = ref<{
  mealId: string
  mealLabel: string
  result: FoodRecognitionResult
} | null>(null)
const todayDate = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
const { weather, fetchWeather: refreshWeather } = useWeather()

const parsePlanContent = (source: any) => {
  if (source?.content) {
    try { return JSON.parse(source.content) } catch { return { meals: [] } }
  }
  return { meals: [] }
}

const planData = computed(() => {
  return parsePlanContent(plan.value)
})

const activeMealPlanSource = computed(() => previewPlan.value || plan.value || null)

const buildMealRecordId = (planId: number | string, mealIndex: number) => `diet-plan:${planId}:meal:${mealIndex}`

const mealPlans = computed(() => {
  const meals = planData.value.meals || []
  const planId = Number(plan.value?.id || 0)
  return meals.map((meal: any, index: number) => {
    const recordId = buildMealRecordId(planId, index)
    return {
      ...meal,
      index,
      recordId,
      calories: getMealCalories(meal),
      completed: completedMealRecordIds.value.includes(recordId)
    }
  })
})

const completedMealCount = computed(() => mealPlans.value.filter(meal => meal.completed).length)

const creationPreviewData = computed(() => parsePlanContent(previewPlan.value))

const creationPreviewMeals = computed(() => {
  return (creationPreviewData.value.meals || []).map((meal: any) => ({
    mealName: meal.mealName || '\u672A\u547D\u540D\u9910\u6B21',
    calories: getMealCalories(meal),
    items: (meal.items || []).map((item: any) => ({
      name: item.name || '\u672A\u547D\u540D\u98DF\u7269',
      amount: item.amount || '-',
      calories: item.calories || 0
    }))
  }))
})

const analysisMealOptions = computed(() => {
  const source = activeMealPlanSource.value
  if (source?.id) {
    const meals = parsePlanContent(source).meals || []
    if (meals.length) {
      return [
        ...meals.map((meal: any, index: number) => ({
          id: `plan-meal-${index}`,
          label: meal.mealName || `餐次 ${index + 1}`,
          recordId: buildMealRecordId(source.id, index)
        })),
        { id: 'snack', label: '加餐', recordId: '' }
      ]
    }
  }

  return [
    { id: 'breakfast', label: '早餐', recordId: '' },
    { id: 'lunch', label: '午餐', recordId: '' },
    { id: 'dinner', label: '晚餐', recordId: '' },
    { id: 'snack', label: '加餐', recordId: '' }
  ]
})

const hasAnalysisReady = computed(() => analysisStatus.value === 'success' && !!analysisResult.value)
const hasRecordedAnalysis = computed(() => !!recordedAnalysisSnapshot.value)

const analysisSummaryTags = computed(() => {
  return (recordedAnalysisSnapshot.value?.result.foods || []).slice(0, 3).map(food => food.name)
})

const buildRecognizedFoodsContext = () => {
  if (!recordedAnalysisSnapshot.value?.result.foods?.length) return ''

  const { mealLabel, result } = recordedAnalysisSnapshot.value
  const foodsSummary = result.foods
    .map(food => `${food.name} ${food.servingSize || ''}（约${food.calories} 千卡）`.trim())
    .join('，')

  return [
    `本次视觉识别并已记录到${mealLabel}的食物：${foodsSummary}。`,
    `这次${mealLabel}已摄入约 ${result.totalCalories} 千卡，蛋白质 ${result.totalProtein}g，碳水 ${result.totalCarbs}g，脂肪 ${result.totalFat}g。`,
    `请将${mealLabel}视为今天已经吃过的一餐，优先规划剩余餐次；如果仍需保留${mealLabel}补充，请控制份量，避免重复堆高热量、糖和脂肪。`
  ].join('')
}

watch(analysisMealOptions, (options) => {
  if (!options.length) {
    analysisMealTarget.value = ''
    return
  }
  if (!options.some(option => option.id === analysisMealTarget.value)) {
    analysisMealTarget.value = options[0].id
  }
}, { immediate: true })

const planPanels = computed(() => {
  if (!plan.value || isEditing.value) return []

  const panels: { title: string; value: string }[] = []

  if (planData.value?.calorieStrategy) {
    panels.push({ title: '热量策略', value: String(planData.value.calorieStrategy) })
  }
  if (planData.value?.hydration) {
    panels.push({ title: '补水建议', value: String(planData.value.hydration) })
  }
  if (planData.value?.macroStrategy?.proteinRule) {
    panels.push({ title: '\u86CB\u767D\u8D28\u89C4\u5219', value: String(planData.value.macroStrategy.proteinRule) })
  }
  if (planData.value?.tips?.length) {
    panels.push({ title: '执行提示', value: String(planData.value.tips[0]) })
  }

  return panels.slice(0, 4)
})

const nutritionQuotes = [
  { quote: '\u4E0D\u9700\u8981\u5B8C\u7F8E\u7684\u4E00\u5929\uFF0C\u53EA\u9700\u8981\u7A33\u5B9A\u7684\u4E00\u5468\u3002', author: 'FitMind' },
  { quote: '\u5148\u628A\u6BCF\u4E00\u9910\u5403\u7A33\u5B9A\uFF0C\u8EAB\u4F53\u53D8\u5316\u4F1A\u6162\u6162\u7AD9\u5230\u4F60\u8FD9\u8FB9\u3002', author: 'FitMind' },
  { quote: '\u7EAA\u5F8B\u4E0D\u662F\u82DB\u523B\uFF0C\u800C\u662F\u8BA9\u6B63\u786E\u9009\u62E9\u53D8\u5F97\u66F4\u7701\u529B\u3002', author: 'FitMind' },
  { quote: '\u4F60\u91CD\u590D\u5403\u4E0B\u53BB\u7684\u4E1C\u897F\uFF0C\u6700\u7EC8\u4F1A\u6210\u4E3A\u4F60\u7684\u72B6\u6001\u3002', author: 'FitMind' }
]

const nutritionQuote = computed(() => {
  const seed = Number(plan.value?.id ?? 0) + Number(planData.value?.meals?.length ?? 0)
  return nutritionQuotes[Math.abs(seed) % nutritionQuotes.length]
})

const executionHighlights = computed(() => {
  const items: string[] = []

  if (planData.value?.calorieStrategy) {
    items.push(`热量策略：${String(planData.value.calorieStrategy)}`)
  }
  if (planData.value?.macroStrategy?.proteinRule) {
    items.push(`蛋白质重点：${String(planData.value.macroStrategy.proteinRule)}`)
  }
  if (planData.value?.hydration) {
    items.push(`补水建议：${String(planData.value.hydration)}`)
  }
  if (planData.value?.tips?.length) {
    items.push(`执行提醒：${String(planData.value.tips[0])}`)
  }

  if (!items.length) {
    return [
      '\u6BCF\u9910\u5148\u4FDD\u8BC1\u86CB\u767D\u8D28\u6765\u6E90\uFF0C\u518D\u6309\u8BAD\u7EC3\u5F3A\u5EA6\u8C03\u6574\u4E3B\u98DF\u3002',
      '\u5168\u5929\u5206\u6B21\u8865\u6C34\uFF0C\u4E0D\u8981\u96C6\u4E2D\u66B4\u996E\u3002',
      '\u4F18\u5148\u628A\u4E09\u9910\u8282\u594F\u5403\u7A33\u5B9A\uFF0C\u518D\u51B3\u5B9A\u662F\u5426\u52A0\u9910\u3002'
    ]
  }

  return items.slice(0, 4)
})

const planMetrics = computed(() => [
  { label: '\u9910\u6B21\u6570\u91CF', value: `${planData.value.meals?.length || 0} \u9910` },
  { label: '\u6267\u884C\u72B6\u6001', value: plan.value?.status === 1 ? '\u5DF2\u5B8C\u6210' : `${completedMealCount.value}/${mealPlans.value.length || 0} \u5DF2\u786E\u8BA4` },
  { label: '\u8865\u6C34\u72B6\u6001', value: planData.value?.hydration ? '\u5DF2\u914D\u7F6E' : '\u5EFA\u8BAE 2L+' }
])

const getMealCalories = (meal: any) => {
  return (meal.items || []).reduce((sum: number, item: any) => sum + (item.calories || 0), 0)
}

const syncCompletedMeals = async (targetPlan = plan.value) => {
  if (!targetPlan?.id) {
    completedMealRecordIds.value = []
    return
  }
  try {
    const records = await getFoodRecords()
    const prefix = `diet-plan:${targetPlan.id}:meal:`
    completedMealRecordIds.value = (Array.isArray(records) ? records : [])
      .map((record: FoodRecord) => String(record.foodId || ''))
      .filter(foodId => foodId.startsWith(prefix))
      .map(foodId => foodId.includes(':recognized:') ? foodId.split(':recognized:')[0] : foodId)
  } catch {
    completedMealRecordIds.value = []
  }
}

const returnToInitialView = () => {
  plan.value = null
  isEditing.value = false
}

const openPreviewPlanDetail = () => {
  if (!previewPlan.value) return
  plan.value = previewPlan.value
  isEditing.value = false
  syncCompletedMeals(previewPlan.value)
}

const getAiWeatherContext = async () => {
  if (!weather.value) {
    try {
      await refreshWeather()
    } catch {
      return undefined
    }
  }
  return toWeatherContextPayload(weather.value)
}
const openAnalysisUpload = () => {
  analysisFileInput.value?.click()
}
const handleAnalysisReset = () => {
  analysisStatus.value = 'idle'
  analysisImage.value = null
  analysisResult.value = null
  analysisErrorMessage.value = ''
  analysisProgress.value = 0
  recordedAnalysisSnapshot.value = null
  analysisMealTarget.value = analysisMealOptions.value[0]?.id || ''
  if (analysisFileInput.value) {
    analysisFileInput.value.value = ''
  }
}
const handleAnalysisFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return
  readImageAsOptimizedDataUrl(files[0])
    .then(handleAnalysisUpload)
    .catch(() => {
      analysisStatus.value = 'error'
      analysisErrorMessage.value = '图片处理失败，请重试'
    })
}
const handleAnalysisUpload = async (imageBase64: string) => {
  analysisStatus.value = 'analyzing'
  analysisImage.value = imageBase64
  analysisResult.value = null
  analysisErrorMessage.value = ''
  analysisProgress.value = 0
  recordedAnalysisSnapshot.value = null
  const progressTimer = setInterval(() => {
    analysisProgress.value += Math.random() * 16
    if (analysisProgress.value >= 90) analysisProgress.value = 90
  }, 260)
  try {
    const result: any = await recognizeFood(imageBase64)
    analysisProgress.value = 100
    analysisResult.value = result as FoodRecognitionResult
    analysisStatus.value = 'success'
    analysisMealTarget.value = analysisMealOptions.value[0]?.id || ''
  } catch (e: any) {
    analysisStatus.value = 'error'
    analysisErrorMessage.value = e?.message || '\u8BC6\u522B\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5'
  } finally {
    clearInterval(progressTimer)
  }
}
const handleRecordAnalysis = async () => {
  if (!analysisResult.value?.foods?.length) return
  const selectedMeal = analysisMealOptions.value.find(option => option.id === analysisMealTarget.value) || analysisMealOptions.value[0]
  const mealLabel = selectedMeal?.label || '未分类'
  const mealRecordPrefix = selectedMeal?.recordId || `manual-meal:${mealLabel}`
  recordingAnalysis.value = true
  try {
    await Promise.all(
      analysisResult.value.foods.map((food, index) =>
        addFoodRecord({
          foodId: `${mealRecordPrefix}:recognized:${Date.now()}:${index}`,
          foodName: `${food.name}（${mealLabel}）`,
          calories: food.calories,
          protein: food.protein,
          carbs: food.carbs,
          fat: food.fat,
          servingSize: `${food.servingSize}｜${mealLabel}`
        })
      )
    )
    if (activeMealPlanSource.value?.id) {
      await syncCompletedMeals(activeMealPlanSource.value)
    }
    recordedAnalysisSnapshot.value = {
      mealId: selectedMeal?.id || '',
      mealLabel,
      result: JSON.parse(JSON.stringify(analysisResult.value))
    }
    ElMessage.success(`已记录本次饮食 ${analysisResult.value.totalCalories} 千卡，并计入${mealLabel}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '\u8BB0\u5F55\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5')
  } finally {
    recordingAnalysis.value = false
  }
}

const handleMealCheckIn = async (meal: any) => {
  if (!plan.value?.id || meal.completed || mealCheckinLoading[meal.index]) return
  mealCheckinLoading[meal.index] = true
  try {
    await addFoodRecord({
      foodId: meal.recordId,
      foodName: `${meal.mealName}（计划确认）`,
      calories: meal.calories,
      protein: 0,
      carbs: 0,
      fat: 0,
      servingSize: '1餐'
    })

    completedMealRecordIds.value = [...completedMealRecordIds.value, meal.recordId]

    const allCompleted = mealPlans.value.length > 0 && completedMealCount.value >= mealPlans.value.length
    if (allCompleted && plan.value.status !== 1) {
      checkinLoading.value = true
      await checkInDietPlan(plan.value.id)
      plan.value = { ...plan.value, status: 1 }
      previewPlan.value = { ...previewPlan.value, status: 1 }
      ElMessage.success('今日饮食已全部确认完成')
    } else {
      ElMessage.success(`${meal.mealName}已确认并记录 ${meal.calories} 千卡`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '确认失败，请稍后重试')
  } finally {
    mealCheckinLoading[meal.index] = false
    checkinLoading.value = false
  }
}

const fetchPlan = async () => {
  try {
    const res = await getTodayDietPlan()
    previewPlan.value = res || null
    if (res && res.status !== 1) {
      plan.value = res
    } else {
      plan.value = null
    }
    await syncCompletedMeals(res || null)
  } catch {
    plan.value = null
    previewPlan.value = null
    completedMealRecordIds.value = []
  }
}

const handleGenerate = async () => {
  loading.value = true
  try {
    const weatherContext = await getAiWeatherContext()
    const usedAnalysisContext = hasRecordedAnalysis.value ? buildRecognizedFoodsContext() : undefined
    const res = await generateDietPlan({
      weather: weatherContext,
      recognizedFoodsContext: usedAnalysisContext
    })
    previewPlan.value = res
    plan.value = null
    ElMessage.success(usedAnalysisContext ? '已基于识别结果生成营养计划' : '\u8425\u517B\u8BA1\u5212\u5DF2\u751F\u6210')
  } catch (e: any) {
    ElMessage.error('\u751F\u6210\u5931\u8D25\uFF1A' + (e?.message || '\u8BF7\u786E\u4FDD\u5DF2\u5B8C\u5584\u8EAB\u4F53\u6307\u6807\u540E\u91CD\u8BD5'))
  } finally {
    loading.value = false
  }
}

const enterEditMode = () => {
  if (!plan.value) return
  editForm.totalCalories = plan.value.totalCalories || planData.value.totalCalories || 2000
  editForm.protein = plan.value.protein || planData.value.protein || 150
  editForm.carbs = plan.value.carbs || planData.value.carbs || 200
  editForm.fat = plan.value.fat || planData.value.fat || 65
  editForm.meals = (planData.value.meals || []).map((m: any) => ({
    mealName: m.mealName || '',
    items: (m.items || []).map((i: any) => ({
      name: i.name || '',
      amount: i.amount || '',
      calories: i.calories || 0
    }))
  }))
  isEditing.value = true
}

const cancelEdit = () => {
  isEditing.value = false
}

const addEditMeal = () => {
  editForm.meals.push({ mealName: '', items: [{ name: '', amount: '', calories: 0 }] })
}

const removeEditMeal = (idx: number) => {
  editForm.meals.splice(idx, 1)
}

const addEditItem = (mIdx: number) => {
  editForm.meals[mIdx].items.push({ name: '', amount: '', calories: 0 })
}

const removeEditItem = (mIdx: number, iIdx: number) => {
  editForm.meals[mIdx].items.splice(iIdx, 1)
}

const saveEdit = async () => {
  if (!plan.value) return

  if (!editForm.totalCalories || editForm.totalCalories <= 0) {
    ElMessage.warning('\u8BF7\u8F93\u5165\u6709\u6548\u7684\u603B\u70ED\u91CF')
    return
  }

  const validMeals = editForm.meals.filter(m => m.mealName.trim() && m.items.some(i => i.name.trim()))
  if (validMeals.length === 0) {
    ElMessage.warning('\u8BF7\u81F3\u5C11\u4FDD\u7559\u4E00\u4E2A\u9910\u6B21\u548C\u98DF\u7269')
    return
  }

  editLoading.value = true
  try {
    const content = JSON.stringify({
      totalCalories: editForm.totalCalories,
      protein: editForm.protein,
      carbs: editForm.carbs,
      fat: editForm.fat,
      meals: validMeals.map(m => ({
        mealName: m.mealName,
        items: m.items.filter(i => i.name.trim())
      }))
    })
    const payload = {
      totalCalories: editForm.totalCalories,
      protein: editForm.protein,
      carbs: editForm.carbs,
      fat: editForm.fat,
      content
    }
    const res = await updateDietPlan(plan.value.id, payload)
    plan.value = res
    previewPlan.value = res
    isEditing.value = false
    ElMessage.success('\u8425\u517B\u8BA1\u5212\u5DF2\u66F4\u65B0')
  } catch (e: any) {
    ElMessage.error('\u66F4\u65B0\u5931\u8D25\uFF1A' + (e?.message || '\u8BF7\u7A0D\u540E\u91CD\u8BD5'))
  } finally {
    editLoading.value = false
  }
}

onMounted(() => { fetchPlan() })
</script>

<style scoped>
.diet-content { width: 100%; }

.plan-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) 320px;
  gap: 24px;
  align-items: start;
}

.plan-layout.editing {
  grid-template-columns: 1fr;
}

.plan-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.plan-layout:not(.editing) .plan-view {
  display: contents;
}

.plan-aside {
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 24px;
}

.aside-card {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  box-shadow: 0 16px 36px rgba(88, 78, 67, 0.07);
  background: linear-gradient(180deg, rgba(255,255,255,0.92), rgba(247,243,235,0.82));
}

.aside-quote-card {
  background:
    radial-gradient(circle at top right, rgba(194, 169, 120, 0.16), transparent 42%),
    linear-gradient(180deg, rgba(255,255,255,0.96), rgba(247,243,235,0.88));
}

.aside-quote {
  margin: 0;
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1.18rem;
  line-height: 1.7;
  letter-spacing: 0.01em;
}

.aside-quote-author {
  margin: 14px 0 0;
  color: var(--text-secondary);
  font-size: 0.84rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.focus-row,
.metric-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
}

.focus-row:last-child,
.metric-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.focus-index {
  min-width: 30px;
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.focus-copy {
  color: var(--text-main);
  line-height: 1.7;
}

.metric-value {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 0.92rem;
  text-align: right;
}

.diet-plan-head {
  grid-column: 1 / -1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.diet-plan-copy {
  flex: 1;
  min-width: 0;
  max-width: 760px;
}

.diet-plan-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.diet-plan-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 1.45rem;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.02em;
}

.diet-plan-desc {
  margin: 10px 0 0;
  max-width: 760px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.diet-plan-badge {
  flex-shrink: 0;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.12);
  border: 1px solid rgba(194, 169, 120, 0.2);
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}


.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

/* Loading */
.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}
.ai-processing { display: flex; align-items: center; }
.processing-ring {
  width: 32px; height: 32px;
  border: 3px solid rgba(34,197,94,0.2);
  border-top-color: #22C55E;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Summary Card */
.summary-card {
  grid-column: 1;
  padding: 26px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(255,255,255,0.92), rgba(247,243,235,0.96));
  border: 1px solid rgba(194, 169, 120, 0.16);
  box-shadow: 0 18px 40px rgba(88, 78, 67, 0.08);
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 20px;
}
.summary-item {
  min-width: 0;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(255,255,255,0.72);
  border: 1px solid rgba(88, 78, 67, 0.08);
}
.summary-divider {
  display: none;
}

/* Meals */
.meals-list {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.meal-section-head {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-family: var(--font-heading);
  letter-spacing: 0.08em;
  margin-top: 4px;
}
.meal-card {
  padding: 20px;
  min-width: 0;
  border-radius: 20px;
  border: 1px solid rgba(88, 78, 67, 0.08);
  box-shadow: 0 14px 32px rgba(88, 78, 67, 0.06);
  display: flex;
  flex-direction: column;
}
.meal-card.completed {
  border-color: rgba(127, 157, 135, 0.28);
  background: linear-gradient(180deg, rgba(248, 252, 248, 0.92), rgba(255,255,255,0.88));
}
.meal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px dashed rgba(88, 78, 67, 0.12);
  padding-bottom: 12px;
}
.meal-item-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid rgba(88, 78, 67, 0.05);
  gap: 8px;
}
.dot-leader {
  flex: 1;
  height: 1px;
  border-bottom: 1px dashed rgba(88, 78, 67, 0.12);
}
.cal-col { min-width: 60px; text-align: right; }
.meal-card-footer {
  margin-top: auto;
  padding-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.meal-status-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(88, 78, 67, 0.06);
  color: var(--text-secondary);
  font-size: 0.78rem;
  line-height: 1;
}
.meal-status-chip.completed {
  background: rgba(127, 157, 135, 0.14);
  color: #4b7a57;
}
.meal-checkin-btn {
  min-width: 108px;
}

.plan-panels {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.meals-edit-list {
  grid-column: 1 / -1;
}

.plan-panel-card {
  padding: 18px 20px;
  border-radius: 18px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  background: rgba(255,255,255,0.78);
}

.plan-panel-value {
  color: var(--text-main);
  line-height: 1.7;
  font-weight: 600;
}

/* Action */
.action-bar,
.completed-banner {
  grid-column: 1 / -1;
  width: min(100%, 860px);
  justify-self: center;
  margin-top: 8px;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  background: linear-gradient(135deg, rgba(255,255,255,0.94), rgba(247,243,235,0.92));
  border-radius: 20px;
  box-shadow: 0 16px 36px rgba(88, 78, 67, 0.08);
}

.action-bar-copy {
  min-width: 0;
}

.action-bar-text {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.action-bar-btn {
  width: auto;
  min-width: 220px;
  white-space: nowrap;
}
.action-bar-progress {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.action-bar-progress-value {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1.35rem;
}

.completed-banner {
  text-align: center;
  padding: 22px 24px;
  border: 1px solid rgba(127, 157, 135, 0.24);
  background: rgba(127, 157, 135, 0.08);
  border-radius: 20px;
}

.completed-banner-text {
  margin: 10px 0 0;
  color: var(--text-secondary);
}

/* Creation */
.creation-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}
.creation-layout.has-preview {
  align-items: stretch;
}
.creation-card {
  padding: 22px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: auto;
}

.creation-card-ai {
  justify-content: space-between;
}

.creation-card-ai.analysis-ready {
  justify-content: flex-start;
}

.creation-card-ai .creation-header {
  text-align: left;
}

.creation-card-ai .ai-visual {
  flex: 1;
  min-height: 160px;
  margin-top: 12px;
  margin-bottom: 12px;
}

.creation-card-ai .ai-visual.ai-visual-loading {
  gap: 14px;
}

.creation-card-ai.analysis-ready .ai-visual {
  flex: 0 0 auto;
  min-height: 112px;
  margin-top: 18px;
  gap: 14px;
}

.creation-card-ai .ai-visual.ai-visual-compact {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.creation-card-ai .nd-btn {
  margin-top: 12px;
  width: 100%;
  align-self: stretch;
}

.ai-visual-caption {
  margin: 0;
  text-align: center;
  max-width: 320px;
  line-height: 1.6;
}

.creation-inline-processing {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.12);
  border: 1px solid rgba(194, 169, 120, 0.18);
}

.analysis-bridge {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(247, 242, 233, 0.88));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.analysis-bridge-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.analysis-bridge-calories {
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--text-main);
}

.analysis-bridge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.analysis-bridge-tag {
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid rgba(194, 169, 120, 0.22);
  background: rgba(255, 255, 255, 0.7);
  font-size: 0.76rem;
  color: var(--text-main);
}

.analysis-bridge-macros {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 12px;
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.creation-card-preview {
  justify-content: flex-start;
  max-height: min(720px, calc(100vh - 180px));
  overflow: hidden;
}

.preview-actions {
  flex-shrink: 0;
}

.preview-meals {
  display: grid;
  gap: 14px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
}

.preview-empty {
  padding: 24px 22px;
  border-radius: 18px;
  border: 1px dashed rgba(194, 169, 120, 0.24);
  background: rgba(255, 255, 255, 0.5);
}

.preview-empty-title {
  margin: 0;
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1.05rem;
}

.preview-empty-desc {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.preview-meal-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(88, 78, 67, 0.08);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.preview-meal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(88, 78, 67, 0.12);
}

.preview-meal-items {
  margin-top: 8px;
}

.preview-item-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(88, 78, 67, 0.05);
}

.preview-item-row:last-child {
  border-bottom: none;
}

.preview-tip {
  margin-bottom: 0;
  line-height: 1.7;
  flex-shrink: 0;
}
.creation-card-analysis {
  align-self: start;
  padding: 16px;
  justify-content: flex-start;
  gap: 10px;
}
.creation-layout:not(.has-preview) .creation-card-analysis .diet-upload-zone {
  min-height: 184px;
  max-height: 220px;
}
.creation-layout:not(.has-preview) .creation-card-analysis .idle-hint {
  min-height: 84px;
  padding: 14px 16px;
}
.creation-layout.has-preview .creation-card-analysis {
  align-self: stretch;
  min-height: 100%;
}
.creation-layout.has-preview .creation-card-analysis .diet-upload-zone {
  flex: 1 1 auto;
  min-height: 240px;
  max-height: none;
}
.creation-layout.has-preview .creation-card-analysis.analysis-success .diet-upload-zone {
  flex: 0 0 auto;
  min-height: 210px;
  max-height: 240px;
}
.creation-layout.has-preview .creation-card-analysis .idle-hint,
.creation-layout.has-preview .creation-card-analysis .error-block,
.creation-layout.has-preview .creation-card-analysis .result-block {
  flex: 1 1 auto;
}
.creation-layout.has-preview .creation-card-analysis.analysis-success .result-block {
  flex: 0 0 auto;
}
.creation-layout.has-preview .creation-card-analysis .idle-hint {
  min-height: 132px;
}
.creation-layout.has-preview .creation-card-analysis .result-block .nd-btn {
  margin-top: auto;
}
.creation-card-analysis .card-top {
  margin-bottom: 2px;
}
.diet-upload-zone {
  position: relative;
  aspect-ratio: 16 / 7.2;
  min-height: 136px;
  max-height: 156px;
  border: 3px dashed rgba(194, 169, 120, 0.28);
  background: rgba(248, 250, 252, 0.02);
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}
.diet-upload-zone:hover {
  border-color: var(--primary);
  background: rgba(194, 169, 120, 0.06);
}
.image-frame {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.62);
}
.preview-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.upload-prompt {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
}
.upload-icon-box {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--primary);
  font-size: 18px;
  color: var(--primary);
  font-family: var(--font-heading);
  font-weight: 900;
  transition: transform 0.2s ease;
}
.diet-upload-zone:hover .upload-icon-box {
  transform: scale(1.08);
}
.upload-title {
  font-family: var(--font-heading);
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--text-main);
  text-align: center;
}
.upload-hint.text-caption {
  font-size: 0.8rem;
}
.upload-hint {
  text-align: center;
}
.scan-overlay {
  position: absolute;
  inset: 0;
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
  width: 44px;
  height: 44px;
  border: 3px solid rgba(194, 169, 120, 0.22);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 12px;
}
.scan-label {
  font-family: var(--font-heading);
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--text-main);
  margin-bottom: 8px;
}
.scan-bar {
  width: 132px;
  height: 6px;
  background: rgba(255, 255, 255, 0.82);
  border: 2px solid rgba(194, 169, 120, 0.22);
  margin: 0 auto 6px;
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
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  border: 2px dashed rgba(194, 169, 120, 0.24);
  border-radius: 14px;
  min-height: 64px;
}
.idle-icon {
  font-size: 18px;
  color: var(--text-secondary);
}
.idle-hint p {
  margin: 0;
  line-height: 1.5;
  text-align: center;
}
.error-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid rgba(201, 124, 122, 0.24);
  background: rgba(201, 124, 122, 0.08);
  border-radius: 14px;
}
.error-block p {
  margin: 0;
  color: #b56f6c;
}
.result-block {
  display: grid;
  gap: 10px;
  align-content: start;
}
.analysis-top-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}
.food-tags-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.analysis-meal-target {
  display: grid;
  gap: 6px;
}
.analysis-meal-target-label {
  letter-spacing: 1px;
}
.analysis-meal-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}
.analysis-meal-option {
  border: 1px solid rgba(194, 169, 120, 0.24);
  background: rgba(255,255,255,0.76);
  color: var(--text-secondary);
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 0.78rem;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
}
.analysis-meal-option:hover {
  border-color: var(--primary);
  color: var(--primary);
}
.analysis-meal-option.active {
  border-color: var(--primary);
  background: rgba(194, 169, 120, 0.12);
  color: var(--text-main);
  font-weight: 700;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.food-tag {
  padding: 3px 9px;
  border: 1px solid var(--primary);
  font-family: var(--font-heading);
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--primary);
}
.nutrition-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.nutri-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 6px;
  border: 2px solid rgba(194, 169, 120, 0.18);
  background: rgba(255,255,255,0.7);
}
.nutri-emoji {
  font-size: 16px;
  margin-bottom: 1px;
}
.nutri-val {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 900;
  color: var(--text-main);
}
.nutri-unit,
.nutri-label {
  font-size: 11px;
  color: var(--text-secondary);
}
.calories-box { border-color: var(--primary); }
.protein-box { border-color: rgba(34, 197, 94, 0.24); }
.carbs-box { border-color: rgba(234, 179, 8, 0.24); }
.fat-box { border-color: rgba(148, 163, 184, 0.24); }

/* AI Visual */
.ai-visual {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 96px;
}
.ai-rings {
  position: relative;
  width: 100px; height: 100px;
  display: flex; align-items: center; justify-content: center;
}
.ring {
  position: absolute; border-radius: 50%;
  border: 2px solid rgba(194, 169, 120, 0.28);
  animation: pulse-ring 3s ease-out infinite;
}
.r1 { width: 60px; height: 60px; animation-delay: 0s; }
.r2 { width: 80px; height: 80px; animation-delay: 0.7s; }
.r3 { width: 100px; height: 100px; animation-delay: 1.4s; }
@keyframes pulse-ring {
  0% { transform: scale(0.8); opacity: 0.8; }
  100% { transform: scale(1.3); opacity: 0; }
}
.ai-center-text {
  font-size: 28px; z-index: 1;
}

/* Form */
.add-btn {
  background: none;
  border: 1px solid rgba(194, 169, 120, 0.26);
  color: var(--primary);
  padding: 4px 10px;
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1px;
  cursor: pointer;
  border-radius: 999px;
}
.add-btn:hover { background: rgba(194, 169, 120, 0.08); }
.remove-btn {
  background: none;
  border: 1px solid rgba(201, 167, 107, 0.28);
  color: var(--warning);
  padding: 4px 8px;
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  cursor: pointer;
  border-radius: 999px;
}
.remove-icon-btn {
  background: none;
  border: 1px solid rgba(201, 124, 122, 0.26);
  color: #b56f6c;
  width: 26px; height: 26px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; border-radius: 999px; font-size: 11px;
}
.add-item-btn {
  background: none; border: none;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 11px; letter-spacing: 1px;
  cursor: pointer; padding: 4px 0;
}
.add-item-btn:hover { color: var(--primary); }

/* Knowledge Section */
.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.knowledge-card {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
}
.knowledge-icon {
  font-size: 24px;
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 10px;
}
.knowledge-card .text-heading { line-height: 1.3; }
.knowledge-card > p { line-height: 1.55; }
.knowledge-detail {
  margin-top: 12px;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  padding-top: 10px;
}
.detail-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 5px 0;
}
.detail-row > :last-child {
  text-align: right;
}

/* Recommendations Section */
.recommendations-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.rec-card {
  padding: 18px;
}
.rec-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rec-badge {
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1px;
  padding: 3px 10px;
  border-radius: 2px;
  font-weight: 700;
}
.badge-cut {
  background: rgba(34,197,94,0.15);
  color: #22C55E;
  border: 1px solid rgba(34,197,94,0.3);
}
.badge-bulk {
  background: rgba(249,115,22,0.15);
  color: #F97316;
  border: 1px solid rgba(249,115,22,0.3);
}
.badge-maintain {
  background: rgba(59,130,246,0.15);
  color: #3B82F6;
  border: 1px solid rgba(59,130,246,0.3);
}
.rec-macros {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.macro-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: rgba(248,250,252,0.03);
  border-radius: 4px;
}
.rec-tips {
  border-top: 1px solid rgba(248,250,252,0.08);
  padding-top: 12px;
}
.rec-tips p {
  margin: 4px 0;
}

/* Utils */
.w-full { width: 100%; }
.block { display: block; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }

@media (max-width: 1024px) {
  .plan-layout { grid-template-columns: 1fr; }
  .plan-aside {
    position: static;
  }
  .creation-layout { grid-template-columns: 1fr; }
  .diet-plan-head { flex-direction: column; }
  .diet-plan-actions {
    width: 100%;
    align-items: stretch;
  }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .meals-list,
  .plan-panels { grid-template-columns: 1fr; }
  .knowledge-grid { grid-template-columns: repeat(2, 1fr); }
  .recommendations-grid { grid-template-columns: 1fr; }
}
@media (max-width: 1280px) {
  .meals-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .plan-panels {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .action-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .action-bar-btn {
    width: 100%;
    min-width: 0;
  }
  .summary-grid { grid-template-columns: 1fr; }
  .knowledge-grid { grid-template-columns: 1fr; }
  .knowledge-card .text-heading,
  .knowledge-card > p {
    min-height: auto;
  }
}

.edit-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.edit-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.edit-meal-card {
  padding: 16px;
  border-left: 3px solid #22C55E;
}

.edit-meal-header {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.edit-meal-header .nd-input {
  flex: 1;
}

.edit-item-row {
  display: grid;
  grid-template-columns: 3fr 2fr 2fr auto;
  gap: 6px;
  align-items: center;
}

@media (max-width: 768px) {
  .edit-summary-grid { grid-template-columns: repeat(2, 1fr); }
  .edit-item-row { grid-template-columns: 1fr 1fr; }
  .preview-meal-head { align-items: flex-start; flex-direction: column; }
  .analysis-top-grid { grid-template-columns: 1fr; }
  .analysis-meal-options { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .nutrition-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>




