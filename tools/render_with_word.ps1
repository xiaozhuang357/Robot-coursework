param(
    [Parameter(Mandatory=$true)][string]$InputDocx,
    [Parameter(Mandatory=$true)][string]$OutputPdf
)

# 仅打开本任务生成的文档，独立实例后台导出，不接触其他已打开文件。
$lineguardWord = $null
$lineguardDocument = $null
try {
    $lineguardWord = New-Object -ComObject Word.Application
    $lineguardWord.Visible = $false
    $lineguardWord.DisplayAlerts = 0
    $lineguardWord.AutomationSecurity = 3
    $lineguardDocument = $lineguardWord.Documents.Open($InputDocx, $false, $true)
    $lineguardDocument.Repaginate()
    $lineguardDocument.ExportAsFixedFormat($OutputPdf, 17)
    [pscustomobject]@{Pdf=$OutputPdf; Pages=$lineguardDocument.ComputeStatistics(2)}
}
finally {
    if ($null -ne $lineguardDocument) {
        $lineguardDocument.Close(0)
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($lineguardDocument)
    }
    if ($null -ne $lineguardWord) {
        $lineguardWord.Quit(0)
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($lineguardWord)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
