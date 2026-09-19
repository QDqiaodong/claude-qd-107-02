<template>
  <div class="tab-page">
    <div class="tab-strip">
      <div
        v-for="t in tabs"
        :key="t.key"
        class="tab-cell"
        :class="{ on: active === t.key }"
        @click="active = t.key"
      >
        <span class="tab-label">{{ t.label }}</span>
        <span class="tab-num">{{ t.count }}</span>
      </div>
      <div class="tab-add" @click="openNew">＋ 归档新卷</div>
    </div>

    <div class="strip-note">
      <span v-if="active === 'all'">全部 {{ rows.length }} 卷</span>
      <span v-else>{{ active }} 年归档 {{ rows.length }} 卷</span>
      <span class="sep">·</span>
      <span class="warn-text">其中 {{ expiredCount }} 卷已到保管期限</span>
    </div>

    <div class="card-list">
      <div v-for="a in rows" :key="a.id" class="row-card" :class="{ dead: a.status === '已销毁' }">
        <div class="row-main">
          <div class="row-left">
            <div class="row-code">{{ a.code }}</div>
            <div class="row-title">{{ a.title }}</div>
          </div>
          <div class="row-meta">
            <span class="chip">{{ roomLabel(a.roomId) }}</span>
            <span class="chip">保管 {{ a.keepYears }} 年</span>
            <span class="chip" :class="{ danger: isExpired(a) }">
              {{ a.archiveYear + a.keepYears }} 年到期
              <template v-if="isExpired(a)">· 已到期</template>
            </span>
            <span v-if="openMap.has(a.id)" class="chip s-open">鉴定会签中</span>
            <span class="chip status" :class="statusClass(a.status)">{{ a.status }}</span>
            <a class="edit-link" @click="toggleEdit(a.id)">
              {{ editing === a.id ? '收起' : '调整' }}
            </a>
          </div>
        </div>

        <div v-if="editing === a.id" class="row-edit">
          <el-form label-width="88px" class="edit-form">
            <el-form-item label="题名">
              <el-input v-model="form.title" />
            </el-form-item>
            <el-form-item label="所在库房">
              <el-select v-model="form.roomId" style="width: 100%">
                <el-option
                  v-for="r in rooms"
                  :key="r.id"
                  :label="roomOptionLabel(r)"
                  :value="r.id"
                  :disabled="!canReceive(r)"
                />
              </el-select>
              <span v-if="form.roomId && !canReceive(roomById(form.roomId))" class="lock-note">
                {{ roomBlockReason(roomById(form.roomId)) }}
              </span>
            </el-form-item>
            <el-form-item label="保管期限">
              <el-input-number v-model="form.keepYears" :min="1" :disabled="openMap.has(a.id)" />
              <span class="unit">年</span>
              <span v-if="openMap.has(a.id)" class="lock-note">
                这卷正在鉴定会签中，会签没齐不许改保管期限
              </span>
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio-button label="在库" />
                <el-radio-button label="已借出" />
              </el-radio-group>
              <span class="lock-note">
                已销毁不能从目录直接改，到期卷要到「到期鉴定台」过会签
              </span>
            </el-form-item>
          </el-form>
          <el-button type="primary" size="small" @click="save">保存</el-button>
          <el-button size="small" @click="editing = null">取消</el-button>
        </div>
      </div>

      <div v-if="!rows.length" class="empty">这一年还没有归档的卷</div>
    </div>

    <el-dialog v-model="dlg" title="归档新卷" width="510px">
      <el-form label-width="88px">
        <el-form-item label="卷宗号">
          <el-input v-model="newForm.code" placeholder="如 D-2026-101" />
        </el-form-item>
        <el-form-item label="题名">
          <el-input v-model="newForm.title" />
        </el-form-item>
        <el-form-item label="归档年度">
          <el-input-number v-model="newForm.archiveYear" :min="1900" :max="2100" />
        </el-form-item>
        <el-form-item label="保管期限">
          <el-input-number v-model="newForm.keepYears" :min="1" />
          <span class="unit">年</span>
        </el-form-item>
        <el-form-item label="放进库房">
          <el-select v-model="newForm.roomId" style="width: 100%">
            <el-option
              v-for="r in rooms"
              :key="r.id"
              :label="roomOptionLabel(r)"
              :value="r.id"
              :disabled="!canReceive(r)"
            />
          </el-select>
          <span v-if="newForm.roomId && !canReceive(roomById(newForm.roomId))" class="lock-note">
            {{ roomBlockReason(roomById(newForm.roomId)) }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="create">归档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveApi, roomApi, appraisalApi } from '../api'

const all = ref([])
const rooms = ref([])
const pending = ref([])
const active = ref('all')
const editing = ref(null)
const form = ref({})
const dlg = ref(false)
const newForm = ref({})

const thisYear = new Date().getFullYear()

/** 正在会签中的卷，目录侧不许改保管期限、不许动状态 */
const openMap = computed(() => {
  const m = new Set()
  pending.value.forEach((r) => {
    if (r.openAppraisalId) m.add(r.archiveId)
  })
  return m
})

const tabs = computed(() => {
  const years = [...new Set(all.value.map((a) => a.archiveYear))].sort((x, y) => y - x)
  const list = [{ key: 'all', label: '全部', count: all.value.length }]
  years.forEach((y) => {
    list.push({ key: y, label: String(y), count: all.value.filter((a) => a.archiveYear === y).length })
  })
  return list
})

const rows = computed(() =>
  active.value === 'all' ? all.value : all.value.filter((a) => a.archiveYear === active.value)
)

const expiredCount = computed(() => all.value.filter(isExpired).length)

function isExpired(a) {
  return a.archiveYear + a.keepYears <= thisYear
}

function roomLabel(id) {
  const hit = rooms.value.find((r) => r.id === id)
  return hit ? hit.name : id
}

function roomById(id) {
  return rooms.value.find((r) => r.id === id) || {}
}

/** 一间库房能不能再收一卷：得在用、且在册没到容量。在册口径与库房页一致。 */
function registeredOf(r) {
  return typeof r.registered === 'number'
    ? r.registered
    : all.value.filter((a) => a.roomId === r.id && a.status !== '已销毁').length
}

function canReceive(r) {
  return r && r.status === '在用' && registeredOf(r) < r.capacity
}

function roomOptionLabel(r) {
  const n = registeredOf(r)
  if (r.status !== '在用') return `${r.code} ${r.name}（${r.status}，不能放卷）`
  if (n > r.capacity) return `${r.code} ${r.name}（超容 ${n - r.capacity} 卷，不能再收）`
  if (n === r.capacity) return `${r.code} ${r.name}（已放满，腾位后再收）`
  return `${r.code} ${r.name}（${r.status}，还能放 ${r.capacity - n} 卷）`
}

function roomBlockReason(r) {
  if (!r || !r.id) return ''
  if (r.status !== '在用') return `这间库房现在是「${r.status}」，不能往里放卷`
  const n = registeredOf(r)
  if (n > r.capacity) return `这间库房已超容 ${n - r.capacity} 卷，先挪走多出来的卷`
  if (n === r.capacity) return '这间库房已经放满，先腾出位子'
  return ''
}

function statusClass(s) {
  if (s === '在库') return 's-ok'
  if (s === '已借出') return 's-warn'
  return 's-off'
}

function toggleEdit(id) {
  if (editing.value === id) {
    editing.value = null
    return
  }
  const hit = all.value.find((a) => a.id === id)
  form.value = { ...hit }
  editing.value = id
}

async function load() {
  try {
    const [archiveList, roomList, pendingList] = await Promise.all([
      archiveApi.list({}),
      roomApi.list({}),
      appraisalApi.pending()
    ])
    all.value = archiveList
    rooms.value = roomList
    pending.value = pendingList
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  const target = rooms.value.find(canReceive)
  newForm.value = {
    archiveYear: thisYear,
    keepYears: 10,
    roomId: target ? target.id : (rooms.value[0] && rooms.value[0].id)
  }
  dlg.value = true
}

async function create() {
  try {
    await archiveApi.create(newForm.value)
    ElMessage.success('已归档')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function save() {
  try {
    await archiveApi.update(form.value.id, form.value)
    ElMessage.success('已保存')
    editing.value = null
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.tab-page {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 0 0 18px;
}
.tab-strip {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 0 16px;
}
.tab-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  color: #5b6478;
}
.tab-cell:hover {
  color: var(--el-color-primary);
}
.tab-cell.on {
  color: var(--el-color-primary);
  font-weight: 600;
  border-bottom-color: var(--el-color-primary);
}
.tab-num {
  font-size: 12px;
  background: #eef1f8;
  color: #6b7280;
  border-radius: 9px;
  padding: 1px 7px;
}
.tab-cell.on .tab-num {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
}
.tab-add {
  margin-left: auto;
  align-self: center;
  font-size: 13px;
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 15px;
  padding: 6px 15px;
  cursor: pointer;
}
.strip-note {
  padding: 12px 20px 4px;
  font-size: 13px;
  color: #6b7280;
}
.strip-note .sep {
  margin: 0 8px;
  color: #ccd1de;
}
.warn-text {
  color: #e6a23c;
}
.card-list {
  padding: 8px 20px 0;
}
.row-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 10px;
  overflow: hidden;
}
.row-card.dead {
  opacity: 0.6;
}
.row-main {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  gap: 18px;
}
.row-left {
  flex: 1;
  min-width: 0;
}
.row-code {
  font-family: Menlo, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
  margin-bottom: 3px;
}
.row-title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.chip {
  font-size: 12px;
  background: #f4f6fb;
  color: #5b6478;
  border-radius: 5px;
  padding: 3px 9px;
}
.chip.danger {
  background: #fdf0f0;
  color: #f56c6c;
}
.chip.status.s-ok {
  background: #f0f9eb;
  color: #529b2e;
}
.chip.status.s-warn {
  background: #fdf6ec;
  color: #b88230;
}
.chip.status.s-off {
  background: #f4f4f5;
  color: #909399;
}
.chip.s-open {
  background: #fdf0e0;
  color: #cf6a00;
  font-weight: 600;
}
.lock-note {
  margin-left: 10px;
  font-size: 12px;
  color: #b88230;
}
.edit-link {
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  margin-left: 4px;
}
.row-edit {
  padding: 14px 18px 16px;
  background: #fafbff;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.edit-form {
  max-width: 520px;
}
.empty {
  text-align: center;
  color: #b0b6c6;
  font-size: 13px;
  padding: 40px 0;
}
.unit {
  margin-left: 8px;
  color: #8b93a7;
  font-size: 13px;
}
</style>
