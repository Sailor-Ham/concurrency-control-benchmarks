import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.AfterProcess
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ngrinder.http.HTTPRequest
import org.ngrinder.http.HTTPRequestControl
import org.ngrinder.http.HTTPResponse
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom

@RunWith(GrinderRunner)
class EnrollmentBurstTest {

    public static GTest test
    public static HTTPRequest request
    public static Map<String, String> headers = [:]

    // 학생 ID 저장을 위한 큐
    public static ConcurrentLinkedQueue<Integer> studentIdQueue = new ConcurrentLinkedQueue<>()

    // 테스트 환경 설정
    public static final String TARGET_IP = "host.docker.internal"
    public static final String TARGET_PORT = "8080"

    // 실험 파라미터 설정 (10초 기준)
    public static final int TOTAL_USERS = 200
    // public static final int TOTAL_USERS = 400
    // public static final int TOTAL_USERS = 600
    
    public static final int TEST_DURATION_SECONDS = 10

    // 테스트 전략 선택(테스트 시마다 주석을 해제하며 교체)
    // public static final String STRATEGY = "no-lock"
    public static final String STRATEGY = "pessimistic-lock"
    // public static final String STRATEGY = "spin-lock"
    // public static final String STRATEGY = "pub-sub-lock"

    public static String targetUrl = "http://${TARGET_IP}:${TARGET_PORT}/v1/enrollments/${STRATEGY}"
    public static String resetUrl = "http://${TARGET_IP}:${TARGET_PORT}/v1/benchmark/reset"

    @BeforeProcess
    public static void beforeProcess() {

        // 커넥션 타임아웃 10초 설정 (버스트 상황에서 WAS 스레드 풀 대기열 적체 대비)
        HTTPRequestControl.setConnectionTimeout(10000)

        test = new GTest(1, "Enrollment Burst - ${STRATEGY}")
        request = new HTTPRequest()
        headers.put("Content-Type", "application/json")

        // [DB 데이터 클렌징] 프로세스 0에서만 초기화하여 멀티 프로세스 환경의 중복 초기화 방지
        if (grinder.processNumber == 0) {
            grinder.logger.info(">>> [DB 초기화] 테스트 전략 세팅 중: ${STRATEGY}")

            try {

                HTTPResponse resetResponse = request.POST(resetUrl, "".getBytes(), headers)

                if (resetResponse.statusCode == 200) {
                    grinder.logger.info(">>> [DB 초기화 완료] 응답: ${resetResponse.bodyText}")
                } else {
                    grinder.logger.error(">>> [DB 초기화 실패] HTTP 상태 코드: ${resetResponse.statusCode}")
                }
            } catch (Exception e) {
                grinder.logger.error(">>> [DB 초기화 에러] 원인: ${e.message}")
            }
        }

        //  [푸아송 리스트 생성 및 로깅]
        // 실제 쓰레드를 제어하진 않지만, 이번 실험의 '부하 설계도'를 기록하는 역할입니다.
        arrivalSchedule = (1..TEST_DURATION_SECONDS).collect { getPoissonCount(AVERAGE_LAMBDA) }

        grinder.logger.info("==================================================")
        grinder.logger.info("[실험 설계 기록] 프로세스 {}번의 부하 분포 예측", grinder.processNumber)
        grinder.logger.info("평균 유입률(λ): {} users/sec", AVERAGE_LAMBDA)
        grinder.logger.info("초당 유입 분포(Poisson): {}", arrivalSchedule)
        grinder.logger.info("==================================================")

        test.record(request)

        // [프로세스별 학생 ID 할당] 중복 ID 방지를 위해 범위를 나눔
        int processNum = grinder.processNumber
        int startId = (processNum * 200) + 1
        int endId = (processNum + 1) * 200

        List<Integer> studentIds = (startId..endId).toList()
        Collections.shuffle(studentIds) 

        studentIdQueue.clear()
        studentIdQueue.addAll(studentIds)

        grinder.logger.info(">>> [데이터 준비 완료] 프로세스 {} 학생 ID {} ~ {} (총 {}개) 셔플 및 큐 적재 완료",
            processNum, startId, endId, studentIds.size())
    }

    @Test
    public void testEnrollmentBurst() {

        // [버스트 트래픽 시뮬레이션]
        // 각 Vuser가 [0, TEST_DURATION_SECONDS) 구간에서 균등 랜덤 딜레이를 가지면서
        // 지정된 시간 창(Time Window) 내에 집중적으로 요청이 몰리는 버스트 패턴을 재현함
        long randomDelay = ThreadLocalRandom.current().nextLong(TEST_DURATION_SECONDS * 1000)
        grinder.sleep(randomDelay, 0)

        // 큐에서 중복되지 않은 무작위 학생 ID를 하나씩 꺼냄
        Integer studentId = studentIdQueue.poll()

        // 1,200명이 모두 소진된 경우 더 이상 API를 쏘지 않고 조기 종료
        if (studentId == null) {
            grinder.logger.info(">>> [테스트 경고] 이 프로세스에 할당된 학생 ID가 모두 소진되었습니다.")
            return
        }

        long courseId = 1L  // 단일 핫스팟(동일 강의) 집중 공략

        String payload = String.format("{\"studentId\": %d, \"courseId\": %d}", studentId, courseId)

        // 수강신청 API POST 요청 (실제 성능 측정 구간)
        HTTPResponse response = request.POST(targetUrl, payload.getBytes(), headers)

        int statusCode = response.statusCode
        String responseBody = response.bodyText

        // 응답 코드별 Custom Validation 로직 (논문 지표 수집용)
        if (statusCode == 200 || statusCode == 201) {
            // [정상 처리] 수강신청 완료 (DB Insert 성공)
            assertThat(statusCode, is(anyOf(equalTo(200), equalTo(201))))
        } else if (statusCode == 400 || statusCode == 409) {
            // [비즈니스 에러] 잔여석 부족 또는 중복 신청
            grinder.logger.info(">>> [비즈니스 예외] 정원 초과 (정상 방어됨) [상태코드: ${statusCode}]")
        } else {
            // [시스템 장애] 5xx (Deadlock, Redis 타임아웃, 커넥션 풀 고갈 등)
            grinder.logger.info(">>> [시스템 장애] 임계점 돌파 및 자원 고갈 [상태코드: ${statusCode}]")
            fail("동시성 제어 실패 또는 리소스 고갈: HTTP ${statusCode}")
        }
    }

    @AfterProcess
    public static void afterProcess() {
        grinder.logger.info(">>> [테스트 종료] 대상 전략: ${STRATEGY}")
    }
}
