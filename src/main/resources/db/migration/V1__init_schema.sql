CREATE TABLE categories (
    category_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_category_id BIGINT NULL,
    category_path VARCHAR(255) NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_category_id)
        REFERENCES categories (category_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE members (
    member_id VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    phone_num VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE profiles (
    member_id VARCHAR(255) NOT NULL,
    image VARCHAR(255) NULL,
    introduce VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_profiles_member
        FOREIGN KEY (member_id)
        REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lessons (
    lesson_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    lesson_type VARCHAR(255) NOT NULL,
    thumbnail_image VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    location VARCHAR(255) NULL,
    close_at DATETIME(6) NULL,
    mentor_id VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    price INT NOT NULL,
    seats INT NULL,
    category_path VARCHAR(255) NOT NULL,
    popularity_score DOUBLE NOT NULL DEFAULT 0.0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE available_times (
    available_time_id VARCHAR(255) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    price INT NOT NULL,
    seats INT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (available_time_id),
    CONSTRAINT fk_available_times_lesson
        FOREIGN KEY (lesson_id)
        REFERENCES lessons (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE options (
    option_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    minute INT NOT NULL,
    price INT NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (option_id),
    CONSTRAINT fk_options_lesson
        FOREIGN KEY (lesson_id)
        REFERENCES lessons (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lesson_temp (
    id VARCHAR(255) NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    score DOUBLE NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedules (
    schedule_id VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    request_content VARCHAR(255) NULL,
    lesson_title VARCHAR(255) NOT NULL,
    lesson_type VARCHAR(255) NOT NULL,
    lesson_description VARCHAR(255) NOT NULL,
    lesson_location VARCHAR(255) NOT NULL,
    lesson_category_name VARCHAR(255) NOT NULL,
    mentor_nickname VARCHAR(255) NOT NULL,
    option_name VARCHAR(255) NULL,
    option_minute INT NULL,
    price INT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    mentee_id VARCHAR(255) NOT NULL,
    lesson_mentor_id VARCHAR(255) NOT NULL,
    option_id VARCHAR(255) NULL,
    available_time_id VARCHAR(255) NULL,
    cancel_reason VARCHAR(255) NULL,
    canceled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedule_times (
    schedule_time_id VARCHAR(255) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    schedule_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (schedule_time_id),
    CONSTRAINT fk_schedule_times_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES schedules (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment (
    id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    order_name VARCHAR(255) NOT NULL,
    payment_status VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    payment_key VARCHAR(255) NULL,
    payment_method VARCHAR(255) NULL,
    psp_raw VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_history (
    payment_history_id VARCHAR(255) NOT NULL,
    payment_id VARCHAR(255) NULL,
    previous_status VARCHAR(255) NULL,
    new_status VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (payment_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refunds (
    refund_id VARCHAR(255) NOT NULL,
    payment_id VARCHAR(255) NULL,
    payment_key VARCHAR(255) NULL,
    order_id VARCHAR(255) NULL,
    refund_status TINYINT NULL,
    refund_amount INT NULL,
    refund_reason VARCHAR(255) NULL,
    transaction_key VARCHAR(255) NULL,
    refunded_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    psp_raw VARCHAR(255) NULL,
    last_attempted_at DATETIME(6) NULL,
    next_attemp_at DATETIME(6) NULL,
    retry_count INT NULL,
    PRIMARY KEY (refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refund_history (
    refund_history_id VARCHAR(255) NOT NULL,
    payment_key VARCHAR(255) NULL,
    previous_status TINYINT NULL,
    new_status TINYINT NULL,
    reason VARCHAR(255) NULL,
    PRIMARY KEY (refund_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_outbox (
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(255) NOT NULL,
    retry_count INT NOT NULL,
    published_at DATETIME(6) NULL,
    processing_started_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reviews (
    review_id VARCHAR(255) NOT NULL,
    score INT NOT NULL,
    content VARCHAR(255) NOT NULL,
    writer_id VARCHAR(255) NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    schedule_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (review_id),
    CONSTRAINT uk_reviews_schedule UNIQUE (schedule_id),
    CONSTRAINT fk_reviews_writer
        FOREIGN KEY (writer_id)
        REFERENCES members (member_id),
    CONSTRAINT fk_reviews_schedule
        FOREIGN KEY (schedule_id)
        REFERENCES schedules (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stocks (
    stock_id VARCHAR(255) NOT NULL,
    service_key VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
