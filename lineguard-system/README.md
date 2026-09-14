# LineGuard 第一阶段全栈仿真原型

本项目将《LineGuard 项目计划书 V1.1》中的核心能力实现为可运行系统：Robot 从 Dock 出发，在单档距模拟线路中移动至指定检测点，采集近距离图像，等待人工复核，然后返回 Dock 并完成锁止、补能确认和数据同步。

首版只验证一条主线，不包含真实电机、单片机、ROS、AI 缺陷识别、复杂越障和商业管理模块。

## 已实现能力

- 完整任务状态机：创建、预检、离站、移动、检测、上传、复核、返航、对接和完成。
- Robot 仿真：位置推进、电量变化、定点拍摄、返航和安全驻停。
- Dock 仿真：驻留锁止、离站释放、返回对接、充电确认和限次重试。
- 中文管理界面：任务控制台、历史任务、设备状态。
- 实时状态：通过 SSE 自动刷新 Robot 位置、设备状态和任务事件。
- 数据持久化：H2 保存任务与状态历史，本地目录保存模拟检测图像。
- 异常演示：低电量、通信中断、未知障碍、对接失败和模糊图像。
- 指令幂等：相同指令编号不会触发重复动作。

## 工程结构

```text
lineguard-system/
├─ backend/                 Java 21 + Spring Boot + H2
│  ├─ src/main/java/        领域状态机、REST、SSE 和设备仿真
│  ├─ src/test/java/        闭环与故障集成测试
│  └─ runtime-data/         运行后生成的数据库与检测图像
├─ frontend/                Vue 3 + TypeScript + Element Plus
│  └─ src/
│     ├─ views/             任务控制台、历史任务、设备状态
│     ├─ stores/            实时数据和任务操作
│     └─ components/        线路视图、状态标签和任务时间线
├─ start-demo.ps1           一键构建并启动
└─ stop-demo.ps1            停止演示服务
```

## 运行环境

- JDK 21
- Maven 3.9+
- Node.js 22 或 24
- npm 10+

不需要单独安装数据库或消息队列。

## 一键启动

在当前目录打开 PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1
```

脚本会在后台启动后端和前端，服务就绪后自动打开：

```text
http://localhost:5173
```

结束演示：

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-demo.ps1
```

运行日志保存在 `.run-logs`，任务数据库和检测图像保存在 `backend/runtime-data`。

## 手动启动

终端一：

```powershell
cd backend
mvn spring-boot:run
```

终端二：

```powershell
cd frontend
npm install
npm run dev
```

## 推荐演示流程

### 正常闭环

1. 打开“任务控制台”。
2. 选择检测点，点击“创建并开始任务”。
3. 观察 Robot 在线路视图中移动，任务状态自动推进。
4. 系统到达“等待复核”后查看检测图像。
5. 选择人工结论，点击“提交结论并下达返航”。
6. 观察 Robot 返回 Dock，任务进入“任务完成”。
7. 在“历史任务”中检查完整状态时间线和复核档案。

默认 A 点任务约 15 秒到达复核阶段，提交复核后约 10 秒完成返航。

### 异常闭环

任务运行时可在右侧注入：

- 低电量：自动转入返航。
- 通信中断：Robot 本地安全停车，恢复通信后可人工返航。
- 未知障碍：进入安全驻停，解除障碍后可人工返航。
- Dock 对接失败：返航对接时重试两次，随后安全驻停。
- 模糊图像：下一次图像不可判读，只允许重新采集一次。
- 重复指令：重发同一个派发指令，任务不会重复执行。

演示不同场景前可点击“重置演示环境”。

## 核心接口

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/v1/tasks` | 创建任务 |
| `POST` | `/api/v1/tasks/{id}/dispatch` | 预检并派发任务 |
| `GET` | `/api/v1/tasks/{id}` | 查询任务、检测和事件 |
| `POST` | `/api/v1/tasks/{id}/review` | 保存人工结论并返航 |
| `POST` | `/api/v1/tasks/{id}/actions/recapture` | 重新采集一次图像 |
| `POST` | `/api/v1/tasks/{id}/actions/return` | 人工返航 |
| `POST` | `/api/v1/tasks/{id}/actions/abort` | 终止任务 |
| `GET` | `/api/v1/devices` | 查询 Robot 与 Dock |
| `GET` | `/api/v1/events/stream` | SSE 实时事件流 |
| `POST` | `/api/v1/simulator/faults` | 注入或解除仿真故障 |
| `POST` | `/api/v1/simulator/reset` | 重置演示数据 |

## 测试与构建

后端：

```powershell
cd backend
mvn test
```

前端：

```powershell
cd frontend
npm run test
npm run build
```

当前自动化测试覆盖正常任务闭环、API 建任务、低电量返航、通信中断、Dock 对接失败和重复指令幂等。

## 后续接入真实设备

后端通过 `DeviceGateway` 隔离任务系统与设备执行端。当前实现连接内置仿真器；后续可以新增 MQTT、串口或硬件网关实现，而不需要改变任务状态机、数据模型和前端页面。
