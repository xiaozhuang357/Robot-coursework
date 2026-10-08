"""在所有验收通过后生成课程提交包，并逐项校验归档内的文件哈希。"""
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED
from hashlib import sha256
from datetime import datetime, timezone
import json
import os
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / '.lineguard_qa/course'
NAME = '2024303524-张立烁'
OUT = ROOT / 'submission'
OUT.mkdir(exist_ok=True)
PAPER = 'LineGuard_课程论文_V2.0'
EXCLUDE = {'node_modules', 'target', 'dist', 'runtime-data', '__pycache__', '.git', '.run-logs', '.qa'}


def read_json(path):
    return json.loads(path.read_text())


def collect_evidence():
    suites = []
    for path in sorted((ROOT / 'lineguard-system/backend/target/surefire-reports').glob('TEST-*.xml')):
        item = ET.parse(path).getroot()
        suite = {k: int(item.attrib[k]) for k in ['tests', 'failures', 'errors', 'skipped']}
        suite['name'] = item.attrib['name']
        suites.append(suite)
    assert sum(s['tests'] for s in suites) == 32, '后端报告数量与论文不符'
    assert all(s['failures'] == s['errors'] == s['skipped'] == 0 for s in suites)
    front = read_json(QA / 'frontend-tests.json')
    assert front['success'] and front['numPassedTests'] == front['numTotalTests'] == 32
    build = (QA / 'frontend-build.log').read_text()
    assert 'built in' in build and 'error TS' not in build
    paper = read_json(QA / 'paper-verification.json')
    assert paper and all(x['passed'] for x in paper)
    smoke = read_json(QA / 'smoke/verification.json')
    layout = read_json(QA / 'smoke/layout-verification.json')
    assert len(smoke['results']) == 6 and all(x['passed'] for x in smoke['results'])
    assert len(layout['results']) == 25 and all(x['passed'] for x in layout['results'])
    assert not smoke['errors'] and not layout['errors']
    model = read_json(QA / 'model-verification.json')
    assert model['passed']
    # 所有相对路径均指向包内文件，避免提交个人机器的绝对路径。
    for item in paper:
        if item['rule'] == '实际 PDF 已生成':
            item['detail'] = PAPER + '.pdf'
    return {
        'student': {'id': '2024303524', 'name': '张立烁'},
        'packagedAt': datetime.now(timezone.utc).isoformat(),
        'scope': '软件仿真与概念模型，不含实机验证',
        'paper': {'file': PAPER + '.pdf', 'pagesIncludingCover': 10, 'checks': paper},
        'backend': {'passed': True, 'tests': 32, 'suites': suites},
        'frontend': {'passed': True, 'tests': 32, 'buildPassed': True,
                     'buildNote': '依赖分包超过 500 kB 的构建提示仍存在；未做性能基准测试'},
        'browserAndHttp': smoke['results'],
        'responsiveLayouts': layout['results'],
        'model': model,
        'platform': 'macOS；Windows 启停脚本未实机验证',
    }


def copy_file(source, target):
    assert source.is_file(), f'缺少文件：{source}'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def copy_source_tree(source, target):
    for current, dirs, files in os.walk(source):
        dirs[:] = [d for d in dirs if d not in EXCLUDE and not d.startswith('.')]
        for name in files:
            if name.startswith('.') or name.endswith(('.tsbuildinfo', '.pyc', '.log')):
                continue
            if name in ('vite.config.js', 'vite.config.d.ts'):
                continue
            path = Path(current) / name
            if not path.is_symlink():
                copy_file(path, target / path.relative_to(source))


def main():
    evidence = collect_evidence()
    target = OUT / (NAME + '.zip')
    with TemporaryDirectory(prefix='lineguard-submit-') as temp:
        stage = Path(temp) / NAME
        stage.mkdir()
        for name in [PAPER + '.docx', PAPER + '.pdf', PAPER + '.md', 'README.md',
                     '《智能机器人软件设计方法与实践》封面及格式规范.docx']:
            copy_file(ROOT / name, stage / name)
        for folder in ['lineguard-system', 'docs', 'design', 'paper/assets']:
            copy_source_tree(ROOT / folder, stage / folder)
        for name in ['LineGuard.blend', 'LineGuard.glb']:
            copy_file(ROOT / 'model' / name, stage / 'model' / name)
        for name in ['build_paper.py', 'verify_paper.py', 'build_course_figures.py', 'verify_demo.mjs']:
            copy_file(ROOT / 'tools' / name, stage / 'tools' / name)
        for name in ['lineguard_dimensions.py', 'lineguard_model.py', 'export_parts.py', 'export_course_model.py']:
            copy_file(ROOT / 'tools/blender' / name, stage / 'tools/blender' / name)
        screenshots = {
            'console.png': '01-人工复核工作台.png',
            'console-1440.png': '02-新建任务与三维场景.png',
            'history.png': '03-历史任务详情.png',
            'devices.png': '04-设备与线路.png',
            'engineering.png': '05-全档距工程视图.png',
            'console-390.png': '06-手机控制台.png',
            'history-mobile.png': '07-手机任务详情.png',
            'devices-mobile.png': '08-手机设备状态.png',
        }
        for source, dest in screenshots.items():
            copy_file(QA / 'smoke' / source, stage / '展示截图' / dest)
        (stage / '验证结果.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
        (stage / '提交说明.txt').write_text(
            '智能机器人软件设计方法与实践 · 课程作业\n2024303524  张立烁\n\n'
            '1. 阅读 LineGuard_课程论文_V2.0.pdf（10 页，含封面），Word 同时提供。\n'
            '2. 源码位于 lineguard-system；结构与运行说明见 docs/代码结构说明.md。\n'
            '3. 安装 JDK 21、Maven、Node.js 后，按 README.md 启动网页演示。\n'
            '4. model/ 内附 Blender 与 GLB 概念模型；展示截图可直接查看。\n'
            '5. design/device_control.pseudo 为后续设备端设计，尚未连接真实硬件。\n'
            '6. 验证结果与文件哈希随包附带；依赖、构建产物及运行数据库不打包。\n', encoding='utf-8')
        files = sorted(p for p in stage.rglob('*') if p.is_file())
        lines = [f'{sha256(p.read_bytes()).hexdigest()}  {p.relative_to(stage).as_posix()}' for p in files]
        (stage / '文件校验清单.sha256').write_text('\n'.join(lines) + '\n')
        partial = OUT / (NAME + '.zip.tmp')
        with ZipFile(partial, 'w', ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(p for p in stage.rglob('*') if p.is_file()):
                archive.write(path, Path(NAME) / path.relative_to(stage))
        with ZipFile(partial) as archive:
            assert archive.testzip() is None, 'ZIP CRC 校验失败'
            assert len(archive.namelist()) == len(lines) + 1
            for line in lines:
                expected, relative = line.split('  ', 1)
                actual = sha256(archive.read(f'{NAME}/{relative}')).hexdigest()
                assert actual == expected, relative
            assert not any(part in EXCLUDE for p in archive.namelist() for part in Path(p).parts)
            # 再真实解压一次，检查中文路径和提交层级。
            extracted = Path(temp) / 'extract'
            archive.extractall(extracted)
            assert (extracted / NAME / (PAPER + '.pdf')).exists()
        partial.replace(target)
        result = {'file': str(target), 'files': len(lines) + 1, 'bytes': target.stat().st_size,
                  'sha256': sha256(target.read_bytes()).hexdigest(), 'zipCrcPassed': True,
                  'manifestPassed': True, 'extractionPassed': True}
        (QA / 'package-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
