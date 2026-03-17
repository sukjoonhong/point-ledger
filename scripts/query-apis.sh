#!/bin/bash

BASE_URL="http://localhost:18080"
MEMBER_ID=20
ASSET_ID=1
ORDER_ID="1"

echo "--------------------------------------------------"
echo "  Point-Ledger Tracing & Query API 시연"
echo "--------------------------------------------------"

echo ">>> [1] 멤버 잔액 조회 (Simple Balance)"
curl -X GET "$BASE_URL/v1/points/tracing/members/$MEMBER_ID/balance"
echo -e "\n"

echo ">>> [2] 멤버 통합 요약 조회 (Summary + Pagination)"
echo "=> 잔액, 활성 자산 리스트, 최근 트랜잭션 내역을 한 번에 확인합니다."
curl -X GET "$BASE_URL/v1/points/tracing/members/$MEMBER_ID/summary?page=0&size=5"
echo -e "\n"

echo ">>> [3] 특정 자산 사용 경로 추적 (Asset Tracing)"
echo "=> 적립된 포인트가 어떤 주문들에서 쪼개져 쓰였는지 확인합니다."
curl -X GET "$BASE_URL/v1/points/tracing/assets/$ASSET_ID"
echo -e "\n"

echo ">>> [4] 특정 주문 포인트 출처 추적 (Order Tracing)"
echo "=> 해당 주문을 결제할 때 어떤 자산들이 소모되었는지 확인합니다."
curl -X GET "$BASE_URL/v1/points/tracing/orders/$ORDER_ID"
echo -e "\n"