<template>
  <div class="app-shell">
    <div class="app-bar">
      <div class="app-brand">
        <span class="app-mark">档</span>
        <span class="app-name">档案馆 · 库房与卷宗调阅管理</span>
      </div>
      <div class="app-tabbar">
        <div
          v-for="t in tabs"
          :key="t.path"
          class="tab"
          :class="{ active: route.path === t.path }"
          @click="go(t.path)"
        >
          {{ t.label }}
        </div>
      </div>
    </div>
    <div class="app-body">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/rooms', label: '库房与卷宗' },
  { path: '/archives', label: '案卷目录' },
  { path: '/appraisals', label: '到期鉴定台' },
  { path: '/retrievals', label: '调阅登记' },
  { path: '/checks', label: '库房温湿度' }
]

function go(path) {
  router.push(path)
}
</script>

<style>
.app-shell {
  min-height: 100vh;
  background: #f7f8fc;
}
.app-bar {
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 0 26px;
}
.app-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 0 10px;
}
.app-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
}
.app-tabbar {
  display: flex;
  gap: 4px;
}
.tab {
  padding: 9px 18px;
  font-size: 14px;
  color: #5b6478;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  border-radius: 6px 6px 0 0;
  transition: all 0.15s;
}
.tab:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.tab.active {
  color: var(--el-color-primary);
  font-weight: 600;
  border-bottom-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.app-body {
  padding: 18px 26px 34px;
}
</style>
