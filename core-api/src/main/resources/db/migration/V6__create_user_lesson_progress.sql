CREATE TABLE user_lesson_progress (
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id    UUID        NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source       VARCHAR(10) NOT NULL,
    PRIMARY KEY (user_id, lesson_id)
);

CREATE INDEX ulp_user_id_idx ON user_lesson_progress (user_id);
