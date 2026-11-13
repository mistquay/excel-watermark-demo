# Excel 水印验证工程

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

依据用户“独立、简单的验证工程”要求选择 Java 21、Spring Boot、Apache POI、原生 HTML/CSS/JavaScript；无数据库、无前端构建。

## Users

需要上传 Excel、调整水印并下载验证结果的开发人员。

## Product Purpose

验证 Excel 的查看背景水印和打印/PDF 页眉图片水印，提供文字、图片及组合水印设置。

## Capabilities and Constraints

- 上传 .xlsx，选择工作表，配置水印，下载处理后的 .xlsx。
- 查看、打印/PDF、两者兼有三种配置；Excel 的打印与内置 PDF 导出使用同一页面层。
- 原文件只在内存中处理，不持久化。
- 默认提供可下载的演示工作簿。
- 推断：面向本机开发验证，不实现账号和存储系统。
