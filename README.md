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

本地运行默认仅监听 `127.0.0.1`，可以通过 `SERVER_ADDRESS` 修改。普通运行不需要安装 Microsoft Excel；下面的真实客户端验收脚本才需要 Excel。

## Docker 镜像与 GitHub Actions

将本工程作为 GitHub 仓库推送后，`.github/workflows/docker-publish.yml` 会自动执行，也可以在 Actions 页面手动运行：

1. 构建时运行 Maven 的 `verify`，包含单元测试。
2. 分别构建并启动 `linux/amd64`（x64）和 `linux/arm64` 镜像，检查中文字体、页面、静态资源、上传解析、PNG 水印预览和三种模式的 Excel 下载。
3. 两个架构均通过后，发布多架构镜像到 `ghcr.io/<GitHub用户或组织>/<仓库名>`，镜像路径自动转为小写。

工作流使用 GitHub 自带的 `GITHUB_TOKEN` 和 `packages: write` 权限，不需要额外配置发布密钥。

| 推送内容 | 生成的镜像标签 |
|---|---|
| 默认分支 | `latest`、`branch-<分支名>`、`sha-<完整提交SHA>` |
| 其他分支 | `branch-<分支名>`、`sha-<完整提交SHA>` |
| Git 标签，例如 `v1.0.0` | `v1.0.0`、`sha-<完整提交SHA>` |

分支名中的 `/` 等字符会由 metadata-action 自动规范化。所有标签都指向同时包含两个架构的镜像索引，Docker 拉取时自动选择对应架构。

镜像只暴露 **`18092/tcp` 一个端口**，页面和全部接口都由同一个 Spring Boot 进程提供，不依赖额外的前端服务、数据库或 Microsoft Excel。容器默认监听 `0.0.0.0`，内置中文字体、非 root 运行用户及 HTTP 健康检查。

启动已发布镜像，替换下面的镜像地址：

```sh
docker run -d \
  --name excel-watermark-demo \
  --restart unless-stopped \
  -p 18092:18092 \
  ghcr.io/你的用户名/你的仓库名:latest
```

打开 `http://服务器IP:18092` 即可使用。修改访问端口只需改端口映射，例如 `-p 8080:18092`，再打开 `http://服务器IP:8080`。容器内端口保持 `18092`，无需设置其他环境变量，也无需挂载卷。

GHCR 首次创建的包通常是私有的。匿名拉取时需要在 GitHub 的 Packages 设置中将镜像设为 Public；保留私有时，先使用具有 `read:packages` 权限的凭据登录 `ghcr.io`。

本地构建与验证（需要 Docker、Python 3）：

```sh
docker build -t excel-watermark-demo:local .
python scripts/smoke_container.py --image excel-watermark-demo:local
docker run --rm -p 18092:18092 excel-watermark-demo:local
```

ARM64 主机上的验证命令加上 `--platform linux/arm64`。CI 使用 QEMU 在构建机上运行 ARM64 容器。

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
scripts/smoke_container.py        HTTP / 容器冒烟检查，仅使用 Python 标准库
Dockerfile                       多阶段构建，内置中文字体
.github/workflows/docker-publish.yml  双架构验证并发布 GHCR
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
