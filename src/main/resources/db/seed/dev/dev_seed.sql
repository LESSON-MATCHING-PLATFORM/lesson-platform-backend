INSERT INTO members (member_id, email, nickname, password, phone_num, created_at, updated_at)
VALUES
    ('mentor-001', 'mentor-001@example.com', '김백엔드', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-1001-0001', NOW(), NOW()),
    ('mentor-002', 'mentor-002@example.com', '이데이터', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-1001-0002', NOW(), NOW()),
    ('mentor-003', 'mentor-003@example.com', '박프론트', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-1001-0003', NOW(), NOW()),
    ('mentee-001', 'mentee-001@example.com', '최멘티', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-2001-0001', NOW(), NOW()),
    ('mentee-002', 'mentee-002@example.com', '정수강', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-2001-0002', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    nickname = VALUES(nickname),
    password = VALUES(password),
    phone_num = VALUES(phone_num),
    updated_at = NOW();

INSERT INTO profiles (member_id, image, introduce, category_id, created_at, updated_at)
VALUES
    ('mentor-001', NULL, 'Spring Boot와 백엔드 아키텍처를 함께 봅니다.', 302, NOW(), NOW()),
    ('mentor-002', NULL, '데이터 파이프라인과 AI 커리어 상담을 진행합니다.', 502, NOW(), NOW()),
    ('mentor-003', NULL, '프론트엔드 포트폴리오와 협업 방식을 다룹니다.', 303, NOW(), NOW()),
    ('mentee-001', NULL, '', 1000, NOW(), NOW()),
    ('mentee-002', NULL, '', 1000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    image = VALUES(image),
    introduce = VALUES(introduce),
    category_id = VALUES(category_id),
    updated_at = NOW();

INSERT INTO lessons (lesson_id, title, lesson_type, thumbnail_image, description, location, close_at,
                     mentor_id, category_id, price, seats, category_path, popularity_score, created_at, updated_at)
VALUES
    ('lesson-mentoring-001', 'Spring 백엔드 1:1 멘토링', 'MENTORING', 'thumb-mentoring-001.png',
     '실무 Spring Boot 설계와 포트폴리오를 함께 점검합니다.', '온라인', '2026-09-30 00:00:00',
     'mentor-001', 302, 50000, NULL, '3:302', 0.0, NOW(), NOW()),
    ('lesson-oneday-001', 'Spring Boot 원데이 클래스', 'ONEDAY', 'thumb-oneday-001.png',
     '하루 동안 REST API와 테스트 코드를 함께 완성합니다.', '서울 강남', '2026-09-15 00:00:00',
     'mentor-001', 302, 30000, 20, '3:302', 0.0, NOW(), NOW()),
    ('lesson-study-001', '백엔드 프로젝트 스터디', 'STUDY', 'thumb-study-001.png',
     '4회차로 진행하는 백엔드 프로젝트 스터디입니다.', '서울 성수', '2026-09-20 00:00:00',
     'mentor-002', 302, 20000, 8, '3:302', 0.0, NOW(), NOW()),
    ('lesson-data-001', '데이터 엔지니어 커리어 멘토링', 'MENTORING', 'thumb-data-001.png',
     '데이터 엔지니어 로드맵과 이직 준비를 상담합니다.', '온라인', '2026-09-25 00:00:00',
     'mentor-002', 502, 55000, NULL, '5:502', 0.0, NOW(), NOW()),
    ('lesson-frontend-001', '프론트엔드 포트폴리오 리뷰', 'MENTORING', 'thumb-frontend-001.png',
     '프로덕트 관점에서 포트폴리오와 UI 구현을 리뷰합니다.', '온라인', '2026-09-25 00:00:00',
     'mentor-003', 303, 45000, NULL, '3:303', 0.0, NOW(), NOW())
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
    ('option-mentoring-001-30', 'lesson-mentoring-001', '30분 상담', 30, 30000, NOW(), NOW()),
    ('option-mentoring-001-60', 'lesson-mentoring-001', '60분 집중 상담', 60, 50000, NOW(), NOW()),
    ('option-data-001-60', 'lesson-data-001', '60분 커리어 상담', 60, 55000, NOW(), NOW()),
    ('option-frontend-001-45', 'lesson-frontend-001', '45분 포트폴리오 리뷰', 45, 45000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    name = VALUES(name),
    minute = VALUES(minute),
    price = VALUES(price),
    updated_at = NOW();

INSERT INTO available_times (available_time_id, lesson_id, start_time, end_time, price, seats, created_at, updated_at)
VALUES
    ('available-time-oneday-001', 'lesson-oneday-001', '2026-08-22 10:00:00', '2026-08-22 17:00:00', 30000, 20, NOW(), NOW()),
    ('available-time-study-001', 'lesson-study-001', '2026-08-24 19:00:00', '2026-08-24 21:00:00', 20000, 8, NOW(), NOW()),
    ('available-time-study-002', 'lesson-study-001', '2026-08-31 19:00:00', '2026-08-31 21:00:00', 20000, 8, NOW(), NOW()),
    ('available-time-study-003', 'lesson-study-001', '2026-09-07 19:00:00', '2026-09-07 21:00:00', 20000, 8, NOW(), NOW()),
    ('available-time-study-004', 'lesson-study-001', '2026-09-14 19:00:00', '2026-09-14 21:00:00', 20000, 8, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    price = VALUES(price),
    seats = VALUES(seats),
    updated_at = NOW();

INSERT INTO stocks (stock_id, service_key, quantity, created_at, updated_at)
VALUES
    ('stock-oneday-001', 'available-time-oneday-001', 20, NOW(), NOW()),
    ('stock-study-001', 'lesson-study-001', 8, NOW(), NOW())
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
    ('schedule-upcoming-mentoring-001', 'APPROVED', '포트폴리오 중심으로 상담받고 싶습니다.',
     'Spring 백엔드 1:1 멘토링', 'MENTORING', '실무 Spring Boot 설계와 포트폴리오를 함께 점검합니다.', '온라인', '백엔드 개발자', '김백엔드',
     '60분 집중 상담', 60, 50000,
     'lesson-mentoring-001', 'mentee-001', 'mentor-001', 'option-mentoring-001-60', NULL,
     NOW(), NOW()),
    ('schedule-upcoming-oneday-001', 'PAYMENT_PENDING', NULL,
     'Spring Boot 원데이 클래스', 'ONEDAY', '하루 동안 REST API와 테스트 코드를 함께 완성합니다.', '서울 강남', '백엔드 개발자', '김백엔드',
     NULL, NULL, 30000,
     'lesson-oneday-001', 'mentee-002', 'mentor-001', NULL, 'available-time-oneday-001',
     NOW(), NOW()),
    ('schedule-past-mentoring-001', 'COMPLETED', NULL,
     '데이터 엔지니어 커리어 멘토링', 'MENTORING', '데이터 엔지니어 로드맵과 이직 준비를 상담합니다.', '온라인', '데이터 엔지니어', '이데이터',
     '60분 커리어 상담', 60, 55000,
     'lesson-data-001', 'mentee-001', 'mentor-002', 'option-data-001-60', NULL,
     NOW(), NOW())
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
    ('schedule-time-upcoming-mentoring-001', 'schedule-upcoming-mentoring-001', '2026-08-21 10:00:00', '2026-08-21 11:00:00'),
    ('schedule-time-upcoming-oneday-001', 'schedule-upcoming-oneday-001', '2026-08-22 10:00:00', '2026-08-22 17:00:00'),
    ('schedule-time-past-mentoring-001', 'schedule-past-mentoring-001', '2026-08-01 14:00:00', '2026-08-01 15:00:00')
ON DUPLICATE KEY UPDATE
    schedule_id = VALUES(schedule_id),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time);
