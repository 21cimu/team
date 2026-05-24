<template>
  <div class="home-screen">
    <header class="home-header nd-container">
      <button type="button" class="home-brand" @click="router.push('/')">
        <span class="home-brand-mark">FM</span>
        <span class="home-brand-copy">
          <strong>FitMind</strong>
          <small>智能训练系统</small>
        </span>
      </button>

      <nav class="home-nav">
        <button type="button" class="home-nav-link" @click="router.push('/app/dashboard')">总览</button>
        <button type="button" class="home-nav-link" @click="router.push('/app/training')">训练</button>
        <button type="button" class="home-nav-link" @click="router.push('/app/diet')">饮食</button>
        <button type="button" class="home-nav-link" @click="router.push('/app/community')">社区</button>
      </nav>

      <div class="home-actions">
        <button type="button" class="nd-btn" @click="router.push('/login')">登录</button>
        <button type="button" class="nd-btn primary" @click="handleStart">进入系统</button>
      </div>
    </header>

    <main class="home-hero nd-container">
      <section class="hero-stage">
        <div class="hero-frame">
          <div class="hero-frame-label">训练闭环</div>
          <div class="hero-frame-copy">
            <span class="hero-frame-index">01</span>
            <div>
              <strong>把训练变成一条连续的进步路径。</strong>
              <p>计划、恢复、饮食记录和 AI 教练在同一节奏里协同工作，而不是分散成彼此割裂的工具。</p>
            </div>
          </div>
          <div class="hero-frame-grid">
            <article>
              <span>信号</span>
              <strong>负荷协同</strong>
              <p>每周训练节奏、饮食摄入和恢复状态会被放在同一张图里理解。</p>
            </article>
            <article>
              <span>教练</span>
              <strong>上下文持续保留</strong>
              <p>AI 会结合你的身体档案、最近记录和当前目标持续给出建议。</p>
            </article>
          </div>
          <div class="hero-frame-track">
            <span></span>
          </div>
        </div>

        <div class="hero-notes">
          <div class="hero-note">
            <span>图谱</span>
            <strong>理解身体部位分布</strong>
          </div>
          <div class="hero-note">
            <span>饮食</span>
            <strong>以拍照记录为先</strong>
          </div>
          <div class="hero-note">
            <span>社区</span>
            <strong>轻量但持续的竞争氛围</strong>
          </div>
        </div>
      </section>

      <section class="hero-copy">
        <div class="text-label">[ FitMind 系统 ]</div>
        <div class="hero-number">01</div>
        <h1 class="text-display-xl hero-title">
          建立一套真正连续、
          <span>清晰可执行的训练方式</span>
        </h1>
        <p class="hero-description">
          FitMind 把健身重新组织成一条有节奏的日常流程。训练计划、饮食记录、
          进度历史、身体档案和 AI 指导在同一个空间内协同运作。
        </p>

        <div class="hero-actions">
          <button type="button" class="nd-btn primary" @click="handleStart">从个人档案开始</button>
          <button type="button" class="nd-btn" @click="router.push('/app/coach')">打开 AI 教练</button>
        </div>

        <div class="hero-ledger">
          <div class="hero-ledger-row">
            <span>模块</span>
            <strong>训练、饮食、图谱、社区</strong>
          </div>
          <div class="hero-ledger-row">
            <span>方式</span>
            <strong>聚焦内容、强调数据层次、减少无效操作</strong>
          </div>
          <div class="hero-ledger-row">
            <span>模式</span>
            <strong>AI 辅助下的日常持续进步</strong>
          </div>
        </div>
      </section>
    </main>

    <section class="home-manifest nd-container">
      <div class="manifest-heading">
        <div class="text-label">[ 系统说明 ]</div>
        <h2 class="text-display-lg">不是一面堆满工具的墙，而是一套更像训练刊物编排的前端体验。</h2>
      </div>

      <div class="manifest-grid">
        <article class="manifest-card nd-card">
          <span class="manifest-code">OV</span>
          <h3>总览像叙事</h3>
          <p>指标不再只是均匀铺开的仪表盘卡片，而是按阅读顺序组织成可理解的信息界面。</p>
        </article>
        <article class="manifest-card nd-card">
          <span class="manifest-code">TR</span>
          <h3>训练像序列</h3>
          <p>执行、恢复和 AI 干预被视作同一套训练流程中的连续动作，而不是孤立页面。</p>
        </article>
        <article class="manifest-card nd-card">
          <span class="manifest-code">NT</span>
          <h3>饮食像上下文</h3>
          <p>饮食记录会和训练负荷、身体适应一起被理解，而不是只留在单独的热量工具里。</p>
        </article>
        <article class="manifest-card nd-card">
          <span class="manifest-code">AI</span>
          <h3>AI 像教练编辑</h3>
          <p>助手会读取你的历史档案，并用一致的逻辑调整下一步训练和饮食建议。</p>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const handleStart = () => {
  if (userStore.token) {
    router.push('/app/dashboard')
  } else {
    router.push('/register')
  }
}
</script>

<style scoped>
.home-screen {
  min-height: 100vh;
  padding: 26px 0 72px;
}

.home-header {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 28px;
  background: rgba(14, 16, 22, 0.72);
  backdrop-filter: blur(18px);
}

.home-brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  border: none;
  background: transparent;
  color: var(--text-inverse);
  padding: 0;
}

.home-brand-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  border: 1px solid rgba(244, 238, 232, 0.14);
  background: rgba(244, 238, 232, 0.06);
  font-family: var(--font-heading);
  font-weight: 800;
  letter-spacing: 0.16em;
}

.home-brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.home-brand-copy strong {
  font-family: var(--font-heading);
  font-size: 1.14rem;
}

.home-brand-copy small {
  color: var(--text-inverse-muted);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.home-nav,
.home-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.home-nav {
  justify-content: center;
}

.home-nav-link {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(244, 238, 232, 0.08);
  border-radius: 999px;
  background: transparent;
  color: var(--text-inverse-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.home-nav-link:hover {
  color: var(--text-inverse);
  border-color: rgba(244, 238, 232, 0.16);
  background: rgba(244, 238, 232, 0.06);
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.92fr);
  gap: 34px;
  align-items: center;
  padding-top: 42px;
}

.hero-stage {
  display: grid;
  gap: 16px;
}

.hero-frame {
  position: relative;
  overflow: hidden;
  min-height: 560px;
  padding: 28px;
  border-radius: 36px;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.36), rgba(255, 255, 255, 0) 28%),
    var(--bg-panel-strong);
  color: var(--text-main);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.24);
}

.hero-frame::before {
  content: "";
  position: absolute;
  inset: 18px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  border-radius: 24px;
  pointer-events: none;
}

.hero-frame-label {
  color: var(--text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.hero-frame-copy {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 28px;
}

.hero-frame-index {
  color: rgba(17, 17, 17, 0.12);
  font-family: var(--font-heading);
  font-size: clamp(4.6rem, 8vw, 7rem);
  line-height: 0.84;
}

.hero-frame-copy strong {
  display: block;
  font-family: var(--font-heading);
  font-size: clamp(2.2rem, 4vw, 3.6rem);
  line-height: 0.96;
}

.hero-frame-copy p {
  max-width: 32ch;
  margin: 14px 0 0;
  color: var(--text-secondary);
  line-height: 1.76;
}

.hero-frame-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: auto;
  padding-top: 180px;
}

.hero-frame-grid article {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(255, 255, 255, 0.52);
}

.hero-frame-grid span,
.hero-note span,
.hero-ledger-row span {
  display: block;
  color: var(--text-secondary);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-frame-grid strong,
.hero-note strong,
.hero-ledger-row strong {
  display: block;
  margin-top: 10px;
  font-family: var(--font-heading);
  font-size: 1.14rem;
}

.hero-frame-grid p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.hero-frame-track {
  margin-top: 18px;
  height: 8px;
  border-radius: 999px;
  background: rgba(17, 17, 17, 0.08);
  overflow: hidden;
}

.hero-frame-track span {
  display: block;
  width: 72%;
  height: 100%;
  background: linear-gradient(90deg, var(--primary), var(--accent));
}

.hero-notes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-note {
  padding: 16px 18px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 20px;
  background: rgba(244, 238, 232, 0.05);
}

.hero-note strong {
  color: var(--text-inverse);
}

.hero-copy {
  position: relative;
  padding-top: 52px;
}

.hero-number {
  position: absolute;
  top: 0;
  right: 0;
  color: rgba(244, 238, 232, 0.1);
  font-family: var(--font-heading);
  font-size: clamp(5rem, 10vw, 8rem);
  line-height: 0.84;
}

.hero-title {
  max-width: 9ch;
  margin-top: 18px;
  color: var(--text-inverse);
}

.hero-title span {
  color: var(--bg-panel-strong);
}

.hero-description {
  max-width: 54ch;
  margin: 18px 0 0;
  color: var(--text-inverse-muted);
  line-height: 1.84;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero-ledger {
  display: grid;
  gap: 12px;
  margin-top: 28px;
}

.hero-ledger-row {
  padding: 16px 18px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 20px;
  background: rgba(244, 238, 232, 0.05);
}

.hero-ledger-row strong {
  color: var(--text-inverse);
}

.home-manifest {
  padding-top: 72px;
}

.manifest-heading {
  max-width: 62rem;
}

.manifest-heading h2 {
  margin-top: 12px;
  color: var(--text-inverse);
}

.manifest-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
}

.manifest-card {
  padding: 24px 22px;
  color: var(--text-main);
}

.manifest-code {
  display: inline-flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(17, 17, 17, 0.04);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-weight: 800;
  letter-spacing: 0.12em;
}

.manifest-card h3 {
  margin: 18px 0 12px;
  font-family: var(--font-heading);
  font-size: 1.2rem;
}

.manifest-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.74;
}

@media (max-width: 1100px) {
  .home-hero,
  .manifest-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 860px) {
  .home-header,
  .home-hero,
  .manifest-grid,
  .hero-frame-grid,
  .hero-notes {
    grid-template-columns: 1fr;
  }

  .home-nav,
  .home-actions {
    justify-content: flex-start;
  }

  .hero-copy {
    padding-top: 70px;
  }
}

@media (max-width: 560px) {
  .home-screen {
    padding-top: 14px;
  }

  .home-header,
  .hero-frame {
    padding: 18px;
  }

  .hero-actions,
  .home-actions {
    flex-direction: column;
  }

  .hero-actions .nd-btn,
  .home-actions .nd-btn {
    width: 100%;
  }
}

.home-screen {
  background:
    linear-gradient(90deg, transparent 0, transparent calc(50% - 1px), rgba(244, 238, 232, 0.05) calc(50% - 1px), rgba(244, 238, 232, 0.05) calc(50% + 1px), transparent calc(50% + 1px));
}

.home-header {
  border-radius: 0;
  border-color: rgba(244, 238, 232, 0.12);
  background: #0f1117;
  backdrop-filter: none;
}

.home-brand-mark,
.manifest-code {
  border-radius: 0;
  background: transparent;
}

.home-nav-link {
  border-radius: 0;
  border-left: none;
  border-right: none;
}

.hero-frame {
  min-height: 620px;
  border-radius: 0;
  background:
    linear-gradient(180deg, rgba(17, 17, 17, 0.03), rgba(17, 17, 17, 0)),
    var(--bg-panel-strong);
}

.hero-frame::before {
  inset: 0;
  border-radius: 0;
  border: none;
  border-top: 10px solid #0f1117;
  border-bottom: 10px solid #0f1117;
}

.hero-frame-grid {
  padding-top: 220px;
}

.hero-frame-grid article,
.manifest-card {
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.hero-frame-grid article {
  border-left: 2px solid rgba(17, 17, 17, 0.14);
}

.hero-notes,
.hero-ledger {
  gap: 0;
}

.hero-note,
.hero-ledger-row {
  border-radius: 0;
  background: transparent;
}

.hero-note + .hero-note,
.hero-ledger-row + .hero-ledger-row {
  border-top: none;
}

.hero-note {
  border-top: 1px solid rgba(244, 238, 232, 0.12);
}

.hero-ledger-row {
  border-top: 1px solid rgba(244, 238, 232, 0.12);
  border-left: none;
  border-right: none;
}

.manifest-grid {
  gap: 0;
  border-top: 1px solid rgba(244, 238, 232, 0.12);
  border-left: 1px solid rgba(244, 238, 232, 0.12);
}

.manifest-card {
  border-radius: 0;
  border-left: none;
  border-top: none;
}

@media (max-width: 860px) {
  .home-screen {
    background: none;
  }

  .manifest-grid {
    gap: 12px;
    border: none;
  }

  .manifest-card {
    border-left: 1px solid rgba(17, 17, 17, 0.12);
    border-top: 1px solid rgba(17, 17, 17, 0.12);
  }
}
</style>
