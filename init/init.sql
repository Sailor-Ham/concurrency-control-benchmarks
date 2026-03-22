SET NAMES utf8mb4;

-- 학생(student) 테이블 생성
CREATE TABLE IF NOT EXISTS student
(
    student_id     BIGINT AUTO_INCREMENT COMMENT '학생 ID',
    student_number VARCHAR(20) NOT NULL COMMENT '학번',
    name           VARCHAR(50) NOT NULL COMMENT '이름',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY pk_student (student_id),
    UNIQUE KEY uk_student_number (student_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
    COMMENT '학생';

-- 강의(course) 테이블 생성
CREATE TABLE IF NOT EXISTS course
(
    course_id      BIGINT AUTO_INCREMENT COMMENT '강의 ID',
    title          VARCHAR(100) NOT NULL COMMENT '강의명',
    max_capacity   BIGINT       NOT NULL COMMENT '최대 수강 인원',
    enrolled_count BIGINT       NOT NULL DEFAULT 0 COMMENT '현재 수강 인원',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY pk_course (course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
    COMMENT '강의';

-- 수강신청(enrollment) 테이블 생성
CREATE TABLE IF NOT EXISTS enrollment
(
    enrollment_id BIGINT AUTO_INCREMENT COMMENT '수강신청 ID',
    student_id    BIGINT   NOT NULL COMMENT '학생 ID',
    course_id     BIGINT   NOT NULL COMMENT '강의 ID',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',

    PRIMARY KEY pk_enrollment (enrollment_id),
    UNIQUE KEY uk_enrollment_student_id_course_id (student_id, course_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
    COMMENT '수강신청';
