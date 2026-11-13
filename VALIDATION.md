# 验证记录

验证日期：2026-11-13（北京时间）。环境：Windows、Java 21.0.7、Microsoft Excel、Chrome。

## 自动化检查

- `mvnw.cmd verify`：12 项定向测试通过，零失败、零错误。
- `node --check src/main/resources/static/app.js`：通过。
- 工作簿解包检查、POI 重新读取、数据/公式/样式保留均有覆盖。

## 浏览器流程

真实浏览器已验证：示例加载、文件选择器上传、三种场景切换、查看/打印预览切换、工作表勾选、预览表切换、图片上传、组合水印、真实下载、未选择工作表时的错误提示、颜色、透明度、角度和疏密控件。

桌面 1440px 与手机 390px 全页截图检查通过；手机页面无整体横向溢出，宽表格在内部滚动；浏览器控制台零错误。

截图位于 `output/playwright/desktop.png`、`mobile.png`，通过浏览器下载的工作簿为 `output/verification/browser-download.xlsx`。

## Microsoft Excel 与 PDF

通过 `scripts/verify-excel.ps1` 新建独立 Excel 实例，分别打开五种导出结果，检查原单元格 `DEMO-001`、原公式 `=SUM(D2:D86)`，并通过 Excel 的 `ExportAsFixedFormat` 导出 PDF。五种文件均成功打开，数据和公式保留。

| 模式 | Excel 普通页眉 | PDF 页数 | 每页渲染结果 |
|---|---|---|---|
| 仅查看 | 无 `&G` | 6 | 无水印 |
| 仅打印/PDF | 包含 `&G` | 6 | 有水印 |
| 双场景 | 包含 `&G` | 6 | 有水印，与仅打印模式一致 |
| 图片、双场景 | 包含 `&G` | 6 | 有图片水印 |
| 组合、画布居中、双场景 | 包含 `&G` | 6 | 有居中组合水印 |

使用 Poppler 将全部 30 页 PDF 渲染为 PNG，并检查测试水印的绿色像素：仅查看模式各页为 0，其余模式每页均存在；仅打印与双场景模式每一页的水印像素数相同。另人工检查首张无水印、文字水印、图片水印和组合水印渲染图。

结构化结果：`output/verification/excel-results.json`、`pdf-results.json`、`render-results.json`。生成样本和 PDF 均在同一目录中，不纳入 Git。

真实打印机未执行纸质输出；打印能力通过 Excel 的同一页面层 PDF 输出验证。WPS、LibreOffice 和第三方转换服务未验收。

## Docker 与 CI 配置验证（2026-11-13）

- 新增 Docker 多阶段构建、中文字体、单端口 `18092`、非 root 用户和健康检查；本地默认监听地址保持 `127.0.0.1`，容器通过环境变量监听 `0.0.0.0`。
- 上游镜像清单已核实：`maven:3.9.11-eclipse-temurin-21` 与 `eclipse-temurin:21-jre-noble` 均包含 Linux amd64、arm64。
- GitHub Actions 经 `actionlint 1.7.12` 检查通过；所用 action 版本已经核实存在。
- `mvnw.cmd test`：12 项测试通过；新增 Python 冒烟脚本语法及本机 HTTP 模式检查通过，覆盖页面、静态资源、示例、上传、中文水印预览及三种 Excel 下载模式。
- 当前本机没有 Docker，因此没有实际执行容器构建、ARM64 容器运行或 GHCR 发布。工作流会在发布前启动两个架构的镜像并运行同一冒烟脚本；首次推送后的 Actions 结果才是镜像构建与发布的实际验收依据。
