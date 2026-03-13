# **Point Ledger System**

본 프로젝트는 대규모 트래픽 환경에서도 데이터 무결성을 완벽하게 보장하도록 설계된 고성능 무료 포인트 관리 시스템입니다. 자산 관리의 핵심인 '신뢰'를 기술적으로 구현하기 위해 이벤트 기반 아키텍처와 원장 중심 설계를 채택했습니다.

## **기술 스택**

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Database**: H2 (In-memory)
- **Build Tool**: Gradle

---

## **실행 방법**

### **1. 빌드 및 실행**

터미널에서 아래 명령어를 실행하십시오. Java 21 환경이 필요합니다.

**Bash**

```shell
# 빌드 및 로컬 프로파일 실행
./gradlew clean build
./gradlew bootRun --args='--spring.profiles.active=local'
```
---

## **아키텍처 설계 원칙 (Architecture)**

### **1. 원장 중심 설계 (Ledger-first Design)**

포인트 시스템의 가장 신뢰할 수 있는 데이터의 원천(Source of Truth)을 거래 원장에 둡니다. 잔액(Balance)은 원장으로부터 관리되는 파생 상태(Derived State)로 취급하며, 모든 상태 변화는 원장의 기록을 통해 증명됩니다.

### **2. Exactly-once 처리를 위한 이벤트 기반 아키텍처**

자산 데이터의 본질은 무결성에 있으며, 단 한 건의 데이터 유실이나 중복 발행도 허용할 수 없습니다. 이를 위해 메시지 기반 아키텍처를 도입하여 복잡한 분산 환경에서도 Exactly-once 처리를 보장하고 원장 기록의 신뢰성을 확보했습니다.

### **3. 후처리 로직의 결합도 분리**

비즈니스 핵심 로직과 부가적인 후처리 로직을 Outbox 이벤트를 기반으로 분리했습니다. 이를 통해 시스템 간 결합도를 낮추고 서비스 확장에 유연하게 대응할 수 있는 구조를 설계했습니다.

### **4. 고성능 무결성 검증 전략**

데이터베이스의 수평적 확장성을 확보하고 동시성 제어 시 발생할 수 있는 데드락(Deadlock)을 방지하기 위해 물리적인 외래키(FK) 제약조건을 의도적으로 생략했습니다. 대신 인덱스 최적화와 애플리케이션 레이어(PointLedgerService)에서의 철저한 검증을 통해 논리적 무결성을 유지합니다.

---

## **핵심 기술적 의사결정**

### **1. Transactional Outbox Pattern**

메시지 브로커와 DB 트랜잭션의 원자성을 보장하기 위해 도입했습니다. 비즈니스 로직 성공 시 이벤트를 `point_outbox` 테이블에 저장하고, 별도의 릴레이 서비스가 이를 발행함으로써 메시지 유실 없는 At-Least-Once Delivery를 보장합니다.

### **2. 비관적 락(Pessimistic Lock) 및 SKIP LOCKED**

- **정합성**: 포인트 사용 시 잔액 경합을 방지하기 위해 `SELECT FOR UPDATE`를 사용합니다.
- **성능**: 배치 작업 시 `SKIP LOCKED`를 적용하여 여러 서버 인스턴스가 대기 없이 병렬로 데이터를 처리할 수 있도록 최적화했습니다.

### **3. 시퀀스 번호(Sequence Num)를 통한 정합성 보장**

분산 환경에서 이벤트의 순서가 뒤바뀌거나 누락되는 시나리오에 대비하여 지갑별 시퀀스 번호를 관리합니다. 시퀀스 갭(Gap) 발생 시 리플레이(Replay) 로직이 개입하여 원장의 무결성을 강제로 동기화합니다.

---

## **API Specification**

### **1. 포인트 이벤트 발행 (Enqueue)**

모든 적립, 사용, 취소 요청은 이 엔드포인트를 통해 비동기로 접수됩니다.

- **URL**: `POST /v1/points/enqueue`
- **Status**: `202 Accepted`

### **Request Body (예시: 적립)**

**JSON**

```json lines
// [적립 예시]
{
    "memberId": 1001,
    "amount": 5000,
    "pointKey": "REQ_EARN_001",
    "originalPointKey": null,
    "type": "EARN",
    "sequenceNum": 1
}

// [적립 취소 예시]
{
    "memberId": 1001,
    "amount": 5000,
    "pointKey": "REQ_CANCEL_001", // 취소 요청 자체의 유니크 키
    "originalPointKey": "REQ_EARN_001", // 원본 적립의 키를 참조
    "type": "CANCEL_EARN",
    "sequenceNum": 2
}
```


---

## **도메인 규칙 및 정책**

- **적립 한도**: 1회 최소 1P ~ 최대 10만P (정책 설정으로 제어 가능)
- **보유 한도**: 개인별 최대 보유 가능 금액 제한 설정 가능
- **사용 우선순위**
    1. 관리자 수기 지급(ADMIN) 포인트 우선 차감
    2. 만료일이 짧게 남은 순서(Early Expiry)로 차감
- **사용 취소**: 만료된 포인트 취소 시 신규 적립(Compensation) 처리를 통해 히스토리 투명성 확보
- **적립 취소**: 사용된 이력이 있는 적립 건은 취소 불가

---
