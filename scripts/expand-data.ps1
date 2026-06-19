<#
Expand Data Script
Adds users (to 200), products (to 100), and 40 random browse records per user.
Usage: .\scripts\expand-data.ps1 [-BaseUrl http://localhost:8080]
#>

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$NumBrowses = 40,
    [int]$TotalProducts = 100
)

Write-Host "=== Expand Data: 200 Users + 100 Products + Browse Records ===" -ForegroundColor Cyan
Write-Host "BASE_URL: $BaseUrl"
Write-Host "Browses per user: $NumBrowses"
Write-Host "Total products: $TotalProducts"
Write-Host ""

$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$sqlFile = Join-Path $projectRoot "docker\init\05-add-users-and-products.sql"

Write-Host "[Step 1] Inserting users and products via SQL..." -ForegroundColor Yellow
docker cp $sqlFile campus-secondhand-mysql-1:/tmp/expand-data.sql
docker exec campus-secondhand-mysql-1 sh -c "mysql -u root -pcampus_root < /tmp/expand-data.sql" 2>&1 | Select-String -NotMatch "Warning"
Write-Host "[Step 1] Done." -ForegroundColor Green

Write-Host ""
Write-Host "[Step 2] Getting user info from database..." -ForegroundColor Yellow
$userCount = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT COUNT(*) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }
$minId = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT MIN(id) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }
$maxId = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT MAX(id) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }
$productCount = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT COUNT(*) FROM t_product" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }

Write-Host "  Total regular users: $userCount"
Write-Host "  User ID range: $minId ~ $maxId"
Write-Host "  Total products: $productCount"
Write-Host ""

Write-Host "[Step 3] Starting browse simulation ($NumBrowses per user)..." -ForegroundColor Yellow

$totalRequests = 0
$successCount = 0
$failCount = 0
$userIdx = 0

for ($userId = [int]$minId; $userId -le [int]$maxId; $userId++) {
    $userIdx++
    Write-Host "  [$userIdx/$userCount] User ID=$userId browsing $NumBrowses products..." -ForegroundColor DarkGray

    $productIds = (1..$TotalProducts | Get-Random -Count $NumBrowses)

    $browseCount = 0
    foreach ($productId in $productIds) {
        $url = "$BaseUrl/test/recommend/record?userId=$userId&productId=$productId"

        try {
            $response = Invoke-RestMethod -Uri $url -Method Get -ErrorAction Stop -TimeoutSec 5

            if ($response.success -eq $true) {
                $browseCount++
                $successCount++
            } else {
                $failCount++
            }
        } catch {
            $failCount++
        }

        $totalRequests++

        if ($browseCount % 20 -eq 0) {
            Write-Host "    $browseCount/$NumBrowses" -ForegroundColor DarkGray
        }

        Start-Sleep -Milliseconds 30
    }

    Write-Host "  [$userIdx/$userCount] User ID=$userId done: $browseCount/$NumBrowses succeeded" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=== Simulation Complete ===" -ForegroundColor Cyan
Write-Host "Total users: $userCount"
Write-Host "Total products: $productCount"
Write-Host "Total requests: $totalRequests"
Write-Host "Success: $successCount"
Write-Host "Failed: $failCount"
