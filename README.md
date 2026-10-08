# LineGuard 机器人课程设计

张立烁 · 2024303524

面向偏远输电区段的常驻式巡检机器人软件设计。Robot 沿限定地线区段前往检测点，上传近距离图像，由人复核后返回 Dock。课程原型实现了任务预检、状态机、故障处置、历史留痕与三维展示；运动、图像和对接由后端仿真。

## 课程提交

最终压缩包：`submission/2024303524-张立烁.zip`。解压后以同名文件夹为入口，包含：

| 文件或目录 | 内容 |
|---|---|
| `LineGuard_课程论文_V2.0.pdf` | 固定排版版，实际 10 页，含 1 页封面；正文页码从 1 开始 |
| `LineGuard_课程论文_V2.0.docx` | 按教师模板整理的可编辑论文 |
| `LineGuard_课程论文_V2.0.md` | 论文内容源文件，Java 片段由构建脚本从实际源码提取 |
| `docs/代码结构说明.md` | 模块职责、数据字段、接口示例、运行步骤和演示方法 |
| `lineguard-system/` | Spring Boot / Vue 完整源码、依赖锁文件及测试 |
| `design/device_control.pseudo` | 尚未接入硬件的设备端周期控制设计 |
| `model/LineGuard.blend`、`.glb` | 可编辑模型与通用交换格式 |
| `展示截图/` | 优化后的控制台、复核档案、设备状态与窄屏展示 |
| `验证结果.json`、`文件校验清单.sha256` | 自动化验证结果和包内完整性清单 |

课程论文围绕用途、软件功能、架构、关键代码与验证展开，含 3 幅图和 2 张表。A4、四边 2.5 cm、1.5 倍行距、宋体正文、黑体标题、一级标题另页；保留教师封面的校徽与身份表格。原有 V1.0 论文和项目计划书留在开发仓库供追溯，不混入最终提交包。

## 启动演示

需要 JDK 21、Maven 3.9、Node.js 22 或 24；首次构建联网下载依赖。macOS/Linux 脚本还需要 Bash、curl、lsof 和 Python 3。确认 `mvn -version` 使用 JDK 21。

```bash
bash lineguard-system/start-demo.sh
# 浏览器：http://localhost:5173
bash lineguard-system/stop-demo.sh
```

Windows 使用 `lineguard-system/start-demo.ps1` 和 `stop-demo.ps1`。本次实测平台为 macOS。手动启动、端口设置及完整接口说明见 [代码结构说明](docs/代码结构说明.md)。

演示顺序：创建任务 → 观察运行 → 查看合成图像并复核 → 返回对接 → 在历史任务中查看记录。控制台的“故障演示”可展开，支持低电量、断联、障碍、对接失败、模糊图及重复指令验证。三维场景可拖动旋转，工程视图可查看全档距。

前端采用统一的浅色巡检工作台布局；复核状态优先展示图像和结论操作，设备与事件作为辅助信息。支持桌面、平板和手机宽度，历史表格在窄屏下可横向滚动。

## 验证

```bash
cd lineguard-system/backend
mvn test
```

```bash
cd lineguard-system/frontend
npm ci
npm test
npm run build
```

2026-10-08：后端 32 项、前端 32 项通过，生产构建成功；浏览器/HTTP 冒烟检查 6 项通过；另检查 320、390、768、1024、1440 px 下三个页面、工程视图和历史抽屉，25 项布局检查通过。前端构建仍有依赖包体积提示，未开展性能基准测试。

软件验证证明流程可运行。`COMPLETED` 表示已返回对接；检测成功还需要可判读图像和人工复核记录。提前召回可能只完成返回流程。

## 重建论文与模型

```bash
python3 -m pip install python-docx pypdf Pillow
python3 tools/build_paper.py
# 用 Word / WPS / LibreOffice 将 Word 导出为同名 PDF，再验证真实页数
python3 tools/verify_paper.py --pdf LineGuard_课程论文_V2.0.pdf
```

`paper/assets/` 已提供论文所需图像。需要重建模型与配图时：

```bash
blender -b -P tools/blender/export_course_model.py
# 非 macOS 环境需要把 LINEGUARD_CJK_FONT 设为本机中文字体路径
python3 tools/build_course_figures.py
python3 tools/build_paper.py
```

尺寸参数来自 `tools/blender/lineguard_dimensions.py`，建模逻辑在 `lineguard_model.py`；浏览器使用同一模型导出的构件描述。模型是结构概念展示，尚未进行机械与电气试验。

论文最终 PDF 已逐页检查。DOCX 中保留规范指定的宋体、黑体、Times New Roman 和 Consolas；其他电脑若字体不同，Word 重排可能改变分页，提交阅读以附带 PDF 为准。

## 当前边界

- 平台为单实例、单机器人仿真，未接入真实设备。
- `DeviceGateway` 负责启停仿真调度；真机还需要指令、回执、遥测与恢复对账协议。
- 检测图由程序生成，结论由人填写；没有自动缺陷识别。
- 未实现鉴权、列表分页和多机调度；复核填写人不是已认证身份。
- H2 保存任务、设备与历史；瞬时故障保存在内存，重启会清除注入故障，并将执行中任务收敛到等待人工处理。
