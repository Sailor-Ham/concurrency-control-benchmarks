import os
from datetime import datetime

import matplotlib.pyplot as plt


def analyze_tps(df_all):
    """
    전처리된 전체 데이터(df_all)를 받아서 TPS(초당 처리량) 지표를 계산하고
    막대 그래프로 시각화해 주는 함수입니다.
    """

    # 테스트 회차별(Order) 평균/최대 TPS 계산
    run_stats = df_all.groupby(['Lock', 'Vuser', 'Order']).agg(
        Run_Mean_TPS=('TPS', 'mean'),
        Run_Peak_TPS=('TPS', 'max')
    ).reset_index()

    # Lock과 Vuser별 4가지 최종 지표 산출
    summary_stats = run_stats.groupby(['Lock', 'Vuser']).agg(
        Worst_Mean_TPS=('Run_Mean_TPS', 'min'),
        Overall_Mean_TPS=('Run_Mean_TPS', 'mean'),
        Best_Mean_TPS=('Run_Mean_TPS', 'max'),
        Average_Peak_TPS=('Run_Peak_TPS', 'mean'),
        Test_Count=('Order', 'count')
    ).reset_index()
    summary_stats = summary_stats.round(2)

    # 데이터프레임(표) 출력
    print("\n=== 각 락(Lock) 및 Vuser별 TPS 분석 결과===")
    print(summary_stats.to_string(index=False))

    os.makedirs('../data/results', exist_ok=True)

    current_time = datetime.now().strftime('%Y%m%d_%H%M%S')
    file_name = f'../data/results/tps_results_{current_time}.csv'

    summary_stats.to_csv(file_name, index=False, encoding='utf-8-sig')
    print(f"TPS 결과 테이블이 '{file_name}'로 저장되었습니다.")

    # 데이터 시각화(Lock & Vuser별 비교 그래프)
    fig, axes = plt.subplots(1, 2, figsize=(16, 6))

    # Pivot: 데이터를 그래프 형태로 재구조화
    # Vuser 수: 가로축(index)
    # 락 종류: 막대기 색깔 구분(columns)
    pivot_mean = summary_stats.pivot(index='Vuser', columns='Lock', values='Overall_Mean_TPS')
    pivot_peak = summary_stats.pivot(index='Vuser', columns='Lock', values='Average_Peak_TPS')

    # 락 타입 순서 설정
    lock_order = ['Pessimistic Lock', 'Spin Lock', 'Pub/Sub Lock']

    # 데이터에 없는 락 이름이 존재할 수 있으므로, 존재하는 락 이름만 필터링
    valid_locks_mean = [lock for lock in lock_order if lock in pivot_mean.columns]
    valid_locks_peak = [lock for lock in lock_order if lock in pivot_peak.columns]

    # 걸러낸 순서대로 피벗 테이블의 칼럼(막대기 순서) 재정렬
    pivot_mean = pivot_mean[valid_locks_mean]
    pivot_peak = pivot_peak[valid_locks_peak]

    # 1. Overall Mean TPS 그래프
    pivot_mean.plot(kind='bar', ax=axes[0], alpha=0.85, width=0.7)
    axes[0].set_title('Overall Mean TPS', fontsize=14, pad=15)  # 그래프 제목
    axes[0].set_xlabel('Vuser', fontsize=12)  # x축 이름
    axes[0].set_ylabel('Mean TPS', fontsize=12)  # y축 이름
    axes[0].tick_params(axis='x', rotation=0)  # x축 글씨(Vuser 수)가 기울어지지 않게 똑바로 세움
    axes[0].grid(axis='y', linestyle='--', alpha=0.6)  # 가로로 점선 그리드 추가

    # 막대기(container) 위에 수치 삽입
    for container in axes[0].containers:
        axes[0].bar_label(container, fmt='%.2f', padding=3, fontsize=10)  # 소수점 둘째자리까지 표기

    # 글씨가 그래프 천장에 붙지 않도록 위쪽 여백(margins) 15% 넉넉하게 줌
    axes[0].margins(y=0.15)

    # 2. Average Peak TPS 그래프
    pivot_peak.plot(kind='bar', ax=axes[1], alpha=0.85, width=0.7)
    axes[1].set_title('Average Peak TPS', fontsize=14, pad=15)  # 그래프 제목
    axes[1].set_xlabel('Vuser', fontsize=12)  # x축 이름
    axes[1].set_ylabel('Peak TPS', fontsize=12)  # y축 이름
    axes[1].tick_params(axis='x', rotation=0)  # x축 글씨(Vuser 수)가 기울어지지 않게 똑바로 세움
    axes[1].grid(axis='y', linestyle='--', alpha=0.6)  # 가로로 점선 그리드 추가

    # 막대기(container) 위에 수치 삽입
    for container in axes[1].containers:
        axes[1].bar_label(container, fmt='%.2f', padding=3, fontsize=9)  # 소수점 둘째자리까지 표기

    # 글씨가 그래프 천장에 붙지 않도록 위쪽 여백(margins) 15% 넉넉하게 줌
    axes[1].margins(y=0.15)

    plt.tight_layout()

    os.makedirs('../data/figures', exist_ok=True)
    img_name = f'../data/figures/tps_graph_{current_time}.png'

    plt.savefig(img_name, dpi=300, bbox_inches='tight')
    print(f"TPS 그래프가 '{img_name}'로 저장되었습니다.")

    plt.show()
