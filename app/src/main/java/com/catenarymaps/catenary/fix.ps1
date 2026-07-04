
$content = Get-Content AddLiveDots.kt -Raw
$content = $content -replace '(?s)layerSettings\s*\.\s*value', 'layerSettings'
Set-Content AddLiveDots.kt -Value $content -Encoding UTF8

