import matplotlib.pyplot as plt

from latency_analysis import analyze_latency
from preprocess import load_all_data
from stability_analysis import analyze_stability
from tps_analysis import analyze_tps

# 시각화(그래프) 공통 설정
plt.rcParams['font.family'] = 'AppleGothic'
plt.rcParams['axes.unicode_minus'] = False

if __name__ == "__main__":

    # 데이터 폴더 경로 지정
    data_dir = '../data/raw/ngrinder'
    print(f"[{data_dir}] 경로에서 데이터를 읽어옵니다...")

    # 전처리 함수 실행
    df_all = load_all_data(data_dir)

    # 데이터가 정상적으로 불러와졌다면 분석 시작
    if df_all is not None:
        print("\n" + "=" * 50)
        print("1. 초당 처리량(TPS) 분석을 시작합니다.")
        print("=" * 50)
        analyze_tps(df_all)

        print("\n" + "=" * 50)
        print("2. 지연 시간 및 p95 Latency 분석을 시작합니다.")
        print("=" * 50)
        analyze_latency(df_all)

        print("\n" + "=" * 50)
        print("3. 안정성 및 2-sigma 분석을 시작합니다.")
        print("=" * 50)
        analyze_stability(df_all)

        print("\n모든 분석이 성공적으로 완료되었습니다.")

    else:
        print("데이터 로딩에 실패하여 분석을 종료합니다. 경로(data_dir)를 다시 확인해주세요.")
