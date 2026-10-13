# Guardiano dell'import notturno del catalogo EN: aspetta che il giro finisca e,
# se il log non ha la riga "FINE." (il giro e' morto a meta': errore fatale,
# processo ucciso...), lo rilancia. Al massimo 5 volte, con 5 minuti di pausa.
# Il giro riparte da dove era: lo stato e' in scripts/.ingest-en-stato.json.
#
#   Start-Process powershell -WindowStyle Hidden -ArgumentList '-ExecutionPolicy','Bypass','-File','scripts\ingest-en-guardiano.ps1','-AspettaPid','<pid del giro>'
param([int]$AspettaPid = 0)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$log = Join-Path $root 'scripts\.ingest-en-log.txt'
$mine = Join-Path $root 'scripts\.ingest-en-guardiano.txt'

function Finito {
  if (-not (Test-Path $log)) { return $false }
  $last = Get-Content $log -Tail 3 -Encoding utf8
  return [bool]($last | Where-Object { $_ -match '\] FINE\.' })
}

if ($AspettaPid -gt 0) { Wait-Process -Id $AspettaPid -ErrorAction SilentlyContinue }
for ($i = 1; $i -le 5; $i++) {
  if (Finito) { "[$(Get-Date -Format s)] il giro e' finito: niente da fare" | Out-File $mine -Encoding utf8 -Append; break }
  "[$(Get-Date -Format s)] il giro non ha finito: rilancio $i/5 fra 5 minuti" | Out-File $mine -Encoding utf8 -Append
  Start-Sleep -Seconds 300
  $p = Start-Process powershell -WindowStyle Hidden -PassThru -ArgumentList '-ExecutionPolicy','Bypass','-File',(Join-Path $root 'scripts\ingest-en-notte.ps1'),'--ritenta','--incompleti','--poi-prezzi'
  Wait-Process -Id $p.Id -ErrorAction SilentlyContinue
}
