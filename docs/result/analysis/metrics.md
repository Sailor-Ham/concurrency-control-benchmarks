# 지표 분석 방법

## TPS

```
 sum by (lock_type) (
    irate(benchmark_lock_service_time_seconds_count{job="concurrency-control"}[1m])
)
```

### 쿼리 분석

- `Metric`: `benchmark_lock_service_time_seconds_count
  - 잠금(Lock) 로직이 포함된 수강신청 서비스의 **누적 요청 완료 횟수**를 나타내는 Counter 지표
- `Function`: `irate(...)[1m]`
  - **순간 증가율(Instant Rate)** 계산 함수. 1분 범위 내 마지막 두 지표의 차이를 시간으로 나누어 **초당 요청 수(RPS)** 로 정규화
  - 수집 주기(Scrape Interval)가 짧은 환경에서 발생하는 미세한 성능 스파이크를 민감하게 포착
- `Aggregation`: `sum by (lock_type)`
  - `was1`, `was2` 등 개별 인스턴스 지표를 합산하여, **잠금 전략별 통합 성능** 산출

### 비고

#### 1. `irate` 함수가 작동하는 방식

Prometheus의 수집 주기가 3초이이기에, Prometheus는 3초 간격으로 "현재까지 누적된 요청 수" 데이터를 가져온다.

- $T_{0sec}$: 1,000건
- $T_{3sec}$: 1,150건 (3초 동안 150건 증가)

이때 `irate`는 $\frac{1150 - 1000}{3sec - 0sec}$를 계산한다.
결과값은 $50 req/s$가 된다.
즉, 수집주기가 3초든 10초든 함수 내부에서 해당 시간만큼 나누기 때문에 결과값은 항상 **'1초당'** 기준으로 나온다.

#### 2. 왜 $\times 3$을 하면 200에 가까워질까?

만약 **Vuser가 200**이라면 동시에 떠 있는 가상 사용자가 200명이라는 뜻이다.
만약 각 사용자가 1초에 1번씩 요청을 보낼 수 있는 환경이라면 전체 RPS(TPS)는 $200$이 나와야 한다.

하지만 결과 값이 $49.7 \text{ req/s}$나 $12.0 \text{ req/s}$처럼 낮게 나오는 이유는 **락(Lock)으로 인한 대기 시간** 때문이다.

- **동시성 제어(e.g., Pessimistic, Spin-Lock, etc.)** 가 작동하면, 한 번에 한 명만 처리되거나 대기열이 생겨 응답 속도가 느려진다.
- 응답이 느려지면 각 Vuser는 다음 요청을 보내기까지 더 오래 기다려야 하므로, 결과적으로 **초당 처리량(RPS)** 이 떨어지게 된다.

---

## 평균 응답 시간

```
sum by (lock_type) (
    irate(benchmark_lock_service_time_seconds_sum{job="concurrency-control"}[1m])
)
/
sum by (lock_type) (
    irate(benchmark_lock_service_time_seconds_count{job="concurrency-control"}[1m])
)
```

### 쿼리 분석

- **분자 (Total Duration Rate):** 모든 요청 처리에 소요된 **총 시간의 초당 증가율**
- **분모 (Throughput Rate):** 동일 시간 동안 완료된 **총 요청 수의 초당 증가율(RPS)**
- **연산 (Division):** '초당 총 소요 시간'을 '초당 요청 수'로 나누어, 결과적으로 **요청 1건당 평균 소요 시간(Seconds per Request)** 도출

### 비고

$$\frac{\text{sum by (lockType) (irate(...sum))}}{\text{sum by (lockType) (irate(...count))}} = \frac{\text{초당 소요된 총 시간 (Seconds / Second)}}{\text{초당 처리된 요청 수 (Requests / Second)}}$$

여기서 분모와 분자에 똑같이 들어있는 **'/ Second(초당)'** 단위가 서로 지워짐

- **결과 단위:** $Seconds / Request$ (요청 1건당 소요 시간)

즉, Prometheus가 3초 주기로 데이터를 가져오더라도, `irate`가 분자와 분모를 똑같이 3으로 나눠서 계산해 주기 때문에 **결과적으로는 수집 주기와 상관없이 항상 일정한 응답 시간** 산출

---

## p95 응답시간

### 쿼리 분석

```
histogram_quantile(0.95,
  sum by (le, lock_type) (
    irate(benchmark_lock_service_time_seconds_bucket{job="concurrency-control"}[1m])
  )
)
```

- `irate(..._bucket[1m])`: 각 응답 시간 구간(bucket)별로 초당 얼마나 많은 요청이 들어오는지 계산
- `sum by (le, lock_type)`: 인스턴스별로 나뉜 버킷 데이터들을 합산하여, 각 전략별 전체 분포 만듦
- `histogram_quantile(0.95, ...)`: 합산된 분포에서 누적 확률이 95%가 되는 지점의 X축 값(시간)을 선형 보간법으로 추정하여 반환

### 비고

- **Tail Latency 관측:** 평균 응답 시간(Mean Latency)은 락 경합 시 발생하는 일시적인 '심각한 지연'을 희석시킬 수 있음
- **신뢰성 지표:** P95 지표는 95%의 사용자가 이 시간 이내에 응답을 받는다는 것을 보장하므로, 분산 시스템이나 동시성 제어 로직의 안정성을 증명하는 핵심 지표
- **평균과 p95 사이의 관계: 롱 테일(Long Tail) 현상**
  - **간격이 좁을 때:** 모든 요청이 일정한 속도로 처리됨(시스템이 안정적이고 예측 가능)
  - **간격이 넓을 때:** 평균은 낮아 보이지만, 특정 상황(락 경합 등)에서 일부 요청이 비정상적으로 오래 대기함. 이를 **롱 테일 지연**이라고 부름
- 평균 응답 시간만으로는 락 경합 시 발생하는 소수 사용자의 극심한 지연을 포착할 수 없으므로, P95를 통해 시스템의 신뢰성 함께 검증

---

## Wait Time $(W)$

```
sum by (lock_type) (
  irate(benchmark_lock_wait_time_seconds_sum{job="concurrency-control"}[1m])
)
/
sum by (lock_type) (
  irate(benchmark_lock_wait_time_seconds_count{job="concurrency-control"}[1m])
)
```

### 쿼리 분석

- **분자(`_sum`):** 락을 획득하기 위해 스레드가 대기한 **총 시간의 초당 증가율**
- **분모(`_count`):** 초당 발생한 **락 획득 시도(또는 성공) 횟수**
- **결과:** "락 한 번을 얻기 위해 평균적으로 몇 초를 기다렸는가?"를 나타내는 **순수 대기 지연(Pure Wait Latency)** 값

### 비고

- `irate`를 사용하므로 분자와 분모를 동일한 시간 단위(1초)로 정규화함
- 수집 주기와 상관없이 분자와 분모에서 시간 단위가 서로 약분되어 사라지므로 **가중치 없이 그대로 사용**
- **락 알고리즘 자체의 오버헤드**

---

## Service Time $(S)$

```
sum by (lock_type) (
  irate(benchmark_lock_service_time_seconds_sum{job="concurrency-control"}[1m])
)
/
sum by (lock_type) (
  irate(benchmark_lock_service_time_seconds_count{job="concurrency-control"}[1m])
)
```

### 쿼리 분석

- **분자:** 초당 누적된 서비스 실행 시간의 증가율 (Seconds / )
- 특정 기간 동안 발생한 **전체 서비스 처리 시간의 합**을 **처리된 요청 수**로 나눈 값

### 비고

- 락 대기 + 로직 실행 + 락 해제
- 사용자에게 느껴지는 전체 속도

---
