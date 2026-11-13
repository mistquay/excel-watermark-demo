# Excel 水印验证工程

一个独立的本地演示：上传 `.xlsx`，设置文字、图片或组合水印，选择查看/打印场景，下载处理后的 Excel。

使用 Java 21、Spring Boot 3.5.13、Apache POI 5.4.1 和原生 HTML/CSS/JavaScript。没有数据库，也没有前端依赖安装或构建步骤。

## 启动

在 PowerShell 中运行：

```powershell
cd D:\project\java\excel-watermark-demo
.\start.ps1
```

打开 <http://127.0.0.1:18092>。脚本优先使用已有 `JAVA_HOME`；未设置时尝试本机 `D:\services\jdks\ms-21.0.7`，其他电脑请自行设置为 JDK 21。首次运行由 Maven Wrapper 下载依赖。

更换端口：

```powershell
.\start.ps1 -Port 18093
```

也可以构建后直接运行：

```powershell
.\mvnw.cmd verify
& "$env:JAVA_HOME\bin\java.exe" -jar target/excel-watermark-demo-1.0.0.jar
```

服务仅监听本机 `127.0.0.1`。普通运行不需要安装 Microsoft Excel；下面的真实客户端验收脚本才需要 Excel。

## 页面操作

1. 上传或拖放 `.xlsx`，也可以点“使用示例 Excel”。示例包含两张表、多页数据和公式。
2. 选择文字、PNG/JPEG 图片，或文字与图片组合；文字支持换行。
3. 调整颜色、不透明度、字号/图片大小、旋转角度、平铺/居中和间隔。
4. 选择生效场景、需要处理的工作表，并切换预览场景。
5. 点击“生成并下载 Excel”，用 Excel 打开结果，再查看打印预览或另存为 PDF。

Excel 最大 20MB，图片最大 5MB、1600 万像素。上传与处理使用内存，不归档原文件；验证脚本生成的样本和截图保存在被 Git 忽略的 `output/`。

## 三种场景

| 配置 | Excel 普通视图 | 打印预览/打印 | Excel 内置导出 PDF |
|---|---|---|---|
| 仅打开表格时可见 | 显示 | 不显示 | 不显示 |
| 打印 / 转 PDF 时可见 | 不显示 | 显示 | 显示 |
| 两个场景都可见 | 显示 | 显示 | 显示 |

打印和 Excel 内置 PDF 导出共用页面层，因此合并为一个选项。这里下载的是带相应配置的 **Excel 文件**，网页本身不提供 Excel 转 PDF 服务。

## 原理与边界

- **查看水印**：PNG 背景图，通过 `XSSFSheet` 的 `picture` 关系写入。Excel 会自然重复背景；“画布居中”指每张 1200×850 背景画布中间的一个水印，查看时画布仍会重复。填充了不透明底色的单元格会遮住背景。
- **打印/PDF 水印**：透明 PNG + VML 页眉图片 + 页眉 `&G`。每个打印页重复，支持 A4/A3/Letter/Legal 尺寸和横向纸张；其他纸型按 A4 估算水印画布，原分页设置不主动调整。
- **双场景**：同一份水印分别写入两个显示层。
- 保留原单元格内容、公式和普通样式。所选工作表的原背景会被替换；打印模式会替换页眉图片，并关闭首页/奇偶页不同设置，使用统一的普通页眉。高级页眉图片版式不在本演示的保留范围内。
- 仅处理 `.xlsx`。`.xls`、`.xlsm`、加密工作簿不支持；旧版 `.xls` 请先另存为 `.xlsx`。
- 网页展示实际工作表前 12 行、最多 8 列及水印示意，不是 Excel 渲染器；真实分页、缩放和位置以 Excel 为准。WPS、LibreOffice 和第三方转换服务需要分别验证。

## 工程入口

```text
src/main/java/com/example/watermark/
  WatermarkApplication.java       启动入口
  WatermarkController.java        上传检查、PNG 预览、示例与 Excel 下载接口
  WatermarkOptions.java           配置及校验
  WatermarkRenderer.java          透明 PNG 文字/图片绘制
  ExcelWatermarkService.java      两种水印写入及示例工作簿
src/main/resources/static/        页面，无前端构建
src/test/                        定向测试
scripts/verify-excel.ps1          可选：真实 Excel 打开及 PDF 导出验收
```

## 验证

```powershell
.\mvnw.cmd verify
```

12 项测试覆盖三种场景、三种内容、工作表选择、公式/数值/样式保留、再次处理时切换场景、居中/横向设置、文件检查、预览与下载接口以及无效输入。

服务启动后，装有 Microsoft Excel 的 Windows 可运行：

```powershell
.\scripts\verify-excel.ps1
```

脚本新建独立的隐藏 Excel 实例，将五种模式的文件打开、检查数据和公式、导出 PDF，结果写入 `output/verification/`。不操作已经打开的用户工作簿。详见 [VALIDATION.md](VALIDATION.md)。
