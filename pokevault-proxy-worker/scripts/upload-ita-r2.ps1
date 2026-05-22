param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,

    [string[]]$SetCodes,

    [string]$CatalogPath,

    [string]$Bucket = "pokevault-images",

    [switch]$SkipImages,

    [switch]$SkipCatalog,

    [switch]$UseWrangler4,

    [int]$Throttle = 6,

    [int]$RetryCount = 3
)

$ErrorActionPreference = "Stop"

function Assert-PathExists {
    param(
        [string]$Path,
        [string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label non trovato: $Path"
    }
}

function Get-TargetSets {
    param(
        [string]$Root,
        [string[]]$RequestedSetCodes
    )

    if ($RequestedSetCodes -and $RequestedSetCodes.Count -gt 0) {
        return $RequestedSetCodes |
            ForEach-Object { $_ -split ',' } |
            ForEach-Object { $_.Trim().ToUpperInvariant() } |
            Where-Object { $_ }
    }

    return Get-ChildItem -LiteralPath $Root -Directory |
        ForEach-Object { $_.Name.Trim().ToUpperInvariant() } |
        Where-Object { $_ }
}

function Upload-Object {
    param(
        [string]$WranglerExecutable,
        [string[]]$WranglerArgsPrefix,
        [string]$WorkerRoot,
        [string]$BucketName,
        [string]$Key,
        [string]$FilePath
    )

    $objectRef = "$BucketName/$Key"
    Write-Host "UPLOAD $objectRef"
    & $WranglerExecutable @WranglerArgsPrefix r2 object put $objectRef --file "$FilePath" --remote
    if ($LASTEXITCODE -ne 0) {
        throw "Upload fallito: $objectRef"
    }
}

function Flush-CompletedJobs {
    param(
        [System.Collections.ArrayList]$Jobs,
        [int]$TotalFiles,
        [ref]$CompletedFiles,
        [ref]$FailedFiles,
        [System.Collections.ArrayList]$FailedKeys
    )

    $finishedJobs = @($Jobs | Where-Object { $_.State -in @("Completed", "Failed", "Stopped") })
    foreach ($job in $finishedJobs) {
        $result = Receive-Job -Job $job -Keep
        if ($job.State -ne "Completed" -or $result.ExitCode -ne 0) {
            $FailedFiles.Value += 1
            $message = if ($result.Output) { $result.Output.Trim() } else { "Errore sconosciuto" }
            [void]$FailedKeys.Add($result.Key)
            Write-Warning "FAILED [$($result.Key)] $message"
        } else {
            $CompletedFiles.Value += 1
            Write-Host ("[{0}/{1}] {2}" -f $CompletedFiles.Value, $TotalFiles, $result.Key)
        }

        Remove-Job -Job $job -Force
        [void]$Jobs.Remove($job)
    }
}

Assert-PathExists -Path $SourceRoot -Label "SourceRoot"

$allowedExtensions = @(".png", ".webp", ".jpg", ".jpeg")
$targetSets = Get-TargetSets -Root $SourceRoot -RequestedSetCodes $SetCodes
$workerRoot = Split-Path -Parent $PSScriptRoot
$wranglerExecutable = $null
$wranglerArgsPrefix = @()

if ($UseWrangler4) {
    $localWranglerCmd = Join-Path $workerRoot "node_modules/.bin/wrangler.cmd"
    $localWrangler = Join-Path $workerRoot "node_modules/.bin/wrangler"

    if (Test-Path -LiteralPath $localWranglerCmd) {
        $wranglerExecutable = $localWranglerCmd
    } elseif (Test-Path -LiteralPath $localWrangler) {
        $wranglerExecutable = $localWrangler
    } else {
        $npmCommandInfo = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if ($npmCommandInfo) {
            $wranglerExecutable = $npmCommandInfo.Source
        }
        if (-not $wranglerExecutable) {
            $npmFallbackInfo = Get-Command npm -ErrorAction SilentlyContinue
            if ($npmFallbackInfo) {
                $wranglerExecutable = $npmFallbackInfo.Source
            }
        }
        if (-not $wranglerExecutable) {
            throw "npm non trovato nel PATH"
        }

        $wranglerArgsPrefix = @("exec", "--yes", "--package", "wrangler@4", "--", "wrangler")
    }
} else {
    $npxCommandInfo = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if ($npxCommandInfo) {
        $wranglerExecutable = $npxCommandInfo.Source
    }
    if (-not $wranglerExecutable) {
        $npxFallbackInfo = Get-Command npx -ErrorAction SilentlyContinue
        if ($npxFallbackInfo) {
            $wranglerExecutable = $npxFallbackInfo.Source
        }
    }
    if (-not $wranglerExecutable) {
        throw "npx non trovato nel PATH"
    }

    $wranglerArgsPrefix = @("wrangler")
}

if ($Throttle -lt 1) {
    throw "Throttle deve essere almeno 1"
}

if ($RetryCount -lt 1) {
    throw "RetryCount deve essere almeno 1"
}

if (-not $SkipImages) {
    if (-not $targetSets -or $targetSets.Count -eq 0) {
        throw "Nessuna espansione trovata in $SourceRoot"
    }

    $uploads = New-Object System.Collections.Generic.List[object]

    foreach ($setCode in $targetSets) {
        $setDir = Join-Path $SourceRoot $setCode
        Assert-PathExists -Path $setDir -Label "Cartella espansione $setCode"

        $files = Get-ChildItem -LiteralPath $setDir -File |
            Where-Object { $allowedExtensions -contains $_.Extension.ToLowerInvariant() } |
            Sort-Object Name

        if (-not $files) {
            Write-Warning "Nessun file immagine trovato per $setCode in $setDir"
            continue
        }

        foreach ($file in $files) {
            $uploads.Add([PSCustomObject]@{
                SetCode = $setCode
                Key = "it/$setCode/$($file.Name)"
                FilePath = $file.FullName
            }) | Out-Null
        }
    }

    $totalFiles = $uploads.Count
    Write-Host "Upload concorrente avviato: $totalFiles file, throttle=$Throttle"

    $jobs = New-Object System.Collections.ArrayList
    $completedFiles = 0
    $failedFiles = 0
    $failedKeys = New-Object System.Collections.ArrayList
    $totalBySet = @{}

    foreach ($upload in $uploads) {
        if (-not $totalBySet.ContainsKey($upload.SetCode)) {
            $totalBySet[$upload.SetCode] = 0
        }
        $totalBySet[$upload.SetCode] += 1
    }

    foreach ($upload in $uploads) {
        while (@($jobs | Where-Object { $_.State -eq "Running" }).Count -ge $Throttle) {
            Wait-Job -Job $jobs -Any | Out-Null
            Flush-CompletedJobs -Jobs $jobs -TotalFiles $totalFiles -CompletedFiles ([ref]$completedFiles) -FailedFiles ([ref]$failedFiles) -FailedKeys $failedKeys
        }

        [void]$jobs.Add((Start-Job -ScriptBlock {
            param($WranglerExecutablePath, $WranglerArgs, $WorkerDir, $BucketName, $UploadKey, $UploadFile, $MaxRetries)

            Set-Location $WorkerDir
            $attempt = 0

            while ($attempt -lt $MaxRetries) {
                $attempt += 1
                $output = & $WranglerExecutablePath @WranglerArgs r2 object put "$BucketName/$UploadKey" --file "$UploadFile" --remote 2>&1 | Out-String
                $exitCode = $LASTEXITCODE

                if ($exitCode -eq 0) {
                    return [PSCustomObject]@{
                        Key = $UploadKey
                        ExitCode = 0
                        Output = $output
                    }
                }

                $isTransient = $output -match '503|Service Unavailable|ETIMEDOUT|ECONNRESET|fetch failed|Internal Error'
                if (-not $isTransient -or $attempt -ge $MaxRetries) {
                    return [PSCustomObject]@{
                        Key = $UploadKey
                        ExitCode = $exitCode
                        Output = "Attempt ${attempt}/${MaxRetries}`n$output"
                    }
                }
            }
        } -ArgumentList $wranglerExecutable, $wranglerArgsPrefix, $workerRoot, $Bucket, $upload.Key, $upload.FilePath, $RetryCount))
    }

    while ($jobs.Count -gt 0) {
        Wait-Job -Job $jobs -Any | Out-Null
        Flush-CompletedJobs -Jobs $jobs -TotalFiles $totalFiles -CompletedFiles ([ref]$completedFiles) -FailedFiles ([ref]$failedFiles) -FailedKeys $failedKeys
    }

    foreach ($setCode in $targetSets) {
        $setTotal = $totalBySet[$setCode]
        if ($setTotal) {
            Write-Host "Espansione prevista ${setCode}: $setTotal file"
        }
    }

    if ($failedFiles -gt 0) {
        Write-Host "File falliti: $($failedKeys -join ', ')"
        throw "Upload completato con $failedFiles errori su $totalFiles file"
    }
}

if (-not $SkipCatalog) {
    if ([string]::IsNullOrWhiteSpace($CatalogPath)) {
        throw "CatalogPath obbligatorio se non usi -SkipCatalog"
    }

    Assert-PathExists -Path $CatalogPath -Label "CatalogPath"
    Upload-Object -WranglerExecutable $wranglerExecutable -WranglerArgsPrefix $wranglerArgsPrefix -WorkerRoot $workerRoot -BucketName $Bucket -Key "it/catalog/cards.cleaned.json" -FilePath $CatalogPath
    Write-Host "Catalogo aggiornato: it/catalog/cards.cleaned.json"
}

Write-Host "Upload ITA completato"