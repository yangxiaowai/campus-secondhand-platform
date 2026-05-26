<#
User Browse Simulation Script
Each user randomly browses 40 products for testing profile and recommendation system
#>

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$NumUsers = 5,
    [int]$NumBrowses = 40,
    [int]$TotalProducts = 60
)

Write-Host "=== User Browse Simulation ===" -ForegroundColor Cyan
Write-Host "BASE_URL: $BaseUrl"
Write-Host "Number of users: $NumUsers"
Write-Host "Browses per user: $NumBrowses"
Write-Host "Total products: $TotalProducts"
Write-Host ""

$totalRequests = 0
$successCount = 0
$failCount = 0

for ($userIdx = 1; $userIdx -le $NumUsers; $userIdx++) {
    $userId = $userIdx + 1  # User ID: 2~6 (user001~user005)
    $username = "user" + $userIdx.ToString("D3")
    
    Write-Host "=== User $username (ID: $userId) browsing ===" -ForegroundColor Yellow
    
    $productIds = (1..$TotalProducts | Get-Random -Count $NumBrowses)
    
    $browseCount = 0
    foreach ($productId in $productIds) {
        $url = "$BaseUrl/test/recommend/record?userId=$userId&productId=$productId"
        
        try {
            $response = Invoke-RestMethod -Uri $url -Method Get -ErrorAction Stop
            
            if ($response.success -eq $true) {
                $browseCount++
                $successCount++
            }
            else {
                $failCount++
            }
        }
        catch {
            $failCount++
        }
        
        $totalRequests++
        
        if ($browseCount % 10 -eq 0) {
            Write-Host "  Browsed $browseCount/$NumBrowses products"
        }
        
        Start-Sleep -Milliseconds 50
    }
    
    Write-Host "  User $username completed, $browseCount successes"
    Write-Host ""
}

Write-Host "=== Simulation Complete ===" -ForegroundColor Cyan
Write-Host "Total requests: $totalRequests"
Write-Host "Success: $successCount"
Write-Host "Failed: $failCount"
Write-Host ""
Write-Host "Tip: Visit $BaseUrl/test/recommend/cache?userId=2 to check cache data"