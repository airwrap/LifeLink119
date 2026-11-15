param([ValidateSet('start','stop')][string]$Action='start',[switch]$OpenBrowser,[ValidateRange(0,2)][double]$ReadDelay=0)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$stateFolder=Join-Path $projectRoot 'verification'
$pidFile=Join-Path $stateFolder 'local-console-process.json'
$serverFile=Join-Path $projectRoot 'console\server.py'
$expectedVersion=[regex]::Match((Get-Content -LiteralPath (Join-Path $projectRoot 'app\build.gradle.kts') -Raw),'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if(!$expectedVersion){throw 'Could not read application version.'}
$address='http://127.0.0.1:8781'
function Get-DemoHealth([int]$Seconds) {
    $request=[System.Net.HttpWebRequest]::Create("$address/api/demo/health")
    $request.Proxy=$null
    $request.Timeout=$Seconds*1000
    $request.ReadWriteTimeout=$Seconds*1000
    $response=$request.GetResponse()
    try {
        $reader=New-Object System.IO.StreamReader($response.GetResponseStream())
        try {return ($reader.ReadToEnd() | ConvertFrom-Json)} finally {$reader.Dispose()}
    } finally {$response.Dispose()}
}
if($Action -eq 'stop') {
    if(!(Test-Path -LiteralPath $pidFile)){Write-Host 'No server started by this launcher was recorded.';exit 0}
    $record=Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $ownedProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$record.processId)" -ErrorAction SilentlyContinue
    if($ownedProcess -and $ownedProcess.CommandLine -like "*$serverFile*") {
        Stop-Process -Id $ownedProcess.ProcessId
        Write-Host 'LifeLink119 local demo stopped. In-memory sessions were cleared.'
    } elseif($ownedProcess) {throw 'Process ID now belongs to another program. It was preserved.'}
    else {Write-Host 'Local demo is already stopped.'}
    exit 0
}
try {$health=Get-DemoHealth 2} catch {$health=$null}
if($health -and $health.mode -eq 'DEMO' -and $health.version -eq $expectedVersion) {
    Write-Host "LifeLink119 demo is already running: $address"
} else {
    $listener=Get-NetTCPConnection -LocalPort 8781 -State Listen -ErrorAction SilentlyContinue
    if($listener){throw 'Port 8781 belongs to another service. It was preserved.'}
    $pythonCommand=Get-Command python -ErrorAction Stop
    & $pythonCommand.Source -c 'import jsonschema'
    if($LASTEXITCODE -ne 0){throw 'Run: python -m pip install -r console/requirements.txt'}
    New-Item -ItemType Directory -Path $stateFolder -Force | Out-Null
    $process=Start-Process -FilePath $pythonCommand.Source -ArgumentList @('-u',('"'+$serverFile+'"'),'--read-delay',$ReadDelay.ToString([System.Globalization.CultureInfo]::InvariantCulture)) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $stateFolder 'local-console-out.log') -RedirectStandardError (Join-Path $stateFolder 'local-console-error.log') -PassThru
    @{processId=$process.Id;server=$serverFile;started=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
    $ready=$false
    for($attempt=0;$attempt -lt 20;$attempt++) {
        Start-Sleep -Milliseconds 400
        try {$health=Get-DemoHealth 1;if($health.mode -eq 'DEMO' -and $health.version -eq $expectedVersion){$ready=$true;break}} catch {}
        if($process.HasExited){break}
    }
    if(!$ready){throw 'Local server did not become ready. See verification/local-console-error.log.'}
    Write-Host "LifeLink119 demo ready: $address"
}
Write-Host "Passenger demo: $address/passenger"
Write-Host "Expanded app snapshots: $address/experience-console"
Write-Host 'For Android, run Connect-Local-Console.cmd after the emulator boots.'
if($OpenBrowser){Start-Process -FilePath "$address/"}
