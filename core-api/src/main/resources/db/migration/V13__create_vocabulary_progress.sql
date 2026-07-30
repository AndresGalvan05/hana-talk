CREATE TABLE user_vocabulary_progress (
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vocabulary_item_id UUID       NOT NULL REFERENCES vocabulary_items(id) ON DELETE CASCADE,
    next_review_at    TIMESTAMPTZ NOT NULL,
    interval_days     INT         NOT NULL,
    correct_streak    INT         NOT NULL,
    PRIMARY KEY (user_id, vocabulary_item_id)
);
