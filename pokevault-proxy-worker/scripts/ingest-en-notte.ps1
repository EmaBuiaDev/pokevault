# Lancia scripts/ingest-en-tutti.mjs staccato dalla sessione e tiene sveglio il
# PC finche' non ha finito (SetThreadExecutionState: niente permessi di
# amministratore, l'effetto finisce con questo processo).
#
#   Start-Process powershell -WindowStyle Hidden -ArgumentList '-ExecutionPolicy','Bypass','-File','scripts\ingest-en-notte.ps1'
#
# Uscita del processo in scripts/.ingest-en-uscita.txt, avanzamento in
# scripts/.ingest-en-log.txt, stato per set in scripts/.ingest-en-stato.json.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Add-Type -Namespace Win32 -Name Power -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);
'@
# ES_CONTINUOUS | ES_SYSTEM_REQUIRED: il sistema non va in sospensione (lo schermo si')
[void][Win32.Power]::SetThreadExecutionState([uint32]'0x80000001')

$out = Join-Path $root 'scripts\.ingest-en-uscita.txt'
"[$(Get-Date -Format s)] avvio" | Out-File -FilePath $out -Encoding utf8 -Append
& node scripts/ingest-en-tutti.mjs *>> $out
"[$(Get-Date -Format s)] uscito con codice $LASTEXITCODE" | Out-File -FilePath $out -Encoding utf8 -Append

[void][Win32.Power]::SetThreadExecutionState([uint32]'0x80000000')
