INSERT INTO members (member_id, email, nickname, password, phone_num, created_at, updated_at)
VALUES
    ('demo-mentor-backend', 'demo.mentor.backend@example.com', '김백엔드', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3100-0001', NOW(), NOW()),
    ('demo-mentor-data', 'demo.mentor.data@example.com', '이데이터', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3100-0002', NOW(), NOW()),
    ('demo-mentor-frontend', 'demo.mentor.frontend@example.com', '박프론트', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3100-0003', NOW(), NOW()),
    ('demo-mentee-001', 'demo.mentee.001@example.com', '최수강', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3200-0001', NOW(), NOW()),
    ('demo-mentee-002', 'demo.mentee.002@example.com', '정멘티', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3200-0002', NOW(), NOW()),
    ('demo-mentee-003', 'demo.mentee.003@example.com', '한테스터', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3200-0003', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    nickname = VALUES(nickname),
    password = VALUES(password),
    phone_num = VALUES(phone_num),
    updated_at = NOW();

INSERT INTO profiles (member_id, image, introduce, category_id, created_at, updated_at)
VALUES
    ('demo-mentor-backend', NULL, 'Spring Boot, JPA, 배포 흐름을 실무 관점에서 봅니다.', 302, NOW(), NOW()),
    ('demo-mentor-data', NULL, '데이터 엔지니어링과 AI 서비스 커리어를 함께 설계합니다.', 502, NOW(), NOW()),
    ('demo-mentor-frontend', NULL, '프론트엔드 포트폴리오와 제품 중심 UI 구현을 리뷰합니다.', 303, NOW(), NOW()),
    ('demo-mentee-001', NULL, '', 1000, NOW(), NOW()),
    ('demo-mentee-002', NULL, '', 1000, NOW(), NOW()),
    ('demo-mentee-003', NULL, '', 1000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    image = VALUES(image),
    introduce = VALUES(introduce),
    category_id = VALUES(category_id),
    updated_at = NOW();

INSERT INTO lessons (lesson_id, title, lesson_type, thumbnail_image, description, location, close_at,
                     mentor_id, category_id, price, seats, category_path, popularity_score, created_at, updated_at)
VALUES
    ('demo-mentoring-backend-future', 'Spring 백엔드 커리어 1:1 멘토링', 'MENTORING', 'default.png',
     '이력서, 포트폴리오, 프로젝트 구조를 함께 점검하는 1:1 멘토링입니다.', '온라인',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 30 DAY,
     'demo-mentor-backend', 302, 50000, NULL, '3:302', 12.5,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 2 DAY, NOW()),
    ('demo-mentoring-data-past', '데이터 엔지니어 이직 상담', 'MENTORING', 'default.png',
     '데이터 파이프라인 경험 정리와 이직 전략을 다루는 멘토링입니다.', '온라인',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 2 DAY,
     'demo-mentor-data', 502, 55000, NULL, '5:502', 5.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 18 DAY, NOW()),
    ('demo-oneday-backend-open', 'Spring Boot 원데이 클래스', 'ONEDAY', 'default.png',
     '하루 동안 API 설계, 예외 처리, 테스트 코드까지 완성합니다.', '서울 강남',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 14 DAY,
     'demo-mentor-backend', 302, 35000, 20, '3:302', 21.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 4 DAY, NOW()),
    ('demo-oneday-frontend-soldout', '프론트엔드 포트폴리오 원데이 리뷰', 'ONEDAY', 'default.png',
     '포트폴리오 화면 구성, 컴포넌트 구조, 배포 상태를 함께 리뷰합니다.', '서울 성수',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 10 DAY,
     'demo-mentor-frontend', 303, 30000, 6, '3:303', 18.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 3 DAY, NOW()),
    ('demo-oneday-backend-past', '지난 Spring 실전 특강', 'ONEDAY', 'default.png',
     '이미 종료된 원데이 클래스 목록/상세 확인용 데이터입니다.', '서울 판교',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 5 DAY,
     'demo-mentor-backend', 302, 25000, 10, '3:302', 2.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 24 DAY, NOW()),
    ('demo-study-backend-open', '백엔드 프로젝트 4주 스터디', 'STUDY', 'default.png',
     '4회차 동안 작은 서비스를 설계하고 배포까지 진행합니다.', '서울 홍대',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 28 DAY,
     'demo-mentor-backend', 302, 80000, 8, '3:302', 30.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 1 DAY, NOW()),
    ('demo-study-data-soldout', '데이터 파이프라인 스터디', 'STUDY', 'default.png',
     '로그 수집부터 대시보드까지 데이터 흐름을 만드는 스터디입니다.', '온라인',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 21 DAY,
     'demo-mentor-data', 502, 70000, 5, '5:502', 16.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 6 DAY, NOW()),
    ('demo-study-frontend-past', '지난 프론트엔드 협업 스터디', 'STUDY', 'default.png',
     '이미 종료된 스터디 목록/상세 확인용 데이터입니다.', '서울 신촌',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 7 DAY,
     'demo-mentor-frontend', 303, 60000, 6, '3:303', 4.0,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 35 DAY, NOW())
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    lesson_type = VALUES(lesson_type),
    thumbnail_image = VALUES(thumbnail_image),
    description = VALUES(description),
    location = VALUES(location),
    close_at = VALUES(close_at),
    mentor_id = VALUES(mentor_id),
    category_id = VALUES(category_id),
    price = VALUES(price),
    seats = VALUES(seats),
    category_path = VALUES(category_path),
    popularity_score = VALUES(popularity_score),
    updated_at = NOW();

INSERT INTO options (option_id, lesson_id, name, minute, price, created_at, updated_at)
VALUES
    ('demo-option-backend-30', 'demo-mentoring-backend-future', '30분 빠른 상담', 30, 30000, NOW(), NOW()),
    ('demo-option-backend-60', 'demo-mentoring-backend-future', '60분 집중 상담', 60, 50000, NOW(), NOW()),
    ('demo-option-data-60', 'demo-mentoring-data-past', '60분 커리어 상담', 60, 55000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    name = VALUES(name),
    minute = VALUES(minute),
    price = VALUES(price),
    updated_at = NOW();

INSERT INTO available_times (available_time_id, lesson_id, start_time, end_time, price, seats, created_at, updated_at)
VALUES
    ('demo-at-oneday-backend-open', 'demo-oneday-backend-open',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 9 DAY + INTERVAL 10 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 9 DAY + INTERVAL 17 HOUR,
     35000, 20, NOW(), NOW()),
    ('demo-at-oneday-frontend-soldout', 'demo-oneday-frontend-soldout',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 6 DAY + INTERVAL 13 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 6 DAY + INTERVAL 18 HOUR,
     30000, 6, NOW(), NOW()),
    ('demo-at-oneday-backend-past', 'demo-oneday-backend-past',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 9 DAY + INTERVAL 10 HOUR,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 9 DAY + INTERVAL 17 HOUR,
     25000, 10, NOW(), NOW()),
    ('demo-at-study-backend-1', 'demo-study-backend-open',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 3 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 3 DAY + INTERVAL 21 HOUR,
     80000, 8, NOW(), NOW()),
    ('demo-at-study-backend-2', 'demo-study-backend-open',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 10 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 10 DAY + INTERVAL 21 HOUR,
     80000, 8, NOW(), NOW()),
    ('demo-at-study-backend-3', 'demo-study-backend-open',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 17 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 17 DAY + INTERVAL 21 HOUR,
     80000, 8, NOW(), NOW()),
    ('demo-at-study-backend-4', 'demo-study-backend-open',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 24 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 24 DAY + INTERVAL 21 HOUR,
     80000, 8, NOW(), NOW()),
    ('demo-at-study-data-1', 'demo-study-data-soldout',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 5 DAY + INTERVAL 20 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 5 DAY + INTERVAL 22 HOUR,
     70000, 5, NOW(), NOW()),
    ('demo-at-study-data-2', 'demo-study-data-soldout',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 12 DAY + INTERVAL 20 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 12 DAY + INTERVAL 22 HOUR,
     70000, 5, NOW(), NOW()),
    ('demo-at-study-frontend-past-1', 'demo-study-frontend-past',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 21 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 21 DAY + INTERVAL 21 HOUR,
     60000, 6, NOW(), NOW()),
    ('demo-at-study-frontend-past-2', 'demo-study-frontend-past',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 14 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 14 DAY + INTERVAL 21 HOUR,
     60000, 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    price = VALUES(price),
    seats = VALUES(seats),
    updated_at = NOW();

INSERT INTO stocks (stock_id, service_key, quantity, created_at, updated_at)
VALUES
    ('demo-stock-oneday-backend-open', 'demo-at-oneday-backend-open', 12, NOW(), NOW()),
    ('demo-stock-oneday-frontend-soldout', 'demo-at-oneday-frontend-soldout', 0, NOW(), NOW()),
    ('demo-stock-oneday-backend-past', 'demo-at-oneday-backend-past', 0, NOW(), NOW()),
    ('demo-stock-study-backend-open', 'demo-study-backend-open', 6, NOW(), NOW()),
    ('demo-stock-study-data-soldout', 'demo-study-data-soldout', 0, NOW(), NOW()),
    ('demo-stock-study-frontend-past', 'demo-study-frontend-past', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    service_key = VALUES(service_key),
    quantity = VALUES(quantity),
    updated_at = NOW();

INSERT INTO schedules (schedule_id, status, request_content,
                       lesson_title, lesson_type, lesson_description, lesson_location, lesson_category_name, mentor_nickname,
                       option_name, option_minute, price,
                       lesson_id, mentee_id, lesson_mentor_id, option_id, available_time_id,
                       created_at, updated_at)
VALUES
    ('demo-schedule-mentoring-future-booked', 'APPROVED', '프로젝트 구조 리뷰를 받고 싶습니다.',
     'Spring 백엔드 커리어 1:1 멘토링', 'MENTORING', '이력서, 포트폴리오, 프로젝트 구조를 함께 점검하는 1:1 멘토링입니다.', '온라인', '백엔드 개발자', '김백엔드',
     '60분 집중 상담', 60, 50000,
     'demo-mentoring-backend-future', 'demo-mentee-001', 'demo-mentor-backend', 'demo-option-backend-60', NULL,
     NOW(), NOW()),
    ('demo-schedule-oneday-open-pending', 'PAYMENT_PENDING', NULL,
     'Spring Boot 원데이 클래스', 'ONEDAY', '하루 동안 API 설계, 예외 처리, 테스트 코드까지 완성합니다.', '서울 강남', '백엔드 개발자', '김백엔드',
     NULL, NULL, 35000,
     'demo-oneday-backend-open', 'demo-mentee-002', 'demo-mentor-backend', NULL, 'demo-at-oneday-backend-open',
     NOW(), NOW()),
    ('demo-schedule-study-open-approved', 'APPROVED', NULL,
     '백엔드 프로젝트 4주 스터디', 'STUDY', '4회차 동안 작은 서비스를 설계하고 배포까지 진행합니다.', '서울 홍대', '백엔드 개발자', '김백엔드',
     NULL, NULL, 80000,
     'demo-study-backend-open', 'demo-mentee-003', 'demo-mentor-backend', NULL, NULL,
     NOW(), NOW()),
    ('demo-schedule-past-completed', 'COMPLETED', NULL,
     '지난 프론트엔드 협업 스터디', 'STUDY', '이미 종료된 스터디 목록/상세 확인용 데이터입니다.', '서울 신촌', '프론트엔드 개발자', '박프론트',
     NULL, NULL, 60000,
     'demo-study-frontend-past', 'demo-mentee-001', 'demo-mentor-frontend', NULL, NULL,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 20 DAY, NOW())
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    request_content = VALUES(request_content),
    lesson_title = VALUES(lesson_title),
    lesson_type = VALUES(lesson_type),
    lesson_description = VALUES(lesson_description),
    lesson_location = VALUES(lesson_location),
    lesson_category_name = VALUES(lesson_category_name),
    mentor_nickname = VALUES(mentor_nickname),
    option_name = VALUES(option_name),
    option_minute = VALUES(option_minute),
    price = VALUES(price),
    lesson_id = VALUES(lesson_id),
    mentee_id = VALUES(mentee_id),
    lesson_mentor_id = VALUES(lesson_mentor_id),
    option_id = VALUES(option_id),
    available_time_id = VALUES(available_time_id),
    updated_at = NOW();

INSERT INTO schedule_times (schedule_time_id, schedule_id, start_time, end_time)
VALUES
    ('demo-st-mentoring-future-booked', 'demo-schedule-mentoring-future-booked',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 2 DAY + INTERVAL 10 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 2 DAY + INTERVAL 11 HOUR),
    ('demo-st-oneday-open-pending', 'demo-schedule-oneday-open-pending',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 9 DAY + INTERVAL 10 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 9 DAY + INTERVAL 17 HOUR),
    ('demo-st-study-open-1', 'demo-schedule-study-open-approved',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 3 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 3 DAY + INTERVAL 21 HOUR),
    ('demo-st-study-open-2', 'demo-schedule-study-open-approved',
     TIMESTAMP(CURRENT_DATE) + INTERVAL 10 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) + INTERVAL 10 DAY + INTERVAL 21 HOUR),
    ('demo-st-past-1', 'demo-schedule-past-completed',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 21 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 21 DAY + INTERVAL 21 HOUR),
    ('demo-st-past-2', 'demo-schedule-past-completed',
     TIMESTAMP(CURRENT_DATE) - INTERVAL 14 DAY + INTERVAL 19 HOUR,
     TIMESTAMP(CURRENT_DATE) - INTERVAL 14 DAY + INTERVAL 21 HOUR)
ON DUPLICATE KEY UPDATE
    schedule_id = VALUES(schedule_id),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time);
