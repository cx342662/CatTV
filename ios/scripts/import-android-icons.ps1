$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$assetsRoot = Join-Path $repoRoot 'ios/App/Assets.xcassets'
New-Item -ItemType Directory -Force $assetsRoot | Out-Null
'{"info":{"author":"xcode","version":1}}' | Set-Content (Join-Path $assetsRoot 'Contents.json') -Encoding utf8
$androidNamespace = 'http://schemas.android.com/apk/res/android'
foreach ($icon in @('ic_tab_home','ic_tab_records','ic_tab_following','ic_tab_mine','ic_live_fab','cattv_home_logo')) {
    $sourcePath = Join-Path $repoRoot "app/src/main/res/drawable/$icon.xml"
    [xml]$vector = Get-Content -LiteralPath $sourcePath -Raw
    $width = $vector.vector.GetAttribute('viewportWidth', $androidNamespace)
    $height = $vector.vector.GetAttribute('viewportHeight', $androidNamespace)
    $paths = foreach ($node in $vector.SelectNodes('//path')) {
        $pathData = [System.Security.SecurityElement]::Escape($node.GetAttribute('pathData', $androidNamespace))
        $translateY = $node.ParentNode.GetAttribute('translateY', $androidNamespace)
        if (!$translateY) { $translateY = '0' }
        if ($icon -eq 'cattv_home_logo') {
            $fill = $node.GetAttribute('fillColor', $androidNamespace)
            if (!$fill -or $fill -eq '#00000000') { $fill = 'none' }
            $stroke = $node.GetAttribute('strokeColor', $androidNamespace)
            $strokeWidth = $node.GetAttribute('strokeWidth', $androidNamespace)
            if (!$stroke) { $stroke = 'none' }
            if (!$strokeWidth) { $strokeWidth = '0' }
            $cap = $node.GetAttribute('strokeLineCap', $androidNamespace)
            $join = $node.GetAttribute('strokeLineJoin', $androidNamespace)
            if (!$cap) { $cap = 'butt' }
            if (!$join) { $join = 'miter' }
            "<path fill=`"$fill`" stroke=`"$stroke`" stroke-width=`"$strokeWidth`" stroke-linecap=`"$cap`" stroke-linejoin=`"$join`" d=`"$pathData`"/>"
        } else {
            "<path fill=`"#000000`" transform=`"translate(0,$translateY)`" d=`"$pathData`"/>"
        }
    }
    $targetDirectory = Join-Path $assetsRoot "$icon.imageset"
    New-Item -ItemType Directory -Force $targetDirectory | Out-Null
    "<svg xmlns=`"http://www.w3.org/2000/svg`" width=`"24`" height=`"24`" viewBox=`"0 0 $width $height`">$($paths -join '')</svg>" | Set-Content (Join-Path $targetDirectory "$icon.svg") -Encoding utf8
    $renderingIntent = if ($icon -eq 'cattv_home_logo') { 'original' } else { 'template' }
    [ordered]@{ images = @([ordered]@{ filename = "$icon.svg"; idiom = 'universal' }); info = [ordered]@{ author = 'xcode'; version = 1 }; properties = [ordered]@{ 'preserves-vector-representation' = $true; 'template-rendering-intent' = $renderingIntent } } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $targetDirectory 'Contents.json') -Encoding utf8
}
