<template>
  <div class="appr-page">
    <div class="head-card">
      <div>
        <div class="head-title">到期鉴定台</div>
        <div class="head-sub">
          口径：归档年度 + 保管期限年数 ≤ {{ thisYear }} 年，在库与借出在外的卷都进台；已销毁的卷不再进台。
          到期卷先开鉴定单、过两人会签，会签齐了才动销毁或续存。
        </div>
      </div>
      <div class="head-stat">
        <div class="stat-num">{{ pending.length }}</div>
        <div class="stat-label">待鉴定卷</div>
      </div>
    </div>

    <div class="section">
      <div class="section-head">
        <span class="section-title">待鉴定名单</span>
        <span class="section-hint">共 {{ pending.length }} 卷 · 未结案 {{ openCount }} 卷</span>
      </div>

      <div v-for="r in pending" :key="r.archiveId" class="p-row" :class="{ open: r.openAppraisalId }">
        <div class="p-main">
          <div class="p-left">
            <div class="p-code">{{ r.code }}</div>
            <div class="p-title">{{ r.title }}</div>
            <div class="p-tags">
              <span class="chip">{{ r.roomName }}</span>
              <span class="chip">{{ r.archiveYear }} 年归档 · 保管 {{ r.keepYears }} 年</span>
              <span class="chip danger">{{ r.expireYear }} 年到期</span>
              <span class="chip" :class="r.archiveStatus === '在库' ? 's-ok' : 's-warn'">
                {{ r.archiveStatus }}
              </span>
              <span v-if="r.outOnLoan" class="chip s-warn">调阅中 · 归还后才能销毁</span>
              <span v-if="r.openAppraisalId" class="chip s-open">
                鉴定会签中 · {{ r.openDate }} 开单
              </span>
            </div>
          </div>
          <div class="p-act">
            <el-button
              v-if="!r.openAppraisalId"
              type="primary"
              size="small"
              @click="openSheet(r)"
            >
              开鉴定单
            </el-button>
            <el-button
              v-else
              type="warning"
              size="small"
              @click="openCountersign(r)"
            >
              会签提交
            </el-button>
          </div>
        </div>
      </div>
      <div v-if="!pending.length" class="empty">没有到期待鉴定的卷</div>
    </div>

    <div class="section">
      <div class="section-head">
        <span class="section-title">各库房现放卷数</span>
        <span class="section-hint">销毁会签齐了当天腾出位子，刷新即对账</span>
      </div>
      <div class="room-grid">
        <div v-for="rm in rooms" :key="rm.id" class="room-cell">
          <span class="room-name">{{ rm.code }} {{ rm.name }}</span>
          <span
            class="room-count"
            :class="{ over: isOver(rm), full: !isOver(rm) && registeredOf(rm) >= rm.capacity }"
          >
            {{ registeredOf(rm) }} / {{ rm.capacity }}
            <template v-if="isOver(rm)"> · 超容 {{ registeredOf(rm) - rm.capacity }}</template>
            <template v-else> · 还能放 {{ availableOf(rm) }}</template>
          </span>
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-head">
        <span class="section-title">鉴定单台账</span>
        <el-radio-group v-model="filter" size="small">
          <el-radio-button label="全部" />
          <el-radio-button label="未结案" />
          <el-radio-button label="已销毁" />
          <el-radio-button label="已续存" />
        </el-radio-group>
      </div>
      <el-table :data="sheets" size="small" stripe>
        <el-table-column label="单号" prop="id" width="70" />
        <el-table-column label="卷宗">
          <template #default="{ row }">{{ archiveLabel(row.archiveId) }}</template>
        </el-table-column>
        <el-table-column label="意见" width="80">
          <template #default="{ row }">
            <span :class="row.opinion === '销毁' ? 'op-bad' : 'op-ok'">
              {{ row.opinion || '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="鉴定人" prop="appraiser" width="100">
          <template #default="{ row }">{{ row.appraiser || '—' }}</template>
        </el-table-column>
        <el-table-column label="分管领导" prop="leader" width="100">
          <template #default="{ row }">{{ row.leader || '—' }}</template>
        </el-table-column>
        <el-table-column label="续存年数" width="90">
          <template #default="{ row }">{{ row.extendYears ? row.extendYears + ' 年' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" />
        <el-table-column label="开单日期" prop="createdDate" width="110" />
        <el-table-column label="结案日期" width="110">
          <template #default="{ row }">{{ row.closedDate || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dlg" title="到期鉴定 · 会签" width="480px">
      <div class="dlg-target" v-if="target">
        <span class="p-code">{{ target.code }}</span>
        <span>{{ target.title }}</span>
      </div>
      <el-form label-width="96px">
        <el-form-item label="鉴定意见">
          <el-radio-group v-model="form.opinion">
            <el-radio-button label="续存" />
            <el-radio-button label="销毁" />
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.opinion === '续存'" label="续存年数">
          <el-input-number v-model="form.extendYears" :min="1" />
          <span class="unit">年，保管期限按年数加上去</span>
        </el-form-item>
        <el-form-item label="鉴定人">
          <el-input v-model="form.appraiser" placeholder="鉴定人姓名" />
        </el-form-item>
        <el-form-item label="分管领导">
          <el-input v-model="form.leader" placeholder="分管领导姓名，不得与鉴定人相同" />
        </el-form-item>
      </el-form>
      <div v-if="target && target.outOnLoan && form.opinion === '销毁'" class="dlg-warn">
        这卷还在调阅中，销毁结论提交不了，请等归还后再提交。
      </div>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="submit">提交会签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { appraisalApi, archiveApi, roomApi } from '../api'

const pending = ref([])
const allSheets = ref([])
const archives = ref([])
const rooms = ref([])
const filter = ref('全部')
const dlg = ref(false)
const target = ref(null)
const form = ref({ opinion: '销毁', appraiser: '', leader: '', extendYears: 1 })

const thisYear = new Date().getFullYear()

const openCount = computed(() => pending.value.filter((r) => r.openAppraisalId).length)

const sheets = computed(() =>
  filter.value === '全部'
    ? allSheets.value
    : allSheets.value.filter((s) => s.status === filter.value)
)

function storedOf(roomId) {
  return archives.value.filter((a) => a.roomId === roomId && a.status !== '已销毁').length
}

function registeredOf(r) {
  return typeof r.registered === 'number' ? r.registered : storedOf(r.id)
}

function availableOf(r) {
  return typeof r.availableSlots === 'number'
    ? r.availableSlots
    : Math.max(r.capacity - storedOf(r.id), 0)
}

function isOver(r) {
  return typeof r.overCapacity === 'boolean'
    ? r.overCapacity
    : storedOf(r.id) > r.capacity
}

function archiveLabel(id) {
  const hit = archives.value.find((a) => a.id === id)
  return hit ? `${hit.code} ${hit.title}` : id
}

async function load() {
  try {
    const [p, s, a, r] = await Promise.all([
      appraisalApi.pending(),
      appraisalApi.list({}),
      archiveApi.list({}),
      roomApi.list({})
    ])
    pending.value = p
    allSheets.value = s
    archives.value = a
    rooms.value = r
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openSheet(row) {
  try {
    await appraisalApi.open(row.archiveId)
    ElMessage.success('已开鉴定单，请两人会签')
    await load()
    const fresh = pending.value.find((r) => r.archiveId === row.archiveId)
    if (fresh) openCountersign(fresh)
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

function openCountersign(row) {
  target.value = row
  form.value = {
    opinion: row.outOnLoan ? '续存' : '销毁',
    appraiser: '',
    leader: '',
    extendYears: 1
  }
  dlg.value = true
}

async function submit() {
  if (!target.value?.openAppraisalId) return
  try {
    await appraisalApi.submit(target.value.openAppraisalId, {
      opinion: form.value.opinion,
      appraiser: form.value.appraiser,
      leader: form.value.leader,
      extendYears: form.value.opinion === '续存' ? form.value.extendYears : null
    })
    ElMessage.success(
      form.value.opinion === '销毁' ? '会签齐，已销毁，库位当天腾出' : '会签齐，已续存'
    )
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.appr-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.head-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
}
.head-sub {
  font-size: 12px;
  color: #8b93a7;
  margin-top: 5px;
  max-width: 820px;
}
.head-stat {
  text-align: center;
  padding: 4px 22px;
}
.stat-num {
  font-size: 30px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.1;
}
.stat-label {
  font-size: 12px;
  color: #8b93a7;
}
.section {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 20px 18px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #3a4256;
}
.section-hint {
  font-size: 12px;
  color: #a0a7bb;
}
.p-row {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 8px;
}
.p-row.open {
  border-color: #f0d488;
  background: #fffdf5;
}
.p-main {
  display: flex;
  align-items: center;
  padding: 11px 14px;
  gap: 12px;
}
.p-left {
  flex: 1;
  min-width: 0;
}
.p-code {
  font-family: Menlo, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
}
.p-title {
  font-size: 14px;
  font-weight: 500;
  margin: 2px 0 6px;
}
.p-tags {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}
.chip {
  font-size: 12px;
  background: #f4f6fb;
  color: #5b6478;
  border-radius: 5px;
  padding: 2px 9px;
}
.chip.danger {
  background: #fdf0f0;
  color: #f56c6c;
}
.chip.s-ok {
  background: #f0f9eb;
  color: #529b2e;
}
.chip.s-warn {
  background: #fdf6ec;
  color: #b88230;
}
.chip.s-open {
  background: #fdf0e0;
  color: #cf6a00;
  font-weight: 600;
}
.empty {
  text-align: center;
  color: #b0b6c6;
  font-size: 13px;
  padding: 26px 0;
}
.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 10px;
}
.room-cell {
  display: flex;
  justify-content: space-between;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13px;
}
.room-count {
  font-family: Menlo, monospace;
  color: var(--el-color-primary);
}
.room-count.full {
  color: #e6a23c;
}
.room-count.over {
  color: #f56c6c;
  font-weight: 700;
}
.dlg-target {
  display: flex;
  gap: 10px;
  align-items: center;
  background: #f7f8fc;
  border-radius: 6px;
  padding: 9px 12px;
  margin-bottom: 14px;
  font-size: 13px;
}
.unit {
  margin-left: 8px;
  color: #8b93a7;
  font-size: 12px;
}
.dlg-warn {
  color: #b88230;
  background: #fdf6ec;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
}
.op-bad {
  color: #f56c6c;
  font-weight: 600;
}
.op-ok {
  color: #529b2e;
  font-weight: 600;
}
</style>
