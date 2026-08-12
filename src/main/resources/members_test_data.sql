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
