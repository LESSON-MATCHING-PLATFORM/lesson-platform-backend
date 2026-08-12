SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO categories (category_id, name, parent_category_id, category_path)
VALUES
    (1000, '미지정', NULL, '1000'),
    (3, '개발', NULL, '3'),
    (302, '백엔드 개발자', 3, '3:302')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    parent_category_id = VALUES(parent_category_id),
    category_path = VALUES(category_path);

INSERT INTO members (member_id, nickname, phone_num, email, password, created_at, updated_at)
VALUES
    ('mentor-001', '김백엔드', '010-1001-0001', 'mentor-001@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', NOW(), NOW()),
    ('mentee-001', '최멘티', '010-2001-0001', 'mentee-001@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    phone_num = VALUES(phone_num),
    email = VALUES(email),
    password = VALUES(password),
    updated_at = NOW();

INSERT INTO profiles (member_id, image, introduce, category_id, created_at, updated_at)
VALUES
    ('mentor-001', NULL, '백엔드 개발 멘토입니다.', 302, NOW(), NOW()),
    ('mentee-001', NULL, '', 1000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    image = VALUES(image),
    introduce = VALUES(introduce),
    category_id = VALUES(category_id),
    updated_at = NOW();

INSERT INTO lessons (lesson_id, title, lesson_type, thumbnail_image, description, location, close_at,
                     mentor_id, category_id, price, seats, category_path, popularity_score, created_at, updated_at)
VALUES
    ('lesson-mentoring-001', '예비 백엔드 개발자를 위한 커피챗', 'MENTORING', 'java.png',
     '백엔드 포트폴리오 첨삭 및 상담', '온라인', '2026-09-30 00:00:00',
     'mentor-001', 302, 30000, NULL, '3:302', 0.0, NOW(), NOW())
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
    ('option-mentoring-001', 'lesson-mentoring-001', '기본 80분', 80, 30000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    name = VALUES(name),
    minute = VALUES(minute),
    price = VALUES(price),
    updated_at = NOW();

INSERT INTO available_times (available_time_id, lesson_id, price, seats, start_time, end_time, created_at, updated_at)
VALUES
    ('available-time-mentoring-001', 'lesson-mentoring-001', 30000, NULL, '2026-08-20 09:00:00', '2026-08-20 22:00:00', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    lesson_id = VALUES(lesson_id),
    price = VALUES(price),
    seats = VALUES(seats),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    updated_at = NOW();

INSERT INTO schedules (schedule_id, lesson_id, option_id, available_time_id,
                       mentee_id, lesson_mentor_id, mentor_nickname, status,
                       lesson_title, lesson_type, lesson_description, lesson_location, lesson_category_name,
                       option_name, option_minute, price, created_at, updated_at)
VALUES
    ('schedule-upcoming-001', 'lesson-mentoring-001', 'option-mentoring-001', 'available-time-mentoring-001',
     'mentee-001', 'mentor-001', '김백엔드', 'APPROVED',
     '예비 백엔드 개발자를 위한 커피챗', 'MENTORING', '백엔드 포트폴리오 첨삭 및 상담', '온라인', '백엔드 개발자',
     '기본 80분', 80, 30000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    lesson_title = VALUES(lesson_title),
    lesson_type = VALUES(lesson_type),
    lesson_description = VALUES(lesson_description),
    lesson_location = VALUES(lesson_location),
    lesson_category_name = VALUES(lesson_category_name),
    mentor_nickname = VALUES(mentor_nickname),
    option_name = VALUES(option_name),
    option_minute = VALUES(option_minute),
    price = VALUES(price),
    updated_at = NOW();

INSERT INTO schedule_times (schedule_time_id, schedule_id, start_time, end_time)
VALUES
    ('schedule-time-upcoming-001', 'schedule-upcoming-001', '2026-08-20 10:00:00', '2026-08-20 11:20:00')
ON DUPLICATE KEY UPDATE
    schedule_id = VALUES(schedule_id),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time);

SET FOREIGN_KEY_CHECKS = 1;
