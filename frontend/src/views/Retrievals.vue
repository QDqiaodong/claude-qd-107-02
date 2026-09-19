<template>
  <div class="transfer-page">
    <el-alert
      v-if="sealedRooms.length"
      class="seal-banner"
      type="error"
      show-icon
      :closable="false"
      :title="`${sealedRooms.map((r) => r.name).join('、')} 封库中：连续两班抄表越限未回温，该库新调阅开不成；已借出的卷归还照常，下一班抄表回到本库区间即回温。`"
    />
    <el-alert
      v-if="failMsg"
      class="fail-alert"
      type="error"
      show-icon
      :title="failMsg"
      @close="failMsg = ''"
    />
    <div class="transfer">
      <div class="side">
        <div class="side-head">
          <span class="side-title">库里可借的卷</span>
          <span class="side-num">{{ available.length }}</span>
        </div>
        <div class="side-body">
          <div
            v-for="a in available"
            :key="a.id"
            class="item"
            :class="{ picked: leftPick === a.id }"
            @click="leftPick = a.id"
          >
            <div class="item-code">
              {{ a.code }}
              <span v-if="roomOf(a).sealed" class="seal-tag">封库中</span>
            </div>
            <div class="item-title">{{ a.title }}</div>
            <div class="item-sub">{{ a.archiveYear }} 年 · 保管 {{ a.keepYears }} 年</div>
          </div>
          <div v-if="!available.length" class="side-empty">库里没有可借的卷了</div>
        </div>
      </div>

      <div class="mid">
        <div class="arrow" :class="{ on: leftPick }" @click="toOut">借出 →</div>
        <div class="arrow" :class="{ on: rightPick }" @click="doReturn">← 收回</div>
      </div>

      <div class="side">
        <div class="side-head">
          <span class="side-title">借出在外</span>
          <span class="side-num">{{ out.length }}</span>
        </div>
        <div class="side-body">
          <div
            v-for="r in out"
            :key="r.id"
            class="item"
            :class="{ picked: rightPick === r.id, over: overdue(r) > 0 }"
            @click="rightPick = r.id"
          >
            <div class="item-code">{{ archiveCode(r.archiveId) }}</div>
            <div class="item-title">{{ archiveTitle(r.archiveId) }}</div>
            <div class="item-sub">
              {{ r.visitor }} · {{ r.dept }}
            </div>
            <div class="item-sub">
              应还 {{ r.dueDate }}
              <span v-if="overdue(r) > 0" class="over">已逾期 {{ overdue(r) }} 天</span>
            </div>
          </div>
          <div v-if="!out.length" class="side-empty">现在没有借出去的卷</div>
        </div>
      </div>
    </div>

    <div class="borrow-bar">
      <template v-if="leftPick">
        <span class="bar-title">登记借出：{{ archiveCode(leftPick) }}</span>
        <span v-if="pickedRoomSealed" class="seal-warn">
          所在库房封库中，这单开不成
        </span>
        <el-input v-model="form.visitor" placeholder="调阅人" style="width: 130px" />
        <el-input v-model="form.dept" placeholder="所在单位" style="width: 170px" />
        <el-date-picker
          v-model="form.retrieveDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="调阅日期"
          style="width: 150px"
        />
        <el-date-picker
          v-model="form.dueDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="应还日期"
          style="width: 150px"
        />
        <el-button type="primary" @click="toOut">确认借出</el-button>
      </template>
      <template v-else-if="rightPick">
        <span class="bar-title">
          收回：{{ archiveCode(rightPick) }} · {{ visitorOf(rightPick) }}
        </span>
        <el-date-picker
          v-model="returnDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="归还日期"
          style="width: 150px"
        />
        <el-button type="primary" @click="doReturn">确认收回</el-button>
      </template>
      <template v-else>
        <span class="bar-hint">左边挑一卷借出去，右边挑一条收回来</span>
      </template>
    </div>

    <div class="history">
      <div class="history-head" @click="showHistory = !showHistory">
        <span>归还档案（{{ history.length }} 条）</span>
        <span class="fold">{{ showHistory ? '收起 ▴' : '展开 ▾' }}</span>
      </div>
      <div v-if="showHistory" class="history-body">
        <div v-for="r in history" :key="r.id" class="hist-row">
          <span class="hist-code">{{ archiveCode(r.archiveId) }}</span>
          <span class="hist-title">{{ archiveTitle(r.archiveId) }}</span>
          <span class="hist-who">{{ r.visitor }}（{{ r.dept }}）</span>
          <span class="hist-date">{{ r.retrieveDate }} → {{ r.returnDate }}</span>
        </div>
        <div v-if="!history.length" class="side-empty">还没有归还记录</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { retrievalApi, archiveApi, roomApi } from '../api'

const archives = ref([])
const retrievals = ref([])
const rooms = ref([])
const leftPick = ref(null)
const rightPick = ref(null)
const form = ref({})
const returnDate = ref(new Date().toISOString().slice(0, 10))
const showHistory = ref(true)
// 硬开封库的新单被拦下时，失败说明留在页面上，直到手动关掉
const failMsg = ref('')

const available = computed(() => archives.value.filter((a) => a.status === '在库'))
const out = computed(() => retrievals.value.filter((r) => r.status === '调阅中'))
const history = computed(() => retrievals.value.filter((r) => r.status === '已归还'))

const sealedRooms = computed(() => rooms.value.filter((r) => r.sealed))

function roomOf(a) {
  return rooms.value.find((r) => r.id === a.roomId) || {}
}

const pickedRoomSealed = computed(() => {
  const a = archives.value.find((x) => x.id === leftPick.value)
  return !!(a && roomOf(a).sealed)
})

function archiveCode(id) {
  const hit = archives.value.find((a) => a.id === id)
  return hit ? hit.code : id
}

function archiveTitle(id) {
  const hit = archives.value.find((a) => a.id === id)
  return hit ? hit.title : ''
}

function visitorOf(id) {
  const hit = retrievals.value.find((r) => r.id === id)
  return hit ? hit.visitor : ''
}

function overdue(r) {
  const due = new Date(r.dueDate + 'T00:00:00')
  const today = new Date(new Date().toISOString().slice(0, 10) + 'T00:00:00')
  const days = Math.floor((today - due) / 86400000)
  return days > 0 ? days : 0
}

async function load() {
  try {
    const [archiveList, retrievalList, roomList] = await Promise.all([
      archiveApi.list({}),
      retrievalApi.list({}),
      roomApi.list({})
    ])
    archives.value = archiveList
    retrievals.value = retrievalList
    rooms.value = roomList
    if (leftPick.value && !available.value.some((a) => a.id === leftPick.value)) {
      leftPick.value = null
    }
    if (rightPick.value && !out.value.some((r) => r.id === rightPick.value)) {
      rightPick.value = null
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function toOut() {
  if (!leftPick.value) return
  try {
    await retrievalApi.create({ ...form.value, archiveId: leftPick.value })
    failMsg.value = ''
    ElMessage.success('已登记借出')
    leftPick.value = null
    form.value = {}
    await load()
  } catch (e) {
    // 开单失败（比如库房封库中）：失败说明留在页面上，单子没落、现放卷数没动
    failMsg.value = `开单失败：${e.message}。这卷没借出去，现放卷数不变。`
    await load()
  }
}

async function doReturn() {
  if (!rightPick.value) return
  try {
    await retrievalApi.giveBack(rightPick.value, returnDate.value)
    ElMessage.success('已收回')
    rightPick.value = null
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.transfer-page {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 18px 20px;
}
.seal-banner {
  margin-bottom: 14px;
}
.fail-alert {
  margin-bottom: 14px;
}
.seal-tag {
  font-size: 11px;
  background: #fde2e2;
  color: #c45656;
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 6px;
  font-weight: 600;
}
.seal-warn {
  font-size: 12px;
  color: #c45656;
  background: #fef4f4;
  border-radius: 5px;
  padding: 3px 10px;
}
.transfer {
  display: flex;
  gap: 14px;
  align-items: stretch;
}
.side {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.side-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
}
.side-num {
  font-size: 12px;
  background: #fff;
  border-radius: 9px;
  padding: 1px 9px;
  color: var(--el-color-primary);
}
.side-body {
  height: 330px;
  overflow-y: auto;
}
.item {
  padding: 10px 14px;
  border-bottom: 1px solid #f4f5f9;
  cursor: pointer;
}
.item:hover {
  background: #fafbff;
}
.item.picked {
  background: var(--el-color-primary-light-9);
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}
.item.over {
  background: #fffafa;
}
.item.over.picked {
  background: var(--el-color-primary-light-9);
}
.item-code {
  font-family: Menlo, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
}
.item-title {
  font-size: 13px;
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-sub {
  font-size: 12px;
  color: #8b93a7;
}
.over {
  color: #f56c6c;
  margin-left: 8px;
}
.side-empty {
  text-align: center;
  color: #b0b6c6;
  font-size: 12px;
  padding: 28px 0;
}
.mid {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  width: 96px;
}
.arrow {
  text-align: center;
  font-size: 13px;
  padding: 9px 0;
  border-radius: 7px;
  background: #f2f4f9;
  color: #b0b6c6;
  cursor: not-allowed;
  user-select: none;
}
.arrow.on {
  background: var(--el-color-primary);
  color: #fff;
  cursor: pointer;
}
.borrow-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
  padding: 12px 16px;
  background: #f7f8fc;
  border-radius: 8px;
  min-height: 56px;
}
.bar-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  margin-right: 6px;
}
.bar-hint {
  font-size: 13px;
  color: #98a0b5;
}
.history {
  margin-top: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}
.history-head {
  display: flex;
  justify-content: space-between;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  background: #fbfcff;
  cursor: pointer;
  color: #5b6478;
}
.fold {
  font-weight: 400;
  color: var(--el-color-primary);
}
.hist-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 16px;
  font-size: 13px;
  border-top: 1px solid #f4f5f9;
}
.hist-code {
  font-family: Menlo, monospace;
  color: #6b7280;
  width: 96px;
}
.hist-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hist-who,
.hist-date {
  color: #8b93a7;
  font-size: 12px;
}
</style>
