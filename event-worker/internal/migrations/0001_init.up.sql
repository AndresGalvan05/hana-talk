CREATE SCHEMA IF NOT EXISTS event_worker;

CREATE TABLE IF NOT EXISTS event_worker.users (
    user_id UUID PRIMARY KEY,
    username TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS event_worker.daily_activity (
    user_id UUID NOT NULL,
    activity_date DATE NOT NULL,
    PRIMARY KEY (user_id, activity_date)
);

CREATE TABLE IF NOT EXISTS event_worker.user_streaks (
    user_id UUID PRIMARY KEY,
    current_streak INT NOT NULL DEFAULT 0,
    last_active_date DATE
);
