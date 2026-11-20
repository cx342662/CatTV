$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$assetsRoot = Join-Path $repoRoot 'ios/App/Assets.xcassets'
New-Item -ItemType Directory -Force $assetsRoot | Out-Null
'{"info":{"author":"xcode","version":1}}' | Set-Content (Join-Path $assetsRoot 'Contents.json') -Encoding utf8
$androidNamespace = 'http://schemas.android.com/apk/res/android'
foreach ($icon in @('ic_tab_home','ic_tab_records','ic_tab_following','ic_tab_mine')) {
    $sourcePath = Join-Path $repoRoot "app/src/main/res/drawable/$icon.xml"
    [xml]$vector = Get-Content -LiteralPath $sourcePath -Raw
    $width = $vector.vector.GetAttribute('viewportWidth', $androidNamespace)
    $height = $vector.vector.GetAttribute('viewportHeight', $androidNamespace)
    $paths = foreach ($node in $vector.SelectNodes('//path')) {
        $pathData = [System.Security.SecurityElement]::Escape($node.GetAttribute('pathData', $androidNamespace))
        $translateY = $node.ParentNode.GetAttribute('translateY', $androidNamespace)
        if (!$translateY) { $translateY = '0' }
        "<path fill=`"#000000`" transform=`"translate(0,$translateY)`" d=`"$pathData`"/>"
    }
    $targetDirectory = Join-Path $assetsRoot "$icon.imageset"
    New-Item -ItemType Directory -Force $targetDirectory | Out-Null
    "<svg xmlns=`"http://www.w3.org/2000/svg`" width=`"24`" height=`"24`" viewBox=`"0 0 $width $height`">$($paths -join '')</svg>" | Set-Content (Join-Path $targetDirectory "$icon.svg") -Encoding utf8
    @{ images = @(@{ filename = "$icon.svg"; idiom = 'universal' }); info = @{ author = 'xcode'; version = 1 }; properties = @{ 'preserves-vector-representation' = $true; 'template-rendering-intent' = 'template' } } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $targetDirectory 'Contents.json') -Encoding utf8
}
