-- Login test users. Plain password for every account: 1234
INSERT INTO members (member_id, email, nickname, password, phone_num, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    CONCAT('testuser', LPAD(n, 3, '0')) AS member_id,
    CONCAT('test.user.', LPAD(n, 3, '0'), '@example.com') AS email,
    CONCAT('테스트유저', LPAD(n, 3, '0')) AS nickname,
    '$2a$10$0w6JQkiTx1FNKMIFGvuIV.ZfD.NY.1h23/cJsqzCoEhzUTXzX7RkW' AS password,
    CONCAT('010-9000-', LPAD(n, 4, '0')) AS phone_num,
    NOW() AS created_at,
    NOW() AS updated_at
FROM seq
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    nickname = VALUES(nickname),
    password = VALUES(password),
    phone_num = VALUES(phone_num),
    updated_at = NOW();

INSERT INTO profiles (member_id, image, introduce, category_id, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT
    CONCAT('testuser', LPAD(n, 3, '0')) AS member_id,
    NULL AS image,
    '' AS introduce,
    1000 AS category_id,
    NOW() AS created_at,
    NOW() AS updated_at
FROM seq
ON DUPLICATE KEY UPDATE
    image = VALUES(image),
    introduce = VALUES(introduce),
    category_id = VALUES(category_id),
    updated_at = NOW();
