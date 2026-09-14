import { createRouter, createWebHistory } from 'vue-router'
import MissionConsole from './views/MissionConsole.vue'
import TaskHistory from './views/TaskHistory.vue'
import DeviceStatus from './views/DeviceStatus.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'console', component: MissionConsole },
    { path: '/history', name: 'history', component: TaskHistory },
    { path: '/devices', name: 'devices', component: DeviceStatus },
  ],
})

