# Point Ledger System

본 프로젝트는 데이터 무결성을 최우선으로 설계된 원장(Ledger) 중심의 포인트 관리 시스템입니다. 단순한 잔액 관리를 넘어, 포인트의 적립부터 사용, 만료, 그리고 부분 취소에 이르는 전체 라이프사이클을 추적
가능한 데이터 구조로 구현했습니다.

## 기술 스택

- Language: Java 21 (LTS)
- Framework: Spring Boot 3.4.3
- Database: H2 (File/In-memory)
- Persistence: Spring Data JPA (Hibernate)
- Concurrency: Pessimistic Locking (DB Level)

---

## 실행 방법

### 1. 애플리케이션 실행

애플리케이션은 18080 포트에서 동작하며, API 서버와 비동기 워커가 동시에 활성화됩니다.

```bash
# 빌드 및 실행 (Java 21 환경 필요)
./gradlew clean bootRun --args='--spring.profiles.active=local,api,worker'
```

### 2. 시나리오 테스트 (Shell Scripts)

터미널 환경에서 시스템의 핵심 기능을 즉시 검증할 수 있도록 curl 기반의 쉘 스크립트를 제공합니다.

```bash
# 스크립트 실행 권한 부여
chmod +x scripts/*.sh

# [시나리오 1] 동기 방식 (관리자 API를 통한 직접 제어 및 즉시 반영)
./scripts/sync-scenario.sh

# [시나리오 2] 비동기 방식 (이벤트 접수 및 워커를 통한 순차 처리)
./scripts/async-scenario.sh

# [시나리오 3] 통합 조회 및 데이터 추적 API 호출
./scripts/query-apis.sh
```

---

## 주요 기능 및 API 명세

### 1. 관리자 서비스 (Synchronous)

운영 도구 또는 백오피스에서 직접 원장을 조작할 때 사용합니다.

* POST /v1/admin/points/issue: 포인트 수동 적립

* POST /v1/admin/points/deduct: 포인트 직접 차감 (주문 번호 기반)

* POST /v1/admin/points/revert-deduction: 기존 차감 건에 대한 부분/전체 취소

### 2. 고성능 이벤트 접수 (Asynchronous)

대규모 트래픽 환경에서 API 응답 속도를 최적화하기 위해 이벤트를 큐잉 처리합니다.

* POST /v1/points/enqueue: 비동기 포인트 처리 요청 접수 (Ingestor)

### 3. 데이터 추적 및 조회 (Tracing)

CQS(Command Query Separation) 원칙에 따라 조회 전용 서비스를 분리하여 제공합니다.

* GET /v1/points/tracing/members/{id}/summary: 멤버별 통합 요약 (잔액, 활성 자산 리스트, 최근 내역 페이징)

* GET /v1/points/tracing/assets/{id}: 특정 적립 건(Asset)의 사용 경로 추적

* GET /v1/points/tracing/orders/{id}: 특정 주문(Order)에서 소모된 포인트의 출처 추적

---
## 모니터링 및 디버깅
H2 데이터베이스 콘솔: http://localhost:18080/h2-console

JDBC URL: jdbc:h2:file:./data/pointdb

User: sa / Password: (없음)

---
## 인터랙티브 API 명세 (Swagger)
로컬 개발 환경에서는 Swagger UI를 통해 전체 API 명세를 확인하고 직접 테스트할 수 있습니다.
(단, `local` 프로파일이 활성화된 상태에서만 접근 가능합니다.)

- Swagger UI: http://localhost:18080/swagger-ui/index.html
- OpenAPI Spec: http://localhost:18080/v3/api-docs

---

## 상세 설계 및 아키텍처

비관적 락을 통한 동시성 제어 전략, 시퀀스 번호를 이용한 멱등성 보장 로직, 만료 포인트의 LIFO 기반 취소 정책 등 기술적 의사결정에 대한 상세 내용은 별도의 문서를 참조하십시오.

- [ARCHITECTURE.md 바로가기](./docs/ARCHITECTURE.md)
- [ERD.md (엔티티 관계도 및 데이터 구조) 바로가기](./src/main/resources/docs/ERD.md)