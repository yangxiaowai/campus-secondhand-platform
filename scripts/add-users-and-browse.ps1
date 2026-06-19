<#
Add 90 Users + Browse Simulation Script
Inserts 90 test users via SQL, then simulates each user browsing 40 random products.
#>

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$NewUsers = 90,
    [int]$NumBrowses = 40,
    [int]$TotalProducts = 60
)

Write-Host "=== Add 90 Users + Browse Simulation ===" -ForegroundColor Cyan
Write-Host "BASE_URL: $BaseUrl"
Write-Host "Adding: $NewUsers users"
Write-Host "Browses per user: $NumBrowses"
Write-Host ""

# Step 1: Copy SQL and insert users
Write-Host "[Step 1] Inserting $NewUsers users into database..." -ForegroundColor Yellow
docker cp docker/init/03-add-90-users.sql campus-secondhand-mysql-1:/tmp/add-users.sql
docker exec campus-secondhand-mysql-1 sh -c "mysql -u root -pcampus_root < /tmp/add-users.sql" 2>&1 | Select-String -NotMatch "Warning"
Write-Host "[Step 1] Users inserted." -ForegroundColor Green

# Step 2: Get total user count to determine user ID range
Write-Host ""
Write-Host "[Step 2] Getting user info from database..." -ForegroundColor Yellow
$userCount = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT COUNT(*) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }
$minId = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT MIN(id) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }
$maxId = docker exec campus-secondhand-mysql-1 mysql -u root -pcampus_root secondhand_market -N -e "SELECT MAX(id) FROM t_user WHERE role=1 AND status=1" 2>&1 | Select-String -NotMatch "Warning" | ForEach-Object { $_.Line.Trim() }

Write-Host "  Total regular users: $userCount"
Write-Host "  User ID range: $minId ~ $maxId"
Write-Host ""

# Step 3: Simulate browsing
Write-Host "[Step 3] Starting browse simulation..." -ForegroundColor Yellow

$totalRequests = 0
$successCount = 0
$failCount = 0

for ($userId = [int]$minId; $userId -le [int]$maxId; $userId++) {
    Write-Host "  User ID=$userId browsing $NumBrowses products..." -ForegroundColor DarkGray
    
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
    
    Write-Host "  User ID=$userId done: $browseCount/$NumBrowses succeeded" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=== Simulation Complete ===" -ForegroundColor Cyan
Write-Host "Total users: $userCount"
Write-Host "Total requests: $totalRequests"
Write-Host "Success: $successCount"
Write-Host "Failed: $failCount"