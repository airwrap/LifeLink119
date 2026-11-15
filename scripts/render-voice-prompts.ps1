param([Parameter(Mandatory=$true)][string]$Manifest,[Parameter(Mandatory=$true)][string]$OutputDirectory,[int]$SampleRate=22050)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Speech
$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $speaker.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'ko-KR' } | Select-Object -First 1
    if (!$voice) { throw 'A local Korean Windows speech voice is required to render fixed fallback prompts.' }
    $speaker.SelectVoice($voice.VoiceInfo.Name)
    $speaker.Rate = -1
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $rows = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($row in $rows) {
        if ($row.file -notmatch '^[a-f0-9]{20}\.wav$') { throw 'Invalid prompt asset name' }
        $target = Join-Path $OutputDirectory $row.file
        if (!(Test-Path -LiteralPath $target)) {
            $format=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo($SampleRate,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
            $speaker.SetOutputToWaveFile($target,$format)
            $speaker.Speak([string]$row.text)
            $speaker.SetOutputToNull()
        }
    }
} finally { $speaker.Dispose() }
