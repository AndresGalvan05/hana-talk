CREATE TABLE IF NOT EXISTS event_worker.lesson_completions (
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, lesson_id)
);

CREATE TABLE IF NOT EXISTS event_worker.user_achievements (
    user_id UUID NOT NULL,
    achievement_code TEXT NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, achievement_code)
);
