-- Placeholder exercise content for the seeded N5 course, so the grading flow
-- is demoable before ai-exercise-svc exists. Fixed UUIDs, same convention as
-- the V7 course/lesson seed.

INSERT INTO exercises (id, lesson_id, type, prompt, options_json, correct_answer) VALUES
(
    '0b4f9a12-3333-4a5e-9d3c-000000000001',
    '0b4f9a12-2222-4a5e-9d3c-000000000001',
    'MCQ',
    'What does "ありがとうございます" mean?',
    '["Good morning","Thank you","Goodbye","Excuse me"]',
    'Thank you'
),
(
    '0b4f9a12-3333-4a5e-9d3c-000000000002',
    '0b4f9a12-2222-4a5e-9d3c-000000000001',
    'FILL_IN_BLANK',
    'What is the romaji for "すみません"?',
    NULL,
    'sumimasen'
),
(
    '0b4f9a12-3333-4a5e-9d3c-000000000003',
    '0b4f9a12-2222-4a5e-9d3c-000000000004',
    'MCQ',
    'Which particle marks the topic in "わたしは がくせい です"?',
    '["は","です","が","を"]',
    'は'
),
(
    '0b4f9a12-3333-4a5e-9d3c-000000000004',
    '0b4f9a12-2222-4a5e-9d3c-000000000004',
    'FILL_IN_BLANK',
    'Complete: わたしは がくせい ___ (the polite copula meaning "is/am")',
    NULL,
    'です'
);
