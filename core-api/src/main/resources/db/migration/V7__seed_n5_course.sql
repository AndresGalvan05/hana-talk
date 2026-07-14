-- Seed content: one JLPT N5 starter course with five lessons, so every
-- environment (local compose, CI, cluster) is demoable out of the box.
-- Fixed UUIDs so environments stay comparable and API examples are stable.

INSERT INTO courses (id, title, jlpt_level, description)
VALUES (
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'JLPT N5: First Steps in Japanese',
    'N5',
    'A beginner course covering greetings, self-introduction, numbers, and the basic sentence pattern AはBです. No prior Japanese required.'
);

INSERT INTO lessons (id, course_id, title, content, position) VALUES
(
    '0b4f9a12-2222-4a5e-9d3c-000000000001',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Greetings (あいさつ)',
    E'Basic Japanese greetings you will hear every day:\n\n'
    || E'- おはようございます (ohayou gozaimasu) — good morning (polite)\n'
    || E'- こんにちは (konnichiwa) — hello / good afternoon\n'
    || E'- こんばんは (konbanwa) — good evening\n'
    || E'- ありがとうございます (arigatou gozaimasu) — thank you (polite)\n'
    || E'- すみません (sumimasen) — excuse me / sorry\n'
    || E'- さようなら (sayounara) — goodbye\n\n'
    || E'Note: こんにちは and こんばんは end in the particle は, which is written "ha" but pronounced "wa".',
    1
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000002',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Self-introduction (じこしょうかい)',
    E'The standard self-introduction pattern:\n\n'
    || E'- はじめまして (hajimemashite) — nice to meet you (first meeting)\n'
    || E'- わたしは アナ です (watashi wa Ana desu) — I am Ana\n'
    || E'- どうぞよろしくおねがいします (douzo yoroshiku onegaishimasu) — please treat me well (closes the introduction)\n\n'
    || E'Pattern: わたしは [name] です。The particle は marks the topic, です is the polite copula ("to be").',
    2
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000003',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Numbers 1–10 (すうじ)',
    E'Numbers one to ten:\n\n'
    || E'1 いち (ichi), 2 に (ni), 3 さん (san), 4 よん/し (yon/shi), 5 ご (go),\n'
    || E'6 ろく (roku), 7 なな/しち (nana/shichi), 8 はち (hachi), 9 きゅう/く (kyuu/ku), 10 じゅう (juu).\n\n'
    || E'4, 7 and 9 each have two readings; よん, なな and きゅう are the safer defaults.\n'
    || E'11–99 are compounds: 11 = じゅういち (10+1), 20 = にじゅう (2×10), 21 = にじゅういち.',
    3
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000004',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'The AはBです pattern',
    E'The most fundamental Japanese sentence pattern: A は B です — "A is B."\n\n'
    || E'- わたしは がくせい です (watashi wa gakusei desu) — I am a student\n'
    || E'- これは ほん です (kore wa hon desu) — this is a book\n'
    || E'- たなかさんは せんせい です (Tanaka-san wa sensei desu) — Mr. Tanaka is a teacher\n\n'
    || E'Negative: では ありません (dewa arimasen) / じゃ ありません (ja arimasen, casual-polite).\n'
    || E'Question: add か — あなたは がくせい ですか (are you a student?).',
    4
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000005',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'This, that and that over there (これ・それ・あれ)',
    E'Japanese has a three-way demonstrative system based on distance from the speaker and listener:\n\n'
    || E'- これ (kore) — this (near the speaker)\n'
    || E'- それ (sore) — that (near the listener)\n'
    || E'- あれ (are) — that over there (far from both)\n'
    || E'- どれ (dore) — which one?\n\n'
    || E'Examples:\n'
    || E'- これは ペン です — this is a pen\n'
    || E'- それは なん ですか (sore wa nan desu ka) — what is that?\n'
    || E'- あれは がっこう です — that (over there) is a school',
    5
);
