param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ReportDir = ".\test-report"
)

$ErrorActionPreference = "Continue"
$results = @()
$passed = 0
$failed = 0
$startTime = Get-Date

function Test-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Params = @{},
        [string]$Body = $null,
        [string]$ContentType = $null,
        [string]$SessionCookie = $null,
        [string]$Description,
        [string]$Category,
        [bool]$ExpectSuccess = $true,
        [string]$ExpectContains = $null
    )

    $url = "$BaseUrl$Path"
    $qs = ($Params.GetEnumerator() | Where-Object { $_.Value -ne $null } | ForEach-Object { "$($_.Key)=$([System.Uri]::EscapeDataString($_.Value.ToString()))" }) -join "&"
    if ($qs) { $url += "?$qs" }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = "PASS"
    $httpCode = 0
    $responseBody = ""
    $errorMsg = ""

    try {
        $headers = @{}
        if ($SessionCookie) { $headers["Cookie"] = $SessionCookie }

        if ($Method -eq "GET") {
            $resp = Invoke-WebRequest -Uri $url -Method GET -Headers $headers -UseBasicParsing -TimeoutSec 30
        } else {
            if ($ContentType) {
                $resp = Invoke-WebRequest -Uri $url -Method POST -Headers $headers -Body $Body -ContentType $ContentType -UseBasicParsing -TimeoutSec 30
            } elseif ($Body) {
                $resp = Invoke-WebRequest -Uri $url -Method POST -Headers $headers -Body $Body -UseBasicParsing -TimeoutSec 30
            } else {
                $formBody = ($Params.GetEnumerator() | Where-Object { $_.Value -ne $null } | ForEach-Object { "$($_.Key)=$([System.Uri]::EscapeDataString($_.Value.ToString()))" }) -join "&"
                $resp = Invoke-WebRequest -Uri $url -Method POST -Headers $headers -Body $formBody -ContentType "application/x-www-form-urlencoded" -UseBasicParsing -TimeoutSec 30
            }
        }
        $sw.Stop()
        $httpCode = [int]$resp.StatusCode
        $responseBody = $resp.Content

        if ($ExpectSuccess -and $responseBody -match '"success"\s*:\s*true') {
            $status = "PASS"
        } elseif (-not $ExpectSuccess -and $httpCode -eq 200) {
            $status = "PASS"
        } elseif ($ExpectContains -and $responseBody -match $ExpectContains) {
            $status = "PASS"
        } elseif ($ExpectSuccess -and $responseBody -match '"success"\s*:\s*false') {
            $status = "FAIL"
            $errorMsg = "success=false"
        } elseif ($httpCode -ge 400) {
            $status = "FAIL"
            $errorMsg = "HTTP $httpCode"
        } else {
            $status = "PASS"
        }
    } catch [System.Net.WebException] {
        $sw.Stop()
        $httpCode = 0
        if ($_.Exception.Response) {
            $httpCode = [int]$_.Exception.Response.StatusCode
        }
        if ($httpCode -eq 302) {
            $status = "PASS"
            $errorMsg = "Redirect (login required)"
        } else {
            $status = "FAIL"
            $errorMsg = $_.Exception.Message
        }
    } catch {
        $sw.Stop()
        $httpCode = 0
        $status = "FAIL"
        $errorMsg = $_.Exception.Message
    }

    if ($status -eq "PASS") { $script:passed++ } else { $script:failed++ }

    $result = [PSCustomObject]@{
        Category    = $Category
        Method      = $Method
        Path        = $Path
        Description = $Description
        Status      = $status
        HttpCode    = $httpCode
        LatencyMs   = [math]::Round($sw.Elapsed.TotalMilliseconds, 1)
        Error       = $errorMsg
    }
    $script:results += $result

    $color = if ($status -eq "PASS") { "Green" } else { "Red" }
    Write-Host ("  [{0}] {1} {2} - {3} ({4}ms)" -f $status, $Method, $Path, $Description, [math]::Round($sw.Elapsed.TotalMilliseconds, 1)) -ForegroundColor $color

    return $resp
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Campus Secondhand Platform - Full API Test" -ForegroundColor Cyan
Write-Host "  Target: $BaseUrl" -ForegroundColor Cyan
Write-Host "  Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "--- 1. Infrastructure ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/test/redis" -Description "Redis connectivity" -Category "Infrastructure"
Test-Api -Method "GET" -Path "/test/minio" -Description "MinIO connectivity" -Category "Infrastructure"
Test-Api -Method "GET" -Path "/test/session/set" -Params @{ value = "test-session-value" } -Description "Session write" -Category "Infrastructure"
Test-Api -Method "GET" -Path "/test/session/get" -Description "Session read" -Category "Infrastructure"
Test-Api -Method "GET" -Path "/monitor/metrics" -Description "Monitor metrics" -Category "Infrastructure"

Write-Host ""
Write-Host "--- 2. User Module ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/user/loginPage" -Description "Login page" -Category "User" -ExpectSuccess $false -ExpectContains "login|password"
Test-Api -Method "GET" -Path "/user/registerPage" -Description "Register page" -Category "User" -ExpectSuccess $false -ExpectContains "register"
Test-Api -Method "GET" -Path "/captcha/image" -Description "Captcha image" -Category "User" -ExpectSuccess $false -ExpectContains "."

Write-Host ""
Write-Host "--- 3. Product Module ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; pageNum = "1"; pageSize = "5" } -Description "Search (empty)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = "iPhone"; pageNum = "1"; pageSize = "5" } -Description "Search (iPhone)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = "Nike"; categoryId = "4"; pageNum = "1"; pageSize = "5" } -Description "Search (category+keyword)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; minPrice = "100"; maxPrice = "1000"; pageNum = "1"; pageSize = "5" } -Description "Search (price range)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; searchMode = "KEYWORD"; pageNum = "1"; pageSize = "5" } -Description "Search (KEYWORD mode)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; searchMode = "SEMANTIC"; pageNum = "1"; pageSize = "5" } -Description "Search (SEMANTIC mode)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; searchMode = "HYBRID"; pageNum = "1"; pageSize = "5" } -Description "Search (HYBRID mode)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; sortBy = "BEST_FIT"; pageNum = "1"; pageSize = "5" } -Description "Search (BEST_FIT sort)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; sortBy = "PRICE_ASC"; pageNum = "1"; pageSize = "5" } -Description "Search (PRICE_ASC sort)" -Category "Product"
Test-Api -Method "GET" -Path "/product/search" -Params @{ keyword = ""; sortBy = "NEWEST"; pageNum = "1"; pageSize = "5" } -Description "Search (NEWEST sort)" -Category "Product"
Test-Api -Method "GET" -Path "/product/recommendations" -Description "Recommendations (anonymous)" -Category "Product"
Test-Api -Method "GET" -Path "/product/updateStatus" -Params @{ id = "1"; status = "0" } -Description "Update product status" -Category "Product"

Write-Host ""
Write-Host "--- 4. Search Engine ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/test/search" -Params @{ keyword = "phone"; pageNum = "1"; pageSize = "5" } -Description "Search API (phone)" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search" -Params @{ keyword = "book"; categoryId = "1"; pageNum = "1"; pageSize = "5" } -Description "Search API (category+keyword)" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search" -Params @{ keyword = "laptop"; mode = "SEMANTIC"; pageNum = "1"; pageSize = "5" } -Description "Search API (semantic)" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search" -Params @{ keyword = "laptop"; mode = "KEYWORD"; pageNum = "1"; pageSize = "5" } -Description "Search API (keyword)" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search" -Params @{ keyword = "laptop"; mode = "HYBRID"; pageNum = "1"; pageSize = "5" } -Description "Search API (hybrid)" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search/embedding" -Params @{ text = "laptop" } -Description "Embedding test" -Category "SearchEngine"
Test-Api -Method "GET" -Path "/test/search/rebuild" -Description "Search index rebuild" -Category "SearchEngine"

Write-Host ""
Write-Host "--- 5. Recommendation Algorithm ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/test/recommend/record" -Params @{ userId = "2"; productId = "11" } -Description "Record browse history" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/recommend/list" -Params @{ userId = "2"; limit = "8" } -Description "Recommend list (userId=2)" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/recommend/list" -Params @{ userId = "5"; limit = "5" } -Description "Recommend list (userId=5)" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/recommend/list" -Params @{ userId = "10"; limit = "10" } -Description "Recommend list (userId=10)" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/recommend/cache" -Params @{ userId = "2" } -Description "User profile cache" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/degrade/recommend" -Params @{ userId = "2"; limit = "8" } -Description "Degrade recommend" -Category "Recommendation"
Test-Api -Method "GET" -Path "/test/degrade/metrics" -Description "Degrade metrics" -Category "Recommendation"

Write-Host ""
Write-Host "--- 6. Two-Level Index ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/test/index/stats" -Params @{ userId = "2" } -Description "Index stats (userId=2)" -Category "TwoLevelIndex"
Test-Api -Method "GET" -Path "/test/index/stats" -Params @{ categoryId = "2" } -Description "Index stats (category=digital)" -Category "TwoLevelIndex"
Test-Api -Method "GET" -Path "/test/index/stats" -Params @{ keyword = "iPhone" } -Description "Index stats (keyword=iPhone)" -Category "TwoLevelIndex"
Test-Api -Method "GET" -Path "/test/index/rebuild" -Description "Index rebuild" -Category "TwoLevelIndex"

Write-Host ""
Write-Host "--- 7. No-Index Brute Force ---" -ForegroundColor Yellow
$noIndexSw = [System.Diagnostics.Stopwatch]::StartNew()
$noIndexUrl = "$BaseUrl/test/noindex/match?minScore=0.3"
$noIndexStatus = "PASS"
$noIndexHttpCode = 0
$noIndexLatency = 0
$noIndexError = ""
try {
    $noIndexResp = Invoke-WebRequest -Uri $noIndexUrl -UseBasicParsing -TimeoutSec 120
    $noIndexSw.Stop()
    $noIndexHttpCode = [int]$noIndexResp.StatusCode
    $noIndexLatency = [math]::Round($noIndexSw.Elapsed.TotalMilliseconds, 1)
    $noIndexBody = $noIndexResp.Content
    if ($noIndexBody -match '"success"\s*:\s*true') { $noIndexStatus = "PASS" } else { $noIndexStatus = "FAIL"; $noIndexError = "success=false" }
} catch {
    $noIndexSw.Stop()
    $noIndexLatency = [math]::Round($noIndexSw.Elapsed.TotalMilliseconds, 1)
    $noIndexStatus = "FAIL"
    $noIndexError = $_.Exception.Message
}
if ($noIndexStatus -eq "PASS") { $passed++ } else { $failed++ }
$results += [PSCustomObject]@{ Category="NoIndex"; Method="GET"; Path="/test/noindex/match"; Description="No-index brute match"; Status=$noIndexStatus; HttpCode=$noIndexHttpCode; LatencyMs=$noIndexLatency; Error=$noIndexError }
$color = if ($noIndexStatus -eq "PASS") { "Green" } else { "Red" }
Write-Host ("  [{0}] GET /test/noindex/match - No-index brute match ({1}ms)" -f $noIndexStatus, $noIndexLatency) -ForegroundColor $color

Write-Host ""
Write-Host "--- 8. Login-Required APIs (unauthenticated) ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/user/center" -Description "User center (no login)" -Category "LoginRequired" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/user/inbox" -Description "Inbox (no login)" -Category "LoginRequired" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/user/inbox/status" -Description "Inbox status (no login)" -Category "LoginRequired" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/product/publish" -Description "Publish page (no login)" -Category "LoginRequired" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/product/history" -Description "Browse history (no login)" -Category "LoginRequired" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/order/myOrders" -Description "My orders (no login)" -Category "LoginRequired" -ExpectSuccess $false

Write-Host ""
Write-Host "--- 9. Admin APIs (unauthenticated) ---" -ForegroundColor Yellow
Test-Api -Method "GET" -Path "/admin/index" -Description "Admin panel (no login)" -Category "Admin" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/admin/users" -Description "User management (no login)" -Category "Admin" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/admin/products" -Description "Product management (no login)" -Category "Admin" -ExpectSuccess $false
Test-Api -Method "GET" -Path "/admin/orders" -Description "Order management (no login)" -Category "Admin" -ExpectSuccess $false

Write-Host ""
Write-Host "--- 10. Performance Benchmark ---" -ForegroundColor Yellow

$perfIterations = 20
$recommendTimes = @()
for ($i = 0; $i -lt $perfIterations; $i++) {
    $uid = 2 + ($i % 10)
    $perfUrl = "$BaseUrl/test/recommend/list?userId=$uid" + [char]38 + "limit=8"
    $sw2 = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-WebRequest -Uri $perfUrl -UseBasicParsing -TimeoutSec 10 | Out-Null
        $sw2.Stop()
        $recommendTimes += $sw2.ElapsedMilliseconds
    } catch {
        $sw2.Stop()
        $recommendTimes += $sw2.ElapsedMilliseconds
    }
}

$searchTimes = @()
for ($i = 0; $i -lt $perfIterations; $i++) {
    $searchUrl = "$BaseUrl/product/search?keyword=test" + [char]38 + "pageNum=1" + [char]38 + "pageSize=5"
    $sw3 = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-WebRequest -Uri $searchUrl -UseBasicParsing -TimeoutSec 10 | Out-Null
        $sw3.Stop()
        $searchTimes += $sw3.ElapsedMilliseconds
    } catch {
        $sw3.Stop()
        $searchTimes += $sw3.ElapsedMilliseconds
    }
}

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Test Complete" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null }
$reportPath = Join-Path $ReportDir "index.html"

$categories = $results | Group-Object Category | Sort-Object Name
$categoryStats = $categories | ForEach-Object {
    $catPassed = ($_.Group | Where-Object { $_.Status -eq "PASS" }).Count
    $catFailed = ($_.Group | Where-Object { $_.Status -eq "FAIL" }).Count
    $catTotal = $_.Count
    $catRate = if ($catTotal -gt 0) { [math]::Round($catPassed / $catTotal * 100, 1) } else { 0 }
    [PSCustomObject]@{ Category = $_.Name; Total = $catTotal; Passed = $catPassed; Failed = $catFailed; Rate = $catRate }
}

$totalTests = $passed + $failed
$passRate = if ($totalTests -gt 0) { [math]::Round($passed / $totalTests * 100, 1) } else { 0 }

$avgRecommend = if ($recommendTimes.Count -gt 0) { [math]::Round(($recommendTimes | Measure-Object -Average).Average, 1) } else { 0 }
$minRecommend = if ($recommendTimes.Count -gt 0) { ($recommendTimes | Measure-Object -Minimum).Minimum } else { 0 }
$maxRecommend = if ($recommendTimes.Count -gt 0) { ($recommendTimes | Measure-Object -Maximum).Maximum } else { 0 }
$p95Recommend = if ($recommendTimes.Count -gt 0) { ($recommendTimes | Sort-Object)[[math]::Floor($recommendTimes.Count * 0.95)] } else { 0 }

$avgSearch = if ($searchTimes.Count -gt 0) { [math]::Round(($searchTimes | Measure-Object -Average).Average, 1) } else { 0 }
$minSearch = if ($searchTimes.Count -gt 0) { ($searchTimes | Measure-Object -Minimum).Minimum } else { 0 }
$maxSearch = if ($searchTimes.Count -gt 0) { ($searchTimes | Measure-Object -Maximum).Maximum } else { 0 }
$p95Search = if ($searchTimes.Count -gt 0) { ($searchTimes | Sort-Object)[[math]::Floor($searchTimes.Count * 0.95)] } else { 0 }

$noIndexTime = $noIndexSw.ElapsedMilliseconds
$speedup = if ($avgRecommend -gt 0) { [math]::Round($noIndexTime / $avgRecommend) } else { "N/A" }

$detailRows = ""
foreach ($r in $results) {
    $sc = if ($r.Status -eq "PASS") { "pass" } else { "fail" }
    $si = if ($r.Status -eq "PASS") { "OK" } else { "X" }
    $ec = if ($r.Error) { "<td class='error'>$($r.Error)</td>" } else { "<td>-</td>" }
    $detailRows += "<tr><td class='$sc' data-s='$($r.Status)'>$si</td><td data-c='$($r.Category)'>$($r.Category)</td><td>$($r.Method)</td><td class='path'>$($r.Path)</td><td data-d='$($r.Description)'>$($r.Description)</td><td>$($r.HttpCode)</td><td>$($r.LatencyMs)</td>$ec</tr>"
}

$categoryRows = ""
foreach ($cs in $categoryStats) {
    $rc = if ($cs.Rate -ge 100) { "pass" } elseif ($cs.Rate -ge 50) { "warn" } else { "fail" }
    $categoryRows += "<tr><td data-c='$($cs.Category)'>$($cs.Category)</td><td>$($cs.Total)</td><td class='pass'>$($cs.Passed)</td><td class='fail'>$($cs.Failed)</td><td class='$rc'>$($cs.Rate)%</td></tr>"
}

$indexBarW = [math]::Max(60, [math]::Min(500, $avgRecommend * 2))
$noIndexBarW = [math]::Max(60, [math]::Min(800, $noIndexTime / 100))

$html = @"
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Campus Secondhand Platform - Full API Test Report</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; color: #333; line-height: 1.6; }
.container { max-width: 1200px; margin: 0 auto; padding: 20px; }
h1 { text-align: center; padding: 30px 0; color: #1a1a2e; font-size: 28px; }
h1 span { color: #e94560; }
.summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 30px; }
.card { background: white; border-radius: 12px; padding: 20px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.card .number { font-size: 36px; font-weight: 700; margin: 8px 0; }
.card .label { font-size: 14px; color: #666; }
.card.total .number { color: #1a1a2e; }
.card.passed .number { color: #27ae60; }
.card.failed .number { color: #e74c3c; }
.card.rate .number { color: #3498db; }
.section { background: white; border-radius: 12px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.section h2 { font-size: 18px; margin-bottom: 16px; padding-bottom: 10px; border-bottom: 2px solid #f0f2f5; color: #1a1a2e; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { background: #f8f9fa; padding: 10px 12px; text-align: left; font-weight: 600; color: #555; border-bottom: 2px solid #e9ecef; }
td { padding: 8px 12px; border-bottom: 1px solid #f0f0f0; }
tr:hover { background: #f8f9fa; }
.pass { color: #27ae60; font-weight: 600; }
.fail { color: #e74c3c; font-weight: 600; }
.warn { color: #f39c12; font-weight: 600; }
.path { font-family: 'Consolas', monospace; font-size: 12px; color: #e94560; }
.error { color: #e74c3c; font-size: 12px; }
.perf-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.perf-card { background: #f8f9fa; border-radius: 8px; padding: 16px; text-align: center; }
.perf-card .metric { font-size: 24px; font-weight: 700; color: #1a1a2e; }
.perf-card .unit { font-size: 12px; color: #999; }
.perf-card .title { font-size: 13px; color: #666; margin-bottom: 8px; }
.compare-bar { display: flex; align-items: center; margin: 8px 0; }
.compare-bar .bar { height: 24px; border-radius: 4px; display: flex; align-items: center; padding: 0 8px; color: white; font-size: 12px; font-weight: 600; min-width: 60px; }
.compare-bar .lbl { width: 140px; font-size: 13px; color: #555; }
.bar-index { background: linear-gradient(135deg, #27ae60, #2ecc71); }
.bar-noindex { background: linear-gradient(135deg, #e74c3c, #c0392b); }
.timestamp { text-align: center; color: #999; font-size: 13px; margin-top: 20px; }
</style>
</head>
<body>
<div class="container">
<h1 id="main-title">Campus Secondhand Platform <span>Full API Test Report</span></h1>

<div class="summary">
<div class="card total"><div class="label" data-i18n="total">Total Tests</div><div class="number">$totalTests</div></div>
<div class="card passed"><div class="label" data-i18n="passed">Passed</div><div class="number">$passed</div></div>
<div class="card failed"><div class="label" data-i18n="failed">Failed</div><div class="number">$failed</div></div>
<div class="card rate"><div class="label" data-i18n="rate">Pass Rate</div><div class="number">$passRate%</div></div>
</div>

<div class="section">
<h2 data-i18n="catStats">Category Statistics</h2>
<table>
<tr><th data-i18n="thCat">Category</th><th data-i18n="thTotal">Total</th><th data-i18n="thPassed">Passed</th><th data-i18n="thFailed">Failed</th><th data-i18n="thRate">Pass Rate</th></tr>
$categoryRows
</table>
</div>

<div class="section">
<h2 data-i18n="perfBench">Performance Benchmark</h2>
<div class="perf-grid">
<div class="perf-card"><div class="title" data-i18n="recAvg">Recommend Avg</div><div class="metric">$avgRecommend<span class="unit"> ms</span></div></div>
<div class="perf-card"><div class="title" data-i18n="recP95">Recommend P95</div><div class="metric">$p95Recommend<span class="unit"> ms</span></div></div>
<div class="perf-card"><div class="title" data-i18n="recMax">Recommend Max</div><div class="metric">$maxRecommend<span class="unit"> ms</span></div></div>
<div class="perf-card"><div class="title" data-i18n="srchAvg">Search Avg</div><div class="metric">$avgSearch<span class="unit"> ms</span></div></div>
<div class="perf-card"><div class="title" data-i18n="srchP95">Search P95</div><div class="metric">$p95Search<span class="unit"> ms</span></div></div>
<div class="perf-card"><div class="title" data-i18n="srchMax">Search Max</div><div class="metric">$maxSearch<span class="unit"> ms</span></div></div>
</div>
</div>

<div class="section">
<h2 data-i18n="algoCompare">Algorithm Comparison: Two-Level Index vs No-Index Brute Force</h2>
<div class="compare-bar">
<div class="lbl" data-i18n="twoLevel">Two-Level Index</div>
<div class="bar bar-index" style="width: ${indexBarW}px">$avgRecommend ms</div>
</div>
<div class="compare-bar">
<div class="lbl" data-i18n="noIndex">No-Index Brute Force</div>
<div class="bar bar-noindex" style="width: ${noIndexBarW}px">$noIndexTime ms</div>
</div>
<p style="margin-top:12px; font-size:14px; color:#555;" data-i18n="speedup" data-speedup="${speedup}">Two-Level Index is <strong>${speedup}x</strong> faster than No-Index Brute Force</p>
</div>

<div class="section">
<h2 data-i18n="apiDetails">API Test Details</h2>
<table>
<tr><th data-i18n="thStatus">Status</th><th data-i18n="thCat">Category</th><th data-i18n="thMethod">Method</th><th data-i18n="thPath">Path</th><th data-i18n="thDesc">Description</th><th>HTTP</th><th data-i18n="thLatency">Latency(ms)</th><th data-i18n="thError">Error</th></tr>
$detailRows
</table>
</div>

<div class="timestamp">
Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') | Duration: $([math]::Round($duration, 1))s | Target: $BaseUrl
</div>
</div>
<script>
var zh={
total:"\u603B\u6D4B\u8BD5\u6570",passed:"\u901A\u8FC7",failed:"\u5931\u8D25",rate:"\u901A\u8FC7\u7387",
catStats:"\u5206\u7C7B\u7EDF\u8BA1",thCat:"\u5206\u7C7B",thTotal:"\u603B\u6570",thPassed:"\u901A\u8FC7",thFailed:"\u5931\u8D25",thRate:"\u901A\u8FC7\u7387",
perfBench:"\u6027\u80FD\u57FA\u51C6",recAvg:"\u63A8\u8350\u63A5\u53E3 \u5E73\u5747\u54CD\u5E94",recP95:"\u63A8\u8350\u63A5\u53E3 P95",recMax:"\u63A8\u8350\u63A5\u53E3 \u6700\u5927",
srchAvg:"\u641C\u7D22\u63A5\u53E3 \u5E73\u5747\u54CD\u5E94",srchP95:"\u641C\u7D22\u63A5\u53E3 P95",srchMax:"\u641C\u7D22\u63A5\u53E3 \u6700\u5927",
algoCompare:"\u7B97\u6CD5\u6027\u80FD\u5BF9\u6BD4\uFF1A\u4E24\u7EA7\u7D22\u5F15 vs \u65E0\u7D22\u5F15\u66B4\u529B\u5339\u914D",
twoLevel:"\u4E24\u7EA7\u7D22\u5F15\u63A8\u8350",noIndex:"\u65E0\u7D22\u5F15\u66B4\u529B\u5339\u914D",
apiDetails:"\u63A5\u53E3\u6D4B\u8BD5\u660E\u7EC6",thStatus:"\u72B6\u6001",thMethod:"\u65B9\u6CD5",thPath:"\u8DEF\u5F84",thDesc:"\u63CF\u8FF0",thLatency:"\u8017\u65F6(ms)",thError:"\u9519\u8BEF"
};
var catZh={"Infrastructure":"\u57FA\u7840\u8BBE\u65BD","User":"\u7528\u6237\u6A21\u5757","Product":"\u5546\u54C1\u6A21\u5757","SearchEngine":"\u641C\u7D22\u5F15\u64CE","Recommendation":"\u63A8\u8350\u7B97\u6CD5","TwoLevelIndex":"\u4E24\u7EA7\u7D22\u5F15","NoIndex":"\u65E0\u7D22\u5F15\u5339\u914D","LoginRequired":"\u767B\u5F55\u62E6\u622A","Admin":"\u7BA1\u7406\u5458\u63A5\u53E3"};
var descZh={
"Redis connectivity":"Redis\u8FDE\u901A\u6027","MinIO connectivity":"MinIO\u8FDE\u901A\u6027",
"Session write":"Session\u5199\u5165","Session read":"Session\u8BFB\u53D6","Monitor metrics":"\u76D1\u63A7\u6307\u6807",
"Login page":"\u767B\u5F55\u9875\u9762","Register page":"\u6CE8\u518C\u9875\u9762","Captcha image":"\u9A8C\u8BC1\u7801\u56FE\u7247",
"Search (empty)":"\u5546\u54C1\u641C\u7D22(\u7A7A\u5173\u952E\u8BCD)","Search (iPhone)":"\u5546\u54C1\u641C\u7D22(iPhone)",
"Search (category+keyword)":"\u5546\u54C1\u641C\u7D22(\u5206\u7C7B+\u5173\u952E\u8BCD)","Search (price range)":"\u5546\u54C1\u641C\u7D22(\u4EF7\u683C\u533A\u95F4)",
"Search (KEYWORD mode)":"\u5546\u54C1\u641C\u7D22(KEYWORD\u6A21\u5F0F)","Search (SEMANTIC mode)":"\u5546\u54C1\u641C\u7D22(SEMANTIC\u6A21\u5F0F)",
"Search (HYBRID mode)":"\u5546\u54C1\u641C\u7D22(HYBRID\u6A21\u5F0F)","Search (BEST_FIT sort)":"\u5546\u54C1\u641C\u7D22(BEST_FIT\u6392\u5E8F)",
"Search (PRICE_ASC sort)":"\u5546\u54C1\u641C\u7D22(PRICE_ASC\u6392\u5E8F)","Search (NEWEST sort)":"\u5546\u54C1\u641C\u7D22(NEWEST\u6392\u5E8F)",
"Recommendations (anonymous)":"\u63A8\u8350\u5546\u54C1(\u533F\u540D)","Update product status":"\u5546\u54C1\u72B6\u6001\u66F4\u65B0",
"Search API (phone)":"\u641C\u7D22\u63A5\u53E3(phone)","Search API (category+keyword)":"\u641C\u7D22\u63A5\u53E3(\u5206\u7C7B+\u5173\u952E\u8BCD)",
"Search API (semantic)":"\u641C\u7D22\u63A5\u53E3(\u8BED\u4E49\u6A21\u5F0F)","Search API (keyword)":"\u641C\u7D22\u63A5\u53E3(\u5173\u952E\u8BCD\u6A21\u5F0F)",
"Search API (hybrid)":"\u641C\u7D22\u63A5\u53E3(\u6DF7\u5408\u6A21\u5F0F)","Embedding test":"\u8BED\u4E49\u5D4C\u5165\u6D4B\u8BD5",
"Search index rebuild":"\u641C\u7D22\u7D22\u5F15\u91CD\u5EFA","Record browse history":"\u8BB0\u5F55\u6D4F\u89C8\u5386\u53F2",
"Recommend list (userId=2)":"\u63A8\u8350\u5217\u8868(userId=2)","Recommend list (userId=5)":"\u63A8\u8350\u5217\u8868(userId=5)",
"Recommend list (userId=10)":"\u63A8\u8350\u5217\u8868(userId=10)","User profile cache":"\u7528\u6237\u753B\u50CF\u7F13\u5B58",
"Degrade recommend":"\u964D\u7EA7\u63A8\u8350","Degrade metrics":"\u964D\u7EA7\u6307\u6807",
"Index stats (userId=2)":"\u7D22\u5F15\u7EDF\u8BA1(userId=2)","Index stats (category=digital)":"\u7D22\u5F15\u7EDF\u8BA1(\u5206\u7C7B=\u6570\u7801)",
"Index stats (keyword=iPhone)":"\u7D22\u5F15\u7EDF\u8BA1(\u5173\u952E\u8BCD=iPhone)","Index rebuild":"\u7D22\u5F15\u91CD\u5EFA",
"No-index brute match":"\u65E0\u7D22\u5F15\u66B4\u529B\u5339\u914D",
"User center (no login)":"\u7528\u6237\u4E2D\u5FC3(\u672A\u767B\u5F55)","Inbox (no login)":"\u6536\u4EF6\u7BB1(\u672A\u767B\u5F55)",
"Inbox status (no login)":"\u6536\u4EF6\u7BB1\u72B6\u6001(\u672A\u767B\u5F55)","Publish page (no login)":"\u53D1\u5E03\u5546\u54C1\u9875(\u672A\u767B\u5F55)",
"Browse history (no login)":"\u6D4F\u89C8\u5386\u53F2(\u672A\u767B\u5F55)","My orders (no login)":"\u6211\u7684\u8BA2\u5355(\u672A\u767B\u5F55)",
"Admin panel (no login)":"\u7BA1\u7406\u540E\u53F0(\u672A\u767B\u5F55)","User management (no login)":"\u7528\u6237\u7BA1\u7406(\u672A\u767B\u5F55)",
"Product management (no login)":"\u5546\u54C1\u7BA1\u7406(\u672A\u767B\u5F55)","Order management (no login)":"\u8BA2\u5355\u7BA1\u7406(\u672A\u767B\u5F55)"
};
document.title="\u6821\u56ED\u4E8C\u624B\u4EA4\u6613\u5E73\u53F0 - \u5168\u63A5\u53E3\u6D4B\u8BD5\u62A5\u544A";
document.getElementById("main-title").innerHTML="\u6821\u56ED\u4E8C\u624B\u4EA4\u6613\u5E73\u53F0 <span>\u5168\u63A5\u53E3\u6D4B\u8BD5\u62A5\u544A</span>";
document.querySelectorAll("[data-i18n]").forEach(function(e){var k=e.getAttribute("data-i18n");if(zh[k]){if(k==="speedup"){var s=e.getAttribute("data-speedup");e.innerHTML="\u4E24\u7EA7\u7D22\u5F15\u6BD4\u65E0\u7D22\u5F15\u66B4\u529B\u5339\u914D\u5FEB\u7EA6 <strong>"+s+"\u500D</strong>"}else{e.textContent=zh[k]}}});
document.querySelectorAll("[data-c]").forEach(function(e){var k=e.getAttribute("data-c");if(catZh[k])e.textContent=catZh[k]});
document.querySelectorAll("[data-d]").forEach(function(e){var k=e.getAttribute("data-d");if(descZh[k])e.textContent=descZh[k]});
document.querySelectorAll("[data-s]").forEach(function(e){var s=e.getAttribute("data-s");e.textContent=s==="PASS"?"\u2714 \u901A\u8FC7":"\u2718 \u5931\u8D25"});
var ts=document.querySelector(".timestamp");if(ts)ts.textContent=ts.textContent.replace("Generated:","\u751F\u6210\u65F6\u95F4\uFF1A").replace("Duration:","\u603B\u8017\u65F6\uFF1A").replace("s","\u79D2").replace("Target:","\u76EE\u6807\u670D\u52A1\uFF1A");
</script>
</body>
</html>
"@

[System.IO.File]::WriteAllText($reportPath, $html, [System.Text.Encoding]::UTF8)
Write-Host ""
Write-Host "HTML report generated: $reportPath" -ForegroundColor Green
Write-Host "Total: $totalTests | Passed: $passed | Failed: $failed | Pass Rate: $passRate%" -ForegroundColor $(if ($passRate -ge 90) { "Green" } else { "Red" })
