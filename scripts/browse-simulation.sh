#!/usr/bin/env bash
# 用户浏览模拟脚本
# 让每个用户随机访问40个商品，用于测试画像系统和推荐系统

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
NUM_USERS=5          # 用户数量 (user001 ~ user005)
NUM_BROWSES=40       # 每个用户浏览商品数量
TOTAL_PRODUCTS=60    # 商品总数

echo "=== 用户浏览模拟脚本 ==="
echo "BASE_URL: $BASE_URL"
echo "用户数量: $NUM_USERS"
echo "每个用户浏览商品数: $NUM_BROWSES"
echo "商品总数: $TOTAL_PRODUCTS"
echo ""

total_requests=0
success_count=0
fail_count=0

for user_idx in $(seq 1 $NUM_USERS); do
    user_id=$((user_idx + 1))  # 用户ID: 2~6 (user001~user005)
    username="user$(printf '%03d' $user_idx)"
    
    echo "=== 用户 $username (ID: $user_id) 开始浏览 ==="
    
    # 生成40个不重复的随机商品ID
    # 使用shuf生成1-60的随机排列，取前40个
    product_ids=$(seq 1 $TOTAL_PRODUCTS | shuf | head -n $NUM_BROWSES)
    
    browse_count=0
    for product_id in $product_ids; do
        # 调用浏览记录接口
        response=$(curl -s "$BASE_URL/test/recommend/record?userId=$user_id&productId=$product_id")
        
        if echo "$response" | grep -q '"success":true'; then
            browse_count=$((browse_count + 1))
            success_count=$((success_count + 1))
        else
            fail_count=$((fail_count + 1))
        fi
        
        total_requests=$((total_requests + 1))
        
        # 每10个商品输出一次进度
        if (( browse_count % 10 == 0 )); then
            echo "  已浏览 $browse_count/$NUM_BROWSES 个商品"
        fi
        
        # 添加微小延迟，避免请求过快
        sleep 0.05
    done
    
    echo "  用户 $username 完成浏览，成功 $browse_count 次"
    echo ""
done

echo "=== 模拟完成 ==="
echo "总请求数: $total_requests"
echo "成功: $success_count"
echo "失败: $fail_count"
echo ""
echo "提示：可以访问 $BASE_URL/user/profile 查看用户画像（需登录）"
echo "或使用 $BASE_URL/test/recommend/cache?userId=2 查看缓存数据"