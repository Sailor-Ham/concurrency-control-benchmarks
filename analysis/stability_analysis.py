import os
from datetime import datetime

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def calculate_stability_metrics(group):
    """
    각 테스트 회차의 데이터를 받아
    '전체 평균'과 '통합 표준편차', '2-sigma 신뢰 구간'을 구하는 함수입니다.
    """

    # n_i: 2초 동안 처리된 전체 요청 건수(TPS 칼럼을 요청 건수의 가중치로 활용)
    # mu_i: 2초 구간 동안의 평균 지연 시간
    # sigma_i: 2초 구간 동안의 지연 시간 표준편차
    n_i = group['TPS']
    mu_i = group['Mean_Test_Time_(ms)']
    sigma_i = group['Test_Time_Standard_Deviation_(ms)']

    # 총 요청 건수(N) 계산
    N = n_i.sum()

    # 데이터가 1개 이하면 통계(분산)를 낼 수 없으므로 0으로 처리하여 반환
    if N <= 1:
        return pd.Series({'Overall_Mean': 0.0, 'Total_Sigma': 0.0, 'Lower_2sigma': 0.0, 'Upper_2sigma': 0.0})

    # 1. 전체 가중 평균(Overall Mean) 계산
    # 요청이 많았던 구간에 가중치를 두어 평균 계산
    mu_total = np.sum(n_i * mu_i) / N

    # 2. 그룹 내 제곱합(SS_within) 계산
    # 각 2초 구간 안에서 발생한 응답 시간의 분산을 모두 더함
    # n_i - 1 이 혹시라도 0 이하가 되어 에러가 나는 것을 방지하기 위해 np.maximum(0, ...) 사용
    ss_within = np.sum(np.maximum(0, n_i - 1) * (sigma_i ** 2))

    # 3. 그룹 간 제곱합(SS_between) 계산
    # 각 2초 구간의 평균(mu_i)과 전체 평균(mu_total)의 차이를 모두 더함
    ss_between = np.sum(n_i * ((mu_i - mu_total) ** 2))

    # 4. 최종 통합 표준편차(sigma_total) 계산
    sigma_total = np.sqrt((ss_within + ss_between) / (N - 1))

    # 5. 2-sigma 신뢰 구간
    # 통계학에서 평균으로부터 ±2표준편차(2-sigma) 안에는 전체 데이터의 약 95.4%가 들어감
    lower_2sigma = mu_total - (2 * sigma_total)  # 하한선 (빠른 응답)
    upper_2sigma = mu_total + (2 * sigma_total)  # 상한선 (느린 응답)

    return pd.Series({
        'Overall_Mean': mu_total,
        'Total_Sigma': sigma_total,
        'Lower_2sigma': max(0, lower_2sigma),  # 응답 시간은 음수가 될 수 없으므로 최소값을 0으로 지정
        'Upper_2sigma': upper_2sigma
    })


def analyze_stability(df_all):
    """
    전처리된 데이터를 받아
    안정성 지표를 계산하고, 에러바(Error Bar) 형태의 그래프로 보여주는 함수입니다.
    """

    # 테스트 회차별 안정성 지표 계산
    run_stability = df_all.groupby(['Lock', 'Vuser', 'Order']).apply(
        calculate_stability_metrics, include_groups=False
    ).reset_index()

    # Lock과 Vuser별 4가지 최종 지표 계산
    stability_summary = run_stability.groupby(['Lock', 'Vuser']).agg(
        Overall_Mean_Latency=('Overall_Mean', 'mean'),
        Avg_Total_Sigma=('Total_Sigma', 'mean'),
        Avg_Lower_Bound=('Lower_2sigma', 'mean'),
        Avg_Upper_Bound=('Upper_2sigma', 'mean')
    ).reset_index()
    stability_summary = stability_summary.round(2)

    # 데이터프레임(표) 출력
    print("\n=== 각 락(Lock) 및 Vuser별 2-sigma 신뢰 구간 분석 결과(ms) ===")
    print(stability_summary.to_string(index=False))

    os.makedirs('../data/results', exist_ok=True)

    current_time = datetime.now().strftime('%Y%m%d_%H%M%S')
    file_name = f'../data/results/stability_results_{current_time}.csv'

    stability_summary.to_csv(file_name, index=False, encoding='utf-8-sig')
    print(f"Stability 결과 테이블이 '{file_name}'로 저장되었습니다.")

    # 데이터 시각화(Lock & Vuser별 비교 그래프) (Error Bar를 이용한 신뢰 구간 표시)
    plt.figure(figsize=(12, 7))

    # 락 타입 순서 설정
    lock_order = ['Pessimistic Lock', 'Spin Lock', 'Pub/Sub Lock']

    stability_summary['Lock'] = pd.Categorical(stability_summary['Lock'], categories=lock_order, ordered=True)
    stability_summary = stability_summary.sort_values(['Lock', 'Vuser'])

    # 그래프 생성
    for lock in lock_order:
        subset = stability_summary[stability_summary['Lock'] == lock]
        if subset.empty:
            continue

        yerr = [
            subset['Overall_Mean_Latency'] - subset['Avg_Lower_Bound'],  # 하한선 길이
            subset['Avg_Upper_Bound'] - subset['Overall_Mean_Latency'],  # 상한선 길이
        ]

        # errorbar: 점(평균)과 선, 그리고 오차 범위(yerr)를 함께 그려주는 기능
        plt.errorbar(
            x=subset['Vuser'].astype(str) + f"\n{lock}",  # x축 글씨가 길어서 겹치는 것을 막기 위해 Vuser 수 뒤에 줄바꿈(\n)을 넣고 락 이름 작성
            y=subset['Overall_Mean_Latency'],  # 점의 위치(평균)
            yerr=yerr,  # 하한선, 상한선 길이 지정
            fmt='o-',  # 'o'는 동그란 점, '-'는 점들을 잇는 실선
            capsize=5,  # 에러바 끝에 수평선(cap) 길이
            capthick=5,  # 에러바 끝 마감선 두께
            markersize=8,  # 동그란 점의 크기
            label=lock,  # 범례(Legend)의 표시 이름
            linewidth=2  # 선의 두께
        )

    plt.title('Stability Analysis: Mean Latency with 2-sigma Confidence Interval', fontsize=15, pad=20)
    plt.xlabel('Vuser', fontsize=12)
    plt.ylabel('Latency (ms)', fontsize=12)
    plt.legend()  # 범례 표시
    plt.grid(axis='y', linestyle='--', alpha=0.5)  # 가로 점섬 그리드
    plt.tight_layout()  # 여백 자동 조절

    os.makedirs('../data/figures', exist_ok=True)
    img_name = f'../data/figures/stability_graph_{current_time}.png'

    plt.savefig(img_name, dpi=300, bbox_inches='tight')
    print(f"Stability 그래프가 '{img_name}'로 저장되었습니다.")

    plt.show()
