
$content = Get-Content MainActivity.kt -Raw
$startMarker = '// Layers for BUS'
$endMarker = '// Highlighted vehicle context dots.'

$startIdx = $content.IndexOf($startMarker)
$endIdx = $content.IndexOf($endMarker)

$layersCode = $content.Substring($startIdx, $endIdx - $startIdx)

$newContent = $content.Substring(0, $startIdx) + $content.Substring($endIdx)
Set-Content MainActivity.kt -Value $newContent -Encoding UTF8

$addLiveDotsContent = Get-Content AddLiveDots.kt -Raw
$insertMarker = '    }' + [Environment]::NewLine + '}' + [Environment]::NewLine

$insertIdx = $addLiveDotsContent.IndexOf($insertMarker)
if ($insertIdx -ge 0) {
    $newAddLiveDotsContent = $addLiveDotsContent.Substring(0, $insertIdx + 6) + [Environment]::NewLine + $layersCode + [Environment]::NewLine + $addLiveDotsContent.Substring($insertIdx + 6)
    Set-Content AddLiveDots.kt -Value $newAddLiveDotsContent -Encoding UTF8
    Write-Host 'Success'
} else {
    Write-Host 'Insert marker not found in AddLiveDots.kt'
}

