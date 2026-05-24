<template>
  <div class="dashboard-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">概览</span>
          <strong class="topbar-page-title">训练总览</strong>
          <span class="topbar-page-meta">{{ todayDate }} · 第{{ weekNum }}周</span>
        </div>
        <div class="topbar-page-actions">
          <button class="nd-btn primary" @click="router.push('/app/coach')">AI 训练顾问</button>
        </div>
      </div>
    </Teleport>

    <section class="dashboard-hero">
      <div class="dashboard-copy">
        <div class="text-label">[ 概览 ]</div>
        <div class="dashboard-index">01</div>
        <h1 class="text-display-md dashboard-title">一页总览你的当前训练周期。</h1>
        <p class="dashboard-description">
          系统整合连续训练天数、负荷、营养摄入与身体适应数据，帮你以一个统一看板全面回顾本周状况，无需在多个工具间频繁切换。
        </p>
        <div class="dashboard-actions">
          <button class="nd-btn primary" @click="router.push('/app/coach')">AI 训练顾问</button>
          <button class="nd-btn" @click="router.push('/app/training')">今日训练计划</button>
        </div>
      </div>

      <div class="dashboard-spotlight nd-card">
        <div class="spotlight-row">
          <span>连续打卡</span>
          <strong>{{ stats.streak }} 天</strong>
        </div>
        <div class="spotlight-row">
          <span>本周训练</span>
          <strong>{{ stats.weeklyTraining }} / 7</strong>
        </div>
        <div class="spotlight-row">
          <span>计划完成率</span>
          <strong>{{ stats.completionRate }}%</strong>
        </div>
        <div class="spotlight-track">
          <span :style="{ width: stats.completionRate + '%' }"></span>
        </div>
      </div>
    </section>

    <section class="metrics-grid">
      <article class="nd-card metric-box">
        <span class="metric-kicker">连续天数</span>
        <strong class="metric-value">{{ stats.streak }}</strong>
        <p>近期训练的坚持情况。</p>
      </article>
      <article class="nd-card metric-box">
        <span class="metric-kicker">训练频率</span>
        <strong class="metric-value">{{ stats.weeklyTraining }}</strong>
        <p>本周已完成的训练次数。</p>
      </article>
      <article class="nd-card metric-box">
        <span class="metric-kicker">卡路里</span>
        <strong class="metric-value">{{ stats.avgCalories }}</strong>
        <p>系统记录的平均消耗卡路里。</p>
      </article>
      <article class="nd-card metric-box">
        <span class="metric-kicker">完成率</span>
        <strong class="metric-value">{{ stats.completionRate }}%</strong>
        <p>已完成计划工作量的比例。</p>
      </article>
    </section>

    <section class="charts-grid">
      <article class="nd-card chart-card">
        <div class="card-header">
          <div>
            <div class="text-label">[ 负荷 ]</div>
            <h2>本周训练时长</h2>
          </div>
          <span class="text-caption">7 天视图</span>
        </div>
        <div ref="barChartRef" class="chart-slot"></div>
      </article>

      <article class="nd-card chart-card">
        <div class="card-header">
          <div>
            <div class="text-label">[ 身体 ]</div>
            <h2>身体指标趋势</h2>
          </div>
          <span class="text-caption">30 天视图</span>
        </div>
        <div ref="lineChartRef" class="chart-slot"></div>
      </article>
    </section>

    <section class="lower-grid">
      <article class="nd-card chart-card heatmap-card">
        <div class="card-header">
          <div>
            <div class="text-label">[ 历史 ]</div>
            <h2>训练热力图</h2>
          </div>
          <span class="text-caption">4 个月</span>
        </div>

        <div class="heatmap">
          <div v-for="(week, wi) in heatmapData" :key="wi" class="heatmap-week">
            <div
              v-for="(day, di) in week"
              :key="di"
              class="heatmap-cell"
              :class="getCellClass(day)"
              :title="`${day.date}: ${day.count} 次训练`"
            ></div>
          </div>
        </div>

        <div class="heatmap-legend">
          <span class="text-caption">少</span>
          <div class="legend-cell level-0"></div>
          <div class="legend-cell level-1"></div>
          <div class="legend-cell level-2"></div>
          <div class="legend-cell level-3"></div>
          <div class="legend-cell level-4"></div>
          <span class="text-caption">多</span>
        </div>
      </article>

      <article class="nd-card chart-card">
        <div class="card-header">
          <div>
            <div class="text-label">[ 营养 ]</div>
            <h2>宏量素分布</h2>
          </div>
          <span class="text-caption">
            {{ nutrition.isToday ? '今日' : nutrition.planDate ? `最近 ${nutrition.planDate}` : '暂无数据' }}
          </span>
        </div>

        <div ref="pieChartRef" class="chart-slot chart-slot-sm"></div>

        <div class="macro-summary">
          <div class="macro-row">
            <span><i class="macro-dot protein-dot"></i>蛋白质</span>
            <strong>{{ nutrition.protein }}g</strong>
          </div>
          <div class="macro-row">
            <span><i class="macro-dot carbs-dot"></i>碳水化合物</span>
            <strong>{{ nutrition.carbs }}g</strong>
          </div>
          <div class="macro-row">
            <span><i class="macro-dot fat-dot"></i>脂肪</span>
            <strong>{{ nutrition.fat }}g</strong>
          </div>
          <div class="macro-row total-row">
            <span>总卡路里</span>
            <strong>{{ nutrition.calories }} kcal</strong>
          </div>
        </div>
      </article>
    </section>

    <section class="quick-links">
      <div class="text-label">[ 快速跳转 ]</div>
      <div class="links-grid">
        <button class="nd-card quick-card" @click="router.push('/app/training')">
          <span class="quick-code">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6.5 6.5h11M6.5 17.5h11M3 12h18M7 9l-1-2.5M17 9l1-2.5M7 15l-1 2.5M17 15l1 2.5"/>
            </svg>
          </span>
          <strong>今日训练</strong>
        </button>
        <button class="nd-card quick-card" @click="router.push('/app/diet')">
          <span class="quick-code">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2C8 2 5 5 5 9c0 2.5 1.2 4.7 3 6.2V20a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-4.8c1.8-1.5 3-3.7 3-6.2 0-4-3-7-7-7z"/>
              <path d="M12 2v10"/>
            </svg>
          </span>
          <strong>营养计划</strong>
        </button>
        <button class="nd-card quick-card" @click="router.push('/app/achievements')">
          <span class="quick-code">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8" r="5"/>
              <path d="M7.21 13.89 6 22l6-3 6 3-1.21-8.11"/>
            </svg>
          </span>
          <strong>成就展示</strong>
        </button>
        <button class="nd-card quick-card" @click="router.push('/app/leaderboard')">
          <span class="quick-code">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="12" width="4" height="9" rx="1"/>
              <rect x="10" y="7" width="4" height="14" rx="1"/>
              <rect x="17" y="3" width="4" height="18" rx="1"/>
            </svg>
          </span>
          <strong>排行榜</strong>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getBodyMetricsTrend, getDashboardStats, getHeatmap, getNutritionToday, getWeeklyTraining } from '../api/dashboard'

const router = useRouter()
const barChartRef = ref()
const lineChartRef = ref()
const pieChartRef = ref()

let barChart: any = null
let lineChart: any = null
let pieChart: any = null

const now = new Date()
const todayDate = now.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
const weekNum = Math.ceil(now.getDate() / 7)

const stats = ref({
  streak: 0,
  weeklyTraining: 0,
  avgCalories: 0,
  completionRate: 0
})

const nutrition = ref({
  protein: 0,
  carbs: 0,
  fat: 0,
  calories: 0,
  planDate: null as string | null,
  isToday: true
})

const heatmapData = ref<any[]>([])
const weeklyTrainingData = ref<any[]>([])
const bodyMetricsData = ref<any[]>([])

const getCellClass = (day: any) => {
  if (!day || day.count === 0) return 'level-0'
  if (day.count === 1) return 'level-1'
  if (day.count === 2) return 'level-2'
  if (day.count === 3) return 'level-3'
  return 'level-4'
}

const fetchDashboardData = async () => {
  const [statsRes, weeklyRes, heatmapRes, nutritionRes, metricsRes] = await Promise.allSettled([
    getDashboardStats(),
    getWeeklyTraining(),
    getHeatmap(),
    getNutritionToday(),
    getBodyMetricsTrend()
  ])

  if (statsRes.status === 'fulfilled' && statsRes.value) {
    const d = statsRes.value as any
    stats.value = {
      streak: d.streak ?? 0,
      weeklyTraining: d.weeklyTraining ?? 0,
      avgCalories: d.avgCalories ?? 0,
      completionRate: d.completionRate ?? 0
    }
  }

  if (weeklyRes.status === 'fulfilled' && weeklyRes.value) {
    weeklyTrainingData.value = (weeklyRes.value as any) || []
  }

  if (heatmapRes.status === 'fulfilled' && heatmapRes.value) {
    heatmapData.value = (heatmapRes.value as any) || []
  }

  if (nutritionRes.status === 'fulfilled' && nutritionRes.value) {
    const d = nutritionRes.value as any
    nutrition.value = {
      protein: Number(d.protein) || 0,
      carbs: Number(d.carbs) || 0,
      fat: Number(d.fat) || 0,
      calories: Number(d.calories) || 0,
      planDate: d.planDate || null,
      isToday: d.isToday !== false
    }
  }

  if (metricsRes.status === 'fulfilled' && metricsRes.value) {
    bodyMetricsData.value = (metricsRes.value as any) || []
  }
}

const CHART_THEME = {
  backgroundColor: 'transparent',
  textStyle: { fontFamily: 'Aptos, sans-serif', color: '#645b53' },
  tooltip: {
    backgroundColor: '#f4eee8',
    borderColor: 'rgba(17, 17, 17, 0.1)',
    borderWidth: 1,
    textStyle: { color: '#1f1c19', fontSize: 12 },
    borderRadius: 16,
    padding: [8, 12],
    extraCssText: 'box-shadow: 0 18px 40px rgba(0,0,0,0.16);'
  },
  grid: { top: 30, right: 18, bottom: 28, left: 38, containLabel: true }
}

const initCharts = () => {
  const barData = weeklyTrainingData.value.length > 0
    ? weeklyTrainingData.value.map((d: any) => d.minutes || 0)
    : [0, 0, 0, 0, 0, 0, 0]
  const barLabels = weeklyTrainingData.value.length > 0
    ? weeklyTrainingData.value.map((d: any) => {
        const day = String(d.day || '')
        const map: Record<string, string> = { Monday: '周一', Tuesday: '周二', Wednesday: '周三', Thursday: '周四', Friday: '周五', Saturday: '周六', Sunday: '周日' }
        return map[day] || day.slice(0, 3)
      })
    : ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

  if (barChartRef.value) {
    barChart = echarts.init(barChartRef.value, null, { renderer: 'canvas' })
    barChart.setOption({
      ...CHART_THEME,
      tooltip: { ...CHART_THEME.tooltip, trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: barLabels,
        axisLine: { lineStyle: { color: 'rgba(100, 91, 83, 0.18)' } },
        axisTick: { show: false },
        axisLabel: { color: '#645b53', fontSize: 11, letterSpacing: 1 }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: 'rgba(100, 91, 83, 0.1)', type: 'dashed' } },
        axisLabel: { color: '#645b53', fontSize: 11 }
      },
      series: [{
        data: barData,
        type: 'bar',
        barWidth: '42%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#17181f' },
            { offset: 1, color: '#b79a72' }
          ]),
          borderRadius: [10, 10, 0, 0]
        }
      }]
    })
  }

  if (lineChartRef.value) {
    lineChart = echarts.init(lineChartRef.value, null, { renderer: 'canvas' })

    let lineLabels: string[] = []
    let lineWeightData: number[] = []
    let lineFatData: number[] = []
    let yMin = 60
    let yMax = 80

    if (bodyMetricsData.value.length > 0) {
      lineLabels = bodyMetricsData.value.map((d: any) => {
        const dateStr = String(d.date || '')
        return dateStr.slice(5).replace('-', '/')
      })
      lineWeightData = bodyMetricsData.value.map((d: any) => Number(d.weight) || 0)
      lineFatData = bodyMetricsData.value.map((d: any) => Number(d.bodyFat) || 0)
      if (lineWeightData.length > 0) {
        const wMin = Math.min(...lineWeightData)
        const wMax = Math.max(...lineWeightData)
        yMin = Math.floor(wMin - 2)
        yMax = Math.ceil(wMax + 2)
      }
    } else {
      lineLabels = ['01', '05', '10', '15', '20', '25', '30']
    }

    const seriesList: any[] = []
    if (lineWeightData.length > 0) {
      seriesList.push({
        name: '体重',
        data: lineWeightData,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#17181f' },
        itemStyle: { color: '#17181f', borderColor: '#f4eee8', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(23,24,31,0.18)' },
            { offset: 1, color: 'rgba(23,24,31,0)' }
          ])
        }
      })
    }
    if (lineFatData.length > 0 && lineFatData.some((v) => v > 0)) {
      seriesList.push({
        name: '体脂率',
        data: lineFatData,
        type: 'line',
        smooth: true,
        symbol: 'diamond',
        symbolSize: 6,
        lineStyle: { width: 2, color: '#b79a72', type: 'dashed' },
        itemStyle: { color: '#b79a72' }
      })
    }

    lineChart.setOption({
      ...CHART_THEME,
      tooltip: { ...CHART_THEME.tooltip, trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: lineLabels,
        axisLine: { lineStyle: { color: 'rgba(100, 91, 83, 0.18)' } },
        axisTick: { show: false },
        axisLabel: { color: '#645b53', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        min: lineWeightData.length > 0 ? yMin : undefined,
        max: lineWeightData.length > 0 ? yMax : undefined,
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: 'rgba(100, 91, 83, 0.1)', type: 'dashed' } },
        axisLabel: { color: '#645b53', fontSize: 11 }
      },
      series: seriesList.length > 0 ? seriesList : [{
        name: '体重',
        data: [],
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#17181f' },
        itemStyle: { color: '#17181f' }
      }]
    })
  }

  if (pieChartRef.value) {
    pieChart = echarts.init(pieChartRef.value, null, { renderer: 'canvas' })
    const pVal = Number(nutrition.value.protein) || 0
    const cVal = Number(nutrition.value.carbs) || 0
    const fVal = Number(nutrition.value.fat) || 0
    const hasData = pVal > 0 || cVal > 0 || fVal > 0

    pieChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { ...CHART_THEME.tooltip, trigger: 'item', formatter: hasData ? undefined : () => '暂无数据' },
      graphic: hasData ? [] : [{
        type: 'text',
        left: 'center',
        top: 'middle',
        style: {
          text: '暂无数据',
          fontSize: 14,
          fontFamily: 'Aptos, sans-serif',
          fill: 'rgba(100, 91, 83, 0.45)',
          letterSpacing: 2
        }
      }],
      series: [{
        type: 'pie',
        radius: ['46%', '76%'],
        center: ['50%', '50%'],
        data: hasData ? [
          { value: pVal * 4, name: '蛋白质', itemStyle: { color: '#17181f' } },
          { value: cVal * 4, name: '碳水化合物', itemStyle: { color: '#b79a72' } },
          { value: fVal * 9, name: '脂肪', itemStyle: { color: '#8d9aa4' } }
        ] : [
          { value: 1, name: '暂无数据', itemStyle: { color: 'rgba(17,17,17,0.08)' }, emphasis: { disabled: true } }
        ],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 10, borderColor: '#f4eee8', borderWidth: 2 },
        emphasis: hasData ? { scaleSize: 4 } : { disabled: true },
        silent: !hasData
      }]
    })
  }
}

const handleResize = () => {
  if (barChart) barChart.resize()
  if (lineChart) lineChart.resize()
  if (pieChart) pieChart.resize()
}

onMounted(async () => {
  await fetchDashboardData()
  initCharts()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (barChart) barChart.dispose()
  if (lineChart) lineChart.dispose()
  if (pieChart) pieChart.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard-content {
  width: 100%;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  margin-bottom: 28px;
}

.dashboard-copy {
  position: relative;
  padding-top: 56px;
  color: var(--text-main);
}

.dashboard-index {
  position: absolute;
  top: 0;
  left: 0;
  color: rgba(17, 17, 17, 0.12);
  font-family: var(--font-heading);
  font-size: clamp(4.8rem, 8vw, 7.2rem);
  line-height: 0.86;
}

.dashboard-title {
  max-width: 10ch;
  margin-top: 16px;
  color: var(--text-main);
}

.dashboard-description {
  max-width: 58ch;
  margin: 18px 0 0;
  color: var(--text-secondary);
  line-height: 1.84;
}

.dashboard-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.dashboard-spotlight {
  padding: 24px;
  color: var(--text-main);
}

.spotlight-row + .spotlight-row {
  margin-top: 18px;
}

.spotlight-row span {
  display: block;
  color: var(--text-secondary);
  font-size: 0.74rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.spotlight-row strong {
  display: block;
  margin-top: 10px;
  font-family: var(--font-heading);
  font-size: 1.7rem;
}

.spotlight-track {
  margin-top: 24px;
  height: 8px;
  border-radius: 999px;
  background: rgba(17, 17, 17, 0.08);
  overflow: hidden;
}

.spotlight-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--primary), var(--accent));
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 28px;
}

.metric-box {
  padding: 22px;
  color: var(--text-main);
}

.metric-kicker {
  display: block;
  color: var(--text-secondary);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.metric-value {
  display: block;
  margin-top: 12px;
  font-family: var(--font-heading);
  font-size: clamp(2rem, 4vw, 2.8rem);
  line-height: 0.96;
}

.metric-box p {
  margin: 12px 0 0;
  color: var(--text-secondary);
  line-height: 1.72;
}

.charts-grid,
.lower-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 28px;
}

.charts-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.lower-grid {
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.85fr);
}

.chart-card {
  padding: 24px;
  color: var(--text-main);
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.08);
}

.card-header h2 {
  margin-top: 8px;
  font-family: var(--font-heading);
  font-size: 1.5rem;
}

.chart-slot {
  width: 100%;
  height: 300px;
  margin-top: 14px;
}

.chart-slot-sm {
  height: 230px;
}

.heatmap {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 18px;
}

.heatmap-week {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}

.heatmap-cell {
  width: 100%;
  max-width: 28px;
  aspect-ratio: 1;
  border-radius: 5px;
  transition: transform 0.16s ease;
}

.heatmap-cell:hover {
  transform: scale(1.2);
}

.level-0 {
  background: rgba(17, 17, 17, 0.06);
}

.level-1 {
  background: rgba(141, 154, 164, 0.24);
}

.level-2 {
  background: rgba(141, 154, 164, 0.42);
}

.level-3 {
  background: rgba(183, 154, 114, 0.5);
}

.level-4 {
  background: #17181f;
}

.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
}

.legend-cell {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.macro-summary {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.macro-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid rgba(17, 17, 17, 0.08);
}

.macro-row span,
.macro-row strong {
  color: var(--text-main);
}

.macro-row span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.total-row {
  border-bottom: none;
  padding-top: 12px;
}

.macro-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.protein-dot {
  background: #17181f;
}

.carbs-dot {
  background: #b79a72;
}

.fat-dot {
  background: #8d9aa4;
}

.links-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 22px;
  border: none;
  color: var(--text-main);
  text-align: left;
}

.quick-code {
  display: inline-flex;
  width: 46px;
  height: 46px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(17, 17, 17, 0.04);
  color: var(--text-secondary);
  flex-shrink: 0;
  transition: color 0.15s, background 0.15s;
}
.quick-card:hover .quick-code {
  background: rgba(17, 17, 17, 0.08);
  color: var(--text-main);
}

.quick-card strong {
  font-family: var(--font-heading);
  font-size: 1.08rem;
}

@media (max-width: 1200px) {
  .metrics-grid,
  .links-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-hero,
  .charts-grid,
  .lower-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .links-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-actions .nd-btn {
    width: 100%;
  }
}

.dashboard-hero {
  gap: 0;
  border-top: 1px solid rgba(244, 238, 232, 0.12);
  padding-top: 24px;
}

.dashboard-spotlight {
  border-radius: 0;
  box-shadow: none;
}

.metrics-grid,
.charts-grid,
.lower-grid,
.links-grid {
  gap: 0;
}

.metric-box,
.chart-card,
.quick-card {
  border-radius: 0;
  box-shadow: none;
}

.metric-box,
.quick-card {
  border-left: none;
}

.metric-box p {
  max-width: 24ch;
}

.card-header {
  border-bottom-color: rgba(17, 17, 17, 0.12);
}

.quick-code {
  border-radius: 0;
  background: transparent;
}

@media (max-width: 1200px) {
  .metrics-grid,
  .charts-grid,
  .lower-grid,
  .links-grid {
    gap: 14px;
  }

  .metric-box,
  .chart-card,
  .quick-card,
  .dashboard-spotlight {
    border-left: 1px solid rgba(17, 17, 17, 0.12);
  }
}
</style>
