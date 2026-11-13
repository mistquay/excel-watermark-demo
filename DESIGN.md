---
name: Excel 水印工坊
description: 本地 Excel 水印验证工具的现有视觉规范
colors:
  ink: "#243b35"
  muted: "#596a62"
  accent: "#23664f"
  accent-soft: "#e9f2ec"
  line: "#dce4de"
  ground: "#f3f6f3"
  white: "#fff"
  accent-hover: "#194e3b"
  focus: "#7aab94"
  error: "#a93232"
  field-line: "#ced9d0"
  preview-ground: "#eaf0eb"
typography:
  display:
    fontFamily: '"Segoe UI", "Microsoft YaHei", sans-serif'
    fontSize: "29px"
    fontWeight: 650
    lineHeight: 1.35
    letterSpacing: "-0.02em"
  title:
    fontFamily: '"Segoe UI", "Microsoft YaHei", sans-serif'
    fontSize: "15px"
    fontWeight: 650
    lineHeight: 1.4
  body:
    fontFamily: '"Segoe UI", "Microsoft YaHei", sans-serif'
    fontSize: "14px"
    lineHeight: 1.55
  label:
    fontFamily: '"Segoe UI", "Microsoft YaHei", sans-serif'
    fontSize: "12px"
    fontWeight: 500
rounded:
  tab: "3px"
  inset: "4px"
  field: "5px"
  control: "6px"
  panel: "8px"
spacing:
  compact: "8px"
  paired: "12px"
  mobile-panel: "20px"
  panel-inline: "24px"
  workspace: "26px"
  page-inline: "32px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
    padding: "10px 17px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.white}"
    textColor: "{colors.accent}"
    rounded: "{rounded.control}"
    padding: "10px 17px"
  button-secondary-hover:
    backgroundColor: "{colors.accent-soft}"
  textarea:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    padding: "9px 10px"
  panel:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.panel}"
  tab-selected:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    rounded: "{rounded.tab}"
    padding: "6px 13px"
---

# Design System: Excel 水印工坊

## Overview

**Creative North Star: "本地验证工具"**

页面采用现有办公工具的浅色界面：系统中文字体、白色表单、淡绿预览底和深绿色主操作。设计服务于用户要求的简单独立验证工程，直接呈现文件、设置和表格示意。

本文件从 `src/main/resources/static/style.css` 与 `index.html` 提取当前实现，供后续维护保持一致，不代表新增的品牌约定或验收结论。

**Key Characteristics:**

- 系统字体和紧凑的表单层级。
- 白色面板、细边框和小圆角。
- 设置与预览在桌面并排，在手机依次排列。

## Colors

### Primary

- `accent` 用于主按钮、品牌链接、当前值与选中状态；`accent-hover` 用于可用主按钮悬停。
- `accent-soft` 用于预览页签选中和次按钮悬停；`focus` 用于键盘焦点。

### Neutral

- `ink` 为正文，`muted` 为帮助文字；`ground` 为页面底，`white` 为面板和纸张。
- `line` 分隔面板和区块，`field-line` 描出输入框；`preview-ground` 区分预览舞台与白纸。
- `error` 仅用于错误状态文字。水印的四个预设色属于可编辑内容，不扩展为界面主题色。

## Typography

所有界面控件沿用同一系统字体栈，角色值见 frontmatter。主标题使用 display，设置与预览标题使用 title，正文使用 body，表单标签与辅助信息主要使用 12px；状态提示使用 13px。品牌名称在桌面为 19px、650 字重。

主标题在手机缩为 25px；品牌缩为 16px。表格在普通预览为 12px、打印示意为 11px；表格和滑块数值采用等宽数字。说明段落最大宽度为 80ch。

## Layout

主容器最大宽度为 1344px，桌面内边距为 `34px 32px 0`。设置栏固定为 322px，右侧预览填满余宽，双栏间距为 26px。设置区块内边距为 `22px 24px`，预览舞台内边距为 28px。成对字段等宽排列，间距为 12px。

- 视口至少 1100px：预览面板粘滞定位，距顶 20px。
- 视口至多 900px：设置栏为 290px，双栏间距为 18px，表格标题纵向排列，底部工具行允许换行。
- 视口至多 700px：设置与预览改为纵向排列，间距为 20px；页面左右留白为 16px，页头高 64px，区块内边距主要为 20px。页脚也改为纵向排列。

表格和预览舞台允许内部滚动；单元格不换行，超过 180px 的内容省略。打印示意的纸张最大宽度为 620px，居中呈现，最小高度随断点从 660px 降至 550px、530px。

## Elevation & Depth

面板主要通过底色、1px 边框和区块分隔建立层次。仅分段选择器的当前项使用 `0 1px 3px #1e493214` 阴影，预览白纸使用 `0 4px 12px #243b350a` 阴影；设置面板无阴影。

## Shapes

面板和上传区使用 panel 圆角，按钮和场景选项使用 control 圆角，输入框使用 field 圆角；页签与嵌套选项使用更小的 tab、inset 圆角。颜色预设为直径 22px 的圆形色块；上传区使用虚线边框。

## Components

- **按钮：**主按钮铺满设置栏，最小高度 47px；次按钮最小高度 43px，手机为 38px。悬停只改变背景，使用 `background 0.15s ease-out`；禁用按钮透明度为 0.5，光标为 `not-allowed`。
- **输入：**文本域最小高度 68px，可纵向调整；下拉框内边距为 `8px 9px`。全局键盘焦点使用 3px 实线轮廓、3px 外偏移，上传区通过 `focus-within` 显示同样轮廓。
- **分段与场景：**水印类型选中项为白底、绿色文字和轻阴影，隐藏单选框获得焦点时在文字块上绘制 2px 绿色轮廓。可见场景选中项为淡绿底和加深边框。预览页签通过 `aria-selected` 显示选中底色；颜色预设通过 `aria-pressed` 显示 2px 绿色外圈。
- **上传：**默认淡绿底、虚线边框；拖放状态加深背景并显示 2px 绿色轮廓。提示行预留最小高度 22px，错误通过 error 文字色标明。
- **预览：**白纸与表格位于独立淡绿舞台；普通视图水印重复平铺，打印示意为居中单幅。表格使用细网格、淡绿表头和首行强调。
- **减少动画：**在 `prefers-reduced-motion: reduce` 下关闭所有 transition，并将滚动行为设为 `auto`。当前没有入场动画。

## Do's and Don'ts

### Do:

- **Do** 沿用现有系统字体、绿色操作色和小圆角，保持验证界面简单。
- **Do** 保留键盘焦点、选中状态和减少动画规则。
- **Do** 在手机使用单列，并让宽表格在容器内部滚动。

### Don't:

- **Don't** 为这个独立验证工程增加设计访谈或品牌审批步骤。
- **Don't** 将水印内容的颜色预设当作界面主题色。
- **Don't** 用预览示意替代实际 Excel 打开、打印和 PDF 导出的验证。
