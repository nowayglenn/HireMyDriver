param([string[]]$OnlyLayouts = @())
$ErrorActionPreference = 'Stop'
$resRoot = Join-Path $PSScriptRoot '../app/src/main/res'
$strings = @{}; $dimens = @{}; $colors = @{}; $styles = @{}; $layouts = @{}; $drawables = @{}
function Convert-Element($element) {
    $attrs = @{}
    foreach($attribute in $element.Attributes) {
        if($attribute.Prefix -eq 'xmlns' -or $attribute.Name -eq 'xmlns') { continue }
        $key = if($attribute.Prefix -eq 'tools') { 'tools_' + $attribute.LocalName } else { $attribute.LocalName }
        $attrs[$key] = $attribute.Value
    }
    $children = @()
    foreach($child in $element.ChildNodes) { if($child.NodeType -eq 'Element') { $children += (Convert-Element $child) } }
    return @{ tag=$element.LocalName; a=$attrs; c=$children }
}
Get-ChildItem -LiteralPath (Join-Path $resRoot 'values') -Filter '*.xml' | ForEach-Object {
    [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw
    foreach($element in $xml.DocumentElement.ChildNodes) {
        if($element.NodeType -ne 'Element') { continue }
        $key=$element.GetAttribute('name')
        switch($element.LocalName) {
            'string' { $strings[$key]=$element.InnerText }
            'dimen' { $dimens[$key]=$element.InnerText }
            'color' { $colors[$key]=$element.InnerText }
            'style' {
                $items=@{}
                foreach($item in $element.ChildNodes) { if($item.NodeType -eq 'Element') { $items[$item.GetAttribute('name').Replace('android:','')]=$item.InnerText } }
                $styles[$key]=@{parent=$element.GetAttribute('parent');items=$items}
            }
        }
    }
}
Get-ChildItem -LiteralPath (Join-Path $resRoot 'layout') -Filter '*.xml' | ForEach-Object {
    [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw
    $layouts[$_.BaseName]=Convert-Element $xml.DocumentElement
}
Get-ChildItem -LiteralPath (Join-Path $resRoot 'drawable') -Filter '*.xml' | ForEach-Object {
    [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw
    $drawables[$_.BaseName]=Convert-Element $xml.DocumentElement
}
$localizedStrings = @{}
foreach($code in @('tl','ilo')) {
    $translated = @{}
    Get-ChildItem -LiteralPath (Join-Path $resRoot "values-$code") -Filter '*.xml' | ForEach-Object {
        [xml]$xml = Get-Content -LiteralPath $_.FullName -Raw
        foreach($element in $xml.DocumentElement.ChildNodes) {
            if($element.LocalName -eq 'string') { $translated[$element.GetAttribute('name')] = $element.InnerText }
        }
    }
    $localizedStrings[$code] = $translated
}
$data=@{strings=$strings;localizedStrings=$localizedStrings;dimens=$dimens;colors=$colors;styles=$styles;layouts=$layouts;drawables=$drawables}
if($OnlyLayouts.Count -gt 0) {
    # Keep presentation-specific fixes on screens outside this requested update.
    $existingText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'android-resources.js') -Raw
    $existing = ($existingText -replace '^const ANDROID\s*=\s*','' -replace ';\s*$','') | ConvertFrom-Json
    foreach($property in $existing.layouts.PSObject.Properties) {
        if($property.Name -notin $OnlyLayouts) { $layouts[$property.Name] = $property.Value }
    }
    $data.styles = $existing.styles
    $data.drawables = $existing.drawables
    $data.dimens = $existing.dimens
}
$json=ConvertTo-Json -InputObject $data -Depth 100 -Compress
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'android-resources.js'), "const ANDROID = $json;", [Text.UTF8Encoding]::new($false))
Write-Output "Exported $($layouts.Count) Android layouts, $($drawables.Count) drawables, and original styles."
