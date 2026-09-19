<template>
  <div class="rank-page">
    <div class="stat-bar">
      <div class="stat">
        <div class="stat-num">{{ rooms.length }}</div>
        <div class="stat-label">库房数</div>
      </div>
      <div class="stat">
        <div class="stat-num">{{ checkedToday }}</div>
        <div class="stat-label">今天已查</div>
      </div>
      <div class="stat">
        <div class="stat-num warn">{{ maxGap }}</div>
        <div class="stat-label">最久没查（天）</div>
      </div>
      <div class="stat">
        <div class="stat-num danger">{{ badCount }}</div>
        <div class="stat-label">温湿度异常</div>
      </div>
      <div class="stat">
        <div class="stat-num seal">{{ sealedCount }}</div>
        <div class="stat-label">封库中（库）</div>
      </div>
    </div>

    <div class="rank-head">
      <span class="rh-title">久未检查排行</span>
      <span class="rh-tip">越久没查的排越前面，点「记录」当场补一次</span>
    </div>

    <div class="rank-list">
      <div v-for="(r, idx) in ranked" :key="r.id" class="rank-row">
        <div class="rank-no" :class="{ top: idx < 2 && r.gap > 0 }">{{ idx + 1 }}</div>
        <div class="rank-info">
          <div class="rank-name">
            {{ r.code }} · {{ r.name }}
            <span class="st" :class="statusClass(r.status)">{{ r.status }}</span>
            <span v-if="r.sealed" class="st st-seal">封库中</span>
            <span v-else-if="warnOf(r)" class="st st-alert">越限预警</span>
          </div>
          <div class="rank-sub">
            <template v-if="r.last">上次检查 {{ r.last.checkDate }}（{{ r.last.checker }}）</template>
            <template v-else>还没有检查记录</template>
            <span class="range">本库区间 {{ rangeText(r) }}</span>
          </div>
          <div v-if="r.sealed" class="seal-note">
            连续两班抄表越限，封库中：该库新调阅开不成，已借出的卷归还照常；
            本班抄表回到区间内即回温解封。抄表口不堵，点「记录」照常补本班。
          </div>
        </div>
        <div class="rank-gap">
          <div class="gap-bar">
            <div
              class="gap-fill"
              :class="{ danger: r.gap >= 5, warn: r.gap >= 2 && r.gap < 5 }"
              :style="{ width: Math.min(r.gap / 12 * 100, 100) + '%' }"
            ></div>
          </div>
          <span class="gap-text" :class="{ danger: r.gap >= 5 }">
            {{ r.gap > 0 ? r.gap + ' 天' : '今天已查' }}
          </span>
        </div>
        <a class="log-link" @click="openLog(r)">{{ logging === r.id ? '收起' : '记录' }}</a>

        <div v-if="logging === r.id" class="log-form">
          <el-form label-width="80px" class="lf">
            <el-form-item label="日期">
              <el-date-picker
                v-model="logForm.checkDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="温度 ℃">
              <el-input-number v-model="logForm.temperature" :precision="1" :step="0.5" :min="-20" :max="60" />
            </el-form-item>
            <el-form-item label="湿度 %">
              <el-input-number v-model="logForm.humidity" :precision="0" :step="1" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="情况说明">
              <el-input
                v-model="logForm.issueDesc"
                placeholder="越出本库上下限时必填"
              />
            </el-form-item>
            <el-form-item label="检查人">
              <el-input v-model="logForm.checker" style="width: 160px" />
            </el-form-item>
          </el-form>
          <div class="lf-actions">
            <el-button type="primary" size="small" @click="submitLog">提交记录</el-button>
            <el-button size="small" @click="logging = null">取消</el-button>
            <span class="lf-hint">
              温湿度按本库区间（{{ rangeText(loggingRoom) }}）判正常/异常；
              连续两班越限封库，本班回到区间内即回温
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="recent">
      <div class="recent-head">最近检查记录</div>
      <div v-for="c in recent" :key="c.id" class="recent-row">
        <span class="rc-date">{{ c.checkDate }}</span>
        <span class="rc-room">{{ roomName(c.roomId) }}</span>
        <span class="rc-val">{{ c.temperature }} ℃ / {{ c.humidity }} %</span>
        <span class="rc-res" :class="c.result === '异常' ? 'bad' : 'good'">{{ c.result }}</span>
        <span class="rc-note">{{ c.issueDesc || '—' }}</span>
      </div>
      <div v-if="!recent.length" class="recent-empty">还没有检查记录</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { checkApi, roomApi } from '../api'

const rooms = ref([])
const checks = ref([])
const logging = ref(null)
const logForm = ref({})

const today = new Date().toISOString().slice(0, 10)

function daysBetween(d) {
  const a = new Date(d + 'T00:00:00')
  const b = new Date(today + 'T00:00:00')
  return Math.floor((b - a) / 86400000)
}

const ranked = computed(() => {
  return rooms.value
    .map((r) => {
      const mine = checks.value
        .filter((c) => c.roomId === r.id)
        .sort((a, b) => (a.checkDate < b.checkDate ? 1 : -1))
      const last = mine.length ? mine[0] : null
      return { ...r, last, gap: last ? Math.max(daysBetween(last.checkDate), 0) : 99 }
    })
    .sort((a, b) => b.gap - a.gap)
})

const checkedToday = computed(() =>
  rooms.value.filter((r) =>
    checks.value.some((c) => c.roomId === r.id && c.checkDate === today)
  ).length
)

const maxGap = computed(() =>
  ranked.value.length ? Math.min(ranked.value[0].gap, 99) : 0
)

const badCount = computed(() => checks.value.filter((c) => c.result === '异常').length)

const sealedCount = computed(() => rooms.value.filter((r) => r.sealed).length)

const loggingRoom = computed(() =>
  rooms.value.find((r) => r.id === logging.value) || {}
)

// 本库上下限：库房自己配的区间，没配就按默认保管区间
function limitsOf(r) {
  return {
    tMin: r.tempMin ?? 14,
    tMax: r.tempMax ?? 24,
    hMin: r.humidityMin ?? 45,
    hMax: r.humidityMax ?? 60
  }
}

function rangeText(r) {
  if (!r || r.id == null) return '14–24℃ · 45–60%'
  const l = limitsOf(r)
  return `${l.tMin}–${l.tMax}℃ · ${l.hMin}–${l.hMax}%`
}

function outOfRange(r, c) {
  const l = limitsOf(r)
  return c.temperature < l.tMin || c.temperature > l.tMax
    || c.humidity < l.hMin || c.humidity > l.hMax
}

// 只越一班只预警：最新一班越限但还没封库
function warnOf(r) {
  if (!r.last) return false
  return outOfRange(r, r.last)
}

const recent = computed(() => checks.value.slice(0, 8))

function statusClass(s) {
  if (s === '在用') return 'st-ok'
  if (s === '整理') return 'st-warn'
  return 'st-off'
}

function roomName(id) {
  const hit = rooms.value.find((r) => r.id === id)
  return hit ? hit.name : id
}

function openLog(r) {
  if (logging.value === r.id) {
    logging.value = null
    return
  }
  logForm.value = { roomId: r.id, checkDate: today, temperature: 22, humidity: 50, checker: '' }
  logging.value = r.id
}

async function submitLog() {
  try {
    await checkApi.create(logForm.value)
    ElMessage.success('已记录')
    logging.value = null
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  try {
    rooms.value = await roomApi.list({})
    checks.value = await checkApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.rank-page {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 18px 20px;
}
.stat-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.stat {
  flex: 1;
  background: #f7f8fc;
  border-radius: 9px;
  padding: 12px 16px;
}
.stat-num {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  line-height: 1.2;
}
.stat-num.warn {
  color: #e6a23c;
}
.stat-num.danger {
  color: #f56c6c;
}
.stat-num.seal {
  color: #c45656;
}
.stat-label {
  font-size: 12px;
  color: #8b93a7;
  margin-top: 2px;
}
.rank-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
}
.rh-title {
  font-size: 15px;
  font-weight: 600;
}
.rh-tip {
  font-size: 12px;
  color: #98a0b5;
}
.rank-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-bottom: 1px solid #f4f5f9;
  flex-wrap: wrap;
}
.rank-row:last-child {
  border-bottom: none;
}
.rank-no {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #f2f4f9;
  color: #8b93a7;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rank-no.top {
  background: #fdf0f0;
  color: #f56c6c;
  font-weight: 600;
}
.rank-info {
  width: 300px;
}
.rank-name {
  font-size: 14px;
  font-weight: 500;
}
.st {
  font-size: 11px;
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 6px;
}
.st-ok {
  background: #f0f9eb;
  color: #529b2e;
}
.st-warn {
  background: #fdf6ec;
  color: #b88230;
}
.st-off {
  background: #f4f4f5;
  color: #909399;
}
.st-seal {
  background: #fde2e2;
  color: #c45656;
  font-weight: 600;
}
.st-alert {
  background: #fdf6ec;
  color: #b88230;
}
.range {
  margin-left: 10px;
  color: #b0b6c6;
}
.seal-note {
  margin-top: 4px;
  font-size: 12px;
  color: #c45656;
  background: #fef4f4;
  border-radius: 6px;
  padding: 6px 10px;
  line-height: 1.6;
}
.rank-sub {
  font-size: 12px;
  color: #98a0b5;
  margin-top: 2px;
}
.rank-gap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
}
.gap-bar {
  flex: 1;
  height: 8px;
  background: #f2f4f9;
  border-radius: 5px;
  overflow: hidden;
}
.gap-fill {
  height: 100%;
  background: #c9d4fc;
  border-radius: 5px;
}
.gap-fill.warn {
  background: #f3d19e;
}
.gap-fill.danger {
  background: #f5a3a3;
}
.gap-text {
  font-size: 12px;
  color: #6b7280;
  width: 64px;
  text-align: right;
}
.gap-text.danger {
  color: #f56c6c;
}
.log-link {
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}
.log-form {
  width: 100%;
  padding: 14px 16px 4px;
  margin: 4px 0 2px;
  background: #fafbff;
  border-radius: 8px;
}
.lf {
  max-width: 560px;
}
.lf-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 80px;
  padding-bottom: 12px;
}
.lf-hint {
  font-size: 12px;
  color: #98a0b5;
}
.recent {
  margin-top: 22px;
}
.recent-head {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}
.recent-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 14px;
  font-size: 13px;
  border-bottom: 1px solid #f4f5f9;
}
.rc-date {
  font-family: Menlo, monospace;
  color: #6b7280;
  width: 96px;
}
.rc-room {
  width: 110px;
}
.rc-val {
  width: 130px;
  color: #5b6478;
}
.rc-res {
  width: 56px;
  font-size: 12px;
}
.rc-res.good {
  color: #529b2e;
}
.rc-res.bad {
  color: #f56c6c;
}
.rc-note {
  flex: 1;
  color: #98a0b5;
  font-size: 12px;
}
.recent-empty {
  color: #b0b6c6;
  font-size: 13px;
  padding: 20px 0;
}
</style>
