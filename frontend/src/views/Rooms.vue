<template>
  <div class="tree-page">
    <div class="tree-pane">
      <div class="pane-head">
        <span class="pane-title">库房 / 卷宗</span>
        <span class="pane-tools">
          <a @click="openRoom(null)">＋ 新库房</a>
          <a @click="openArchive(null)">＋ 新卷宗</a>
        </span>
      </div>

      <div v-for="r in rooms" :key="r.id" class="room-node">
        <div
          class="room-row"
          :class="{ picked: picked.type === 'room' && picked.data.id === r.id }"
          @click="pickRoom(r)"
        >
          <span class="caret" @click.stop="toggle(r.id)">{{ open.has(r.id) ? '▾' : '▸' }}</span>
          <span class="room-code">{{ r.code }}</span>
          <span class="room-name">{{ r.name }}</span>
          <span v-if="r.sealed" class="seal-tag">封库中</span>
          <span v-if="isOver(r)" class="over-tag">超容 {{ countOf(r.id) - r.capacity }} 卷</span>
          <span class="room-count" :class="{ over: isOver(r) }">{{ countOf(r.id) }}/{{ r.capacity }}</span>
          <span class="dot" :class="statusClass(r.status)"></span>
        </div>

        <div v-if="open.has(r.id)" class="child-list">
          <div
            v-for="a in archivesOf(r.id)"
            :key="a.id"
            class="child-row"
            :class="{ picked: picked.type === 'archive' && picked.data.id === a.id }"
            @click="pickArchive(a)"
          >
            <span class="child-code">{{ a.code }}</span>
            <span class="child-title">{{ a.title }}</span>
            <span v-if="openMap.has(a.id)" class="child-tag open">会签中</span>
            <span class="child-tag">{{ a.status }}</span>
          </div>
          <div v-if="!archivesOf(r.id).length" class="child-empty">这间库房还是空的</div>
        </div>
      </div>
    </div>

    <div class="detail-pane">
      <template v-if="picked.type === 'room'">
        <div class="detail-head">
          <span class="detail-title">{{ picked.data.code }} · {{ picked.data.name }}</span>
          <span class="detail-sub">库房</span>
        </div>
        <el-alert
          v-if="saveFail"
          class="fail-alert"
          type="error"
          show-icon
          :closable="false"
          :title="saveFail"
        />
        <el-form label-width="90px" class="detail-form">
          <el-form-item label="名称">
            <el-input v-model="roomForm.name" />
          </el-form-item>
          <el-form-item label="容量（卷）">
            <el-input-number v-model="roomForm.capacity" :min="1" />
            <span
              v-if="formCapacityBelowRegistered"
              class="lock-note"
            >
              低于在册 {{ countOf(picked.data.id) }} 卷，保存会被打回，得先挪走
              {{ countOf(picked.data.id) - roomForm.capacity }} 卷
            </span>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="roomForm.status">
              <el-radio-button label="在用" />
              <el-radio-button label="整理" />
              <el-radio-button label="停用" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="温度区间">
            <el-input-number v-model="roomForm.tempMin" :precision="1" :step="0.5" />
            <span class="unit">℃ ～</span>
            <el-input-number v-model="roomForm.tempMax" :precision="1" :step="0.5" />
            <span class="unit">℃</span>
          </el-form-item>
          <el-form-item label="湿度区间">
            <el-input-number v-model="roomForm.humidityMin" :precision="0" :step="1" :min="0" :max="100" />
            <span class="unit">% ～</span>
            <el-input-number v-model="roomForm.humidityMax" :precision="0" :step="1" :min="0" :max="100" />
            <span class="unit">%</span>
          </el-form-item>
        </el-form>
        <div v-if="picked.data.sealed" class="seal-note">
          封库中（{{ picked.data.sealedDate }} 起）：连续两班抄表越本库上下限。
          该库新调阅开不成，已借出的卷归还照常；封库只能由抄表解除——
          下一班抄表回到区间内即回温，改这里的状态或区间都解不了封。
        </div>
        <el-alert
          v-if="isOver(picked.data)"
          class="over-alert"
          type="error"
          show-icon
          :closable="false"
          title="这间库房已经超容"
          :description="capacityNoteOf(picked.data)"
        />
        <div class="detail-note">
          现放 {{ inStockOf(roomForm.id) }} 卷（在库）· 外借未还 {{ outOf(roomForm.id) }} 卷 ·
          在册 {{ registeredOf(picked.data) }} 卷，
          <span :class="{ 'note-over': isOver(picked.data) }">
            还能放 {{ availableOf(picked.data) }} 卷
          </span>。
        </div>
        <el-button type="primary" @click="saveRoom">保存库房</el-button>
      </template>

      <template v-else-if="picked.type === 'archive'">
        <div class="detail-head">
          <span class="detail-title">{{ picked.data.code }}</span>
          <span class="detail-sub">案卷</span>
        </div>
        <el-form label-width="90px" class="detail-form">
          <el-form-item label="题名">
            <el-input v-model="archiveForm.title" />
          </el-form-item>
          <el-form-item label="保管期限">
            <el-input-number
              v-model="archiveForm.keepYears"
              :min="1"
              :disabled="openMap.has(archiveForm.id)"
            />
            <span class="unit">年</span>
            <span v-if="openMap.has(archiveForm.id)" class="lock-note">
              会签中，不能改
            </span>
          </el-form-item>
          <el-form-item label="所属库房">
            <el-select v-model="archiveForm.roomId" style="width: 100%">
              <el-option
                v-for="r in rooms"
                :key="r.id"
                :label="`${r.code} ${r.name}（${r.status}）`"
                :value="r.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="archiveForm.status">
              <el-radio-button label="在库" />
              <el-radio-button label="已借出" />
            </el-radio-group>
            <span class="lock-note">销毁请到「到期鉴定台」会签</span>
          </el-form-item>
        </el-form>
        <div class="detail-note">
          {{ picked.data.archiveYear }} 年归档，保管 {{ archiveForm.keepYears }} 年 →
          {{ picked.data.archiveYear + archiveForm.keepYears }} 年到期。
        </div>
        <el-button type="primary" @click="saveArchive">保存案卷</el-button>
      </template>

      <div v-else class="detail-empty">
        <p>左边点一间库房或者一卷宗</p>
        <p class="tip">库房能看到放了哪些卷；卷宗能改题名、保管期限和所在库房。</p>
      </div>
    </div>
  </div>

  <el-dialog v-model="roomDlg" title="新增库房" width="440px">
    <el-form label-width="90px">
      <el-form-item label="编号">
        <el-input v-model="newRoom.code" placeholder="如 R-05" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="newRoom.name" placeholder="如 五号库房" />
      </el-form-item>
      <el-form-item label="容量（卷）">
        <el-input-number v-model="newRoom.capacity" :min="1" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="newRoom.status" style="width: 100%">
          <el-option label="在用" value="在用" />
          <el-option label="整理" value="整理" />
          <el-option label="停用" value="停用" />
        </el-select>
      </el-form-item>
      <el-form-item label="温度区间">
        <el-input-number v-model="newRoom.tempMin" :precision="1" :step="0.5" />
        <span class="unit">℃ ～</span>
        <el-input-number v-model="newRoom.tempMax" :precision="1" :step="0.5" />
        <span class="unit">℃</span>
      </el-form-item>
      <el-form-item label="湿度区间">
        <el-input-number v-model="newRoom.humidityMin" :precision="0" :step="1" :min="0" :max="100" />
        <span class="unit">% ～</span>
        <el-input-number v-model="newRoom.humidityMax" :precision="0" :step="1" :min="0" :max="100" />
        <span class="unit">%</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="roomDlg = false">取消</el-button>
      <el-button type="primary" @click="createRoom">建立</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="archiveDlg" title="新增案卷" width="500px">
    <el-form label-width="90px">
      <el-form-item label="卷宗号">
        <el-input v-model="newArchive.code" placeholder="如 D-2026-100" />
      </el-form-item>
      <el-form-item label="题名">
        <el-input v-model="newArchive.title" />
      </el-form-item>
      <el-form-item label="归档年度">
        <el-input-number v-model="newArchive.archiveYear" :min="1900" :max="2100" />
      </el-form-item>
      <el-form-item label="保管期限">
        <el-input-number v-model="newArchive.keepYears" :min="1" />
        <span class="unit">年</span>
      </el-form-item>
      <el-form-item label="放进库房">
        <el-select v-model="newArchive.roomId" style="width: 100%">
          <el-option
            v-for="r in rooms"
            :key="r.id"
            :label="`${r.code} ${r.name}（${r.status}）`"
            :value="r.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="archiveDlg = false">取消</el-button>
      <el-button type="primary" @click="createArchive">归档</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roomApi, archiveApi, appraisalApi } from '../api'

const rooms = ref([])
const archives = ref([])
const pending = ref([])
const open = ref(new Set())
const picked = ref({ type: null, data: {} })
const roomForm = ref({})
const archiveForm = ref({})
const roomDlg = ref(false)
const archiveDlg = ref(false)
const newRoom = ref({})
const newArchive = ref({})
// 容量改小等保存被服务端打回时，失败说明留在库房详情里，直到下次改对
const saveFail = ref('')

const openMap = computed(() => {
  const m = new Set()
  pending.value.forEach((r) => {
    if (r.openAppraisalId) m.add(r.archiveId)
  })
  return m
})

// 容量输入框一旦低于当前在册数，当场提示“保存会被打回”，不再只是改个颜色
const formCapacityBelowRegistered = computed(
  () => picked.value.type === 'room'
    && typeof roomForm.value.capacity === 'number'
    && roomForm.value.capacity < countOf(picked.value.data.id)
)

function statusClass(s) {
  if (s === '在用') return 'ok'
  if (s === '整理') return 'warn'
  return 'off'
}

function countOf(roomId) {
  return archives.value.filter((a) => a.roomId === roomId && a.status !== '已销毁').length
}

/** 在册数：优先用服务端按 archive 表实时算的口径，刷新即对账；旧数据回退本地统计。 */
function registeredOf(r) {
  return typeof r.registered === 'number' ? r.registered : countOf(r.id)
}

/** 还能放几卷：服务端给的不会是负数；回退时本地夹到 0。 */
function isOver(r) {
  return typeof r.overCapacity === 'boolean'
    ? r.overCapacity
    : countOf(r.id) > r.capacity
}

function availableOf(r) {
  return typeof r.availableSlots === 'number'
    ? r.availableSlots
    : Math.max(r.capacity - countOf(r.id), 0)
}

function capacityNoteOf(r) {
  if (r.capacityNote) return r.capacityNote
  const n = countOf(r.id)
  return `已经超容：在册 ${n} 卷，容量只有 ${r.capacity} 卷，多出 ${n - r.capacity} 卷。先把多出来的卷挪走，才能再收新卷或从别的库改挂卷进来。`
}

function inStockOf(roomId) {
  return archives.value.filter((a) => a.roomId === roomId && a.status === '在库').length
}

function outOf(roomId) {
  return archives.value.filter((a) => a.roomId === roomId && a.status === '已借出').length
}

function archivesOf(roomId) {
  return archives.value.filter((a) => a.roomId === roomId)
}

function toggle(id) {
  const s = new Set(open.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  open.value = s
}

function pickRoom(r) {
  picked.value = { type: 'room', data: { ...r } }
  roomForm.value = { ...r }
  saveFail.value = ''
  const s = new Set(open.value)
  s.add(r.id)
  open.value = s
}

function pickArchive(a) {
  picked.value = { type: 'archive', data: { ...a } }
  archiveForm.value = { ...a }
  saveFail.value = ''
}

async function load() {
  try {
    const [roomList, archiveList, pendingList] = await Promise.all([
      roomApi.list({}),
      archiveApi.list({}),
      appraisalApi.pending()
    ])
    rooms.value = roomList
    archives.value = archiveList
    pending.value = pendingList
    if (picked.value.type === 'room') {
      const hit = rooms.value.find((r) => r.id === picked.value.data.id)
      if (hit) {
        const keepFail = saveFail.value
        pickRoom(hit)
        saveFail.value = keepFail
      }
    } else if (picked.value.type === 'archive') {
      const hit = archives.value.find((a) => a.id === picked.value.data.id)
      if (hit) pickArchive(hit)
    } else if (rooms.value.length) {
      // 首次进来：默认展开第一间库房并选中它，一打开就有内容
      pickRoom(rooms.value[0])
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openRoom() {
  newRoom.value = {
    capacity: 50,
    status: '在用',
    tempMin: 14,
    tempMax: 24,
    humidityMin: 45,
    humidityMax: 60
  }
  roomDlg.value = true
}

function openArchive() {
  newArchive.value = { archiveYear: new Date().getFullYear(), keepYears: 10 }
  archiveDlg.value = true
}

async function createRoom() {
  try {
    await roomApi.create(newRoom.value)
    ElMessage.success('已建立')
    roomDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function createArchive() {
  try {
    await archiveApi.create(newArchive.value)
    ElMessage.success('已归档')
    archiveDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveRoom() {
  try {
    await roomApi.update(roomForm.value.id, roomForm.value)
    ElMessage.success('已保存')
    saveFail.value = ''
    await load()
  } catch (e) {
    // 容量改小被打回：失败说明留在详情上，刷新后库房在册数/还能放几卷仍按服务端口径对得上
    saveFail.value = `保存库房被拒：${e.message}`
    ElMessage.error(e.message)
    await load()
  }
}

async function saveArchive() {
  try {
    await archiveApi.update(archiveForm.value.id, archiveForm.value)
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.tree-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.tree-pane {
  width: 470px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-color-primary-light-9);
}
.pane-title {
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
}
.pane-tools a {
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}
.room-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f2f3f7;
}
.room-row:hover {
  background: #fafbff;
}
.room-row.picked {
  background: var(--el-color-primary-light-9);
}
.caret {
  width: 14px;
  color: #98a0b5;
  font-size: 12px;
}
.room-code {
  font-family: Menlo, monospace;
  font-size: 13px;
  color: var(--el-color-primary);
  width: 52px;
}
.room-name {
  flex: 1;
  font-size: 14px;
}
.room-count {
  font-size: 12px;
  color: #8b93a7;
}
.room-count.over {
  color: #f56c6c;
  font-weight: 600;
}
.over-tag {
  font-size: 11px;
  background: #fde2e2;
  color: #c45656;
  border-radius: 4px;
  padding: 1px 6px;
  font-weight: 600;
}
.over-alert {
  margin: 0 0 16px 90px;
}
.fail-alert {
  margin: 0 0 16px;
}
.note-over {
  color: #f56c6c;
  font-weight: 600;
}
.seal-tag {
  font-size: 11px;
  background: #fde2e2;
  color: #c45656;
  border-radius: 4px;
  padding: 1px 6px;
  font-weight: 600;
}
.seal-note {
  font-size: 13px;
  color: #c45656;
  background: #fef4f4;
  border-radius: 6px;
  padding: 10px 12px;
  margin: 0 0 16px 90px;
  line-height: 1.7;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.ok {
  background: #67c23a;
}
.dot.warn {
  background: #e6a23c;
}
.dot.off {
  background: #c0c4cc;
}
.child-list {
  background: #fbfcff;
  border-bottom: 1px solid #f2f3f7;
}
.child-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px 8px 44px;
  cursor: pointer;
  font-size: 13px;
}
.child-row:hover,
.child-row.picked {
  background: var(--el-color-primary-light-9);
}
.child-code {
  font-family: Menlo, monospace;
  color: #6b7280;
  width: 92px;
}
.child-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.child-tag {
  font-size: 12px;
  color: #8b93a7;
}
.child-tag.open {
  color: #cf6a00;
  background: #fdf0e0;
  border-radius: 4px;
  padding: 1px 7px;
}
.lock-note {
  margin-left: 10px;
  font-size: 12px;
  color: #b88230;
}
.child-empty {
  padding: 10px 16px 10px 44px;
  font-size: 12px;
  color: #b0b6c6;
}
.detail-pane {
  flex: 1;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 18px 20px;
  min-height: 300px;
}
.detail-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
}
.detail-sub {
  font-size: 12px;
  color: #8b93a7;
}
.detail-note {
  font-size: 13px;
  color: #6b7280;
  background: #f7f8fc;
  border-radius: 6px;
  padding: 10px 12px;
  margin: 0 0 16px 90px;
}
.detail-empty {
  color: #8b93a7;
  text-align: center;
  padding-top: 70px;
  font-size: 14px;
}
.detail-empty .tip {
  font-size: 12px;
  color: #b0b6c6;
  margin-top: 6px;
}
.unit {
  margin-left: 8px;
  color: #8b93a7;
  font-size: 13px;
}
</style>
