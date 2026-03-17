#!/bin/bash

BASE_URL="http://localhost:18080"
MEMBER_ID=20

if command -v jq >/dev/null 2>&1; then JSON_FORMATTER="jq"; else JSON_FORMATTER="python3 -m json.tool"; fi

echo ">>> [1] A 포인트 적립 (1,000원)"
curl -X POST "$BASE_URL/v1/admin/points/issue" \
     -H "Content-Type: application/json" \
     -d "{\"memberId\": $MEMBER_ID, \"amount\": 1000, \"pointKey\": \"SYNC-A\", \"source\": \"EVENT\"}"
echo -e "\n"

echo ">>> [2] B 포인트 적립 (500원)"
curl -X POST "$BASE_URL/v1/admin/points/issue" \
     -H "Content-Type: application/json" \
     -d "{\"memberId\": $MEMBER_ID, \"amount\": 500, \"pointKey\": \"SYNC-B\", \"source\": \"EVENT\"}"
echo -e "\n"

echo ">>> [3] 포인트 사용 (1,200원) -> A(1000) + B(200) 차감"
curl -X POST "$BASE_URL/v1/admin/points/deduct" \
     -H "Content-Type: application/json" \
     -d "{\"memberId\": $MEMBER_ID, \"amount\": 1200, \"pointKey\": \"USE-C\", \"orderId\": \"1\"}"
echo -e "\n"

echo ">>> [4] 부분 취소 (1,100원)"
curl -X POST "$BASE_URL/v1/admin/points/revert-deduction" \
     -H "Content-Type: application/json" \
     -d "{
           \"memberId\": $MEMBER_ID,
           \"amount\": 1100,
           \"pointKey\": \"REV-D\",
           \"originalPointKey\": \"USE-C\",
           \"orderId\": \"1\"
         }"
echo -e "\n"

echo ">>> [5] 최종 상태 조회"
curl -s -X GET "$BASE_URL/v1/points/tracing/members/$MEMBER_ID/summary?page=0&size=10" | jq
echo -e "\n"