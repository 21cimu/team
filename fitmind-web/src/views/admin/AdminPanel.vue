<template>
  <div class="admin-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">系统管理</span>
          <strong class="topbar-page-title">后台总览</strong>
        </div>
        <div class="topbar-page-actions">
          <span class="topbar-page-meta">用户 {{ stats.totalUsers || 0 }}</span>
          <span class="topbar-page-meta">帖子 {{ stats.totalPosts || 0 }}</span>
          <span class="topbar-page-meta">成就 {{ stats.totalAchievements || 0 }}</span>
        </div>
      </div>
    </Teleport>

    <div class="page-header">
      <h2 class="text-title">系统管理</h2>
      <div class="header-stats">
        <div class="stat-card">
          <div class="stat-value text-primary">{{ stats.totalUsers || 0 }}</div>
          <div class="stat-label text-caption text-secondary">用户总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value text-primary">{{ stats.totalPosts || 0 }}</div>
          <div class="stat-label text-caption text-secondary">帖子总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value text-primary">{{ stats.totalAchievements || 0 }}</div>
          <div class="stat-label text-caption text-secondary">成就总数</div>
        </div>
      </div>
    </div>

    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">用户管理</button>
      <button class="tab-btn" :class="{ active: activeTab === 'posts' }" @click="activeTab = 'posts'">帖子管理</button>
      <button class="tab-btn" :class="{ active: activeTab === 'achievements' }" @click="activeTab = 'achievements'">成就管理</button>
    </div>

    <div v-if="activeTab === 'users'" class="tab-content">
      <div class="toolbar">
        <input v-model="userKeyword" class="neon-input" placeholder="搜索用户名/昵称..." @keyup.enter="loadUsers" />
        <button class="nd-btn" @click="loadUsers">搜索</button>
      </div>
      <div v-if="userLoading" class="loading-state"><span class="status-dot blink"></span> 加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>ID</th><th>用户名</th><th>昵称</th><th>邮箱</th><th>角色</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td>{{ u.email || '-' }}</td>
            <td><span class="role-tag" :class="u.role?.toLowerCase()">{{ u.role }}</span></td>
            <td><span class="status-tag" :class="u.status === 1 ? 'active' : 'disabled'">{{ u.status === 1 ? '正常' : '禁用' }}</span></td>
            <td class="actions">
              <button class="action-btn" @click="toggleUserStatus(u)">{{ u.status === 1 ? '禁用' : '启用' }}</button>
              <button class="action-btn" @click="toggleUserRole(u)">{{ u.role === 'ADMIN' ? '降为用户' : '升为管理员' }}</button>
              <button class="action-btn danger" @click="handleDeleteUser(u)" v-if="u.role !== 'ADMIN' && u.role !== 'admin'">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <button class="nd-btn" :disabled="userPage <= 1" @click="userPage--; loadUsers()">上一页</button>
        <span class="text-caption">第 {{ userPage }} 页</span>
        <button class="nd-btn" :disabled="users.length < userSize" @click="userPage++; loadUsers()">下一页</button>
      </div>
    </div>

    <div v-if="activeTab === 'posts'" class="tab-content">
      <div v-if="postLoading" class="loading-state"><span class="status-dot blink"></span> 加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr><th>ID</th><th>用户ID</th><th>内容</th><th>点赞</th><th>时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in posts" :key="p.id">
            <td>{{ p.id }}</td>
            <td>{{ p.userId }}</td>
            <td class="text-ellipsis">{{ p.content }}</td>
            <td>{{ p.likes }}</td>
            <td>{{ p.createTime }}</td>
            <td><button class="action-btn danger" @click="handleDeletePost(p)">删除</button></td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <button class="nd-btn" :disabled="postPage <= 1" @click="postPage--; loadPosts()">上一页</button>
        <span class="text-caption">第 {{ postPage }} 页</span>
        <button class="nd-btn" :disabled="posts.length < postSize" @click="postPage++; loadPosts()">下一页</button>
      </div>
    </div>

    <div v-if="activeTab === 'achievements'" class="tab-content">
      <button class="nd-btn mb-md" @click="showAddAchievement = true">+ 新增成就</button>
      <div v-if="showAddAchievement" class="form-card mb-md">
        <div class="form-row">
          <input v-model="achForm.name" class="neon-input" placeholder="成就名称" />
          <input v-model="achForm.icon" class="neon-input" placeholder="图标 (emoji)" />
        </div>
        <div class="form-row">
          <input v-model="achForm.category" class="neon-input" placeholder="分类 (training/nutrition/social)" />
          <input v-model="achForm.rarity" class="neon-input" placeholder="稀有度 (common/rare/epic/legendary)" />
        </div>
        <div class="form-row">
          <input v-model.number="achForm.target" class="neon-input" type="number" placeholder="目标值" />
          <input v-model.number="achForm.sortOrder" class="neon-input" type="number" placeholder="排序" />
        </div>
        <textarea v-model="achForm.description" class="neon-input" placeholder="成就描述" rows="2"></textarea>
        <div class="form-actions">
          <button class="nd-btn" @click="handleCreateAchievement">创建</button>
          <button class="nd-btn secondary" @click="showAddAchievement = false">取消</button>
        </div>
      </div>
      <div v-if="achLoading" class="loading-state"><span class="status-dot blink"></span> 加载中...</div>
      <div v-else class="achievement-grid">
        <div v-for="a in achievements" :key="a.id" class="achievement-card">
          <div class="ach-icon">{{ a.icon }}</div>
          <div class="ach-info">
            <div class="ach-name text-label">{{ a.name }}</div>
            <div class="ach-desc text-caption text-secondary">{{ a.description }}</div>
            <div class="ach-meta text-caption">
              <span class="meta-tag">{{ a.category }}</span>
              <span class="meta-tag" :class="a.rarity">{{ a.rarity }}</span>
              <span>目标: {{ a.target }}</span>
            </div>
          </div>
          <button class="action-btn danger" @click="handleDeleteAchievement(a)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getAdminDashboard, listUsers, updateUserStatus, updateUserRole, deleteUser, listPosts, deletePost, createAchievement, deleteAchievement } from '../../api/admin'
import { getMyAchievements } from '../../api/achievement'

const activeTab = ref('users')
const stats = ref<any>({})

const users = ref<any[]>([])
const userKeyword = ref('')
const userPage = ref(1)
const userSize = ref(20)
const userLoading = ref(false)

const posts = ref<any[]>([])
const postPage = ref(1)
const postSize = ref(20)
const postLoading = ref(false)

const achievements = ref<any[]>([])
const achLoading = ref(false)
const showAddAchievement = ref(false)
const achForm = ref({
  name: '', description: '', icon: '', category: 'training', rarity: 'common', target: 1, sortOrder: 0
})

const loadStats = async () => {
  try { stats.value = await getAdminDashboard() as any } catch {}
}

const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await listUsers({ page: userPage.value, size: userSize.value, keyword: userKeyword.value || undefined }) as any
    users.value = res?.records || []
  } catch { users.value = [] }
  finally { userLoading.value = false }
}

const loadPosts = async () => {
  postLoading.value = true
  try {
    const res = await listPosts({ page: postPage.value, size: postSize.value }) as any
    posts.value = res?.records || []
  } catch { posts.value = [] }
  finally { postLoading.value = false }
}

const loadAchievements = async () => {
  achLoading.value = true
  try {
    const res = await getMyAchievements() as any
    achievements.value = Array.isArray(res) ? res : []
  } catch { achievements.value = [] }
  finally { achLoading.value = false }
}

const toggleUserStatus = async (u: any) => {
  try {
    await updateUserStatus(u.id, u.status === 1 ? 0 : 1)
    loadUsers()
  } catch {}
}

const toggleUserRole = async (u: any) => {
  try {
    await updateUserRole(u.id, u.role === 'ADMIN' || u.role === 'admin' ? 'USER' : 'ADMIN')
    loadUsers()
  } catch {}
}

const handleDeleteUser = async (u: any) => {
  if (!confirm(`确定删除用户 ${u.username}?`)) return
  try { await deleteUser(u.id); loadUsers() } catch {}
}

const handleDeletePost = async (p: any) => {
  if (!confirm('确定删除该帖子?')) return
  try { await deletePost(p.id); loadPosts() } catch {}
}

const handleCreateAchievement = async () => {
  try {
    await createAchievement(achForm.value)
    showAddAchievement.value = false
    achForm.value = { name: '', description: '', icon: '', category: 'training', rarity: 'common', target: 1, sortOrder: 0 }
    loadAchievements()
  } catch {}
}

const handleDeleteAchievement = async (a: any) => {
  if (!confirm(`确定删除成就 ${a.name}?`)) return
  try { await deleteAchievement(a.id); loadAchievements() } catch {}
}

watch(activeTab, (tab) => {
  if (tab === 'users') loadUsers()
  else if (tab === 'posts') loadPosts()
  else if (tab === 'achievements') loadAchievements()
})

onMounted(() => { loadStats(); loadUsers() })
</script>

<style scoped>
.admin-page { padding: 20px; }

.page-header { margin-bottom: 24px; }
.header-stats { display: flex; gap: 16px; margin-top: 16px; }
.stat-card {
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(249, 115, 22, 0.2);
  padding: 16px 24px;
  border-radius: 4px;
  min-width: 120px;
  text-align: center;
}
.stat-value { font-size: 28px; font-weight: 700; font-family: var(--font-heading); }
.stat-label { margin-top: 4px; }

.tabs { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 1px solid rgba(248, 250, 252, 0.1); padding-bottom: 8px; }
.tab-btn {
  background: none; border: 1px solid transparent; color: var(--text-secondary);
  padding: 8px 16px; cursor: pointer; font-family: var(--font-heading); font-size: 13px;
  border-radius: 4px 4px 0 0; transition: all 0.15s;
}
.tab-btn:hover { color: var(--text-primary); }
.tab-btn.active { color: var(--primary); border-color: var(--primary); border-bottom-color: transparent; background: rgba(249, 115, 22, 0.05); }

.toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
.neon-input {
  background: rgba(17, 24, 39, 0.6); border: 1px solid rgba(248, 250, 252, 0.1);
  color: var(--text-primary); padding: 8px 12px; border-radius: 4px; font-family: var(--font-heading);
  font-size: 13px; outline: none; transition: border-color 0.15s;
}
.neon-input:focus { border-color: var(--primary); }

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 10px 12px; border-bottom: 1px solid rgba(249, 115, 22, 0.3); color: var(--primary); font-family: var(--font-heading); font-weight: 400; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid rgba(248, 250, 252, 0.05); }
.data-table tr:hover { background: rgba(249, 115, 22, 0.03); }
.text-ellipsis { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.role-tag, .status-tag {
  display: inline-block; padding: 2px 8px; border-radius: 2px; font-size: 11px;
  font-family: var(--font-heading); text-transform: uppercase;
}
.role-tag.admin, .role-tag.ADMIN { background: rgba(249, 115, 22, 0.2); color: var(--primary); }
.role-tag.user, .role-tag.USER { background: rgba(34, 197, 94, 0.2); color: #22c55e; }
.status-tag.active { background: rgba(34, 197, 94, 0.2); color: #22c55e; }
.status-tag.disabled { background: rgba(239, 68, 68, 0.2); color: #ef4444; }

.actions { display: flex; gap: 6px; }
.action-btn {
  background: none; border: 1px solid rgba(248, 250, 252, 0.1); color: var(--text-secondary);
  padding: 4px 8px; font-size: 11px; cursor: pointer; border-radius: 2px;
  font-family: var(--font-heading); transition: all 0.15s;
}
.action-btn:hover { border-color: var(--primary); color: var(--primary); }
.action-btn.danger:hover { border-color: #ef4444; color: #ef4444; }

.pagination { display: flex; align-items: center; gap: 12px; margin-top: 16px; }

.form-card {
  background: rgba(17, 24, 39, 0.6); border: 1px solid rgba(249, 115, 22, 0.2);
  padding: 16px; border-radius: 4px;
}
.form-row { display: flex; gap: 8px; margin-bottom: 8px; }
.form-row .neon-input { flex: 1; }
textarea.neon-input { width: 100%; resize: vertical; }
.form-actions { display: flex; gap: 8px; margin-top: 12px; }

.achievement-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.achievement-card {
  display: flex; align-items: center; gap: 12px;
  background: rgba(17, 24, 39, 0.6); border: 1px solid rgba(248, 250, 252, 0.05);
  padding: 12px; border-radius: 4px;
}
.ach-icon { font-size: 28px; flex-shrink: 0; }
.ach-info { flex: 1; overflow: hidden; }
.ach-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ach-desc { font-size: 11px; margin-top: 2px; }
.ach-meta { display: flex; gap: 6px; margin-top: 4px; align-items: center; }
.meta-tag {
  padding: 1px 6px; border-radius: 2px; font-size: 10px;
  background: rgba(248, 250, 252, 0.05); text-transform: uppercase;
}
.meta-tag.rare { background: rgba(59, 130, 246, 0.2); color: #3b82f6; }
.meta-tag.epic { background: rgba(168, 85, 247, 0.2); color: #a855f7; }
.meta-tag.legendary { background: rgba(249, 115, 22, 0.2); color: var(--primary); }

.loading-state { padding: 40px; text-align: center; }
.mb-md { margin-bottom: 12px; }
</style>
