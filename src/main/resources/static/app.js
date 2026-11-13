const $ = (selector) => document.querySelector(selector);
const form = $("#settings-form");
const missingLogoMessage = "请为图片水印选择一张 PNG 或 JPEG 图片。";
let excelFile = null;
let workbook = null;
let scene = "screen";
let previewUrl = null;
let previewAbort = null;
let inspectAbort = null;
let previewTimer = null;
let downloading = false;
let inspecting = false;

const exampleRows = [
  ["序号", "设备编码", "站点名称", "巡检结果", "负责人", "备注"],
  ...Array.from({ length: 11 }, (_, i) => [
    i + 1,
    `DEMO-${String(i + 1).padStart(3, "0")}`,
    `演示站点 ${i + 1}`,
    i === 6 ? "待复查" : "正常",
    "演示人员",
    "合成示例数据",
  ]),
];

function status(message, error = false) {
  $("#status").textContent = message;
  $("#status").classList.toggle("error", error);
}

async function checked(response) {
  if (response.ok) return response;
  const error = await response.json().catch(() => ({}));
  throw new Error(error.message || `处理失败（${response.status}），请重试`);
}

function settingsData() {
  const data = new FormData(form);
  data.delete("sheets");
  if (data.get("type") === "TEXT") data.delete("logo");
  return data;
}

function updateControls() {
  const type = form.elements.type.value;
  $("#text-field").hidden = type === "IMAGE";
  $("#logo-field").hidden = type === "TEXT";
  $("#text").required = type !== "IMAGE";
  $("#logo").required = type !== "TEXT";
  $("#opacity-value").value =
    `${Math.round(Number($("#opacity").value) * 100)}%`;
  $("#fontSize-value").value = `${$("#fontSize").value} pt`;
  $("#angle-value").value = `${$("#angle").value}°`;
  $("#density").disabled = $("#layout").value === "single";
  $(".color-options")
    .querySelectorAll(".swatch")
    .forEach((button) =>
      button.setAttribute(
        "aria-pressed",
        String(button.dataset.color === $("#color").value),
      ),
    );
  $("#download-button").disabled = !excelFile || downloading || inspecting;
  const sheetIndex = Number($("#preview-sheet").value || 0);
  const selected =
    !workbook ||
    [...document.querySelectorAll("input[name=sheets]:checked")].some(
      (input) => Number(input.value) === sheetIndex,
    );
  const visible =
    selected &&
    (form.elements.visibility.value === "BOTH" ||
      form.elements.visibility.value.toLowerCase() === scene);
  $("#watermark-layer").hidden = !visible;
  $("#scene-state").textContent = !selected
    ? "未选中此工作表"
    : visible
      ? "此场景显示水印"
      : "此场景不显示水印";
}

async function updatePreview() {
  updateControls();
  previewAbort?.abort();
  previewAbort = new AbortController();
  if (form.elements.type.value !== "TEXT" && !$("#logo").files.length) {
    $("#watermark-layer").style.backgroundImage = "none";
    status(missingLogoMessage);
    return;
  }
  try {
    const response = await checked(
      await fetch("/api/preview", {
        method: "POST",
        body: settingsData(),
        signal: previewAbort.signal,
      }),
    );
    const url = URL.createObjectURL(await response.blob());
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = url;
    $("#watermark-layer").style.backgroundImage = `url("${url}")`;
    if ($("#status").textContent === missingLogoMessage) {
      status(excelFile ? "工作簿已就绪，调整设置后即可生成下载。" : "水印预览已更新，请上传 Excel 或使用示例。");
    }
  } catch (error) {
    if (error.name !== "AbortError") {
      $("#watermark-layer").style.backgroundImage = "none";
      status(error.message, true);
    }
  }
}

function schedulePreview() {
  updateControls();
  clearTimeout(previewTimer);
  previewTimer = setTimeout(updatePreview, 220);
}

function renderTable() {
  const selected = workbook?.sheets[Number($("#preview-sheet").value || 0)];
  const rows = selected?.rows || exampleRows;
  const cols = Math.max(
    1,
    ...rows.map((row) => {
      const last = row.findLastIndex((cell) => cell !== "");
      return last + 1;
    }),
  );
  const table = $("#preview-table");
  table.replaceChildren();
  const header = table.createTHead().insertRow();
  for (const text of [
    "",
    ...Array.from({ length: cols }, (_, index) =>
      String.fromCharCode(65 + index),
    ),
  ]) {
    const th = document.createElement("th");
    th.scope = "col";
    th.textContent = text;
    header.append(th);
  }
  const body = table.createTBody();
  rows.forEach((cells, index) => {
    const row = body.insertRow();
    for (const text of [
      index + 1,
      ...Array.from({ length: cols }, (_, col) => cells[col] || ""),
    ]) {
      row.insertCell().textContent = text;
    }
  });
  $("#preview-sheet-title").textContent = selected?.name || "设备巡检记录";
  $("#preview-source").textContent = selected
    ? `前 ${rows.length} 行 · 共 ${selected.rowCount} 个非空行`
    : "合成示例内容";
  updateControls();
}

async function loadFile(file) {
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".xlsx"))
    return status("请选择 .xlsx 文件；旧版 .xls 请先另存为 .xlsx。", true);
  if (file.size > 20 * 1024 * 1024)
    return status("Excel 超过 20MB，请缩小文件后重试。", true);
  inspectAbort?.abort();
  const controller = new AbortController();
  inspectAbort = controller;
  inspecting = true;
  updateControls();
  status("正在读取工作簿…");
  try {
    const data = new FormData();
    data.append("file", file);
    const response = await checked(
      await fetch("/api/inspect", {
        method: "POST",
        body: data,
        signal: controller.signal,
      }),
    );
    const result = await response.json();
    excelFile = file;
    workbook = result;
    $("#file-title").textContent = file.name;
    $("#file-description").textContent =
      `${(file.size / 1024).toFixed(1)} KB · ${workbook.sheets.length} 个工作表 · 点击可更换文件`;
    $("#sheet-section").hidden = false;
    $("#sheet-options").replaceChildren();
    $("#preview-sheet").replaceChildren();
    workbook.sheets.forEach((sheet) => {
      const label = document.createElement("label");
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.name = "sheets";
      checkbox.value = sheet.index;
      checkbox.checked = true;
      label.append(checkbox, document.createTextNode(sheet.name));
      $("#sheet-options").append(label);
      $("#preview-sheet").add(new Option(sheet.name, sheet.index));
    });
    $("#preview-sheet").disabled = false;
    renderTable();
    status("工作簿已就绪，调整设置后即可生成下载。");
  } catch (error) {
    if (error.name !== "AbortError") status(error.message, true);
  } finally {
    if (inspectAbort === controller) {
      inspecting = false;
      updateControls();
    }
  }
}

form.addEventListener("input", schedulePreview);
form.addEventListener("change", schedulePreview);
$(".color-options").addEventListener("click", (event) => {
  const button = event.target.closest("[data-color]");
  if (button) {
    $("#color").value = button.dataset.color;
    schedulePreview();
  }
});
$("#excel-file").addEventListener("change", (event) =>
  loadFile(event.target.files[0]),
);
$("#preview-sheet").addEventListener("change", renderTable);
$("#logo").addEventListener("change", () => {
  if ($("#logo").files[0]?.size > 5 * 1024 * 1024) {
    $("#logo").value = "";
    status("水印图片不能超过 5MB。", true);
  }
});
for (const button of document.querySelectorAll("[data-scene]")) {
  button.addEventListener("click", () => {
    scene = button.dataset.scene;
    document
      .querySelectorAll("[data-scene]")
      .forEach((tab) =>
        tab.setAttribute("aria-selected", String(tab === button)),
      );
    $("#preview-stage").setAttribute("aria-labelledby", button.id);
    $("#preview-stage").classList.toggle("print", scene === "print");
    $("#page-number").hidden = scene !== "print";
    updateControls();
  });
}
for (const name of ["dragenter", "dragover"])
  $("#drop-zone").addEventListener(name, (event) => {
    event.preventDefault();
    $("#drop-zone").classList.add("dragging");
  });
for (const name of ["dragleave", "drop"])
  $("#drop-zone").addEventListener(name, (event) => {
    event.preventDefault();
    $("#drop-zone").classList.remove("dragging");
    if (name === "drop") loadFile(event.dataTransfer.files[0]);
  });
$("#sample-button").addEventListener("click", async () => {
  $("#sample-button").disabled = true;
  try {
    const response = await checked(await fetch("/api/sample"));
    await loadFile(new File([await response.blob()], "水印演示数据.xlsx"));
  } catch (error) {
    status(error.message, true);
  } finally {
    $("#sample-button").disabled = false;
  }
});
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!excelFile || downloading || inspecting) return;
  const sheets = [...document.querySelectorAll("input[name=sheets]:checked")];
  if (!sheets.length) return status("请至少选择一个工作表。", true);
  downloading = true;
  $("#download-button").textContent = "正在生成…";
  updateControls();
  status("正在为工作簿生成水印…");
  try {
    const data = settingsData();
    data.append("file", excelFile);
    sheets.forEach((input) => data.append("sheets", input.value));
    const response = await checked(
      await fetch("/api/watermark", { method: "POST", body: data }),
    );
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = excelFile.name.replace(/\.xlsx$/i, "_水印.xlsx");
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 30_000);
    status("已生成下载。请打开 Excel，并在打印预览或导出的 PDF 中核实效果。");
  } catch (error) {
    status(error.message, true);
  } finally {
    downloading = false;
    $("#download-button").textContent = "生成并下载 Excel";
    updateControls();
  }
});
renderTable();
updatePreview();
