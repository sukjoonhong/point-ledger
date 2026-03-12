# Getting Started


# Architecture
* 포인트 잔액보다 거래 원장을 source of truth로 둔다.
* 잔액은 원장으로부터 관리되는 파생 상태로 본다.
* 후처리 로직은 outbox 이벤트를 기반으로 분리 가능하도록 설계.