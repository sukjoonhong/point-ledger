#!/bin/bash

BASE_URL="http://localhost:18080"
MEMBER_ID=20

if command -v jq >/dev/null 2>&1; then JSON_FORMATTER="jq"; else JSON_FORMATTER="python3 -m json.tool"; fi

echo "=================================================="
echo "  Point-Ledger 비동기 골든 시나리오 시작 (Member: $MEMBER_ID)"
echo "=================================================="

echo ">>> [1] 비동기 적립 A (1,000원) - Seq 1"
curl -s -X POST "$BASE_URL/v1/points/enqueue" \
     -H "Content-Type: application/json" \
     -d "{\"memberId\": $MEMBER_ID, \"amount\": 1000, \"pointKey\": \"AS-A\", \"type\": \"EARN\", \"sequenceNum\": 1, \"source\": \"SYSTEM\"}" | $JSON_FORMATTER
echo -e "\n"
sleep 1

echo ">>> [2] 비동기 적립 B (500원) - Seq 2"
curl -s -X POST "$BASE_URL/v1/points/enqueue" \
     -H "Content-Type: application/json" \
     -d "{\"memberId\": $MEMBER_ID, \"amount\": 500, \"pointKey\": \"AS-B\", \"type\": \"EARN\", \"sequenceNum\": 2, \"source\": \"SYSTEM\"}" | $JSON_FORMATTER
echo -e "\n"
sleep 1

echo ">>> [3] 비동기 사용 C (1,200원) - Seq 3 (Order: AS-100)"
curl -s -X POST "$BASE_URL/v1/points/enqueue" \
     -H "Content-Type: application/json" \
     -d "{
           \"memberId\": $MEMBER_ID,
           \"amount\": 1200,
           \"pointKey\": \"AS-C\",
           \"type\": \"USE\",
           \"orderId\": \"AS-100\",
           \"sequenceNum\": 3
         }" | $JSON_FORMATTER
echo -e "\n"
sleep 1

echo ">>> [4] 비동기 부분 취소 D (1,100원) - Seq 4"
curl -s -X POST "$BASE_URL/v1/points/enqueue" \
     -H "Content-Type: application/json" \
     -d "{
           \"memberId\": $MEMBER_ID,
           \"amount\": 1100,
           \"pointKey\": \"AS-D\",
           \"originalPointKey\": \"AS-C\",
           \"type\": \"CANCEL_USE\",
           \"orderId\": \"AS-100\",
           \"sequenceNum\": 4
         }" | $JSON_FORMATTER
echo -e "\n"

echo "최종 처리 결과를 확인하기 위해 잠시 대기합니다..."
sleep 2

echo ">>> [5] 최종 통합 요약 조회"
curl -s -X GET "$BASE_URL/v1/points/tracing/members/$MEMBER_ID/summary" | $JSON_FORMATTER
echo -e "\n"