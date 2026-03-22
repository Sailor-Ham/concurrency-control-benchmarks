# Distributed Lock Burst Benchmark

> 서울신학대학교(STU) 일반대학원 IT융합학과 석사논문 주제: RDBMS, Redis 및 Zookeeper 기반 동시성 제어 전략의 성능 및 안정성 비교 분석

본 프로젝트는 **수강신청(Enrollment)** 시스템을 모사하여, 특정 시점에 요청이 폭증하는 **버스트 트래픽(Burst Traffic)** 상황에서 각 분산 락 메커니즘이 시스템의 정합성과 가용성을 얼마나 안정적으로 방어하는지 연구합니다.

---

## 시스템 아키텍처

![데이터베이스(MySQL) 중심의 동시성 제어 아키텍처](/docs/images/readme/database-level-concurrency-control-architecture.png)

> **그림 1:** 데이터베이스(MySQL) 중심의 동시성 제어 아키텍처. `NoLock`, `Optimistic Lock`, `Pessimistic Lock`, `MySQL Named Lock` 전략이 구현됩니다.

![데이터베이스(MySQL) 중심의 동시성 제어 아키텍처](/docs/images/readme/redis-based-distributed-locking-architecture.png)

> **그림 1:** Redis 기반의 동시성 제어 아키텍처. `Spin Lock`(`Lettuce`)과 `Pub/Sub Lock`(`Redisson`) 전략이 구현됩니다.

![Zookeeper 기반의 동시성 제어 아키텍처](/docs/images/readme/zookeeper-based-distributed-coordination-architecture.png)

> **그림 3:** Zookeeper 기반의 분산 코디네이터 아키텍처. `Zookeeper Lock`(`Curator`) 전략이 구현됩니다.

- **Load Tester:** nGrinder
- **Web Server:** Nginx (L4 Load Balancer)
- **Application Server:** Spring Boot (Java)
- **Database:** MySQL
- **Distributed Lock Managers:**
  - **Redis:** Lettuce(Spin Lock), Redisson(Pub/Sub Lock)
  - **Zookeeper:** Apache Curator

---

## 기술 스택

- **Programming Language:** Java21
- **Framework:** Spring Boot 3.5.11, Spring Data JPA
- **Database:** MySQL 8.0
- **NoSQL:** Redis
- **Load Testing Tool:** nGrinder
- **Observability:** Prometheus, Grafana, Zipkin

---

## 데이터 모델링 (ERD)

본 프로젝트에서의 테스트 시나리오는 수강신청 시스템입니다.
수강신청 시나리오를 바탕으로 동시성 이슈를 재현하며 이를 해결하기 위한 다양한 전략을 분석합니다.

수강신청 시나리오는 다음과 같은 핵심 도메인으로 구성됩니다.

- **COURSE:** 강의 정보를 담는 엔터티로, 동시성 제어의 핵심 타겟(공유 자원)이 됩니다.
- **STUDENT:** 학생 정보를 담는 엔터티로, 부하 테스트를 위한 사용자 더미 데이터입니다.
- **ENROLLMENT:** 수강신청 정보를 담는 엔터티로, `COURSE`와 `STUDENT`의 매핑 테이블입니다. (중복 신청 방지를 위한 Unique Constraint(`student_id`, `course_id`) 적용)

<!-- TODO: Enrollment ERD 이미지 삽입 -->

![Enrollment ERD]()

---

## 비교 분석 대상

| 방식                 | 구현 도구           | 비고             |
| :------------------- | :------------------ | :--------------- |
| **No Lock**          | MySQL (JPA)         | 기본             |
| **Optimistic Lock**  | MySQL (JPA)         | 석사 논문        |
| **Pessimistic Lock** | MySQL (JPA)         | 학술 / 석사 논문 |
| **MySQL Named Lock** | MySQL (JPA)         | 석사 논문        |
| **Spin Lock**        | Redis (Lettuce)     | 학술 / 석사 논문 |
| **Pub/Sub Lock**     | Redis (Redisson)    | 학술 / 석사 논문 |
| **Zookeeper Lock**   | Zookeeper (Curator) | 석사 논문        |

---

## 테스트 시나리오: Burst Traffic

본 연구는 일반적인 요청 유입이 아닌, 수강신청 오픈 직후 발생하는 **폭발적인 트래픽** 상황을 가정합니다.

### 1. 트래픽 모델링

- **Target:** 수강신청 API (Enrollment API)
- **Vuser 단계:** 200, 400, 600명 (단계별 경합 강도 측정)
- **Pattern:** nGrinder의 **Ramp-up** 기능을 활용하여 10초 내에 모든 Vuser가 투입되는 급격한 부하 곡선 형성

### 2. 서비스 처리 모델

서버 내 로직이 수행되는 시간은 실제 환경과 유사하게 **정규 분포**를 따르도록 시물레이션합니다.

- **처리 시간:** 각 요청의 서비스 시간은 고정되지 않고 통계적 유의미함을 가진 난수로 발생시켜 분석의 객관성을 확보합니다.
- **통계적 유의성:** 중심극한정리(Central Limit Theorem, CLT)에 의거하여 표본의 크기를 $n = 36 (n \ge 30)$ 이상으로 확보함으로써 데이터의 신뢰도를 보장합니다.

### 3. 주요 측정 지표

| 지표                                       | 정의 및 산출식                                  | 분석적 의미 및 목적                                     |
| :----------------------------------------- | :---------------------------------------------- | :------------------------------------------------------ |
| **TPS (초당 처리량)**                      | $\frac{\text{총 성공 요청 수}}{총 테스트 시간}$ | 시스템의 최대 처리 용량 및 효율성 검증                  |
| **Mean (평균 응답 시간; $\mu$)**           | $\frac{1}{n}\sum X_i$                           | 일반적인 사용자 체감 응답 속도 (산술 평균)              |
| **Std Dev (응답 시간 표준편차; $\sigma$)** | $\sqrt{\frac{1}{n}\sum(X_i-\mu)^2}$             | 응답 시간의 변동성 및 **시스템의 안정성** 측정          |
| **$2\sigma$ Interval ($2\sigma$ 구간)**    | $[\mu - 2\sigma, \mu + 2\sigma]$                | 전체 요청의 $95.4\%$가 포함되는 통계적 신뢰 범위        |
| **$95th\%$ (p95 응답 시간)**               | 하위 $5\%$ 경계의 응답 시간                     | 상위 $95\%$ 유저가 겪는 최대 지연 시간 (Long-tail 분석) |
| **Wait Time (대기 시간; $W$)**             | $T_{service\_start} - T_{arrival}$              | 락 경합으로 인해 큐에서 소요된 병목 시간                |
| **Service Time (서비스 시간; $S$)**        | $T_{complete} - T_{service\_start}$             | 정규분포를 따르는 순수 로직 수행 시간                   |
| **System CPU Usage (시스템 CPU 점유율)**   | System CPU 점유율                               | 시스템 연산 오버헤드                                    |
| **System Mem Usage(시스템 메모리 점유량)** | JVM Heap Memory 점유량                          | 동시 요청 증가에 따른 메모리 고갈 위험도 분석           |
| **Redis CPU Usage (Redis CPU 점유율)**     | Redis CPU 점유율                                | Redis 연산 오버헤드                                     |
