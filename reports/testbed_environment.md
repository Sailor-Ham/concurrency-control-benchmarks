# 동시성 제어 성능 평가 테스트 베드 명세서

본 문서는 동시성 제어 벤치마크 테스트가 수행된 시스템 환경, 하드웨어 사양, 소프트웨어 설정 및 네트워크 아키텍처에 대한 상세 명세입니다.

---

## 1. 하드웨어 및 리소스 할당 사양

테스트 베드를 구동하는 Docker Host 머신은 총 **10 Core CPU**를 보유하고 있으며, 각 컨테이너에는 병목 지점을 명확히 파악하기 위한 전략적인 자원 제한(Limits)이 적용되었습니다.

| 컴포넌트(Service)     | 컨테이너 수 | CPU 할당량(Limits) | 메모리 할당량(Limits) | 비고                               |
| :-------------------- | :---------- | :----------------- | :-------------------- | :--------------------------------- |
| **WAS (Spring Boot)** | 2           | `2.0` Cores        | `2048M`               | 컨테이너당 2 코어로 연산 능력 제한 |
| **MySQL Database**    | 1           | 제한 없음(최대 10) | 제한 없음             | 호스트의 가용 자원 모두 활용 가능  |
| **Nginx (LB)**        | 1           | 기본값             | 기본값                | Round-Robin 방식 부하 분산         |

---

## 2. 핵심 애플리케이션 및 스레드 풀 설정

극한의 트래픽 급증(Burst Traffic)과 단일 레코드(Hot-row) 경합 상황을 테스트하기 위해 의도적으로 인프라 파이프라인의 크기를 최대로 확장한 부하 테스트 설정입니다.

### 2.1. Spring Boot (Tomcat) 설정

- **Max Threads (`server.tomcat.threads.max`):** 200
  - 동시에 로직을 수행할 수 있는 최대 스레드 수
- **Accept Count (`server.tomcat.accept-count`):** 500
  - 스레드를 할당받기 전 대기할 수 있는 OS 수준의 큐 크기
  - _단일 WAS당 최대 수용 가능 동시 연결(Capacity): 700_
- **Max Connections (`server.tomcat.max-connections`):** 1000
  - 물리적 TCP 연결 최대치 허용

### 2.2. 데이터베이스 커넥션 풀 (HikariCP) 설정

- **Maximum Pool Size (`spring.datasource.hikari.maximum-pool-size`):** 150
  - 단일 WAS가 MySQL과 맺을 수 있는 최대 커넥션 수
  - _was1(150) + was2(150) = DB에 인입되는 최대 동시 커넥션 수: 300_
- **Connection Timeout (`spring.datasource.hikari.connection-timeout`):** 5000 ms (5초)
