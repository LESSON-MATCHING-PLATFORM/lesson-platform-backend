use fillinv;

INSERT INTO members (member_id, email, nickname, password, phone_num, created_at, updated_at)
VALUES
    (1, 'member-001@example.com', '김개발', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-1234-5678', NOW(), NOW()),
    (2, 'member-002@example.com', '이백엔드', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-2345-6789', NOW(), NOW()),
    (3, 'member-003@example.com', '박인프라', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-3456-7890', NOW(), NOW()),
    (4, 'member-004@example.com', '최디비', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-4567-8901', NOW(), NOW()),
    (5, 'member-005@example.com', '정멘토', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-5678-9012', NOW(), NOW()),
    (6, 'member-006@example.com', '강시스템', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-6789-0123', NOW(), NOW()),
    (7, 'member-007@example.com', '윤에이피아이', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-7890-1234', NOW(), NOW()),
    (8, 'member-008@example.com', '임자바', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-8901-2345', NOW(), NOW()),
    (9, 'member-009@example.com', '한스프링', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-9012-3456', NOW(), NOW()),
    (10, 'member-010@example.com', '신클라우드', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQUb4vC', '010-0123-4567', NOW(), NOW());

select * from members;
select * from categories;
select * from profiles;