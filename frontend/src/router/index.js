import { createRouter, createWebHistory } from 'vue-router'
import Rooms from '../views/Rooms.vue'
import Archives from '../views/Archives.vue'
import Appraisals from '../views/Appraisals.vue'
import Retrievals from '../views/Retrievals.vue'
import Checks from '../views/Checks.vue'

const routes = [
  { path: '/', redirect: '/rooms' },
  { path: '/rooms', component: Rooms, meta: { title: '库房与卷宗' } },
  { path: '/archives', component: Archives, meta: { title: '案卷目录' } },
  { path: '/appraisals', component: Appraisals, meta: { title: '到期鉴定台' } },
  { path: '/retrievals', component: Retrievals, meta: { title: '调阅登记' } },
  { path: '/checks', component: Checks, meta: { title: '库房温湿度' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
