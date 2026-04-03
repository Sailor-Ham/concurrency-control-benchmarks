import os
from datetime import datetime

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def calculate_run_metrics(group):
    """
    각 테스트 회차의 데이터를 받아
    해당 테스트의 '평균 지연 시간'과 'p95'를 수학적으로 추정하는 함수입니다.
    """

    # n_i: 2초 동안 처리된 전체 요청 건수(TPS 칼럼을 요청 건수의 가중치로 활용)
    # mu_i: 2초 구간 동안의 평균 지연 시간
    # sigma_i: 2초 구간 동안의 지연 시간 표준편차
    n_i = group['TPS']
    mu_i = group['Mean_Test_Time_(ms)']
    sigma_i = group['Test_Time_Standard_Deviation_(ms)']

    # 해당 테스트 회차의 총 요청 건수
    N = n_i.sum()

    # 만약 처리된 요청이 0건이라면 지연 시간 또한 0으로 반환
    if N == 0:
        return pd.Series({'Run_Mean_Latency': 0.0, 'Run_p95_Latency': 0.0})

    # 1. 전체 가중 평균(Overall Weighted Mean) 계산
    # 요청 건수(n_i)가 많았던 구간의 지연 시간(mu_i)에 더 큰 가중치를 주어 평균 계산
    mu_total = np.sum(n_i * mu_i) / N

    # 2. 전체 결합 분산(Pooled Variance) 계산
    # 통계학의 '제곱의 평균' 공식을 이용해 여러 구간의 분산을 하나로 합치기
    e_x2_i = (sigma_i ** 2) + (mu_i ** 2)  # 각 구간의 제곱 평균
    e_x2_total = np.sum(n_i * e_x2_i) / N  # 전체 제곱 평균의 가중합

    # 최종 전체 분산
    sigma_total_sq = max(0, e_x2_total - (mu_total ** 2))  # 컴퓨터의 소수점 계산 오차로 인해 음수가 나오는 것을 막기 위해 max(0, ...) 사용

    # 3. 로그 정규 분포(Log-Normal) 파라미터 변환
    # 응답 시간은 Long-tail 비대칭 형태를 띠므로, 로그 정규 분포 모델을 사용해 변환
    m = mu_total
    v = sigma_total_sq

    # 평균이나 분산이 0에 수렴하면(완벽히 일정하면) p95도 평균값과 동일
    if m <= 0 or v == 0:
        p95 = m
    else:
        # 일반 통계값을 로그 정규 분포용 파라미터로 변환하는 수학 공식
        sigma_log = np.sqrt(np.log(1 + (v / (m ** 2))))
        mu_log = np.log(m) - (sigma_log ** 2) / 2

        # 4. 최종 p95 계산
        # 정규분포에서 상위 5% 지점을 가리키는 Z-Score(1.64485)를 곱해서 가장 느린 축에 속하는 응답시간 검색
        p95 = np.exp(mu_log + 1.64485 * sigma_log)

    # 계산된 평균 지연 시간과 p95 꼬리 지연 시간을 결과로 반환
    return pd.Series({'Run_Mean_Latency': mu_total, 'Run_p95_Latency': p95})


def analyze_latency(df_all):
    """
    전처리된 전체 데이터(df_all)를 받아 응답 시간(Latency) 지표를 분석하고
    막대 그래프로 보여주는 함수입니다.
    """

    run_stats = df_all.groupby(['Lock', 'Vuser', 'Order']).apply(
        calculate_run_metrics, include_groups=False
    ).reset_index()

    # Lock과 Vuser별 4가지 최종 지표 계산
    summary_stats = run_stats.groupby(['Lock', 'Vuser']).agg(
        Worst_Mean_Latency=('Run_Mean_Latency', 'max'),
        Overall_Mean_Latency=('Run_Mean_Latency', 'mean'),
        Best_Mean_Latency=('Run_Mean_Latency', 'min'),
        Average_p95_Latency=('Run_p95_Latency', 'mean')
    ).reset_index()
    summary_stats = summary_stats.round(2)

    # 데이터프레임(표) 출력
    print("\n=== 각 락(Lock) 및 Vuser별 Latency 분석 결과(ms) ===")
    print(summary_stats.to_string(index=False))

    os.makedirs('../data/results', exist_ok=True)

    current_time = datetime.now().strftime('%Y%m%d_%H%M%S')
    file_name = f'../data/results/latency_results_{current_time}.csv'

    summary_stats.to_csv(file_name, index=False, encoding='utf-8-sig')
    print(f"Latency 결과 테이블이 '{file_name}'로 저장되었습니다.")

    # 데이터 시각화(Lock & Vuser별 비교 그래프)
    fig, axes = plt.subplots(1, 2, figsize=(16, 6))

    # Pivot: 데이터를 그래프 형태로 재구조화
    # Vuser 수: 가로축(index)
    # 락 종류: 막대기 색깔 구분(columns)
    pivot_mean = summary_stats.pivot(index='Vuser', columns='Lock', values='Overall_Mean_Latency')
    pivot_p95 = summary_stats.pivot(index='Vuser', columns='Lock', values='Average_p95_Latency')

    # 락 타입 순서 설정
    lock_order = ['Pessimistic Lock', 'Spin Lock', 'Pub/Sub Lock']

    # 데이터에 없는 락 이름이 존재할 수 있으므로, 존재하는 락 이름만 필터링
    valid_locks_mean = [lock for lock in lock_order if lock in pivot_mean.columns]
    valid_locks_p95 = [lock for lock in lock_order if lock in pivot_p95.columns]

    # 걸러낸 순서대로 피벗 테이블의 칼럼(막대기 순서) 재정렬
    pivot_mean = pivot_mean[valid_locks_mean]
    pivot_p95 = pivot_p95[valid_locks_p95]

    # 1. Overall Mean Latency 그래프
    pivot_mean.plot(kind='bar', ax=axes[0], alpha=0.85, width=0.7)
    axes[0].set_title('Overall Mean Latency', fontsize=14, pad=15)  # 그래프 제목
    axes[0].set_xlabel('Vuser', fontsize=12)  # x축 이름
    axes[0].set_ylabel('Mean Latency (ms)', fontsize=12)  # y축 이름
    axes[0].tick_params(axis='x', rotation=0)  # x축 글씨(Vuser 수)가 기울어지지 않게 똑바로 세움
    axes[0].grid(axis='y', linestyle='--', alpha=0.6)  # 가로로 점선 그리드 추가

    # 막대기(container) 위에 수치 삽입
    for container in axes[0].containers:
        axes[0].bar_label(container, fmt='%.2f', padding=3, fontsize=9, rotation=90)  # 소수점 둘째자리까지 표기

    # 글씨가 그래프 천장에 붙지 않도록 위쪽 여백(margins) 15% 넉넉하게 줌
    axes[0].margins(y=0.15)

    # 2. Average p95 Latency 그래프
    pivot_p95.plot(kind='bar', ax=axes[1], alpha=0.85, width=0.7)
    axes[1].set_title('Average p95 Latency (Log-Normal Estimated)', fontsize=14, pad=15)  # 그래프 제목
    axes[1].set_xlabel('Vuser', fontsize=12)  # x축 이름
    axes[1].set_ylabel('p95 Latency (ms)', fontsize=12)  # y축 이름
    axes[1].tick_params(axis='x', rotation=0)  # x축 글씨(Vuser 수)가 기울어지지 않게 똑바로 세움
    axes[1].grid(axis='y', linestyle='--', alpha=0.6)  # 가로로 점선 그리드 추가

    # 막대기(container) 위에 수치 삽입
    for container in axes[1].containers:
        axes[1].bar_label(container, fmt='%.2f', padding=3, fontsize=9, rotation=90)  # 소수점 둘째자리까지 표기

    # 글씨가 그래프 천장에 붙지 않도록 위쪽 여백(margins) 15% 넉넉하게 줌
    axes[1].margins(y=0.15)

    plt.tight_layout()

    os.makedirs('../data/figures', exist_ok=True)
    img_name = f'../data/figures/latency_graph_{current_time}.png'

    plt.savefig(img_name, dpi=300, bbox_inches='tight')
    print(f"Latency 그래프가 '{img_name}'로 저장되었습니다.")

    plt.show()
