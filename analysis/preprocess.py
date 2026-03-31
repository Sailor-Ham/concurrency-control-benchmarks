import glob
import os

import pandas as pd


def load_and_preprocess_ngrinder_data(file_path):
    """
    단일 nGrinder CSV 파일을 읽어와 분석에 용이하게 데이터를 가공(전처리)하는 함수입니다.
    """

    # 파일 경로에서 파일 이름 추출
    file_name = os.path.basename(file_path)

    # 파일명에서 '-result.csv' 글자를 지우고, 남은 글달을 하이픈('-')을 기준으로 쪼개어 리스트 작성
    parts = file_name.replace('-result.csv', '').split('-')

    # 쪼갠 단어들 중에서 락(Lock) 원본 이름, Vuser(가상 사용자 수), 테스트 순서 추출
    raw_lock_type = "-".join(parts[:-2])
    vuser_count = int(parts[-2])
    test_order = parts[-1]

    # 락 이름 포맷 변경
    lock_name_mapping = {
        'pessimistic-lock': 'Pessimistic Lock',
        'spin-lock': 'Spin Lock',
        'pub-sub-lock': 'Pub/Sub Lock'
    }
    lock_type = lock_name_mapping.get(raw_lock_type, raw_lock_type)

    # 판다스 라이브러리를 이용해 CSV 파일 안의 데이터를 표 형태로 메모리에 불러오기
    df = pd.read_csv(file_path)

    # 'DateTime' 칼럼을 날짜/시간 데이터 타입으로 변환 후 시간 순서대로 데이터 정렬
    df['DateTime'] = pd.to_datetime(df['DateTime'])
    df = df.sort_values('DateTime').reset_index(drop=True)

    # 'Elapsed_Sec(경과 시간)' 칼럼 만들기
    df['Elapsed_Sec'] = (df['DateTime'] - df['DateTime'].iloc[0]).dt.total_seconds()

    # 락 이름, Vuser 수, 테스트 회차 칼럼 추가
    df['Lock'] = lock_type
    df['Vuser'] = vuser_count
    df['Order'] = test_order

    return df


def load_all_data(data_dir):
    """
    지정된 폴더(data_dir) 안에 있는 모든 CSV 결과 파일을 한 번에 불러와 거대한 하나의 표로 합치는 함수입니다.
    이 함수를 다른 파일(analysis.py 등)에서 불러와 사용합니다.
    """

    # 결과 파일 리스트 생성
    all_files = glob.glob(os.path.join(data_dir, '*result.csv'))

    df_list = []
    for file in all_files:
        try:
            # 전처리 함수를 이용해 표 형태로 데이터를 불러오기
            processed_df = load_and_preprocess_ngrinder_data(file)
            df_list.append(processed_df)
        except Exception as e:
            print(f"파일 로드 실패 ({file}): {e}")

    # 데이터 존재 시 모두 합쳐 반환
    if df_list:
        df_all = pd.concat(df_list, ignore_index=True)
        print(f"총 {len(df_list)}개의 파일을 성공적으로 로드하고 병합했습니다.")

        return df_all
    else:
        print("조건에 맞는 CSV 파일을 찾지 못했습니다. 경로(data_dir)를 다시 확인해주세요.")
        return None
