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
import java.util.concurrent.atomic.AtomicIntegerArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@RunWith(GrinderRunner)
class EnrollmentBurstPubSub500Test {

    public static GTest test
    public static HTTPRequest request
    public static Map<String, String> headers = [:]

    // 학생 ID 저장을 위한 큐
    public static ConcurrentLinkedQueue<Integer> studentIdQueue = new ConcurrentLinkedQueue<>()

    // 실제 유입 카운트를 기록할 Thread-safe 배열 (넉넉하게 15초로 설정)
    public static AtomicIntegerArray actualArrivals = new AtomicIntegerArray(15)

    // 테스트 시작 시간을 기록할 변수
    public static String testStartTime

    // 테스트 환경 설정
    public static final String TARGET_IP = "host.docker.internal"
    public static final String TARGET_PORT = "8080"

    // 실험 파라미터 설정 (테스트 시간: 10초)
    // TODO: 테스트 인원수에 맞게 이 값을 500, 750, 1000으로 변경하세요.
    public static final int TOTAL_USERS = 500
    // public static final int TOTAL_USERS = 750
    // public static final int TOTAL_USERS = 1000

    public static final int TEST_DURATION_SECONDS = 10

    // 테스트 전략 선택
    // TODO: 테스트 전략에 맞게 값을 변경하세요.
    // public static final String STRATEGY = "no-lock"
    // public static final String STRATEGY = "pessimistic-lock"
    // public static final String STRATEGY = "spin-lock"
    public static final String STRATEGY = "pub-sub-lock"

    public static String targetUrl = "http://${TARGET_IP}:${TARGET_PORT}/v1/enrollments/${STRATEGY}"
    public static String resetUrl = "http://${TARGET_IP}:${TARGET_PORT}/v1/benchmarks/reset"

    @BeforeProcess
    public static void beforeProcess() {

        // 커넥션 타임아웃 10초 설정
        HTTPRequestControl.setConnectionTimeout(10000)

        test = new GTest(1, "Enrollment Burst Test - ${STRATEGY}")
        request = new HTTPRequest()
        headers.put("Content-Type", "application/json")

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm")
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
        testStartTime = sdf.format(new Date())

        // [DB 데이터 클렌징] 프로세스 0에서만 초기화
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

        // 매 프로세스 시작 시 실제 유입 카운트 배열 초기화
        for (int i = 0; i < actualArrivals.length(); i++) {
            actualArrivals.set(i, 0)
        }

        test.record(request)

        // 프로세스별 학생 ID 할당
        int processNumber = grinder.processNumber
        int startId = (processNumber * 200) + 1
        int endId = (processNumber + 1) * 200

        List<Integer> studentIds = (startId..endId).toList()
        Collections.shuffle(studentIds) // 학생 ID 랜덤 섞기

        studentIdQueue.clear()
        studentIdQueue.addAll(studentIds)

        grinder.logger.info(">>> [데이터 준비 완료] 프로세스 {} 학생 ID {} ~ {} (총 {}명) 큐 적재 완료",
            processNumber, startId, endId, studentIds.size())
    }

    @Test
    public void testEnrollmentBurst() {

        // 지수 감쇠형 버스트 시뮬레이션
        double u = ThreadLocalRandom.current().nextDouble()
        double lambdaShape = 1.0
        double maxTime = (double) TEST_DURATION_SECONDS

        // 역함수 샘플링 공식을 통한 시간 계산
        double timeInSec = -Math.log(1 - u * (1 - Math.exp(-lambdaShape * maxTime))) / lambdaShape
        long randomDelay = (long) (timeInSec * 1000)

        // [실제 유입 기록] 딜레이 시간을 초 단위로 환산하여 카운트 1 증가
        int fireSecond = (int) (randomDelay / 1000)
        if (fireSecond >= 0 && fireSecond < actualArrivals.length()) {
            actualArrivals.incrementAndGet(fireSecond)
        }

        // 계산된 시간만큼 대기 후 발사
        grinder.sleep(randomDelay, 0)

        // 큐에서 무작위 학생 ID 추출
        Integer studentId = studentIdQueue.poll()

        if (studentId == null) {
            grinder.logger.warn(">>> [테스트 경고] 이 프로세스에 할당된 학생 ID가 모두 소진되었습니다.")
            return
        }

        long courseId = 1L

        String payload = String.format("{\"studentId\": %d, \"courseId\": %d}", studentId, courseId)

        // API POST 요청
        HTTPResponse response = request.POST(targetUrl, payload.getBytes(), headers)

        int statusCode = response.statusCode

        // 검증 로직
        if (statusCode == 200 || statusCode == 201) {
            assertThat(statusCode, is(anyOf(equalTo(200), equalTo(201))))
        } else if (statusCode == 400 || statusCode == 409) {
            grinder.logger.info(">>> [비즈니스 예외] 정원 초과 [상태코드: ${statusCode}]")
        } else {
            grinder.logger.info(">>> [시스템 장애] 자원 고갈 [상태코드: ${statusCode}]]")
            fail("동시성 제어 실패: HTTP ${statusCode}")
        }
    }

    @AfterProcess
    public static void afterProcess() {

        // 테스트 종료 시 10초간의 실제 유입량을 리스트로 변환하여 출력
        List<Integer> actualList = []
        for (int i = 0; i < TEST_DURATION_SECONDS; i++) {
            actualList.add(actualArrivals.get(i))
        }

        try {
            // [동적 파일명 생성] 인원수(TOTAL_USERS)와 시간(testStartTime) 조합
            String fileName = String.format("/tmp/arrivals_%d_%s.txt", TOTAL_USERS, testStartTime)
            File resultFile = new File(fileName)
            
            // 파일에 프로세스별 배열 기록
            resultFile.append("Process " + grinder.processNumber + ": " + actualList.toString() + "\n")
            
        } catch (Exception e) {
            // 무시
        }
    }
}
