param([string]$BaseUrl = 'http://127.0.0.1:18092')
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskOutput = Join-Path $taskRoot 'output/verification'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskSample = Join-Path $taskOutput 'sample.xlsx'
Invoke-WebRequest -Uri "$BaseUrl/api/sample" -OutFile $taskSample

# 合成透明 PNG 图片，仅作为图片水印测试输入。
Add-Type -AssemblyName System.Drawing
$taskLogo = Join-Path $taskOutput 'logo.png'
$taskBitmap = [Drawing.Bitmap]::new(220, 80)
$taskGraphics = [Drawing.Graphics]::FromImage($taskBitmap)
try {
    $taskGraphics.Clear([Drawing.Color]::Transparent)
    $taskBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(35, 102, 79))
    $taskFont = [Drawing.Font]::new('Arial', 30, [Drawing.FontStyle]::Bold)
    try { $taskGraphics.DrawString('DEMO', $taskFont, $taskBrush, 12, 12) }
    finally { $taskBrush.Dispose(); $taskFont.Dispose() }
    $taskBitmap.Save($taskLogo, [Drawing.Imaging.ImageFormat]::Png)
} finally { $taskGraphics.Dispose(); $taskBitmap.Dispose() }

$taskCases = @(
    @{ Name='screen'; Visibility='SCREEN'; Type='TEXT'; Layout='tile' },
    @{ Name='print'; Visibility='PRINT'; Type='TEXT'; Layout='tile' },
    @{ Name='both'; Visibility='BOTH'; Type='TEXT'; Layout='tile' },
    @{ Name='image'; Visibility='BOTH'; Type='IMAGE'; Layout='tile' },
    @{ Name='combined'; Visibility='BOTH'; Type='BOTH'; Layout='single' }
)
foreach ($taskCase in $taskCases) {
    $taskTarget = Join-Path $taskOutput ($taskCase.Name + '.xlsx')
    & curl.exe --fail --silent --show-error -o $taskTarget -F "file=@$taskSample" -F "logo=@$taskLogo" `
        -F "visibility=$($taskCase.Visibility)" -F "type=$($taskCase.Type)" -F "layout=$($taskCase.Layout)" `
        -F 'text=INTERNAL DEMO' -F 'opacity=0.24' -F 'fontSize=32' "$BaseUrl/api/watermark"
    if ($LASTEXITCODE -ne 0) { throw "生成 $($taskCase.Name) 失败" }
}

# 新建独立 Excel 实例，始终只关闭本脚本打开的文件。
$taskExcel = New-Object -ComObject Excel.Application
$taskWorkbook = $null
try {
    $taskExcel.Visible = $false
    $taskExcel.DisplayAlerts = $false
    $taskExcel.EnableEvents = $false
    $taskExcel.AutomationSecurity = 3
    $taskResults = @()
    foreach ($taskCase in $taskCases) {
        $taskPath = Join-Path $taskOutput ($taskCase.Name + '.xlsx')
        $taskWorkbook = $taskExcel.Workbooks.Open($taskPath, 0, $true)
        $taskSheet = $taskWorkbook.Worksheets.Item(1)
        $taskCenterHeader = $taskSheet.PageSetup.CenterHeader
        $taskValue = $taskSheet.Cells.Item(2, 2).Text
        if ($taskValue -ne 'DEMO-001') { throw 'Excel 打开后单元格数据异常' }
        $taskExpectedPrint = $taskCase.Visibility -ne 'SCREEN'
        if ($taskCenterHeader.Contains('&G') -ne $taskExpectedPrint) { throw '打印页眉配置与预期不符' }
        $taskFormulaSheet = $taskWorkbook.Worksheets.Item(2)
        $taskFormula = $taskFormulaSheet.Cells.Item(87, 4).Formula
        if ($taskFormula -ne '=SUM(D2:D86)') { throw '原有公式未保留' }
        $taskPdf = Join-Path $taskOutput ($taskCase.Name + '.pdf')
        $taskWorkbook.ExportAsFixedFormat(0, $taskPdf)
        $taskResults += [pscustomobject]@{
            Case = $taskCase.Name; OpenedByExcel = $true; Header = $taskCenterHeader
            DataPreserved = $true; FormulaPreserved = $true
            PdfBytes = (Get-Item -LiteralPath $taskPdf).Length
        }
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskSheet) | Out-Null
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskFormulaSheet) | Out-Null
        $taskWorkbook.Close($false)
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskWorkbook) | Out-Null
        $taskWorkbook = $null
    }
    $taskResults | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'excel-results.json') -Encoding utf8NoBOM
    $taskResults | Format-Table
} finally {
    if ($taskWorkbook) { $taskWorkbook.Close($false) }
    $taskExcel.Quit()
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskExcel) | Out-Null
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
