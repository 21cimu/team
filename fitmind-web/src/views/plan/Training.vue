<template>
  <div class="training-content">
    <!-- ── Topbar ── -->
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">训练计划</span>
          <strong class="topbar-page-title">{{ pageTitle }}</strong>
          <span class="topbar-page-meta">{{ todayDate }}</span>
        </div>
        <div class="topbar-page-actions header-actions">
          <template v-if="executingPlan && plan">
            <div class="ai-intensity-control">
              <span class="text-caption">AI 干预深度</span>
              <div class="intensity-btns">
                <button v-for="level in ['LOW', 'MED', 'HIGH']" :key="level"
                  :class="['intensity-btn', { active: aiIntensity === level }]"
                  @click="aiIntensity = level">{{ level === 'LOW' ? '低' : level === 'MED' ? '中' : '高' }}</button>
              </div>
            </div>
            <button class="nd-btn" @click="stopExecuting">← 返回计划页</button>
            <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
          </template>
          <template v-else-if="viewingPlanDetail && plan && !isEditing">
            <button class="nd-btn" @click="closePlanDetail">← 返回计划页</button>
            <button v-if="plan.status === 0" class="nd-btn primary" @click="startExecuting">▶ 开始执行</button>
            <button class="nd-btn" @click="confirmEditPlan" :disabled="loading">修改计划</button>
            <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
          </template>
          <template v-else-if="viewingPlanDetail && isEditing">
            <button class="nd-btn primary" @click="saveEdit" :disabled="editLoading">
              {{ editLoading ? '[ 保存中... ]' : '保存修改' }}
            </button>
            <button class="nd-btn" @click="cancelEdit">取消</button>
          </template>
        </div>
      </div>
    </Teleport>

    <!-- ── Page Header ── -->
    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">{{ pageKicker }}</div>
        <h1 class="text-display-md text-primary">{{ pageHeading }}</h1>
        <p class="text-secondary mt-xs">{{ todayDate }}</p>
      </div>
      <div class="header-actions">
        <template v-if="executingPlan && plan">
          <div class="ai-intensity-control">
            <span class="text-caption">AI 干预深度</span>
            <div class="intensity-btns">
              <button v-for="level in ['LOW', 'MED', 'HIGH']" :key="level"
                  :class="['intensity-btn', { active: aiIntensity === level }]"
                  @click="aiIntensity = level">{{ level === 'LOW' ? '低' : level === 'MED' ? '中' : '高' }}</button>
              </div>
          </div>
          <button class="nd-btn" @click="stopExecuting">← 返回计划页</button>
          <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
        </template>
        <template v-else-if="viewingPlanDetail && plan && !isEditing">
          <button class="nd-btn" @click="closePlanDetail">← 返回计划页</button>
          <button v-if="plan.status === 0" class="nd-btn primary" @click="startExecuting">▶ 开始执行</button>
          <button class="nd-btn" @click="confirmEditPlan" :disabled="loading">修改计划</button>
          <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
        </template>
        <template v-else-if="viewingPlanDetail && isEditing">
          <button class="nd-btn primary" @click="saveEdit" :disabled="editLoading">
            {{ editLoading ? '[ 保存中... ]' : '保存修改' }}
          </button>
          <button class="nd-btn" @click="cancelEdit">取消</button>
        </template>
      </div>
    </div>

    <!-- ════════════════ 执行模式 ════════════════ -->
    <div v-if="executingPlan && plan && !loading" class="plan-view">
      <div class="mode-inline-actions mb-lg">
        <button class="nd-btn" @click="stopExecuting">← 返回计划页</button>
        <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
      </div>

      <!-- Summary -->
      <div class="nd-card summary-card mb-2xl">
        <div class="summary-grid">
          <div class="summary-item">
            <div class="text-caption text-secondary">训练重点</div>
            <div class="text-heading mt-xs text-primary">{{ plan.targetMuscleGroup || planData.targetMuscleGroup || '未设定' }}</div>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <div class="text-caption text-secondary">预计用时</div>
            <div class="text-display-md mt-xs">{{ plan.estimatedDuration || planData.estimatedDuration || 0 }}<span class="text-label ml-xs">分钟</span></div>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <div class="text-caption text-secondary">动作总数</div>
            <div class="text-display-md mt-xs">{{ planData.exercises?.length || 0 }}<span class="text-label ml-xs">个</span></div>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <div class="text-caption text-secondary">当前状态</div>
            <div class="text-heading mt-xs" :class="plan.status === 1 ? 'text-success' : 'text-warning'">
              {{ plan.status === 1 ? '✓ 已完成' : '○ 待执行' }}
            </div>
          </div>
        </div>
      </div>

      <!-- Exercise Matrix List -->
      <div class="text-label mb-lg">
        <span>[ 动作序列 ]</span>
      </div>
      <div class="exercises-list mb-2xl">
        <div v-for="(ex, index) in planData.exercises" :key="index"
          class="nd-card matrix-card mb-lg"
          :class="{ 'card-complete': isExerciseComplete(index) }">
          <div class="matrix-header">
            <div class="matrix-title">
              <span class="ex-code">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="ex-sep">::</span>
              <span class="ex-cn-name">{{ ex.name }}</span>
              <span class="type-badge" :class="'type-' + getExerciseType(ex)">{{ getTypeLabel(getExerciseType(ex)) }}</span>
            </div>
            <div class="matrix-meta">
              <template v-if="getExerciseType(ex) === 'strength'">
                <span class="meta-tag">{{ ex.sets }} 组</span>
                <span class="meta-tag">{{ ex.reps }} 次/目标</span>
                <CountdownTimer :totalSeconds="ex.restSeconds || 60" :size="44" :strokeWidth="3" @complete="onRestComplete(index)" />
              </template>
              <template v-else-if="getExerciseType(ex) === 'cardio'">
                <span class="meta-tag">{{ ex.duration || 30 }} 分钟</span>
                <span class="meta-tag">{{ ex.distance || '-' }} 公里</span>
                <span class="meta-tag">配速 {{ formatPace(ex.pace) }}</span>
              </template>
              <template v-else-if="getExerciseType(ex) === 'flexibility'">
                <span class="meta-tag">{{ ex.holdTime || 30 }}秒 保持</span>
                <span class="meta-tag">{{ ex.rounds || 3 }} 轮</span>
              </template>
            </div>
          </div>

          <!-- Strength -->
          <div v-if="getExerciseType(ex) === 'strength'" class="data-matrix">
            <div v-for="s in ex.sets" :key="s" class="set-cell"
              :class="{ 'set-done': getSetData(index, s).done, 'set-active': activeSet.exIndex === index && activeSet.setNum === s, 'set-failed': getSetData(index, s).done && getSetData(index, s).reps !== null && getSetData(index, s).reps! < ex.reps }"
              @click="activateSet(index, s)">
              <div class="set-label">第{{ s }}组</div>
              <input v-if="activeSet.exIndex === index && activeSet.setNum === s" v-model.number="getSetData(index, s).weight" type="number" class="set-input weight-input" placeholder="公斤" step="0.5" min="0" @touchstart.stop />
              <span v-else class="set-value">{{ getSetData(index, s).weight || '-' }}</span>
              <input v-if="activeSet.exIndex === index && activeSet.setNum === s" v-model.number="getSetData(index, s).reps" type="number" class="set-input reps-input" placeholder="次数" min="0" @touchstart.stop />
              <span v-else class="set-value">{{ getSetData(index, s).reps || '-' }}</span>
              <button class="set-check" :class="{ checked: getSetData(index, s).done }" @click.stop="confirmSet(index, s, ex)">
                {{ getSetData(index, s).done ? '✓' : '○' }}
              </button>
            </div>
          </div>

          <!-- Cardio -->
          <div v-else-if="getExerciseType(ex) === 'cardio'" class="cardio-entry">
            <div class="cardio-fields">
              <div class="cardio-field">
                <label class="field-label">实际时长</label>
                <div class="field-input-group">
                  <input
                    v-model.number="getCardioData(index).actualDuration"
                    type="number"
                    class="nd-input small"
                    placeholder="分钟"
                    min="0"
                    @input="syncCardioPace(index)"
                  />
                  <span class="field-unit">分钟</span>
                </div>
              </div>
              <div class="cardio-field">
                <label class="field-label">实际距离</label>
                <div class="field-input-group">
                  <input
                    v-model.number="getCardioData(index).actualDistance"
                    type="number"
                    class="nd-input small"
                    placeholder="公里"
                    min="0"
                    step="0.1"
                    @input="syncCardioPace(index)"
                  />
                  <span class="field-unit">公里</span>
                </div>
              </div>
              <div class="cardio-field">
                <label class="field-label">实际配速</label>
                <div class="field-input-group">
                  <input :value="formatPace(getCardioData(index).actualPace || undefined)" type="text" class="nd-input small" placeholder="自动计算" readonly />
                  <span class="field-unit">分/公里</span>
                </div>
              </div>
            </div>
            <button class="cardio-confirm-btn" :class="{ 'cardio-done': getCardioData(index).done }" @click="confirmCardioSet(index, ex)">
              {{ getCardioData(index).done ? '✓ 已完成' : '确认完成' }}
            </button>
          </div>

          <!-- Flexibility -->
          <div v-else-if="getExerciseType(ex) === 'flexibility'" class="flex-entry">
            <div class="flex-rounds">
              <div v-for="r in (ex.rounds || 3)" :key="r" class="round-cell"
                :class="{ 'round-done': getFlexData(index).completedRounds >= r, 'round-active': getFlexData(index).currentRound === r && !getFlexData(index).done }"
                @click="activateFlexRound(index, r, ex)">
                <div class="round-label">第{{ r }}轮</div>
                <div class="round-hold">
                  <template v-if="getFlexData(index).currentRound === r && !getFlexData(index).done">
                    <input v-model.number="getFlexData(index).actualHoldTime" type="number" class="set-input" placeholder="秒" min="0" />
                  </template>
                  <template v-else>
                    <span class="set-value">{{ getFlexData(index).completedRounds >= r ? (getFlexData(index).holdTimes[r-1] || ex.holdTime || 30) + 's' : '-' }}</span>
                  </template>
                </div>
                <button class="set-check" :class="{ checked: getFlexData(index).completedRounds >= r }" @click.stop="confirmFlexRound(index, r, ex)">
                  {{ getFlexData(index).completedRounds >= r ? '✓' : '○' }}
                </button>
              </div>
            </div>
            <div v-if="getFlexData(index).done" class="flex-complete-tag">✓ 全部轮次完成</div>
          </div>

          <!-- AI Terminal -->
          <div v-if="getIntervention(index)" class="ai-terminal">
            <span class="terminal-prompt">&gt;</span>
            <span class="terminal-text">{{ getIntervention(index) }}</span>
          </div>
        </div>
      </div>

      <!-- Progress Bar -->
      <div class="completion-progress mb-2xl" v-if="planData.exercises?.length">
        <div class="flex-between mb-sm">
          <span class="text-caption">协议完成度</span>
          <span class="text-caption text-primary">{{ completedUnitsCount }} / {{ totalUnitsCount }}</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: (completedUnitsCount / totalUnitsCount * 100) + '%' }"></div>
        </div>
      </div>

      <!-- AI Intervention Log -->
      <div v-if="interventionLog.length > 0" class="nd-card intervention-log mb-2xl">
        <div class="card-header mb-md">
          <span class="text-label">AI 干预日志</span>
          <button class="clear-log-btn" @click="interventionLog = []">清除</button>
        </div>
        <div class="log-entries">
          <div v-for="(entry, i) in interventionLog" :key="i" class="log-entry">
            <span class="log-time">{{ entry.time }}</span>
            <span class="log-msg">{{ entry.message }}</span>
          </div>
        </div>
      </div>

      <!-- Check In / Done -->
      <div class="action-bar" v-if="plan.status === 0">
        <button class="nd-btn primary w-full" @click="handleCheckIn" :disabled="checkinLoading">
          {{ checkinLoading ? '[ 处理中... ]' : '标记协议为已完成' }}
        </button>
      </div>
      <div class="completed-banner" v-else>
        <span class="text-label text-success">✓ 协议已完成 — 执行得很好</span>
        <button class="nd-btn" @click="stopExecuting">← 返回计划页</button>
        <button class="nd-btn primary" @click="resetPlan">创建新计划</button>
      </div>
    </div>

    <!-- ════════════════ 详情模式 ════════════════ -->
    <div v-else-if="viewingPlanDetail && plan && !loading" class="plan-detail-view">
      <div class="mode-inline-actions">
        <button class="nd-btn" @click="closePlanDetail">← 返回计划页</button>
        <button v-if="!isEditing && plan.status === 0" class="nd-btn primary" @click="startExecuting">▶ 开始执行</button>
        <button v-if="!isEditing" class="nd-btn" @click="confirmEditPlan">修改计划</button>
        <button class="nd-btn" @click="resetPlan" :disabled="loading">重置计划</button>
      </div>

      <div v-if="!isEditing" class="nd-card detail-hero-card">
        <div class="detail-hero-copy">
          <div class="detail-kicker">[ 计划详情 ]</div>
          <h2 class="detail-title">{{ plan.planName || planData.targetMuscleGroup || '今日训练计划' }}</h2>
          <p class="detail-desc">
            {{ plan.status === 1
              ? '当前计划已完成，这里展示的是本次训练的结构与动作清单。'
              : '先检查动作结构与参数，再决定是否进入执行模式。' }}
          </p>
        </div>
        <div class="detail-hero-stats">
          <div class="detail-stat">
            <span class="detail-stat-label">训练重点</span>
            <strong class="detail-stat-value">{{ plan.targetMuscleGroup || planData.targetMuscleGroup || '未设定' }}</strong>
          </div>
          <div class="detail-stat">
            <span class="detail-stat-label">预计时长</span>
            <strong class="detail-stat-value">{{ plan.estimatedDuration || planData.estimatedDuration || 0 }} 分钟</strong>
          </div>
          <div class="detail-stat">
            <span class="detail-stat-label">当前状态</span>
            <strong class="detail-stat-value" :class="plan.status === 1 ? 'text-success' : 'text-warning'">
              {{ plan.status === 1 ? '✓ 已完成' : '○ 待执行' }}
            </strong>
          </div>
        </div>
      </div>
      <div v-else class="nd-card summary-card mb-sm">
        <div class="edit-summary-grid">
          <div class="edit-field">
            <label class="text-caption text-secondary">计划名称</label>
            <input v-model="editForm.planName" type="text" class="nd-input" placeholder="输入计划名称" />
          </div>
          <div class="edit-field">
            <label class="text-caption text-secondary">训练重点</label>
            <input v-model="editForm.targetMuscleGroup" type="text" class="nd-input" placeholder="例如：胸部、背部、腿部" />
          </div>
          <div class="edit-field">
            <label class="text-caption text-secondary">预计时长（分钟）</label>
            <input v-model.number="editForm.estimatedDuration" type="number" class="nd-input" placeholder="45" min="1" />
          </div>
        </div>
      </div>

      <div class="detail-section-head">
        <span class="text-label">{{ isEditing ? '[ 计划编辑 ]' : '[ 动作清单 ]' }}</span>
        <span class="text-caption text-secondary">{{ planData.exercises?.length || 0 }} 个动作</span>
      </div>

        <div v-if="!isEditing && planData.exercises?.length" class="nd-card detail-progress-card">
          <div class="detail-progress-head">
            <div class="detail-progress-copy">
              <span class="text-caption text-secondary">训练进度</span>
              <strong class="detail-progress-value">{{ completedUnitsCount }} / {{ totalUnitsCount }}</strong>
            </div>
            <span class="detail-progress-rate">{{ completionPercent }}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: completionPercent + '%' }"></div>
          </div>
          <div v-if="plan.status === 0" class="detail-progress-actions">
            <button class="nd-btn primary" @click="handleCheckIn" :disabled="checkinLoading">
              {{ checkinLoading ? '[ 处理中... ]' : '标记协议为已完成' }}
            </button>
          </div>
          <div v-else class="detail-progress-done">
            <span class="text-label text-success">✓ 当前计划已完成</span>
          </div>
        </div>

        <div v-if="isEditing" class="exercises-edit-list mb-2xl">
          <div v-for="(ex, idx) in editForm.exercises" :key="idx" class="nd-card edit-exercise-card mb-lg">
            <div class="edit-ex-header">
              <div class="edit-ex-num">{{ String(idx + 1).padStart(2, '0') }}</div>
              <div class="edit-ex-type-select">
                <button v-for="t in ['strength', 'cardio', 'flexibility']" :key="t" class="type-select-btn"
                  :class="{ active: ex.type === t, ['type-' + t]: true }" @click="ex.type = t">{{ getTypeLabel(t) }}</button>
              </div>
              <button class="remove-btn" @click="removeEditExercise(idx)">✕</button>
            </div>
            <div class="edit-ex-name">
              <input v-model="ex.name" type="text" class="nd-input" placeholder="动作名称" />
            </div>
            <div class="edit-ex-params">
              <template v-if="ex.type === 'strength'">
                <div class="edit-param"><label class="text-caption">组数</label><input v-model.number="ex.sets" type="number" class="nd-input small" min="1" /></div>
                <div class="edit-param"><label class="text-caption">次数</label><input v-model.number="ex.reps" type="number" class="nd-input small" min="1" /></div>
                <div class="edit-param"><label class="text-caption">休息（秒）</label><input v-model.number="ex.restSeconds" type="number" class="nd-input small" min="0" /></div>
              </template>
              <template v-else-if="ex.type === 'cardio'">
                <div class="edit-param"><label class="text-caption">时长（分钟）</label><input v-model.number="ex.duration" type="number" class="nd-input small" min="1" /></div>
                <div class="edit-param"><label class="text-caption">距离（公里）</label><input v-model.number="ex.distance" type="number" class="nd-input small" min="0" step="0.1" /></div>
                <div class="edit-param"><label class="text-caption">配速（秒/公里）</label><input v-model.number="ex.pace" type="number" class="nd-input small" min="0" /></div>
                <div class="edit-param"><label class="text-caption">坡度</label><input v-model.number="ex.incline" type="number" class="nd-input small" min="0" max="15" /></div>
              </template>
              <template v-else-if="ex.type === 'flexibility'">
                <div class="edit-param"><label class="text-caption">保持（秒）</label><input v-model.number="ex.holdTime" type="number" class="nd-input small" min="1" /></div>
                <div class="edit-param"><label class="text-caption">轮数</label><input v-model.number="ex.rounds" type="number" class="nd-input small" min="1" /></div>
              </template>
            </div>
          </div>
          <button class="nd-btn w-full" @click="addEditExercise">+ 添加动作</button>
        </div>

        <div v-else-if="planData.exercises?.length" class="detail-ex-list">
          <article
            v-for="(ex, index) in planData.exercises"
            :key="index"
            class="nd-card detail-ex-card"
            @mouseenter="handleDetailExerciseHover(ex, $event)"
            @mousemove="handleDetailExerciseMove($event)"
            @mouseleave="handleDetailExerciseLeave"
          >
            <div class="detail-ex-header">
              <div class="detail-ex-title">
              <span class="detail-ex-num">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="detail-ex-copy">
                <strong class="detail-ex-name">{{ ex.name }}</strong>
                <span class="detail-ex-focus">{{ getExerciseFocus(ex) }}</span>
              </div>
            </div>
            <span class="detail-ex-tag" :class="'type-' + getExerciseType(ex)">{{ getTypeLabel(getExerciseType(ex)) }}</span>
            </div>
            <div class="detail-ex-body">
              <div class="detail-ex-prescription">{{ getExercisePrescription(ex) }}</div>

              <div v-if="plan.status === 0" class="detail-progress-followup">
                <template v-if="getExerciseType(ex) === 'strength'">
                  <div class="detail-set-list">
                    <div v-for="s in ex.sets" :key="s" class="detail-set-row" :class="{ done: getSetData(index, s).done }">
                      <span class="detail-row-label">第{{ s }}组</span>
                      <span class="detail-row-status">{{ getSetData(index, s).done ? '已确认' : '待确认' }}</span>
                      <button class="detail-check-btn" :class="{ done: getSetData(index, s).done }" @click="confirmSet(index, s, ex)">
                        {{ getSetData(index, s).done ? '已完成' : '确认' }}
                      </button>
                    </div>
                  </div>
                </template>

                <template v-else-if="getExerciseType(ex) === 'cardio'">
                  <div class="detail-cardio-grid">
                    <label class="detail-input-field">
                      <span class="detail-row-label">实际时长</span>
                      <input
                        v-model.number="getCardioData(index).actualDuration"
                        type="number"
                        class="nd-input"
                        placeholder="分钟"
                        min="0"
                        @input="syncCardioPace(index)"
                      />
                    </label>
                    <label class="detail-input-field">
                      <span class="detail-row-label">实际距离</span>
                      <input
                        v-model.number="getCardioData(index).actualDistance"
                        type="number"
                        class="nd-input"
                        placeholder="公里"
                        min="0"
                        step="0.1"
                        @input="syncCardioPace(index)"
                      />
                    </label>
                    <label class="detail-input-field">
                      <span class="detail-row-label">实际配速</span>
                      <input :value="formatPace(getCardioData(index).actualPace || undefined)" type="text" class="nd-input" placeholder="自动计算" readonly />
                    </label>
                  </div>
                  <button class="detail-check-btn detail-cardio-btn" :class="{ done: getCardioData(index).done }" @click="confirmCardioSet(index, ex)">
                    {{ getCardioData(index).done ? '✓ 已完成' : '确认完成有氧' }}
                  </button>
                </template>

                <template v-else>
                  <div class="detail-flex-panel">
                    <div class="detail-flex-summary">
                      <span>已完成 {{ getFlexData(index).completedRounds }} / {{ ex.rounds || 3 }} 轮</span>
                      <span v-if="getFlexData(index).done" class="text-success">✓ 全部轮次完成</span>
                    </div>
                    <div class="detail-set-list">
                      <div
                        v-for="r in (ex.rounds || 3)"
                        :key="r"
                        class="detail-set-row"
                        :class="{ done: getFlexData(index).completedRounds >= r }"
                      >
                        <span class="detail-row-label">第{{ r }}轮</span>
                        <span class="detail-row-status">
                          {{ getFlexData(index).completedRounds >= r ? '已确认' : (getFlexData(index).currentRound === r ? '当前轮次' : '待确认') }}
                        </span>
                        <button
                          class="detail-check-btn"
                          :class="{ done: getFlexData(index).completedRounds >= r }"
                          :disabled="getFlexData(index).completedRounds < r - 1 || getFlexData(index).completedRounds >= r"
                          @click="confirmFlexRound(index, r, ex)"
                        >
                          {{ getFlexData(index).completedRounds >= r ? '已完成' : '确认' }}
                        </button>
                      </div>
                    </div>
                    <div class="detail-round-tags">
                      <span
                        v-for="r in (ex.rounds || 3)"
                        :key="r"
                        class="detail-round-tag"
                        :class="{ done: getFlexData(index).completedRounds >= r, active: getFlexData(index).currentRound === r && !getFlexData(index).done }"
                      >
                        {{ r }}
                      </span>
                    </div>
                  </div>
                </template>
              </div>

              <div v-if="getIntervention(index)" class="detail-inline-tip">
                {{ getIntervention(index) }}
              </div>
            </div>
          </article>
        </div>

        <Teleport to="body">
          <div
            v-if="hoveredDetailExercisePreview?.imageUrl"
            class="detail-ex-preview-floating"
            :style="{ left: detailExercisePreviewPos.x + 'px', top: detailExercisePreviewPos.y + 'px' }"
          >
            <div class="detail-ex-preview-tooltip">
              <div class="detail-ex-preview-image-wrap">
                <img
                  class="detail-ex-preview-image"
                  :src="hoveredDetailExercisePreview.imageUrl"
                  :alt="hoveredDetailExercisePreview.displayName || hoveredDetailExercisePreview.name"
                  loading="lazy"
                />
              </div>
              <div class="detail-ex-preview-body">
                <div class="detail-ex-preview-title-row">
                  <span class="detail-ex-preview-title">{{ hoveredDetailExercisePreview.displayName || hoveredDetailExercisePreview.name }}</span>
                  <span v-if="hoveredDetailExercisePreview.difficulty" class="detail-ex-preview-badge">
                    {{ hoveredDetailExercisePreview.difficulty }}
                  </span>
                </div>
                <div v-if="hoveredDetailExercisePreview.displayPrimaryMuscle || hoveredDetailExercisePreview.primaryMuscle" class="detail-ex-preview-muscle">
                  {{ hoveredDetailExercisePreview.displayPrimaryMuscle || hoveredDetailExercisePreview.primaryMuscle }}
                </div>
                <div v-if="hoveredDetailExercisePreview.displayTarget || hoveredDetailExercisePreview.target" class="detail-ex-preview-target">
                  {{ hoveredDetailExercisePreview.displayTarget || hoveredDetailExercisePreview.target }}
                </div>
              </div>
            </div>
          </div>
        </Teleport>

        <div v-if="interventionLog.length > 0" class="nd-card intervention-log">
          <div class="card-header mb-md">
            <span class="text-label">AI 干预日志</span>
            <button class="clear-log-btn" @click="interventionLog = []">清除</button>
          </div>
          <div class="log-entries">
            <div v-for="(entry, i) in interventionLog" :key="i" class="log-entry">
              <span class="log-time">{{ entry.time }}</span>
              <span class="log-msg">{{ entry.message }}</span>
            </div>
          </div>
        </div>

        <div v-else class="nd-card detail-empty">
          <span class="text-caption text-secondary">当前计划暂无动作数据。</span>
        </div>
      </div>

    <!-- ════════════════ 创建 + 预览模式 ════════════════ -->
    <div v-else-if="!executingPlan" class="creation-scene">
        <!-- Hero -->
        <div class="creation-hero nd-card">
          <div class="creation-hero-copy">
            <div style="font-size:0.7rem;font-weight:700;letter-spacing:0.18em;text-transform:uppercase;color:var(--text-secondary);margin-bottom:10px;">[ 今日计划 ]</div>
            <h1 style="font-family:var(--font-heading);font-size:1.75rem;font-weight:800;color:var(--text-main);margin:0 0 10px;letter-spacing:-0.02em;">
              {{ plan ? '今日训练计划已就绪' : '今天还没有训练计划？' }}
            </h1>
            <p style="font-size:0.88rem;color:var(--text-secondary);line-height:1.7;max-width:420px;margin:0;">
              {{ plan ? '计划已生成。可以直接开始执行，也可以先查看完整动作详情后再决定。' : '选择 AI 智能生成或手动从动作图谱中创建属于你的训练方案，开始今日训练。' }}
            </p>
          </div>
          <div class="creation-hero-stats">
            <div class="hero-stat">
              <span style="display:block;font-size:0.7rem;color:var(--text-muted);letter-spacing:0.06em;margin-bottom:5px;">今日日期</span>
              <strong style="font-size:0.95rem;font-weight:700;color:var(--text-main);">{{ todayDate }}</strong>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span style="display:block;font-size:0.7rem;color:var(--text-muted);letter-spacing:0.06em;margin-bottom:5px;">{{ plan ? '动作数量' : '建议训练时长' }}</span>
              <strong style="font-size:0.95rem;font-weight:700;color:var(--text-main);">
                {{ plan ? (planData.exercises?.length || 0) : 45 }}
                <small style="font-size:0.7rem;color:var(--text-secondary);font-weight:400;">{{ plan ? ' 个' : ' 分钟' }}</small>
              </strong>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span style="display:block;font-size:0.7rem;color:var(--text-muted);letter-spacing:0.06em;margin-bottom:5px;">{{ plan ? '当前状态' : '可选动作类型' }}</span>
              <strong style="font-size:0.95rem;font-weight:700;" :style="plan ? (plan.status===1 ? 'color:#22C55E' : 'color:#F97316') : 'color:var(--text-main)'">
                {{ plan ? (plan.status===1 ? '✓ 已完成' : '○ 待执行') : '力量 · 有氧 · 柔韧' }}
              </strong>
            </div>
          </div>
        </div>

        <!-- 选择区 -->
        <div class="creation-choices">
          <!-- AI 生成卡片 -->
          <div class="nd-card choice-card choice-card-ai">
            <div class="choice-badge">[ AI 生成 ]</div>
            <h2 class="choice-title">智能生成计划</h2>
            <p class="choice-desc">AI 分析你的身体指标、历史记录和当前状态，自动生成一套包含力量、有氧与柔韧的个性化混合计划。</p>
            <div class="choice-features">
              <div class="feat-row"><span class="feat-dot"></span>根据身体指标智能调配</div>
              <div class="feat-row"><span class="feat-dot"></span>自动平衡三种训练类型</div>
              <div class="feat-row"><span class="feat-dot"></span>支持 AI 干预实时反馈</div>
            </div>
            <div class="choice-visual" :class="{ 'choice-visual-loading': loading }">
              <div class="ai-rings">
                <div class="ring r1"></div>
                <div class="ring r2"></div>
                <div class="ring r3"></div>
                <div class="ai-center-text">AI</div>
              </div>
              <div v-if="loading" class="choice-inline-processing">
                <div class="processing-ring"></div>
                <span class="text-caption text-warning">AI 正在生成训练计划...</span>
              </div>
            </div>
            <button class="nd-btn primary w-full choice-btn" @click="handleGenerate" :disabled="loading">
              {{ plan ? '⟳ 重新生成计划' : '⚡ 立即生成 AI 计划' }}
            </button>
          </div>

          <!-- 手动构建卡片 -->
          <div class="nd-card choice-card choice-card-manual">
            <div class="choice-badge choice-badge-manual">[ 手动构建 ]</div>
            <h2 class="choice-title">自定义训练方案</h2>
            <p class="choice-desc">从动作图谱中精选动作，自由搭配力量、有氧、柔韧训练，创建完全符合你今日目标的专属计划。</p>
            <div class="choice-features">
              <div class="feat-row"><span class="feat-dot feat-dot-manual"></span>从图谱中自由选择动作</div>
              <div class="feat-row"><span class="feat-dot feat-dot-manual"></span>灵活配置组数次数参数</div>
              <div class="feat-row"><span class="feat-dot feat-dot-manual"></span>支持力量 · 有氧 · 柔韧</div>
            </div>
            <div class="choice-visual manual-type-showcase">
              <div class="type-block type-strength">
                <span class="type-block-label">力量</span>
                <div class="type-block-bars">
                  <div class="tbar tbar-1"></div>
                  <div class="tbar tbar-2"></div>
                  <div class="tbar tbar-3"></div>
                </div>
              </div>
              <div class="type-block type-cardio">
                <span class="type-block-label">有氧</span>
                <svg class="type-wave" viewBox="0 0 80 32" fill="none">
                  <path d="M0 16 Q10 4 20 16 Q30 28 40 16 Q50 4 60 16 Q70 28 80 16" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round"/>
                </svg>
              </div>
              <div class="type-block type-flexibility">
                <span class="type-block-label">柔韧</span>
                <div class="type-flex-dots">
                  <div class="fdot"></div><div class="fdot"></div>
                  <div class="fdot"></div><div class="fdot"></div>
                </div>
              </div>
            </div>
            <button class="nd-btn primary w-full choice-btn" @click="openManualModal">
              ✎ 手动创建计划
            </button>
          </div>
        </div>

        <!-- ── 计划预览卡片（生成后展示）── -->
        <Transition name="preview-slide">
          <div v-if="plan" class="nd-card plan-preview-card">
            <!-- 卡片头部 -->
            <div class="preview-card-header">
              <div class="preview-card-info">
                <div class="preview-card-kicker">[ 今日计划已就绪 ]</div>
                <div class="preview-card-title">{{ plan.planName || planData.targetMuscleGroup || '今日训练计划' }}</div>
                <div class="preview-card-meta">
                  <span>{{ planData.exercises?.length || 0 }} 个动作</span>
                  <span class="meta-dot">·</span>
                  <span>{{ plan.estimatedDuration || planData.estimatedDuration || 0 }} 分钟</span>
                  <span class="meta-dot">·</span>
                  <span :class="plan.status === 1 ? 'text-success' : 'text-warning'">
                    {{ plan.status === 1 ? '✓ 已完成' : '○ 待执行' }}
                  </span>
                </div>
              </div>
              <div class="preview-card-actions">
                <button class="nd-btn" @click="resetPlan">重置</button>
                <button class="nd-btn" @click="openPlanDetail">查看详情</button>
                <button v-if="plan.status === 0" class="nd-btn primary" @click="startExecuting">▶ 开始执行</button>
              </div>
            </div>

            <!-- 动作列表（固定高度 + 内部滚动） -->
            <div class="preview-ex-list">
              <div v-if="!planData.exercises?.length" class="preview-empty">
                <span class="text-caption text-secondary">暂无动作数据</span>
              </div>
              <div v-for="(ex, i) in planData.exercises" :key="i"
                class="preview-ex-row"
                :class="{ 'preview-ex-done': isExerciseComplete(i) }">
                <span class="preview-ex-num">{{ String(i + 1).padStart(2, '0') }}</span>
                <span class="preview-ex-name">{{ ex.name }}</span>
                <span class="preview-ex-tag" :class="'type-' + getExerciseType(ex)">{{ getTypeLabel(getExerciseType(ex)) }}</span>
                <span class="preview-ex-params">
                  <template v-if="getExerciseType(ex) === 'strength'">{{ ex.sets }} 组 × {{ ex.reps }} 次</template>
                  <template v-else-if="getExerciseType(ex) === 'cardio'">{{ ex.duration || 30 }} 分钟</template>
                  <template v-else>{{ ex.holdTime || 30 }}s × {{ ex.rounds || 3 }} 轮</template>
                </span>
                <span v-if="isExerciseComplete(i)" class="preview-ex-check">✓</span>
              </div>
            </div>
          </div>
        </Transition>
    </div>

    <!-- ── 手动构建弹窗 ── -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showManualModal" class="modal-overlay" @click.self="closeManualModal">
          <div class="modal-box">
            <div class="modal-header">
              <div class="modal-title-area">
                <span class="text-caption text-primary">[ 手动构建 ]</span>
                <strong class="modal-title">
                  {{ manualStep === 1 ? '创建训练分组' : (manualForm.planName || manualForm.target || '添加动作') }}
                </strong>
              </div>
              <div class="modal-header-actions">
                <div class="modal-steps">
                  <span :class="['step-dot', { active: manualStep >= 1, done: manualStep > 1 }]">1</span>
                  <span class="step-line"></span>
                  <span :class="['step-dot', { active: manualStep >= 2 }]">2</span>
                </div>
                <button class="modal-close-btn" @click="closeManualModal">✕</button>
              </div>
            </div>

            <div v-if="manualStep === 1" class="modal-body step1-body">
              <div class="field-full">
                <label>计划名称</label>
                <input v-model="manualForm.planName" type="text" class="nd-input" placeholder="例如：胸背日训练" autofocus />
              </div>
              <div>
                <label>目标肌肉群</label>
                <input v-model="manualForm.target" type="text" class="nd-input" placeholder="胸部、背部、腿部" />
              </div>
              <div>
                <label>预计时长（分钟）</label>
                <input v-model.number="manualForm.duration" type="number" class="nd-input" placeholder="45" min="1" />
              </div>
            </div>

            <div v-else class="modal-body step2-body">
              <div class="modal-split">
                <div class="modal-atlas">
                  <div class="atlas-search-bar">
                    <div class="modal-search-wrap">
                      <svg class="modal-search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
                      <input v-model="atlasSearch" type="text" class="nd-input modal-search-input" placeholder="搜索动作..." @input="debouncedFetchAtlas" />
                    </div>
                    <div class="atlas-filters">
                      <button v-for="cat in ['全部', '力量', '有氧', '柔韧']" :key="cat" class="filter-btn"
                        :class="{ active: atlasCategory === cat }" @click="setAtlasCategory(cat)">{{ cat }}</button>
                    </div>
                  </div>
                  <div class="atlas-list">
                    <div v-if="atlasLoading" class="atlas-state"><span class="text-caption text-secondary">加载中...</span></div>
                    <div v-else-if="atlasExercises.length === 0" class="atlas-state"><span class="text-caption text-secondary">未找到匹配的动作</span></div>
                    <button v-else v-for="ex in atlasExercises" :key="ex.id || ex.name" class="atlas-item"
                      :class="{ 'atlas-item-added': isExAddedFromAtlas(ex) }" @click="addFromAtlas(ex)">
                      <span class="atlas-item-name">{{ ex.displayName || ex.name }}</span>
                      <span class="atlas-item-tag" :class="'type-' + (ex.exerciseType || 'strength')">{{ getTypeLabel(ex.exerciseType || 'strength') }}</span>
                      <span class="atlas-item-plus">{{ isExAddedFromAtlas(ex) ? '✓' : '+' }}</span>
                    </button>
                  </div>
                </div>

                <div class="modal-selected">
                  <div class="selected-header">
                    <span class="text-caption">已选动作</span>
                    <span class="text-caption text-primary">{{ manualForm.exercises.length }} 个</span>
                  </div>
                  <div v-if="manualForm.exercises.length === 0" class="atlas-state">
                    <p class="text-caption text-secondary">← 从左侧选择动作</p>
                  </div>
                  <div v-else class="selected-list">
                    <div v-for="(ex, idx) in manualForm.exercises" :key="idx" class="selected-exercise-card">
                      <button class="sel-ex-remove" @click="removeExercise(idx)">✕</button>
                      <div class="sel-ex-img-col">
                        <div class="sel-ex-img-wrap">
                          <img v-if="ex.imageUrl" :src="ex.imageUrl" :alt="ex.name" class="sel-ex-img" />
                          <div v-else class="sel-ex-img-placeholder">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M6.5 6.5h11M6.5 9.5h11M9 12.5c0 1.657 1.343 3 3 3s3-1.343 3-3-1.343-3-3-3-3 1.343-3 3z"/></svg>
                          </div>
                        </div>
                      </div>
                      <div class="sel-ex-detail-col">
                        <div class="sel-ex-header">
                          <span class="sel-ex-num">{{ String(idx + 1).padStart(2, '0') }}</span>
                          <span class="sel-ex-name" :title="ex.name">{{ ex.name }}</span>
                          <span class="sel-ex-badge" :class="'type-' + ex.type">{{ getTypeLabel(ex.type) }}</span>
                        </div>
                        <div class="sel-ex-params">
                          <template v-if="ex.type === 'strength'">
                            <div class="param-field"><label>组数</label><input v-model.number="ex.sets" type="number" class="nd-input mini w-xs" min="1" /></div>
                            <div class="param-field"><label>次数</label><input v-model.number="ex.reps" type="number" class="nd-input mini w-xs" min="1" /></div>
                            <div class="param-field"><label>休息(s)</label><input v-model.number="ex.restSeconds" type="number" class="nd-input mini w-md" min="0" /></div>
                          </template>
                          <template v-else-if="ex.type === 'cardio'">
                            <div class="param-field"><label>时长(m)</label><input v-model.number="ex.duration" type="number" class="nd-input mini w-sm" min="1" /></div>
                            <div class="param-field"><label>距离(km)</label><input v-model.number="ex.distance" type="number" class="nd-input mini w-sm" min="0" step="0.1" /></div>
                          </template>
                          <template v-else-if="ex.type === 'flexibility'">
                            <div class="param-field"><label>保持(s)</label><input v-model.number="ex.holdTime" type="number" class="nd-input mini w-sm" min="1" /></div>
                            <div class="param-field"><label>轮数</label><input v-model.number="ex.rounds" type="number" class="nd-input mini w-xs" min="1" /></div>
                          </template>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="modal-footer">
              <button v-if="manualStep === 1" class="nd-btn" @click="closeManualModal">取消</button>
              <button v-else class="nd-btn" @click="manualStep = 1">&larr; 上一步</button>
              <div class="modal-footer-right">
                <button v-if="manualStep === 1" class="nd-btn primary" @click="goToManualStep2"
                  :disabled="!manualForm.planName.trim() && !manualForm.target.trim()">
                  下一步：添加动作 →
                </button>
                <button v-else class="nd-btn primary" @click="handleManualCreate"
                  :disabled="manualLoading || manualForm.exercises.length === 0">
                  {{ manualLoading ? '[ 保存中... ]' : '创建训练计划' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTodayTrainingPlan, generateTrainingPlan, checkInTrainingPlan, createManualTrainingPlan, updateTrainingPlan } from '../../api/ai'
import { getExercises } from '../../api/exercise'
import { useWeather } from '../../composables/useWeather'
import { localizeExercise } from '../../utils/exerciseLocalization'
import { toWeatherContextPayload } from '../../utils/aiContext'
import { ElMessage, ElMessageBox } from 'element-plus'
import CountdownTimer from '../../components/CountdownTimer.vue'

type ExerciseType = 'strength' | 'cardio' | 'flexibility'
const CHAT_TRAINING_DRAFT_KEY = 'fitmind-chat-training-draft'
const TARGET_MUSCLE_GROUPS = ['胸部', '背部', '腿部', '肩部', '手臂', '腹部', '核心', '臀部', '全身']

interface SetData {
  weight: number | null
  reps: number | null
  done: boolean
}

interface CardioSetData {
  actualDuration: number | null
  actualDistance: number | null
  actualPace: number | null
  done: boolean
}

interface FlexSetData {
  currentRound: number
  completedRounds: number
  actualHoldTime: number | null
  holdTimes: number[]
  done: boolean
}

interface InterventionEntry {
  time: string
  message: string
  exIndex: number
}

interface ManualExercise {
  name: string
  type: ExerciseType
  sets: number
  reps: number
  restSeconds: number
  duration: number
  distance: number
  pace: number
  incline: number
  holdTime: number
  rounds: number
  imageUrl?: string
}

interface ChatTrainingDraft {
  prompt: string
  response: string
  targetMuscleGroup?: string
}

const TYPE_LABELS: Record<ExerciseType, string> = {
  strength: '力量',
  cardio: '有氧',
  flexibility: '柔韧'
}

const EXERCISE_NAME_MAP: Record<string, string> = {
  '杠铃卧推': 'BARBELL BENCH PRESS',
  '哑铃卧推': 'DUMBBELL BENCH PRESS',
  '上斜哑铃卧推': 'INCLINE DUMBBELL PRESS',
  '下斜杠铃卧推': 'DECLINE BARBELL PRESS',
  '蝴蝶机夹胸': 'PEC DECK FLY',
  '龙门架夹胸': 'CABLE CROSSOVER',
  '俯卧撑': 'PUSH-UP',
  '双杠臂屈伸': 'DIP',
  '杠铃深蹲': 'BARBELL SQUAT',
  '前蹲': 'FRONT SQUAT',
  '腿举': 'LEG PRESS',
  '腿弯举': 'LEG CURL',
  '腿屈伸': 'LEG EXTENSION',
  '弓步蹲': 'LUNGE',
  '保加利亚分腿蹲': 'BULGARIAN SPLIT SQUAT',
  '罗马尼亚硬拉': 'ROMANIAN DEADLIFT',
  '硬拉': 'DEADLIFT',
  '杠铃划船': 'BARBELL ROW',
  '哑铃划船': 'DUMBBELL ROW',
  '引体向上': 'PULL-UP',
  '高位下拉': 'LAT PULLDOWN',
  '坐姿划船': 'SEATED ROW',
  '面拉': 'FACE PULL',
  '杠铃推举': 'OVERHEAD PRESS',
  '哑铃推举': 'DUMBBELL SHOULDER PRESS',
  '侧平举': 'LATERAL RAISE',
  '前平举': 'FRONT RAISE',
  '反向飞鸟': 'REVERSE FLY',
  '杠铃弯举': 'BARBELL CURL',
  '哑铃弯举': 'DUMBBELL CURL',
  '锤式弯举': 'HAMMER CURL',
  '集中弯举': 'CONCENTRATION CURL',
  '牧师椅弯举': 'PREACHER CURL',
  '三头下压': 'TRICEP PUSHDOWN',
  '仰卧臂屈伸': 'SKULL CRUSHER',
  '颈后臂屈伸': 'OVERHEAD TRICEP EXTENSION',
  '绳索下压': 'CABLE TRICEP KICKBACK',
  '卷腹': 'CRUNCH',
  '反向卷腹': 'REVERSE CRUNCH',
  '悬垂举腿': 'HANGING LEG RAISE',
  '平板支撑': 'PLANK',
  '俄罗斯转体': 'RUSSIAN TWIST',
  '山羊挺身': 'HYPEREXTENSION',
  '提踵': 'CALF RAISE',
  '单臂哑铃集中弯举': 'CONCENTRATION CURL',
  '窄距卧推': 'CLOSE-GRIP BENCH PRESS',
  '宽距引体向上': 'WIDE-GRIP PULL-UP',
  '窄距引体向上': 'CLOSE-GRIP PULL-UP',
  '坐姿哑铃肩推': 'SEATED DUMBBELL PRESS',
  '阿诺德推举': 'ARNOLD PRESS',
  '绳索侧平举': 'CABLE LATERAL RAISE',
  '单臂划船': 'ONE-ARM ROW',
  '器械推胸': 'MACHINE CHEST PRESS',
  '器械夹胸': 'MACHINE PEC FLY',
  '腿外展': 'HIP ABDUCTION',
  '腿内收': 'HIP ADDUCTION',
  '坐姿提踵': 'SEATED CALF RAISE',
  '站姿提踵': 'STANDING CALF RAISE',
  '农夫走': 'FARMER WALK',
  '壶铃摆荡': 'KETTLEBELL SWING',
  '战绳': 'BATTLE ROPE',
  '跳箱': 'BOX JUMP',
  '药球旋转抛': 'MEDICINE BALL ROTATIONAL THROW',
  '跑步': 'RUNNING',
  '慢跑': 'JOGGING',
  '快走': 'BRISK WALKING',
  '骑行': 'CYCLING',
  '游泳': 'SWIMMING',
  '跳绳': 'JUMP ROPE',
  '划船机': 'ROWING MACHINE',
  '椭圆机': 'ELLIPTICAL',
  '登山机': 'STAIR CLIMBER',
  '腿部拉伸': 'LEG STRETCH',
  '髋屈肌拉伸': 'HIP FLEXOR STRETCH',
  '腘绳肌拉伸': 'HAMSTRING STRETCH',
  '股四头肌拉伸': 'QUAD STRETCH',
  '肩部拉伸': 'SHOULDER STRETCH',
  '胸部拉伸': 'CHEST STRETCH',
  '背部拉伸': 'BACK STRETCH',
  '臀部拉伸': 'GLUTE STRETCH',
  '小腿拉伸': 'CALF STRETCH',
  '猫牛式': 'CAT-COW STRETCH',
  '下犬式': 'DOWNWARD DOG',
  '婴儿式': 'CHILD POSE',
  '蝴蝶式': 'BUTTERFLY STRETCH',
  '鸽子式': 'PIGEON POSE'
}

const CARDIO_KEYWORDS = [
  // 中文
  '跑步', '慢跑', '快走', '骑行', '游泳', '跳绳', '划船机', '椭圆机', '登山机', '跑', '走', '骑', '游',
  // 英文
  'running', 'jogging', 'cycling', 'swimming', 'jump rope', 'rowing machine', 'elliptical', 'stair climber', 'cardio', 'treadmill'
]
const FLEX_KEYWORDS = [
  // 中文
  '拉伸', '伸展', '猫牛式', '下犬式', '婴儿式', '蝴蝶式', '鸽子式', '瑜伽', '柔韧', '泡沫轴',
  // 英文
  'stretch', 'yoga', 'cat-cow', 'downward', 'warrior', 'pigeon', 'child pose', 'cobra', 'flexibility', 'mobility', 'foam roll'
]

const plan = ref<any>(null)
const userReset = ref(false)  // 用户主动重置标志，防止 fetchPlan 覆盖
const executingPlan = ref(false)  // 是否进入执行模式
const viewingPlanDetail = ref(false)
const loading = ref(false)
const manualLoading = ref(false)
const checkinLoading = ref(false)
const aiIntensity = ref<'LOW' | 'MED' | 'HIGH'>('MED')
const isEditing = ref(false)
const editLoading = ref(false)
const editForm = reactive({
  planName: '',
  targetMuscleGroup: '',
  estimatedDuration: 45,
  exercises: [] as ManualExercise[]
})

const todayDate = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
const { weather, fetchWeather: refreshWeather } = useWeather()
const route = useRoute()
const router = useRouter()

const pageKicker = computed(() => {
  if (executingPlan.value) return '[ 训练协议 ]'
  if (viewingPlanDetail.value) return '[ 计划详情 ]'
  return '[ 今日计划 ]'
})

const pageHeading = computed(() => {
  if (executingPlan.value) return '执行协议'
  if (viewingPlanDetail.value) return '训练详情'
  return '训练方案'
})

const pageTitle = computed(() => {
  if (executingPlan.value) return '执行方案'
  if (viewingPlanDetail.value) return '计划详情'
  return '今日协议'
})

const manualForm = reactive({
  planName: '',
  target: '',
  duration: 45,
  exercises: [] as ManualExercise[]
})

// 动作图谱相关状态
const manualStep = ref(1)
const showManualModal = ref(false)
const atlasExercises = ref<any[]>([])
const atlasSearch = ref('')
const atlasCategory = ref('全部')
const atlasLoading = ref(false)
const allAtlasExercises = ref<any[]>([])  // 全量缓存，只拉一次
let atlasTimer: ReturnType<typeof setTimeout> | null = null
const hoveredDetailExercisePreview = ref<any | null>(null)
const detailExercisePreviewPos = ref({ x: 0, y: 0 })
let detailExerciseHoverToken = 0

const setRecords = ref<Record<string, SetData>>({})
const cardioRecords = ref<Record<string, CardioSetData>>({})
const flexRecords = ref<Record<string, FlexSetData>>({})
const activeSet = ref<{ exIndex: number; setNum: number }>({ exIndex: -1, setNum: -1 })
const interventions = ref<Record<number, string>>({})
const interventionLog = ref<InterventionEntry[]>([])

const getExerciseType = (ex: any): ExerciseType => {
  if (ex.type && ['strength', 'cardio', 'flexibility'].includes(ex.type)) {
    return ex.type
  }
  const name = `${ex.name || ''} ${ex.rawName || ''}`.toLowerCase()
  if (CARDIO_KEYWORDS.some(k => name.includes(k))) return 'cardio'
  if (FLEX_KEYWORDS.some(k => name.includes(k))) return 'flexibility'
  return 'strength'
}

const getTypeLabel = (type: ExerciseType): string => TYPE_LABELS[type]

const getExerciseFocus = (ex: any): string => {
  const type = getExerciseType(ex)
  if (type === 'strength') return '力量刺激'
  if (type === 'cardio') return '有氧耐力'
  return '柔韧恢复'
}

const getExercisePrescription = (ex: any): string => {
  const type = getExerciseType(ex)
  if (type === 'strength') {
    return `${ex.sets || 0} 组 × ${ex.reps || 0} 次 · 休息 ${ex.restSeconds || 60} 秒`
  }
  if (type === 'cardio') {
    return `${ex.duration || 30} 分钟 · ${ex.distance || '-'} 公里 · 配速 ${formatPace(ex.pace)}`
  }
  return `保持 ${ex.holdTime || 30} 秒 · ${ex.rounds || 3} 轮`
}

const normalizeExerciseLookupKey = (value?: string | null) =>
  String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[（）()]/g, '')
    .replace(/[\-_/]/g, ' ')
    .replace(/\s+/g, ' ')

const resolveExercisePreview = async (exercise: any) => {
  if (exercise?.imageUrl) {
    return exercise
  }

  if (allAtlasExercises.value.length === 0) {
    await fetchAtlasExercises()
  }

  const lookupKeys = [
    exercise?.rawName,
    exercise?.name,
    exercise?.displayName,
    exercise?.displayNameEn
  ]
    .map(normalizeExerciseLookupKey)
    .filter(Boolean)

  if (lookupKeys.length === 0) return null

  const matchByKeys = (item: any) => {
    const candidates = [
      item?.name,
      item?.displayName,
      item?.displayNameEn
    ]
      .map(normalizeExerciseLookupKey)
      .filter(Boolean)

    return candidates.some((candidate) => lookupKeys.includes(candidate))
  }

  const exactMatch = allAtlasExercises.value.find(matchByKeys)
  if (exactMatch) return exactMatch

  return allAtlasExercises.value.find((item: any) => {
    const candidates = [
      item?.name,
      item?.displayName,
      item?.displayNameEn
    ]
      .map(normalizeExerciseLookupKey)
      .filter(Boolean)

    return candidates.some((candidate) =>
      lookupKeys.some((key) => candidate.includes(key) || key.includes(candidate))
    )
  }) || null
}

const updateDetailExercisePreviewPos = (element: HTMLElement) => {
  const rect = element.getBoundingClientRect()
  const cardWidth = 288
  const cardHeight = 220
  const gap = 16
  const fitsRight = rect.right + gap + cardWidth <= window.innerWidth - 12
  const maxY = Math.max(12, window.innerHeight - cardHeight - 12)

  detailExercisePreviewPos.value = {
    x: fitsRight ? rect.right + gap : Math.max(12, rect.left - cardWidth - gap),
    y: Math.min(Math.max(12, rect.top + rect.height / 2 - cardHeight / 2), maxY)
  }
}

const handleDetailExerciseHover = async (exercise: any, event: MouseEvent) => {
  const token = ++detailExerciseHoverToken
  updateDetailExercisePreviewPos(event.currentTarget as HTMLElement)
  const preview = await resolveExercisePreview(exercise)
  if (token === detailExerciseHoverToken) {
    hoveredDetailExercisePreview.value = preview
  }
}

const handleDetailExerciseMove = (event: MouseEvent) => {
  updateDetailExercisePreviewPos(event.currentTarget as HTMLElement)
}

const handleDetailExerciseLeave = () => {
  detailExerciseHoverToken += 1
  hoveredDetailExercisePreview.value = null
}

const formatPace = (pace: number | undefined): string => {
  if (!pace) return '--:--'
  const min = Math.floor(pace / 60)
  const sec = pace % 60
  return `${min}'${String(sec).padStart(2, '0')}"`
}

const getSetKey = (exIndex: number, setNum: number) => `${exIndex}-${setNum}`

const getSetData = (exIndex: number, setNum: number): SetData => {
  const key = getSetKey(exIndex, setNum)
  if (!setRecords.value[key]) {
    setRecords.value[key] = { weight: null, reps: null, done: false }
  }
  return setRecords.value[key]
}

const getCardioData = (exIndex: number): CardioSetData => {
  const key = `${exIndex}-cardio`
  if (!cardioRecords.value[key]) {
    cardioRecords.value[key] = { actualDuration: null, actualDistance: null, actualPace: null, done: false }
  }
  return cardioRecords.value[key]
}

const syncCardioPace = (exIndex: number) => {
  const data = getCardioData(exIndex)
  const duration = Number(data.actualDuration)
  const distance = Number(data.actualDistance)

  if (Number.isFinite(duration) && Number.isFinite(distance) && duration > 0 && distance > 0) {
    data.actualPace = Math.round((duration * 60) / distance)
    return
  }

  data.actualPace = null
}

const getFlexData = (exIndex: number): FlexSetData => {
  const key = `${exIndex}-flex`
  if (!flexRecords.value[key]) {
    const ex = planData.value.exercises?.[exIndex]
    const totalRounds = ex?.rounds || 3
    flexRecords.value[key] = { currentRound: 1, completedRounds: 0, actualHoldTime: null, holdTimes: new Array(totalRounds).fill(0), done: false }
  }
  return flexRecords.value[key]
}

const activateSet = (exIndex: number, setNum: number) => {
  if (getSetData(exIndex, setNum).done) return
  activeSet.value = { exIndex, setNum }
}

const confirmSet = (exIndex: number, setNum: number, exercise: any) => {
  const data = getSetData(exIndex, setNum)
  if (data.done) {
    data.done = false
    return
  }
  data.done = true
  if (data.reps === null) data.reps = exercise.reps
  activeSet.value = { exIndex: -1, setNum: -1 }
  analyzeStrengthPerformance(exIndex, setNum, exercise)
}

const confirmCardioSet = (exIndex: number, exercise: any) => {
  const data = getCardioData(exIndex)
  if (data.done) {
    data.done = false
    return
  }
  syncCardioPace(exIndex)
  data.done = true
  analyzeCardioPerformance(exIndex, exercise)
}

const activateFlexRound = (exIndex: number, round: number, exercise: any) => {
  const data = getFlexData(exIndex)
  if (data.completedRounds >= round) return
  data.currentRound = round
  data.actualHoldTime = exercise.holdTime || 30
}

const confirmFlexRound = (exIndex: number, round: number, exercise: any) => {
  const data = getFlexData(exIndex)
  if (data.completedRounds >= round) return
  if (!data.done && round !== data.currentRound) return
  const holdTime = data.actualHoldTime || exercise.holdTime || 30
  data.holdTimes[round - 1] = holdTime
  data.completedRounds = round
  data.actualHoldTime = null
  if (round >= (exercise.rounds || 3)) {
    data.done = true
  } else {
    data.currentRound = round + 1
  }
  analyzeFlexPerformance(exIndex, round, exercise)
}

const isExerciseComplete = (exIndex: number) => {
  const ex = planData.value.exercises?.[exIndex]
  if (!ex) return false
  const type = getExerciseType(ex)
  if (type === 'strength') {
    for (let s = 1; s <= ex.sets; s++) {
      if (!getSetData(exIndex, s).done) return false
    }
    return true
  } else if (type === 'cardio') {
    return getCardioData(exIndex).done
  } else if (type === 'flexibility') {
    return getFlexData(exIndex).done
  }
  return false
}

const getEnglishName = (cnName: string): string => {
  return EXERCISE_NAME_MAP[cnName] || cnName.toUpperCase().replace(/\s+/g, '_')
}

const completedUnitsCount = computed(() => {
  let count = 0
  const exercises = planData.value.exercises || []
  for (let i = 0; i < exercises.length; i++) {
    const type = getExerciseType(exercises[i])
    if (type === 'strength') {
      for (let s = 1; s <= exercises[i].sets; s++) {
        if (getSetData(i, s).done) count++
      }
    } else if (type === 'cardio') {
      if (getCardioData(i).done) count++
    } else if (type === 'flexibility') {
      count += getFlexData(i).completedRounds
    }
  }
  return count
})

const totalUnitsCount = computed(() => {
  return (planData.value.exercises || []).reduce((sum: number, ex: any) => {
    const type = getExerciseType(ex)
    if (type === 'strength') return sum + (ex.sets || 0)
    if (type === 'cardio') return sum + 1
    if (type === 'flexibility') return sum + (ex.rounds || 3)
    return sum
  }, 0)
})

const completionPercent = computed(() => {
  if (!totalUnitsCount.value) return 0
  return Math.round((completedUnitsCount.value / totalUnitsCount.value) * 100)
})

const analyzeStrengthPerformance = (exIndex: number, setNum: number, exercise: any) => {
  const current = getSetData(exIndex, setNum)
  const targetReps = exercise.reps
  const intensityLevel = aiIntensity.value

  if (intensityLevel === 'LOW') return

  const messages: string[] = []

  if (current.reps !== null && current.reps < targetReps) {
    const deficit = targetReps - current.reps
    const ratio = current.reps / targetReps
    const ratioPercent = (ratio * 100).toFixed(0)

    if (ratio < 0.5) {
      if (current.weight) {
        const suggestedWeight = (current.weight * 0.75).toFixed(1)
        messages.push(`[系统研判] 肌肉耐力显著衰退，完成率 ${ratioPercent}%。下一组已自动下调目标重量至 ${suggestedWeight}公斤。`)
      } else {
        messages.push(`[系统研判] 肌肉耐力显著衰退，完成率 ${ratioPercent}%。建议大幅降低负重。`)
      }
    } else {
      if (current.weight) {
        const suggestedWeight = (current.weight * 0.9).toFixed(1)
        messages.push(`[系统研判] 次数不足 ${deficit}次，建议下调重量至 ${suggestedWeight}公斤 以保证训练容量。`)
      } else {
        messages.push(`[系统研判] 次数不足 ${deficit}次，建议适当降低负重以保证训练容量。`)
      }
    }
  }

  if (setNum >= 2 && intensityLevel !== 'LOW') {
    const prev = getSetData(exIndex, setNum - 1)
    if (prev.done && current.reps !== null && prev.reps !== null) {
      const dropPercent = ((prev.reps - current.reps) / prev.reps) * 100
      if (dropPercent > 30) {
        messages.push(`[系统研判] 连续组间衰退 ${dropPercent.toFixed(0)}%，肌肉疲劳积累过快。建议延长组间休息至 ${Math.round((exercise.restSeconds || 60) * 1.5)}s。`)
      }
    }
  }

  if (setNum >= 3 && intensityLevel === 'HIGH') {
    const allReps: number[] = []
    for (let s = 1; s <= setNum; s++) {
      const d = getSetData(exIndex, s)
      if (d.done && d.reps !== null) allReps.push(d.reps)
    }
    if (allReps.length >= 3) {
      const lastThree = allReps.slice(-3)
      const isConsistentDecline = lastThree[0] > lastThree[1] && lastThree[1] > lastThree[2]
      if (isConsistentDecline) {
        messages.push(`[系统研判] 检测到连续3组递减趋势，肌肉已进入深度疲劳区间。建议减少剩余组数或切换至轻量激活模式。`)
      }
    }
  }

  if (current.reps !== null && current.reps > targetReps * 1.3 && intensityLevel !== 'LOW') {
    const overPercent = (current.reps / targetReps * 100).toFixed(0)
    if (current.weight) {
      const suggestedWeight = (current.weight * 1.1).toFixed(1)
      messages.push(`[系统研判] 超额完成 ${overPercent}%，能力储备充足。建议增重至 ${suggestedWeight}公斤 以维持刺激强度。`)
    } else {
      messages.push(`[系统研判] 超额完成 ${overPercent}%，能力储备充足。建议适当增重以维持刺激强度。`)
    }
  }

  if (setNum >= exercise.sets && current.reps !== null && current.reps >= targetReps && intensityLevel !== 'LOW') {
    messages.push(`[系统研判] 最后一组轻松完成，可追加力竭组以最大化肌肉刺激。`)
  }

  if (messages.length > 0) {
    const msg = messages[0]
    interventions.value[exIndex] = msg
    const now = new Date()
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
    interventionLog.value.unshift({
      time: timeStr,
      message: `[${getEnglishName(exercise.rawName || exercise.name)}] ${msg}`,
      exIndex
    })
    if (interventionLog.value.length > 50) {
      interventionLog.value = interventionLog.value.slice(0, 50)
    }
  } else {
    delete interventions.value[exIndex]
  }
}

const analyzeCardioPerformance = (exIndex: number, exercise: any) => {
  const data = getCardioData(exIndex)
  const intensityLevel = aiIntensity.value
  if (intensityLevel === 'LOW') return

  const messages: string[] = []
  const targetDuration = exercise.duration || 30
  const targetPace = exercise.pace || 360
  const targetDistance = exercise.distance || 5

  if (data.actualDuration !== null) {
    const durationRatio = data.actualDuration / targetDuration
    if (durationRatio < 0.7) {
      const newDuration = Math.round(targetDuration * 0.8)
      messages.push(`[系统研判] 有氧耐力不足，仅完成目标时长的 ${(durationRatio * 100).toFixed(0)}%。建议下调目标时长至 ${newDuration} 分钟，逐步建立有氧基础。`)
    } else if (durationRatio > 1.1) {
      const newDuration = Math.round(targetDuration * 1.15)
      messages.push(`[系统研判] 超额完成有氧时长 ${(durationRatio * 100).toFixed(0)}%，心肺能力充足。建议延长至 ${newDuration} 分钟以持续提升有氧阈值。`)
    }
  }

  if (data.actualPace !== null && targetPace > 0) {
    const paceDiff = data.actualPace - targetPace
    if (paceDiff > 30) {
      const newPace = Math.round(targetPace * 1.1)
      messages.push(`[系统研判] 配速偏慢 ${Math.round(paceDiff)}秒/km，建议调整目标配速至 ${formatPace(newPace)}。`)
    } else if (paceDiff < -15) {
      const newPace = Math.round(targetPace * 0.95)
      messages.push(`[系统研判] 配速优于目标 ${Math.round(Math.abs(paceDiff))}秒/km，建议提升目标配速至 ${formatPace(newPace)}。`)
    }
  }

  if (data.actualDistance !== null && targetDistance > 0) {
    const distRatio = data.actualDistance / targetDistance
    if (distRatio < 0.6) {
      const newDistance = (targetDistance * 0.75).toFixed(1)
      messages.push(`[系统研判] 距离完成率 ${(distRatio * 100).toFixed(0)}%，建议缩短目标距离至 ${newDistance}km 以保证训练质量。`)
    }
  }

  if (messages.length > 0) {
    interventions.value[exIndex] = messages[0]
    const now = new Date()
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
    interventionLog.value.unshift({
      time: timeStr,
      message: `[${getEnglishName(exercise.rawName || exercise.name)}] ${messages[0]}`,
      exIndex
    })
    if (interventionLog.value.length > 50) {
      interventionLog.value = interventionLog.value.slice(0, 50)
    }
  } else {
    delete interventions.value[exIndex]
  }
}

const analyzeFlexPerformance = (exIndex: number, round: number, exercise: any) => {
  const data = getFlexData(exIndex)
  const intensityLevel = aiIntensity.value
  if (intensityLevel === 'LOW') return

  const messages: string[] = []
  const targetHoldTime = exercise.holdTime || 30
  const actualHoldTime = data.holdTimes[round - 1] || targetHoldTime

  if (actualHoldTime < targetHoldTime * 0.6) {
    const newHoldTime = Math.round(targetHoldTime * 0.7)
    messages.push(`[系统研判] 保持时长不足，完成率 ${((actualHoldTime / targetHoldTime) * 100).toFixed(0)}%。建议缩短保持时间至 ${newHoldTime} 秒，避免过度拉伸导致损伤。`)
  } else if (actualHoldTime >= targetHoldTime) {
    const newHoldTime = Math.round(targetHoldTime * 1.2)
    messages.push(`[系统研判] 保持时长达标 ${((actualHoldTime / targetHoldTime) * 100).toFixed(0)}%，柔韧性良好。建议延长保持时间至 ${newHoldTime} 秒以深化拉伸效果。`)
  }

  if (round >= 2) {
    const prevHoldTime = data.holdTimes[round - 2] || targetHoldTime
    if (prevHoldTime > 0 && actualHoldTime < prevHoldTime * 0.7) {
      messages.push(`[系统研判] 轮间保持时长衰退明显，肌肉柔韧性下降。建议减少轮数，确保每轮质量。`)
    }
  }

  if (messages.length > 0) {
    interventions.value[exIndex] = messages[0]
    const now = new Date()
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
    interventionLog.value.unshift({
      time: timeStr,
      message: `[${getEnglishName(exercise.rawName || exercise.name)}] ${messages[0]}`,
      exIndex
    })
    if (interventionLog.value.length > 50) {
      interventionLog.value = interventionLog.value.slice(0, 50)
    }
  } else {
    delete interventions.value[exIndex]
  }
}

const getIntervention = (exIndex: number): string => {
  return interventions.value[exIndex] || ''
}

const onRestComplete = (exIndex: number) => {
  const ex = planData.value.exercises?.[exIndex]
  if (!ex) return
  if (aiIntensity.value !== 'LOW') {
    interventions.value[exIndex] = `[系统提示] 休息结束，准备执行 ${getEnglishName(ex.rawName || ex.name)} 下一组。`
  }
}

const setManualExerciseType = (idx: number, type: ExerciseType) => {
  manualForm.exercises[idx].type = type
}

const addExercise = () => {
  manualForm.exercises.push({ name: '', type: 'strength', sets: 3, reps: 10, restSeconds: 60, duration: 30, distance: 5, pace: 360, incline: 0, holdTime: 30, rounds: 3 })
}

const removeExercise = (idx: number) => {
  manualForm.exercises.splice(idx, 1)
}

// ── 动作图谱相关函数 ─────────────────────────────────────────────────
const openManualModal = () => {
  manualStep.value = 1
  manualForm.planName = ''
  manualForm.target = ''
  manualForm.duration = 45
  manualForm.exercises = []
  atlasSearch.value = ''
  atlasCategory.value = '全部'
  showManualModal.value = true
  document.body.style.overflow = 'hidden'
}

const closeManualModal = () => {
  showManualModal.value = false
  document.body.style.overflow = ''
}

const goToManualStep2 = async () => {
  manualStep.value = 2
  if (allAtlasExercises.value.length === 0) {
    await fetchAtlasExercises()
  } else {
    filterAtlasExercises()
  }
}

// 只做一次全量拉取并缓存
const fetchAtlasExercises = async () => {
  atlasLoading.value = true
  try {
    const params: Record<string, string> = {}
    const res = await getExercises(params)
    const raw = Array.isArray(res) ? res : (res as any)?.list || (res as any)?.data || []
    allAtlasExercises.value = raw.map((item: any) => {
      const loc = localizeExercise(item)
      const exType = getExerciseType({ name: (loc.displayName || '') + ' ' + (loc.name || ''), type: loc.category || loc.type })
      return { ...loc, exerciseType: exType }
    })
    filterAtlasExercises()
  } catch (e) {
    allAtlasExercises.value = []
    atlasExercises.value = []
  } finally {
    atlasLoading.value = false
  }
}

// 前端分类 + 搜索过滤
const filterAtlasExercises = () => {
  const keyword = atlasSearch.value.trim().toLowerCase()
  const cat = atlasCategory.value
  atlasExercises.value = allAtlasExercises.value.filter(ex => {
    // 分类过滤（使用预计算的 exerciseType）
    if (cat !== '全部') {
      const catMap: Record<string, string> = { '力量': 'strength', '有氧': 'cardio', '柔韧': 'flexibility' }
      if (ex.exerciseType !== catMap[cat]) return false
    }
    // 关键词搜索
    if (keyword) {
      const haystack = ((ex.displayName || '') + ' ' + (ex.name || '') + ' ' + (ex.displayTarget || '') + ' ' + (ex.target || '')).toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    return true
  })
}

const debouncedFetchAtlas = () => {
  if (atlasTimer) clearTimeout(atlasTimer)
  atlasTimer = setTimeout(filterAtlasExercises, 200)
}

const setAtlasCategory = (cat: string) => {
  atlasCategory.value = cat
  filterAtlasExercises()
}

const isExAddedFromAtlas = (ex: any) => {
  const label = ex.displayName || ex.name || ''
  return manualForm.exercises.some(e => e.name === label)
}

const addFromAtlas = (ex: any) => {
  const label = ex.displayName || ex.name || ''
  if (isExAddedFromAtlas(ex)) {
    const idx = manualForm.exercises.findIndex(e => e.name === label)
    if (idx !== -1) manualForm.exercises.splice(idx, 1)
    return
  }
  // 用中文 displayName 匹配中文关键词，英文 name 匹配英文关键词，两者都传入
  const type = getExerciseType({ name: (ex.displayName || '') + ' ' + (ex.name || ''), type: ex.category || ex.type })
  const defaults: ManualExercise = {
    name: label,
    type,
    sets: 3, reps: 10, restSeconds: 60,
    duration: 30, distance: 5, pace: 360, incline: 0,
    holdTime: 30, rounds: 3,
    imageUrl: ex.imageUrl || ''
  }
  manualForm.exercises.push(defaults)
}

const planData = computed(() => {
  if (plan.value?.content) {
    try {
      const parsed = JSON.parse(plan.value.content)
      const exercises = Array.isArray(parsed?.exercises)
        ? parsed.exercises.map((ex: any) => {
            const localized = localizeExercise(ex)
            return {
              ...ex,
              ...localized,
              rawName: ex?.name || '',
              name: localized.displayName || ex?.name || ''
            }
          })
        : []

      return {
        ...parsed,
        exercises
      }
    } catch (e) {
      return { exercises: [] }
    }
  }
  return { exercises: [] }
})

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

const readChatTrainingDraft = (): ChatTrainingDraft | null => {
  try {
    const raw = localStorage.getItem(CHAT_TRAINING_DRAFT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ChatTrainingDraft
    if (!parsed?.prompt || !parsed?.response) return null
    return parsed
  } catch {
    return null
  }
}

const clearChatTrainingDraft = () => {
  localStorage.removeItem(CHAT_TRAINING_DRAFT_KEY)
}

const getCoachTargetMuscleGroup = () => {
  if (route.query.from !== 'coach' || route.query.autogenerate !== '1') {
    return ''
  }
  const target = Array.isArray(route.query.target) ? route.query.target[0] : route.query.target
  return typeof target === 'string' ? target.trim() : ''
}

const clearCoachQuery = async () => {
  if (route.query.from !== 'coach') return
  await router.replace({ path: route.path })
}

const extractFirstNumber = (text: string, regex: RegExp, fallback: number) => {
  const match = text.match(regex)
  return match ? Number(match[1]) : fallback
}

const extractRangeMax = (text: string, fallback: number) => {
  const rangeMatch = text.match(/(\d+)\s*(?:到|至|\-|~)\s*(\d+)\s*次/)
  if (rangeMatch) return Number(rangeMatch[2])
  const singleMatch = text.match(/(?:每组|做)?\s*(\d+)\s*次/)
  if (singleMatch) return Number(singleMatch[1])
  if (text.includes('力竭')) return fallback
  return fallback
}

const resolveDraftTargetMuscleGroup = (draft: ChatTrainingDraft) => {
  if (draft.targetMuscleGroup?.trim()) return draft.targetMuscleGroup.trim()
  return TARGET_MUSCLE_GROUPS.find(item => draft.prompt.includes(item) || draft.response.includes(item)) || '综合训练'
}

const parseChatExerciseName = (paragraph: string) => {
  const stripped = paragraph
    .replace(/^第[一二三四五六七八九十\d]+个?动作(?:选择|是|做|采用|安排)?/, '')
    .replace(/^(动作(?:选择|是|做|采用|安排)?)/, '')
    .replace(/^(建议|推荐|先做|先进行)/, '')
    .trim()
  const firstSegment = stripped.split(/[，。；：]/)[0]?.trim() || ''
  return firstSegment
    .replace(/^(选择|做|采用|安排|可以做|选|用)/, '')
    .trim()
}

const parseChatExercises = (response: string): ManualExercise[] => {
  const paragraphs = response
    .replace(/\r/g, '')
    .split(/\n+/)
    .map(item => item.trim())
    .filter(Boolean)

  const exerciseParagraphs = paragraphs.filter(item =>
    /^第[一二三四五六七八九十\d]+个?动作/.test(item) ||
    (item.includes('动作') && /(卧推|飞鸟|深蹲|硬拉|推举|划船|下拉|弯举|俯卧撑|臂屈伸|拉伸|跑步|慢跑|骑行|平板支撑)/.test(item))
  )

  return exerciseParagraphs
    .map<ManualExercise | null>(paragraph => {
      const name = parseChatExerciseName(paragraph)
      if (!name) return null

      const type = getExerciseType({ name })
      if (type === 'cardio') {
        return {
          name,
          type,
          sets: 1,
          reps: 0,
          restSeconds: 30,
          duration: extractFirstNumber(paragraph, /(\d+)\s*分钟/, 30),
          distance: extractFirstNumber(paragraph, /(\d+(?:\.\d+)?)\s*公里/, 5),
          pace: 360,
          incline: 0,
          holdTime: 0,
          rounds: 1
        }
      }

      if (type === 'flexibility') {
        return {
          name,
          type,
          sets: 1,
          reps: 0,
          restSeconds: 20,
          duration: 0,
          distance: 0,
          pace: 0,
          incline: 0,
          holdTime: extractFirstNumber(paragraph, /(\d+)\s*秒/, 30),
          rounds: extractFirstNumber(paragraph, /(\d+)\s*轮/, 3)
        }
      }

      return {
        name,
        type,
        sets: extractFirstNumber(paragraph, /(\d+)\s*组/, 3),
        reps: extractRangeMax(paragraph, 12),
        restSeconds: extractFirstNumber(paragraph, /休息\s*(\d+)\s*秒/, 60),
        duration: 0,
        distance: 0,
        pace: 0,
        incline: 0,
        holdTime: 0,
        rounds: 0
      }
    })
    .filter((item): item is ManualExercise => Boolean(item))
}

const buildChatTrainingPayload = (draft: ChatTrainingDraft) => {
  const exercises = parseChatExercises(draft.response)
  if (exercises.length === 0) return null

  const targetMuscleGroup = resolveDraftTargetMuscleGroup(draft)
  const estimatedDuration = Math.max(30, Math.min(90, exercises.length * 10 + 10))
  return {
    planName: `${targetMuscleGroup}训练计划`,
    targetMuscleGroup,
    estimatedDuration,
    content: JSON.stringify({
      targetMuscleGroup,
      estimatedDuration,
      exercises
    })
  }
}

const applyTrainingPlanState = (nextPlan: any) => {
  plan.value = nextPlan
  userReset.value = false
  viewingPlanDetail.value = false
  executingPlan.value = false
  setRecords.value = {}
  cardioRecords.value = {}
  flexRecords.value = {}
  interventions.value = {}
  interventionLog.value = []
  isEditing.value = false
}

const fetchPlan = async () => {
  // 用户已主动重置，跳过自动加载，保持初始页
  if (userReset.value) return
  try {
    const res = await getTodayTrainingPlan()
    plan.value = res
  } catch (e) {
    plan.value = null
  }
}

const resetPlan = () => {
  userReset.value = true
  executingPlan.value = false
  viewingPlanDetail.value = false
  plan.value = null
  setRecords.value = {}
  cardioRecords.value = {}
  flexRecords.value = {}
  interventions.value = {}
  interventionLog.value = []
  isEditing.value = false
}

const startExecuting = () => {
  viewingPlanDetail.value = false
  executingPlan.value = true
}

const stopExecuting = () => {
  executingPlan.value = false
  isEditing.value = false
}

const openPlanDetail = () => {
  executingPlan.value = false
  viewingPlanDetail.value = true
  isEditing.value = false
}

const closePlanDetail = () => {
  viewingPlanDetail.value = false
  isEditing.value = false
}

const confirmEditPlan = async () => {
  if (!plan.value) return
  try {
    await ElMessageBox.confirm(
      '将进入计划修改状态。此时可以调整动作组数、次数、时长等计划内容；未保存前不会生效。是否继续？',
      '进入修改模式',
      {
        confirmButtonText: '继续修改',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    enterEditMode()
  } catch {
    return
  }
}

const handleGenerate = async (options?: { targetMuscleGroup?: string; silentSuccess?: boolean; replaceExisting?: boolean }) => {
  loading.value = true
  try {
    const weatherContext = await getAiWeatherContext()
    const res = await generateTrainingPlan({
      weather: weatherContext,
      targetMuscleGroup: options?.targetMuscleGroup || undefined,
      replaceExisting: options?.replaceExisting ?? Boolean(plan.value)
    })
    plan.value = res
    userReset.value = false  // 新计划已创建，清除重置标志
    viewingPlanDetail.value = false
    executingPlan.value = false
    setRecords.value = {}
    cardioRecords.value = {}
    flexRecords.value = {}
    interventions.value = {}
    interventionLog.value = []
    if (!options?.silentSuccess) {
      ElMessage.success(options?.targetMuscleGroup
        ? `${options.targetMuscleGroup}训练计划已生成`
        : '协议已生成')
    }
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e?.message || '请确保已完善身体指标后重试'))
  } finally {
    loading.value = false
  }
}

const handleCoachAutoGenerate = async () => {
  if (route.query.from !== 'coach' || route.query.autogenerate !== '1') {
    return
  }

  const targetMuscleGroup = getCoachTargetMuscleGroup()
  if (plan.value) {
    try {
      await ElMessageBox.confirm(
        targetMuscleGroup
          ? `今天已经有训练计划。是否保留当前计划，还是替换为新的${targetMuscleGroup}训练计划？`
          : '今天已经有训练计划。是否保留当前计划，还是替换为新的训练计划？',
        '训练计划已存在',
        {
          confirmButtonText: '替换当前计划',
          cancelButtonText: '保留当前计划',
          type: 'warning',
          distinguishCancelAndClose: true
        }
      )
    } catch {
      ElMessage.info('已保留当前训练计划')
      await clearCoachQuery()
      return
    }
  }

  await handleGenerate({
    targetMuscleGroup: targetMuscleGroup || undefined,
    silentSuccess: true,
    replaceExisting: Boolean(plan.value)
  })

  if (plan.value) {
    ElMessage.success(targetMuscleGroup
      ? `已从 AI 对话加入${targetMuscleGroup}训练计划`
      : '已从 AI 对话加入训练计划')
  }
  await clearCoachQuery()
}

const handleCoachChatImport = async () => {
  if (route.query.from !== 'coach' || route.query.import !== 'chat-plan') {
    return
  }

  const draft = readChatTrainingDraft()
  if (!draft) {
    await clearCoachQuery()
    return
  }

  const payload = buildChatTrainingPayload(draft)
  if (!payload) {
    ElMessage.warning('未能从这段对话中识别出可加入计划的动作')
    clearChatTrainingDraft()
    await clearCoachQuery()
    return
  }

  if (plan.value) {
    try {
      await ElMessageBox.confirm(
        '今天已经有训练计划。是否保留当前计划，还是替换为对话中解析出的动作计划？',
        '训练计划已存在',
        {
          confirmButtonText: '替换当前计划',
          cancelButtonText: '保留当前计划',
          type: 'warning',
          distinguishCancelAndClose: true
        }
      )
    } catch {
      ElMessage.info('已保留当前训练计划')
      clearChatTrainingDraft()
      await clearCoachQuery()
      return
    }
  }

  loading.value = true
  try {
    const res = plan.value
      ? await updateTrainingPlan(plan.value.id, payload)
      : await createManualTrainingPlan(payload)
    applyTrainingPlanState(res)
    ElMessage.success('已将对话中的动作加入训练计划')
  } catch (e: any) {
    ElMessage.error('加入失败：' + (e?.message || '请稍后重试'))
  } finally {
    loading.value = false
    clearChatTrainingDraft()
    await clearCoachQuery()
  }
}

const handleManualCreate = async () => {
  if (!manualForm.planName.trim() && !manualForm.target.trim()) {
    ElMessage.warning('请输入分组名称或目标肌肉群')
    return
  }
  const validExercises = manualForm.exercises.filter(e => e.name.trim())
  if (validExercises.length === 0) {
    ElMessage.warning('请至少添加一个训练动作')
    return
  }

  manualLoading.value = true
  try {
    const planName = manualForm.planName.trim() || `自定义训练 - ${manualForm.target}`
    const payload = {
      planName,
      targetMuscleGroup: manualForm.target,
      estimatedDuration: manualForm.duration,
      content: JSON.stringify({
        targetMuscleGroup: manualForm.target,
        estimatedDuration: manualForm.duration,
        exercises: validExercises
      })
    }
    const res = await createManualTrainingPlan(payload)
    plan.value = res
    userReset.value = false  // 新计划已创建，清除重置标志
    viewingPlanDetail.value = false
    executingPlan.value = false
    setRecords.value = {}
    cardioRecords.value = {}
    flexRecords.value = {}
    manualStep.value = 1
    ElMessage.success('训练计划已创建')
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.message || '请稍后重试'))
  } finally {
    manualLoading.value = false
  }
}

const handleCheckIn = async () => {
  if (!plan.value) return
  checkinLoading.value = true
  try {
    await checkInTrainingPlan(plan.value.id)
    plan.value.status = 1
    ElMessage.success('协议已完成，干得漂亮！')
  } catch (e: any) {
    ElMessage.error('签到失败：' + (e?.message || '请稍后重试'))
  } finally {
    checkinLoading.value = false
  }
}

const enterEditMode = () => {
  if (!plan.value) return
  editForm.planName = plan.value.planName || ''
  editForm.targetMuscleGroup = plan.value.targetMuscleGroup || planData.value.targetMuscleGroup || ''
  editForm.estimatedDuration = plan.value.estimatedDuration || planData.value.estimatedDuration || 45
  editForm.exercises = (planData.value.exercises || []).map((ex: any) => ({
    name: ex.name || '',
    type: getExerciseType(ex),
    sets: ex.sets || 3,
    reps: ex.reps || 10,
    restSeconds: ex.restSeconds || 60,
    duration: ex.duration || 30,
    distance: ex.distance || 5,
    pace: ex.pace || 360,
    incline: ex.incline || 0,
    holdTime: ex.holdTime || 30,
    rounds: ex.rounds || 3
  }))
  isEditing.value = true
}

const cancelEdit = () => {
  isEditing.value = false
}

const addEditExercise = () => {
  editForm.exercises.push({ name: '', type: 'strength', sets: 3, reps: 10, restSeconds: 60, duration: 30, distance: 5, pace: 360, incline: 0, holdTime: 30, rounds: 3 })
}

const removeEditExercise = (idx: number) => {
  editForm.exercises.splice(idx, 1)
}

const saveEdit = async () => {
  if (!plan.value) return

  if (!editForm.targetMuscleGroup.trim()) {
    ElMessage.warning('请输入训练重点')
    return
  }

  const validExercises = editForm.exercises.filter(e => e.name.trim())
  if (validExercises.length === 0) {
    ElMessage.warning('请至少保留一个训练动作')
    return
  }

  editLoading.value = true
  try {
    const content = JSON.stringify({
      targetMuscleGroup: editForm.targetMuscleGroup,
      estimatedDuration: editForm.estimatedDuration,
      exercises: validExercises
    })
    const payload = {
      planName: editForm.planName || `自定义训练 - ${editForm.targetMuscleGroup}`,
      targetMuscleGroup: editForm.targetMuscleGroup,
      estimatedDuration: editForm.estimatedDuration,
      content
    }
    const res = await updateTrainingPlan(plan.value.id, payload)
    plan.value = res
    setRecords.value = {}
    cardioRecords.value = {}
    flexRecords.value = {}
    interventions.value = {}
    isEditing.value = false
    ElMessage.success('协议已更新')
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.message || '请稍后重试'))
  } finally {
    editLoading.value = false
  }
}

onMounted(async () => {
  await fetchPlan()
  await handleCoachChatImport()
  await handleCoachAutoGenerate()
})
</script>

<style scoped>
.training-content { width: 100%; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.ai-intensity-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.intensity-btns {
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 999px;
  padding: 2px;
}

.intensity-btn {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  font-weight: 700;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 999px;
  transition: all 0.15s;
}

.intensity-btn.active {
  background: var(--primary);
  color: #fffaf4;
}

.intensity-btn:hover:not(.active) {
  color: var(--text-secondary);
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.ai-processing {
  display: flex;
  align-items: center;
}

.choice-visual-loading {
  flex-direction: column;
  gap: 14px;
}

.choice-inline-processing {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.12);
  border: 1px solid rgba(194, 169, 120, 0.18);
}

.processing-ring {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(194, 169, 120, 0.2);
  border-top-color: #c2a978;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.summary-card { padding: 24px; }

.mode-inline-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-grid {
  display: flex;
  align-items: center;
  gap: 24px;
}

.summary-item { flex: 1; }

.summary-divider {
  width: 1px;
  height: 60px;
  background: rgba(88, 78, 67, 0.1);
}

.matrix-card {
  padding: 20px;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.matrix-card.card-complete {
  border-left-color: #22C55E;
  opacity: 0.85;
}

.matrix-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.matrix-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.ex-code {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 28px;
  font-weight: 900;
  color: rgba(194, 169, 120, 0.55);
  line-height: 1;
}

.ex-sep {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 20px;
  color: rgba(111, 103, 93, 0.24);
  font-weight: 300;
}

.ex-cn-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ex-en-name {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 12px;
  letter-spacing: 1.5px;
  color: var(--text-muted);
  font-weight: 600;
  white-space: nowrap;
  margin-left: 4px;
}

.type-badge {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 9px;
  letter-spacing: 1px;
  padding: 2px 8px;
  border-radius: 2px;
  font-weight: 700;
  margin-left: 8px;
  flex-shrink: 0;
}

.type-badge.type-strength {
  background: rgba(249, 115, 22, 0.15);
  color: #F97316;
  border: 1px solid rgba(249, 115, 22, 0.3);
}

.type-badge.type-cardio {
  background: rgba(59, 130, 246, 0.15);
  color: #3B82F6;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.type-badge.type-flexibility {
  background: rgba(168, 85, 247, 0.15);
  color: #A855F7;
  border: 1px solid rgba(168, 85, 247, 0.3);
}

.matrix-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.meta-tag {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  padding: 3px 8px;
  background: rgba(249, 115, 22, 0.1);
  color: var(--primary);
  border-radius: 2px;
  font-weight: 600;
}

/* Strength Data Matrix */
.data-matrix {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(248,250,252,0.1) transparent;
}

.set-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 56px;
  padding: 8px 6px;
  background: rgba(248,250,252,0.03);
  border: 1px solid rgba(248,250,252,0.06);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.set-cell:hover {
  background: rgba(248,250,252,0.06);
}

.set-cell.set-active {
  border-color: var(--primary);
  background: rgba(249, 115, 22, 0.08);
}

.set-cell.set-done {
  border-color: rgba(34, 197, 94, 0.3);
  background: rgba(34, 197, 94, 0.06);
}

.set-cell.set-failed {
  border-color: rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.06);
}

.set-label {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 9px;
  letter-spacing: 1px;
  color: rgba(248,250,252,0.3);
  font-weight: 700;
}

.set-value {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 13px;
  color: rgba(248,250,252,0.5);
  font-weight: 600;
  min-height: 20px;
  display: flex;
  align-items: center;
}

.set-input {
  width: 44px;
  height: 22px;
  text-align: center;
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 13px;
  font-weight: 700;
  color: #F8FAFC;
  background: rgba(248,250,252,0.08);
  border: 1px solid rgba(248,250,252,0.15);
  border-radius: 2px;
  outline: none;
  padding: 0 2px;
  -moz-appearance: textfield;
}

.set-input::-webkit-outer-spin-button,
.set-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.set-input:focus {
  border-color: var(--primary);
  background: rgba(249, 115, 22, 0.1);
}

.set-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid rgba(248,250,252,0.15);
  background: none;
  color: rgba(248,250,252,0.3);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.set-check.checked {
  border-color: #22C55E;
  background: rgba(34, 197, 94, 0.15);
  color: #22C55E;
}

.set-check:hover {
  border-color: var(--primary);
  color: var(--primary);
}

/* Cardio Entry */
.cardio-entry {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cardio-fields {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.cardio-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 9px;
  letter-spacing: 1px;
  color: rgba(248,250,252,0.3);
  font-weight: 700;
}

.field-input-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-unit {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  color: rgba(248,250,252,0.25);
  font-weight: 600;
  flex-shrink: 0;
}

.cardio-confirm-btn {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 12px;
  letter-spacing: 1px;
  font-weight: 700;
  padding: 10px 20px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #3B82F6;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s;
}

.cardio-confirm-btn:hover {
  background: rgba(59, 130, 246, 0.2);
}

.cardio-confirm-btn.cardio-done {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.3);
  color: #22C55E;
}

/* Flexibility Entry */
.flex-entry {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.flex-rounds {
  display: flex;
  gap: 8px;
}

.round-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 64px;
  padding: 10px 8px;
  background: rgba(248,250,252,0.03);
  border: 1px solid rgba(248,250,252,0.06);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.round-cell:hover {
  background: rgba(248,250,252,0.06);
}

.round-cell.round-active {
  border-color: #A855F7;
  background: rgba(168, 85, 247, 0.08);
}

.round-cell.round-done {
  border-color: rgba(34, 197, 94, 0.3);
  background: rgba(34, 197, 94, 0.06);
}

.round-label {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 9px;
  letter-spacing: 1px;
  color: rgba(248,250,252,0.3);
  font-weight: 700;
}

.round-hold {
  min-height: 20px;
  display: flex;
  align-items: center;
}

.flex-complete-tag {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 11px;
  letter-spacing: 1px;
  color: #22C55E;
  font-weight: 600;
  padding: 6px 12px;
  background: rgba(34, 197, 94, 0.06);
  border-radius: 3px;
  text-align: center;
}

/* AI Terminal */
.ai-terminal {
  margin-top: 12px;
  padding: 8px 12px;
  background: rgba(34, 197, 94, 0.04);
  border-left: 2px solid rgba(34, 197, 94, 0.4);
  border-radius: 0 3px 3px 0;
  font-family: 'Courier New', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.5;
  animation: terminal-appear 0.3s ease;
}

@keyframes terminal-appear {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.terminal-prompt {
  color: #22C55E;
  font-weight: bold;
  margin-right: 6px;
}

.terminal-text {
  color: rgba(34, 197, 94, 0.85);
  letter-spacing: 0.3px;
}

/* Intervention Log */
.intervention-log {
  padding: 16px 20px;
}

.intervention-log .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(248,250,252,0.08);
  padding-bottom: 8px;
}

.clear-log-btn {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  padding: 3px 10px;
  background: none;
  border: 1px solid rgba(248,250,252,0.1);
  color: rgba(248,250,252,0.4);
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
}

.clear-log-btn:hover {
  border-color: rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.log-entries {
  max-height: 200px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(248,250,252,0.1) transparent;
}

.log-entry {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(248,250,252,0.03);
  font-family: 'Courier New', 'Consolas', monospace;
  font-size: 11px;
}

.log-time {
  color: rgba(248,250,252,0.25);
  flex-shrink: 0;
}

.log-msg {
  color: rgba(34, 197, 94, 0.7);
  line-height: 1.4;
}

/* Progress */
.progress-track {
  height: 6px;
  background: rgba(248,250,252,0.08);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #F97316, #22C55E);
  border-radius: 3px;
  transition: width 0.4s ease;
}

.completed-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  border: 2px solid rgba(34, 197, 94, 0.3);
  background: rgba(34, 197, 94, 0.05);
  border-radius: 4px;
  flex-wrap: wrap;
}

.plan-detail-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
}

.detail-hero-copy {
  flex: 1;
  min-width: 0;
}

.detail-kicker {
  font-family: var(--font-heading);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #c2a978;
  margin-bottom: 8px;
}

.detail-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 1.4rem;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.02em;
}

.detail-desc {
  margin: 10px 0 0;
  max-width: 520px;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--text-secondary);
}

.detail-hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 12px;
  width: min(420px, 100%);
  flex-shrink: 0;
}

.detail-stat {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(17, 17, 17, 0.03);
  border: 1px solid rgba(17, 17, 17, 0.06);
}

.detail-stat-label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

.detail-stat-value {
  display: block;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-main);
}

.detail-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-progress-card {
  padding: 18px 20px;
}

.detail-progress-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-progress-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-progress-value {
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--text-main);
}

.detail-progress-rate {
  font-family: var(--font-heading);
  font-size: 1.4rem;
  font-weight: 800;
  color: var(--primary);
}

.detail-progress-actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.detail-progress-done {
  margin-top: 14px;
}

.detail-ex-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-ex-card {
  padding: 18px 20px;
  transition: border-color 0.16s ease, transform 0.16s ease;
}

.detail-ex-card:hover {
  border-color: rgba(127, 157, 135, 0.22);
}

.detail-ex-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-ex-title {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.detail-ex-num {
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 800;
  color: rgba(194, 169, 120, 0.8);
  letter-spacing: 0.08em;
  padding-top: 2px;
}

.detail-ex-copy {
  min-width: 0;
}

.detail-ex-name {
  display: block;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-main);
}

.detail-ex-preview-floating {
  position: fixed;
  z-index: 1400;
  width: 288px;
  pointer-events: none;
}

.detail-ex-preview-tooltip {
  width: 100%;
  height: 220px;
  display: grid;
  grid-template-rows: 144px minmax(0, 1fr);
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  background: rgba(255, 252, 247, 0.98);
  box-shadow: 0 20px 38px rgba(82, 69, 54, 0.18);
  backdrop-filter: blur(10px);
  animation: tooltipFadeIn 0.12s ease-out;
}

@keyframes tooltipFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.detail-ex-preview-image-wrap {
  background:
    linear-gradient(180deg, rgba(194, 169, 120, 0.22), rgba(194, 169, 120, 0.04)),
    rgba(244, 239, 231, 0.92);
}

.detail-ex-preview-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.detail-ex-preview-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  padding: 10px 14px 12px;
  overflow: hidden;
}

.detail-ex-preview-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.detail-ex-preview-title {
  font-family: var(--font-heading);
  font-size: 14px;
  line-height: 1.35;
  color: var(--text-main);
}

.detail-ex-preview-badge {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(127, 157, 135, 0.14);
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.detail-ex-preview-muscle {
  font-size: 12px;
  color: var(--primary);
  font-weight: 600;
}

.detail-ex-preview-target {
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.detail-ex-focus {
  display: block;
  margin-top: 6px;
  font-size: 0.78rem;
  color: var(--text-secondary);
}

.detail-ex-tag {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
  flex-shrink: 0;
}

.detail-ex-tag.type-strength {
  background: rgba(249, 115, 22, 0.1);
  color: #F97316;
  border: 1px solid rgba(249, 115, 22, 0.2);
}

.detail-ex-tag.type-cardio {
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.detail-ex-tag.type-flexibility {
  background: rgba(168, 85, 247, 0.1);
  color: #A855F7;
  border: 1px solid rgba(168, 85, 247, 0.2);
}

.detail-ex-body {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(17, 17, 17, 0.06);
}

.detail-ex-prescription {
  font-size: 0.86rem;
  line-height: 1.7;
  color: var(--text-secondary);
}

.detail-progress-followup {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-set-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-set-row {
  display: grid;
  grid-template-columns: 64px 1fr 1fr 82px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(17, 17, 17, 0.06);
  background: rgba(17, 17, 17, 0.025);
}

.detail-set-row.done {
  background: rgba(34, 197, 94, 0.06);
  border-color: rgba(34, 197, 94, 0.18);
}

.detail-row-label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-secondary);
}

.detail-row-status {
  font-size: 0.8rem;
  color: var(--text-main);
}

.detail-mini-input {
  min-width: 0;
  padding: 9px 10px;
  font-size: 0.84rem;
}

.detail-check-btn {
  height: 38px;
  border-radius: 10px;
  border: 1px solid rgba(17, 17, 17, 0.12);
  background: #fff;
  color: var(--text-main);
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s ease;
}

.detail-check-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.detail-check-btn.done {
  border-color: rgba(34, 197, 94, 0.25);
  background: rgba(34, 197, 94, 0.08);
  color: #16a34a;
}

.detail-check-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  border-color: rgba(17, 17, 17, 0.08);
  color: var(--text-secondary);
}

.detail-cardio-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-input-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-cardio-btn {
  align-self: flex-end;
  min-width: 132px;
}

.detail-flex-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-flex-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.detail-flex-editor {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-round-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-round-tag {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-secondary);
  background: rgba(17, 17, 17, 0.06);
  border: 1px solid transparent;
}

.detail-round-tag.active {
  border-color: rgba(168, 85, 247, 0.25);
  background: rgba(168, 85, 247, 0.08);
  color: #9333ea;
}

.detail-round-tag.done {
  border-color: rgba(34, 197, 94, 0.18);
  background: rgba(34, 197, 94, 0.08);
  color: #16a34a;
}

.detail-inline-tip {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(34, 197, 94, 0.05);
  border-left: 3px solid rgba(34, 197, 94, 0.35);
  font-size: 0.78rem;
  line-height: 1.6;
  color: #166534;
}

.detail-empty {
  padding: 36px 24px;
  text-align: center;
}

/* Creation Layout */
/* ── 创建场景 ──────────────────────────────────────── */
.creation-scene {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Hero 横幅 */
.creation-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 32px 36px;
  color: var(--text-main);
}
.creation-hero-copy { flex: 1; }
.creation-hero-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-shrink: 0;
}
.hero-stat { text-align: center; }
.hero-stat-divider { width: 1px; height: 36px; background: rgba(17,17,17,0.1); }

/* 选择区双栏 */
.creation-choices {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  flex: 1;
}

/* 选择卡片基础 */
.choice-card {
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 0;
  color: #1f1c19 !important;
  -webkit-font-smoothing: antialiased;
}
.choice-badge {
  display: inline-block;
  font-family: var(--font-heading);
  font-size: 0.7rem;
  letter-spacing: 0.12em;
  font-weight: 700;
  color: #c2a978;
  margin-bottom: 12px;
}
.choice-badge-manual { color: var(--secondary); }
.choice-title {
  font-family: var(--font-heading);
  font-size: 1.3rem;
  font-weight: 800;
  color: var(--text-main);
  margin: 0 0 10px;
  letter-spacing: -0.01em;
}
.choice-desc {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.65;
  margin-bottom: 20px;
}
.choice-features {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}
.feat-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.83rem;
  color: var(--text-secondary);
}
.feat-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #c2a978;
  flex-shrink: 0;
}
.feat-dot-manual { background: var(--secondary); }

/* 视觉区域 */
.choice-visual {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  margin-bottom: 20px;
}
.choice-btn { margin-top: auto; }

/* 手动构建三类型展示 */
.manual-type-showcase {
  gap: 12px;
  align-items: stretch;
  justify-content: stretch;
}
.type-block {
  flex: 1;
  border-radius: 14px;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-height: 90px;
  justify-content: center;
}
.type-block.type-strength { background: rgba(23,24,31,0.07); }
.type-block.type-cardio   { background: rgba(183,154,114,0.12); }
.type-block.type-flexibility { background: rgba(127,157,135,0.12); }
.type-block-label { font-size: 0.75rem; font-weight: 700; color: var(--text-secondary); letter-spacing: 0.06em; }
.type-block-bars { display: flex; gap: 4px; align-items: flex-end; }
.tbar {
  width: 8px;
  border-radius: 3px 3px 0 0;
  background: #17181f;
  opacity: 0.5;
}
.tbar-1 { height: 18px; }
.tbar-2 { height: 28px; opacity: 0.7; }
.tbar-3 { height: 22px; opacity: 0.6; }
.type-block.type-cardio .type-wave { width: 64px; color: #b79a72; opacity: 0.8; }
.type-flex-dots { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; }
.fdot {
  width: 10px; height: 10px;
  border-radius: 50%;
  background: #7f9d87;
  opacity: 0.6;
}
.fdot:nth-child(2) { opacity: 0.4; transform: scale(0.8); }
.fdot:nth-child(3) { opacity: 0.8; transform: scale(1.15); }
.fdot:nth-child(4) { opacity: 0.5; }

/* 旧 creation-layout 兼容保留 */
.creation-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.creation-card { padding: 24px; display: flex; flex-direction: column; min-width: 0; height: 100%; }
.creation-card-ai { justify-content: flex-start; }

/* 手动构建器展开状态：Step2 占满整行 */
.manual-builder-expanded {
  grid-column: 1 / -1;
}

/* Step2 顶栏 */
.builder-step2-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.08);
}
.step2-title { display: flex; align-items: center; gap: 8px; }
.back-btn {
  flex-shrink: 0;
  padding: 6px 14px;
  border: 1px solid rgba(17, 17, 17, 0.12);
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s;
}
.back-btn:hover { background: rgba(17,17,17,0.05); color: var(--text-main); }

/* 双栏分割 */
.builder-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  min-height: 460px;
}

/* 左侧动作图谱面板 */
.atlas-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-right: 1px solid rgba(17,17,17,0.08);
  padding-right: 20px;
}
.atlas-search-bar { display: flex; flex-direction: column; gap: 8px; }
.atlas-filters { display: flex; gap: 6px; flex-wrap: wrap; }
.filter-btn {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(17,17,17,0.1);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-btn.active { background: var(--primary); border-color: var(--primary); color: #fffaf4; }

.atlas-list {
  flex: 1;
  overflow-y: auto;
  max-height: 380px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 4px;
}
.atlas-list::-webkit-scrollbar { width: 4px; }
.atlas-list::-webkit-scrollbar-thumb { background: rgba(17,17,17,0.12); border-radius: 2px; }
.atlas-state { padding: 40px 0; text-align: center; }
.atlas-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s;
}
.atlas-item:hover { background: rgba(127,157,135,0.08); border-color: rgba(127,157,135,0.18); }
.atlas-item-added { background: rgba(127,157,135,0.12) !important; border-color: rgba(127,157,135,0.3) !important; }
.atlas-item-name { flex: 1; font-size: 0.88rem; color: var(--text-main); }
.atlas-item-tag { font-size: 0.72rem; padding: 2px 8px; border-radius: 999px; background: rgba(17,17,17,0.06); color: var(--text-secondary); }
.atlas-item-plus {
  width: 22px; height: 22px;
  display: grid; place-items: center;
  border-radius: 50%;
  background: rgba(127,157,135,0.15);
  color: var(--secondary);
  font-size: 0.9rem; font-weight: 700;
  flex-shrink: 0;
}
.atlas-item-added .atlas-item-plus { background: rgba(127,157,135,0.35); }

/* 右侧已选动作面板 */
.selected-panel { display: flex; flex-direction: column; gap: 10px; }
.selected-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(17,17,17,0.08);
}
.selected-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
.selected-list {
  flex: 1;
  overflow-y: auto;
  max-height: 380px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}
.selected-list::-webkit-scrollbar { width: 4px; }
.selected-list::-webkit-scrollbar-thumb { background: rgba(17,17,17,0.12); border-radius: 2px; }
.selected-exercise-card {
  position: relative;
  border: 1px solid rgba(17,17,17,0.08);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(17,17,17,0.02);
  display: flex;
  gap: 12px;
  align-items: center;
}
.sel-ex-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(17, 17, 17, 0.04);
  color: var(--text-secondary);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}
.sel-ex-remove:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.3);
}
/* 左：仅图片列 */
.sel-ex-img-col {
  width: 174px;
  flex-shrink: 0;
  
}
.sel-ex-img-wrap {
  width: 100%;
  height: 90px;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(17,17,17,0.06);
}
.sel-ex-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 35%;
  display: block;
}
.sel-ex-img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(17,17,17,0.25);
}
/* 右：详情列（动作信息 + 参数） */
.sel-ex-detail-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 24px; /* 为绝对定位的删除按钮留出空间 */
}
.sel-ex-header { 
  display: flex; 
  align-items: center; 
  gap: 6px; 
}
.sel-ex-num { 
  font-family: var(--font-heading); 
  font-size: 0.75rem; 
  color: var(--text-secondary); 
  letter-spacing: 0.1em; 
  flex-shrink: 0; 
}
.sel-ex-name { 
  font-size: 0.85rem; 
  font-weight: 600; 
  color: var(--text-main); 
  white-space: nowrap; 
  overflow: hidden; 
  text-overflow: ellipsis; 
}
.sel-ex-badge { 
  font-size: 0.65rem; 
  padding: 2px 6px; 
  border-radius: 999px; 
  background: rgba(17,17,17,0.06); 
  color: var(--text-secondary); 
  display: inline-block; 
  flex-shrink: 0;
}
.sel-ex-params { 
  display: flex; 
  gap: 8px; 
  flex-wrap: nowrap; 
}
.param-field { 
  display: flex; 
  flex-direction: column; 
  gap: 3px; 
  align-items: flex-start; 
}
.param-field label { 
  font-size: 0.72rem; 
  font-weight: 600;
  color: var(--text-secondary); 
  letter-spacing: 0.02em; 
  white-space: nowrap; 
}

/* AI Visual */
.ai-visual {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 140px;
}

.ai-rings {
  position: relative;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid rgba(249, 115, 22, 0.3);
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
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 900;
  color: var(--primary);
  z-index: 1;
}

/* Manual Exercise Builder */
.exercise-builder-block {
  padding: 12px;
  background: rgba(248,250,252,0.03);
  border: 1px solid rgba(248,250,252,0.06);
  border-radius: 4px;
}

.builder-row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.builder-type-select {
  display: flex;
  gap: 2px;
}

.type-select-btn {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  font-weight: 700;
  padding: 3px 10px;
  border: 1px solid rgba(248,250,252,0.1);
  background: transparent;
  color: rgba(248,250,252,0.3);
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
}

.type-select-btn.active.type-strength {
  background: rgba(249, 115, 22, 0.15);
  border-color: rgba(249, 115, 22, 0.3);
  color: #F97316;
}

.type-select-btn.active.type-cardio {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.3);
  color: #3B82F6;
}

.type-select-btn.active.type-flexibility {
  background: rgba(168, 85, 247, 0.15);
  border-color: rgba(168, 85, 247, 0.3);
  color: #A855F7;
}

.type-select-btn:hover:not(.active) {
  color: rgba(248,250,252,0.6);
}

.builder-row-name {
  margin-bottom: 8px;
}

.builder-row-params {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.add-btn {
  background: none;
  border: 1px solid rgba(249, 115, 22, 0.3);
  color: var(--primary);
  padding: 4px 12px;
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1px;
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
}

.add-btn:hover { background: rgba(249, 115, 22, 0.1); }

.remove-btn {
  background: none;
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #ef4444;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 2px;
  font-size: 12px;
}

/* Utilities */
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.w-full { width: 100%; }
.block { display: block; }

@media (max-width: 1024px) {
  .detail-ex-preview-floating { display: none; }
  .creation-layout { grid-template-columns: 1fr; }
  .summary-grid { flex-wrap: wrap; }
  .matrix-header { flex-direction: column; align-items: flex-start; gap: 10px; }
  .matrix-meta { align-self: flex-end; }
  .cardio-fields { grid-template-columns: 1fr; }
  .detail-hero-card { flex-direction: column; }
  .detail-hero-stats { width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .detail-ex-list { grid-template-columns: 1fr; }
  .detail-cardio-grid { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .header-actions { flex-direction: column; align-items: flex-end; gap: 8px; }
  .mode-inline-actions { width: 100%; }
  .ex-code { font-size: 22px; }
  .ex-cn-name { font-size: 15px; }
  .ex-en-name { font-size: 10px; }
  .set-cell { min-width: 48px; padding: 6px 4px; }
  .cardio-fields { grid-template-columns: 1fr; }
  .flex-rounds { flex-wrap: wrap; }
  .detail-hero-card { padding: 20px; }
  .detail-hero-stats { grid-template-columns: 1fr; }
  .detail-ex-card { padding: 16px; }
  .detail-ex-header { flex-direction: column; }
  .detail-set-row { grid-template-columns: 1fr; }
  .detail-check-btn,
  .detail-cardio-btn { width: 100%; }
  .detail-flex-editor { align-items: stretch; }
}

.edit-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.edit-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.edit-exercise-card {
  padding: 16px;
  border-left: 3px solid var(--primary);
}

.edit-ex-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.edit-ex-num {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 24px;
  font-weight: 900;
  color: rgba(249, 115, 22, 0.3);
}

.edit-ex-type-select {
  display: flex;
  gap: 2px;
  background: rgba(248,250,252,0.05);
  border-radius: 3px;
  padding: 2px;
  flex: 1;
}

.type-select-btn {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 10px;
  letter-spacing: 1px;
  font-weight: 700;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: rgba(248,250,252,0.3);
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
}

.type-select-btn.active.type-strength {
  background: rgba(249, 115, 22, 0.2);
  color: #F97316;
}

.type-select-btn.active.type-cardio {
  background: rgba(59, 130, 246, 0.2);
  color: #3B82F6;
}

.type-select-btn.active.type-flexibility {
  background: rgba(168, 85, 247, 0.2);
  color: #A855F7;
}

.type-select-btn:hover:not(.active) {
  color: rgba(248,250,252,0.6);
}

.remove-btn {
  width: 28px;
  height: 28px;
  border-radius: 3px;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.remove-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.5);
}

.edit-ex-name {
  margin-bottom: 12px;
}

.edit-ex-params {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.edit-param {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 100px;
}

@media (max-width: 768px) {
  .edit-summary-grid { grid-template-columns: 1fr; }
  .edit-ex-params { flex-direction: column; }
  .edit-param { min-width: auto; }
}

/* ── 手动构建触发卡片装饰 ──────────────────────────────────────── */
.manual-trigger-card {
  justify-content: flex-start;
}
.manual-icon-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 160px;
}
.manual-icon-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.mi-cell {
  width: 62px;
  height: 62px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-family: var(--font-heading);
  font-size: 1.2rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}
.mi-cell.type-strength { background: rgba(23,24,31,0.08); color: #17181f; }
.mi-cell.type-cardio   { background: rgba(183,154,114,0.18); color: #b79a72; }
.mi-cell.type-flexibility { background: rgba(127,157,135,0.18); color: #7f9d87; }
.mi-cell.mi-plus {
  background: rgba(17,17,17,0.04);
  border: 1.5px dashed rgba(17,17,17,0.16);
  color: var(--text-secondary);
  font-size: 1.6rem;
  font-weight: 400;
}

/* ── 弹窗（Teleport 到 body，全部用 :global）─────────────────────── */
/* ── 弹窗动画 ─────────────────────────────────────────── */
:global(.modal-fade-enter-active),
:global(.modal-fade-leave-active) {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
:global(.modal-fade-enter-from),
:global(.modal-fade-leave-to) {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}

/* 遮罩 */
:global(.modal-overlay) {
  position: fixed;
  inset: 0;
  z-index: 9998;
  background: rgba(15, 14, 12, 0.5);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

/* 弹窗容器 */
:global(.modal-box) {
  width: 100%;
  max-width: 1060px;
  max-height: 90vh;
  background: #fcf8f1;
  border-radius: 28px;
  box-shadow: 0 32px 80px rgba(0,0,0,0.25), 0 0 0 1px rgba(17,17,17,0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #1f1c19;
  font-family: "Aptos", "Segoe UI Variable Text", "Microsoft YaHei UI", sans-serif;
}

/* 头部 */
:global(.modal-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(17,17,17,0.07);
  flex-shrink: 0;
}
:global(.modal-title-area) {
  display: flex;
  align-items: center;
  gap: 10px;
}
:global(.modal-title) {
  font-size: 1rem;
  font-weight: 700;
  color: #1f1c19;
}
:global(.modal-header-actions) {
  display: flex;
  align-items: center;
  gap: 14px;
}
:global(.modal-steps) {
  display: flex;
  align-items: center;
  gap: 6px;
}
:global(.step-dot) {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  font-weight: 700;
  background: rgba(17,17,17,0.06);
  color: #888;
  transition: all 0.2s;
}
:global(.step-dot.active) { background: #1f1c19; color: #f4eee8; }
:global(.step-dot.done)   { background: rgba(127,157,135,0.28); color: #3d6e4e; }
:global(.step-line) { width: 20px; height: 1px; background: rgba(17,17,17,0.12); }
:global(.modal-close-btn) {
  width: 30px; height: 30px;
  border-radius: 50%;
  border: none;
  background: rgba(17,17,17,0.06);
  color: #888;
  font-size: 0.85rem;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.15s;
}
:global(.modal-close-btn:hover) { background: rgba(17,17,17,0.11); color: #1f1c19; }

/* ── 弹窗 body ───────────────────────────────────────── */
:global(.modal-body) {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  min-height: 0;
}

/* Step 1：两列表单 */
:global(.step1-body) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 20px;
  align-content: start;
}
/* 计划名称独占一行 */
:global(.step1-body .field-full) {
  grid-column: 1 / -1;
}

/* Step 2 */
:global(.step2-body) {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}
:global(.modal-split) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
:global(.modal-atlas) {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-right: 1px solid rgba(17,17,17,0.07);
  padding: 20px 20px 16px;
  overflow: hidden;
}
:global(.modal-selected) {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 20px 16px;
  overflow: hidden;
}
:global(.modal-box .atlas-list) {
  max-height: none;
  flex: 1;
  overflow-y: auto;
}
:global(.modal-box .selected-list) {
  max-height: none;
  flex: 1;
  overflow-y: auto;
}

/* 底部操作 */
:global(.modal-footer) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-top: 1px solid rgba(17,17,17,0.07);
  flex-shrink: 0;
  gap: 10px;
}
:global(.modal-footer-right) { display: flex; gap: 10px; }

/* ── 弹窗内表单元素 ──────────────────────────────────── */
:global(.modal-box label) {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: #645b53;
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}
:global(.modal-box .nd-input) {
  display: block;
  width: 100%;
  box-sizing: border-box;
  background: rgba(17,17,17,0.04);
  border: 1.5px solid rgba(17,17,17,0.1);
  color: #1f1c19;
  border-radius: 12px;
  padding: 11px 14px;
  font-size: 0.92rem;
  font-family: inherit;
  transition: border-color 0.15s;
}
:global(.modal-box .nd-input:focus) {
  outline: none;
  border-color: rgba(17,17,17,0.28);
  background: rgba(17,17,17,0.06);
}
:global(.modal-box .nd-input::placeholder) { color: #bbb; }
:global(.modal-box .nd-input.small) {
  padding: 6px 9px;
  font-size: 0.82rem;
  border-radius: 8px;
  width: 68px;
}
:global(.modal-box .nd-input.mini) {
  padding: 2px 4px;
  height: 24px;
  font-size: 0.75rem;
  border-radius: 4px;
  text-align: center;
}
:global(.modal-box .nd-input.mini.w-xs) {
  width: 58px;
}
:global(.modal-box .nd-input.mini.w-sm) {
  width: 58px;
}
:global(.modal-box .nd-input.mini.w-md) {
  width: 70px;
}

/* 搜索框包装：带图标的全宽输入 */
:global(.modal-search-wrap) {
  position: relative;
  width: 100%;
}
:global(.modal-search-icon) {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #aaa;
  pointer-events: none;
  flex-shrink: 0;
}
:global(.modal-search-input) {
  width: 100% !important;
  box-sizing: border-box !important;
  padding-left: 34px !important;
  font-size: 0.88rem !important;
  border-radius: 10px !important;
  padding-top: 9px !important;
  padding-bottom: 9px !important;
}
:global(.modal-search-input:focus) {
  border-color: rgba(17,17,17,0.25) !important;
}

/* ══════════════════════════════════════════════════
   计划预览卡片
══════════════════════════════════════════════════ */
.plan-preview-card {
  padding: 0;
  overflow: hidden;
  border-radius: 16px;
  color: #1f1c19;
}

/* 卡片头部 */
.preview-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.07);
  flex-wrap: wrap;
}

.preview-card-info {
  flex: 1;
  min-width: 0;
}

.preview-card-kicker {
  font-family: var(--font-heading);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #c2a978;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.preview-card-title {
  font-family: var(--font-heading);
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.01em;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.meta-dot {
  color: rgba(17, 17, 17, 0.2);
  font-weight: 300;
}

.preview-card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 动作列表：固定高度 + 内部滚动 */
.preview-ex-list {
  height: 280px;
  overflow-y: auto;
  padding: 8px 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(17, 17, 17, 0.1) transparent;
}

.preview-ex-list::-webkit-scrollbar {
  width: 4px;
}

.preview-ex-list::-webkit-scrollbar-thumb {
  background: rgba(17, 17, 17, 0.12);
  border-radius: 2px;
}

/* 每行动作：参考 atlas-item 风格 */
.preview-ex-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 24px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.04);
  transition: background 0.12s;
  cursor: default;
}

.preview-ex-row:last-child {
  border-bottom: none;
}

.preview-ex-row:hover {
  background: rgba(17, 17, 17, 0.025);
}

.preview-ex-row.preview-ex-done {
  opacity: 0.55;
}

.preview-ex-num {
  font-family: var(--font-heading);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: rgba(194, 169, 120, 0.7);
  flex-shrink: 0;
  width: 24px;
}

.preview-ex-name {
  flex: 1;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview-ex-tag {
  font-size: 0.68rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
  letter-spacing: 0.03em;
}

.preview-ex-tag.type-strength {
  background: rgba(249, 115, 22, 0.1);
  color: #F97316;
  border: 1px solid rgba(249, 115, 22, 0.2);
}

.preview-ex-tag.type-cardio {
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.preview-ex-tag.type-flexibility {
  background: rgba(168, 85, 247, 0.1);
  color: #A855F7;
  border: 1px solid rgba(168, 85, 247, 0.2);
}

.preview-ex-params {
  font-size: 0.78rem;
  color: var(--text-secondary);
  flex-shrink: 0;
  min-width: 90px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.preview-ex-check {
  font-size: 0.75rem;
  color: #22C55E;
  font-weight: 700;
  flex-shrink: 0;
  width: 18px;
  text-align: center;
}

.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px 0;
}

/* 预览卡片滑入动画 */
.preview-slide-enter-active {
  animation: preview-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.preview-slide-leave-active {
  animation: preview-out 0.2s ease forwards;
}

@keyframes preview-in {
  from { opacity: 0; transform: translateY(16px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes preview-out {
  from { opacity: 1; transform: translateY(0); }
  to   { opacity: 0; transform: translateY(8px); }
}

/* 响应式 */
@media (max-width: 768px) {
  .preview-card-header { flex-direction: column; align-items: flex-start; }
  .preview-card-actions { width: 100%; justify-content: flex-end; }
  .preview-ex-params { min-width: 70px; }
  .preview-ex-list { height: 220px; }
}
</style>
