-- Replaces the ten shallow N5 lessons with five chapter-depth ones,
-- structured (vocabulary, grammar points, dialogue, culture note) instead
-- of flat text. Content is original writing referencing Genki I's topic
-- sequence and scope only -- never transcribed from the copyrighted
-- textbook (reference-material/, gitignored, carries an explicit
-- no-scanning-and-uploading notice).

-- Vocabulary becomes a real table, not JSON embedded in lesson content --
-- a future spaced-repetition feature needs per-item, per-user review
-- tracking, which is only queryable against real rows.
CREATE TABLE vocabulary_items (
    id       UUID    PRIMARY KEY,
    lesson_id UUID   NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    japanese TEXT    NOT NULL,
    reading  TEXT    NOT NULL,
    meaning  TEXT    NOT NULL,
    position INTEGER NOT NULL
);

CREATE INDEX vocabulary_items_lesson_id_idx ON vocabulary_items (lesson_id);

-- Lesson content becomes structured JSON (grammar points, dialogue, culture
-- note) instead of flat text -- same string-column-holding-JSON pattern
-- exercises.options_json already uses.
ALTER TABLE lessons RENAME COLUMN content TO content_json;

-- Replace the ten existing lessons outright, not an edit. FK cascades
-- (exercises -> lessons, exercise_attempts -> exercises, user_lesson_progress
-- -> lessons) clean up V9's seeded exercises and any progress rows
-- automatically -- only test/demo accounts have any progress at this stage,
-- no real end users to lose data.
DELETE FROM lessons WHERE course_id = '0b4f9a12-1111-4a5e-9d3c-000000000001';

INSERT INTO lessons (id, course_id, title, content_json, position) VALUES
(
    '0b4f9a12-2222-4a5e-9d3c-000000000001',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'New Friends (あたらしい ともだち)',
    '{"grammarPoints": [{"title": "わたしは [name] です — self-introduction", "explanation": "The basic self-introduction pattern: topic marker は plus the polite copula です. Once a topic is established, it can be dropped from later sentences in the same conversation.", "examples": [{"japanese": "わたしは ハナ です。", "romaji": "watashi wa Hana desu.", "english": "I am Hana."}, {"japanese": "がくせい です。", "romaji": "gakusei desu.", "english": "I am a student. (topic omitted, understood from context)"}]}, {"title": "Nationality and occupation with ~じん", "explanation": "Attach ~じん (person) to a country name to say someone''s nationality. Occupations are separate nouns that slot into the same X は Y です pattern.", "examples": [{"japanese": "にほんじん です。", "romaji": "nihon-jin desu.", "english": "I am Japanese."}, {"japanese": "かいしゃいん です。", "romaji": "kaishain desu.", "english": "I am a company employee."}]}, {"title": "Negative: では ありません / じゃ ありません", "explanation": "To negate です, replace it with では ありません (formal) or じゃ ありません (casual-polite). Both remove です first, then add the negative ending.", "examples": [{"japanese": "がくせい では ありません。", "romaji": "gakusei dewa arimasen.", "english": "I am not a student."}, {"japanese": "せんせい じゃ ありません。", "romaji": "sensei ja arimasen.", "english": "I am not a teacher. (casual-polite)"}]}, {"title": "Yes/no questions with か", "explanation": "Add か to the end of a statement to turn it into a yes/no question. Word order never changes, and no question mark is needed.", "examples": [{"japanese": "がくせい です か。", "romaji": "gakusei desu ka.", "english": "Are you a student?"}, {"japanese": "はい、がくせい です。", "romaji": "hai, gakusei desu.", "english": "Yes, I am a student."}]}, {"title": "Noun1 の Noun2", "explanation": "の links two nouns, usually meaning the first modifies or possesses the second — similar to ''s or \"of\" in English, but always in this fixed order.", "examples": [{"japanese": "にほんごの せんせい です。", "romaji": "nihongo no sensei desu.", "english": "I am a Japanese-language teacher."}, {"japanese": "ハナさんの ほん です。", "romaji": "Hana-san no hon desu.", "english": "It is Hana''s book."}]}, {"title": "も — also/too", "explanation": "も replaces は when the same statement applies to another topic as well.", "examples": [{"japanese": "ハナさんは がくせい です。ケンさんも がくせい です。", "romaji": "Hana-san wa gakusei desu. Ken-san mo gakusei desu.", "english": "Hana is a student. Ken is also a student."}]}, {"title": "はじめまして and どうぞよろしくおねがいします", "explanation": "Two fixed social formulas that bracket a first-time introduction: はじめまして opens it (only ever used at a first meeting), and どうぞよろしくおねがいします closes it — it has no direct English translation, roughly \"please treat me well going forward.\"", "examples": [{"japanese": "はじめまして。わたしは ハナ です。どうぞよろしくおねがいします。", "romaji": "hajimemashite. watashi wa Hana desu. douzo yoroshiku onegaishimasu.", "english": "Nice to meet you. I am Hana. Please treat me well."}]}], "dialogue": {"title": "しんにゅうせいの ひ (New Student Day)", "lines": [{"speaker": "ハナ", "japanese": "はじめまして。わたしは ハナ です。にほんじん です。", "english": "Nice to meet you. I am Hana. I am Japanese."}, {"speaker": "ケン", "japanese": "はじめまして。ケン です。アメリカじん です。がくせい です。", "english": "Nice to meet you. I am Ken. I am American. I am a student."}, {"speaker": "ハナ", "japanese": "わたしも がくせい です。どうぞよろしくおねがいします。", "english": "I am also a student. Please treat me well."}, {"speaker": "ケン", "japanese": "どうぞよろしくおねがいします。", "english": "Please treat me well, too."}]}, "cultureNote": {"title": "Names and ~さん", "body": "In Japanese, ~さん is attached after almost anyone''s name as a neutral, polite title — closer to a mandatory politeness marker than an equivalent of Mr./Ms. It is never attached to your own name; doing so sounds like referring to yourself in the third person with excessive self-regard. Family name is used far more often than given name outside of close friendships, unlike the given-name-first norm common in English."}}',
    1
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000002',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Shopping (かいもの)',
    '{"grammarPoints": [{"title": "これ／それ／あれ／どれ", "explanation": "A three-way distance system for standalone objects: これ (near speaker), それ (near listener), あれ (far from both), どれ (which one, question form).", "examples": [{"japanese": "これは ほん です。", "romaji": "kore wa hon desu.", "english": "This is a book."}, {"japanese": "それは なん ですか。", "romaji": "sore wa nan desu ka.", "english": "What is that (near you)?"}]}, {"title": "この／その／あの／どの + Noun", "explanation": "The adjective forms of the demonstrative system above — used directly before a noun instead of standing alone.", "examples": [{"japanese": "この ペンは いくら ですか。", "romaji": "kono pen wa ikura desu ka.", "english": "How much is this pen?"}, {"japanese": "どの とけいが すき ですか。", "romaji": "dono tokei ga suki desu ka.", "english": "Which watch do you like?"}]}, {"title": "ここ／そこ／あそこ／どこ", "explanation": "The same distance system applied to places: here, there, over there, where.", "examples": [{"japanese": "かさは そこに あります。", "romaji": "kasa wa soko ni arimasu.", "english": "The umbrella is there."}, {"japanese": "トイレは どこ ですか。", "romaji": "toire wa doko desu ka.", "english": "Where is the bathroom?"}]}, {"title": "だれの Noun — whose", "explanation": "だれ (who) plus の asks who something belongs to, following the same Noun1のNoun2 possession pattern from the previous lesson.", "examples": [{"japanese": "これは だれの ほん ですか。", "romaji": "kore wa dare no hon desu ka.", "english": "Whose book is this?"}, {"japanese": "ハナさんの ほん です。", "romaji": "Hana-san no hon desu.", "english": "It is Hana''s book."}]}, {"title": "Noun じゃないです — casual negative", "explanation": "A casual-polite alternative to では ありません, used constantly in everyday speech. Both are correct; じゃないです is simply less formal.", "examples": [{"japanese": "これは わたしの かさ じゃないです。", "romaji": "kore wa watashi no kasa janai desu.", "english": "This is not my umbrella."}]}, {"title": "Asking and stating prices", "explanation": "いくら ですか asks a price; the answer states a number directly followed by the counter えん, with no particle needed between them.", "examples": [{"japanese": "この ほんは いくら ですか。", "romaji": "kono hon wa ikura desu ka.", "english": "How much is this book?"}, {"japanese": "せんえん です。", "romaji": "sen-en desu.", "english": "It is 1,000 yen."}]}, {"title": "~ね — seeking agreement", "explanation": "Adding ね to the end of a sentence invites the listener to agree, similar to \"right?\" or \"isn''t it?\" in English — softer and more conversational than a flat statement.", "examples": [{"japanese": "たかい です ね。", "romaji": "takai desu ne.", "english": "That is expensive, isn''t it."}]}], "dialogue": {"title": "ほんやで (At the Bookstore)", "lines": [{"speaker": "きゃく", "japanese": "すみません、この ほんは いくら ですか。", "english": "Excuse me, how much is this book?"}, {"speaker": "てんいん", "japanese": "それは にせんえん です。", "english": "That is 2,000 yen."}, {"speaker": "きゃく", "japanese": "そうですか。たかい です ね。あの とけいは いくら ですか。", "english": "I see. That is expensive, isn''t it. How much is that watch over there?"}, {"speaker": "てんいん", "japanese": "あれは せんえん です。", "english": "That one is 1,000 yen."}, {"speaker": "きゃく", "japanese": "じゃあ、あれを ください。", "english": "Then, that one please."}]}, "cultureNote": {"title": "Cash and customer service", "body": "Japan remains a heavily cash-based society for everyday shopping, though card and mobile payment acceptance has grown substantially. It is common for a tray (トレー) to sit beside the register specifically for exchanging cash and receipts without the customer and clerk touching hands directly — a habit that predates and outlasted any specific health concern, simply a long-standing norm."}}',
    2
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000003',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Making a Date (デートの やくそく)',
    '{"grammarPoints": [{"title": "ます-form verbs", "explanation": "Polite Japanese verbs end in ます in the present/future affirmative. This is the first verb form taught because its conjugation pattern is completely regular, unlike the plain dictionary form.", "examples": [{"japanese": "まいにち べんきょうします。", "romaji": "mainichi benkyou shimasu.", "english": "I study every day."}]}, {"title": "Verb types and dictionary form", "explanation": "Japanese verbs fall into a small number of conjugation groups, but every verb has a single ます-form regardless of group — the group only matters once you move beyond polite speech to plain form, covered in a later lesson.", "examples": [{"japanese": "たべます／のみます／みます", "romaji": "tabemasu / nomimasu / mimasu", "english": "eat / drink / watch (three different verbs, same ます pattern)"}]}, {"title": "を／に／で — object, destination/time, place of action", "explanation": "を marks the direct object; に marks a destination or a specific point in time; で marks the place where an action happens.", "examples": [{"japanese": "ごはんを たべます。", "romaji": "gohan wo tabemasu.", "english": "I eat a meal."}, {"japanese": "がっこうに いきます。", "romaji": "gakkou ni ikimasu.", "english": "I go to school."}, {"japanese": "こうえんで あいます。", "romaji": "kouen de aimasu.", "english": "I meet (someone) at the park."}]}, {"title": "Time references and に", "explanation": "A specific clock time or day takes に; general relative time words like today or tomorrow do not.", "examples": [{"japanese": "しちじに おきます。", "romaji": "shichiji ni okimasu.", "english": "I get up at 7 o''clock."}, {"japanese": "あした あいましょう。", "romaji": "ashita aimashou.", "english": "Let''s meet tomorrow. (no に — relative time word)"}]}, {"title": "~ませんか — invitations", "explanation": "Adding ませんか to a verb stem turns a statement into a soft invitation, literally \"won''t you...?\" — more natural and less presumptuous than a direct suggestion.", "examples": [{"japanese": "いっしょに えいがを みませんか。", "romaji": "issho ni eiga wo mimasenka.", "english": "Won''t you watch a movie together with me?"}]}, {"title": "Frequency adverbs", "explanation": "A small set of adverbs describing how often something happens, ranging from always to never — the negative-leaning ones (あまり, ぜんぜん) require a negative verb ending.", "examples": [{"japanese": "いつも べんきょうします。", "romaji": "itsumo benkyou shimasu.", "english": "I always study."}, {"japanese": "ぜんぜん べんきょうしません。", "romaji": "zenzen benkyou shimasen.", "english": "I do not study at all."}]}, {"title": "Word order and particles", "explanation": "Japanese is a subject-object-verb language where particles, not position, mark each word''s grammatical role — this makes word order far more flexible than in English, as long as the verb stays last.", "examples": [{"japanese": "わたしは がっこうで ともだちに あいます。", "romaji": "watashi wa gakkou de tomodachi ni aimasu.", "english": "I meet a friend at school."}]}, {"title": "Topic は revisited", "explanation": "は marks what the sentence is about, which is not always the grammatical subject — this becomes more noticeable once sentences have multiple particles competing for attention.", "examples": [{"japanese": "しゅうまつは いそがしい です。", "romaji": "shuumatsu wa isogashii desu.", "english": "As for the weekend, it is busy."}]}], "dialogue": {"title": "しゅうまつの やくそく (Weekend Plans)", "lines": [{"speaker": "ハナ", "japanese": "しゅうまつ、いっしょに えいがを みませんか。", "english": "Won''t you watch a movie together this weekend?"}, {"speaker": "ケン", "japanese": "いいですね。なんじに あいましょうか。", "english": "Sounds good. What time should we meet?"}, {"speaker": "ハナ", "japanese": "どようびの ごご さんじに あいましょう。", "english": "Let''s meet at 3pm on Saturday."}, {"speaker": "ケン", "japanese": "わかりました。こうえんで あいましょう。", "english": "Understood. Let''s meet at the park."}]}, "cultureNote": {"title": "Indirect invitations", "body": "~ませんか is preferred over a flat command form precisely because it leaves room for the other person to decline gracefully — directness is often read as pushy in casual social invitations. This pattern of softening requests and invitations through grammar, rather than tone alone, recurs throughout polite Japanese."}}',
    3
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000004',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'The First Date (はじめての デート)',
    '{"grammarPoints": [{"title": "あります／います revisited", "explanation": "あります is for inanimate things, います is for animate things (people, animals) — the existence pattern is [place] に [thing] が あります／います.", "examples": [{"japanese": "こうえんに ひとが たくさん います。", "romaji": "kouen ni hito ga takusan imasu.", "english": "There are a lot of people in the park."}]}, {"title": "Describing where things are", "explanation": "Location words combine with の plus a noun to describe relative position — うえ (on top), した (under), まえ (in front), うしろ (behind), なか (inside), そば (near).", "examples": [{"japanese": "えきの まえで あいました。", "romaji": "eki no mae de aimashita.", "english": "We met in front of the station."}]}, {"title": "Past tense of です", "explanation": "です becomes でした in the past affirmative; the negative past is じゃありませんでした (casual) or ではありませんでした (formal).", "examples": [{"japanese": "たのしい ひ でした。", "romaji": "tanoshii hi deshita.", "english": "It was a fun day."}]}, {"title": "Past tense of verbs", "explanation": "ます becomes ました in the past affirmative, and ません becomes ませんでした in the past negative — completely regular for every ます-form verb.", "examples": [{"japanese": "こうえんで あいました。", "romaji": "kouen de aimashita.", "english": "We met at the park."}, {"japanese": "あめが ふりましたが、でかけませんでした。", "romaji": "ame ga furimashita ga, dekakemasendeshita.", "english": "It rained, but we did not go out."}]}, {"title": "も in a new context", "explanation": "も extends naturally to past-tense statements the same way it does in the present: adding another topic for which the same thing was true.", "examples": [{"japanese": "ハナさんも きました。", "romaji": "Hana-san mo kimashita.", "english": "Hana also came."}]}, {"title": "Duration with 時間", "explanation": "時間 attaches to a number to express a span of time (as opposed to a specific clock time, which uses じ alone) — さんじかん means \"three hours,\" not \"3 o''clock.\"", "examples": [{"japanese": "さんじかん あるきました。", "romaji": "sanjikan arukimashita.", "english": "We walked for three hours."}]}, {"title": "たくさん — a lot", "explanation": "A quantity adverb placed before the verb or noun it modifies, used for both countable and uncountable amounts.", "examples": [{"japanese": "しゃしんを たくさん とりました。", "romaji": "shashin wo takusan torimashita.", "english": "We took a lot of photos."}]}, {"title": "と — and / with", "explanation": "と has two related uses: joining an exhaustive list of nouns (\"A and B,\" nothing else), and marking a companion (\"together with someone\").", "examples": [{"japanese": "パンと ぎゅうにゅうを かいました。", "romaji": "pan to gyuunyuu wo kaimashita.", "english": "We bought bread and milk."}, {"japanese": "ともだちと でかけました。", "romaji": "tomodachi to dekakemashita.", "english": "I went out with a friend."}]}], "dialogue": {"title": "しゅうまつは どうでしたか (How Was Your Weekend?)", "lines": [{"speaker": "ケン", "japanese": "しゅうまつは どうでしたか。", "english": "How was your weekend?"}, {"speaker": "ハナ", "japanese": "こうえんの まえで ハルさんに あいました。いっしょに さんじかん あるきました。", "english": "I met Haru in front of the park. We walked together for three hours."}, {"speaker": "ケン", "japanese": "たのしそうですね。", "english": "That sounds fun."}, {"speaker": "ハナ", "japanese": "はい、しゃしんを たくさん とりました。ハルさんも たのしかったと いいました。", "english": "Yes, we took a lot of photos. Haru also said it was fun."}]}, "cultureNote": {"title": "Casual outings in Japan", "body": "Parks, aquariums, and shopping districts (商店街) are common, low-cost destinations for a first outing between new friends — walking together for an extended time, as in the dialogue above, is a normal and unremarkable way to spend an afternoon, not necessarily read as a formal date the way it might be in other contexts."}}',
    4
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000005',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Trip to Okinawa (おきなわ りょこう)',
    '{"grammarPoints": [{"title": "い-adjectives", "explanation": "Adjectives ending in い conjugate on their own without needing です for their negative or noun-modifying forms — たかい becomes たかく ない in the negative.", "examples": [{"japanese": "この みせは たかい です。", "romaji": "kono mise wa takai desu.", "english": "This shop is expensive."}, {"japanese": "やすく ないです。", "romaji": "yasuku naidesu.", "english": "It is not cheap."}]}, {"title": "な-adjectives", "explanation": "な-adjectives behave more like nouns: な is inserted only when directly modifying a noun, and です attaches without any change for a predicate.", "examples": [{"japanese": "しずかな うみ です。", "romaji": "shizuka na umi desu.", "english": "It is a quiet ocean."}, {"japanese": "うみは しずか です。", "romaji": "umi wa shizuka desu.", "english": "The ocean is quiet. (no な before です)"}]}, {"title": "Adjective past tense", "explanation": "い-adjectives replace the final い with かった for the past; な-adjectives simply use でした, the same past tense です already has.", "examples": [{"japanese": "たのしかった です。", "romaji": "tanoshikatta desu.", "english": "It was fun."}, {"japanese": "きれい でした。", "romaji": "kirei deshita.", "english": "It was pretty."}]}, {"title": "Adjectives modifying nouns", "explanation": "Both adjective types can sit directly in front of a noun to describe it, without needing です in between.", "examples": [{"japanese": "きれいな うみ", "romaji": "kirei na umi", "english": "a pretty ocean"}, {"japanese": "たかい ホテル", "romaji": "takai hoteru", "english": "an expensive hotel"}]}, {"title": "すき（な）／きらい（な） + が", "explanation": "Likes and dislikes behave grammatically like な-adjectives, and the thing liked or disliked takes が rather than を.", "examples": [{"japanese": "うみが すき です。", "romaji": "umi ga suki desu.", "english": "I like the ocean."}, {"japanese": "あついのが きらい です。", "romaji": "atsui no ga kirai desu.", "english": "I dislike hot weather."}]}, {"title": "~ましょう／~ましょうか", "explanation": "~ましょう proposes doing something together (\"let''s...\"); ~ましょうか asks whether to do it together, slightly softer and more tentative.", "examples": [{"japanese": "うみに いきましょう。", "romaji": "umi ni ikimashou.", "english": "Let''s go to the ocean."}, {"japanese": "なにを たべましょうか。", "romaji": "nani wo tabemashouka.", "english": "What shall we eat?"}]}, {"title": "Counting with つ", "explanation": "A small generic counter (ひとつ、ふたつ、みっつ...) usable for most objects when a more specific counter is not known — the safest default for counting things in early study.", "examples": [{"japanese": "おみやげを みっつ かいました。", "romaji": "omiyage wo mittsu kaimashita.", "english": "I bought three souvenirs."}]}], "dialogue": {"title": "おきなわりょこうの けいかく (Planning the Okinawa Trip)", "lines": [{"speaker": "ハナ", "japanese": "おきなわの うみは ほんとうに きれい ですよ。", "english": "Okinawa''s ocean is really pretty."}, {"speaker": "ケン", "japanese": "いいですね。りょうりも おいしい ですか。", "english": "Sounds great. Is the food good too?"}, {"speaker": "ハナ", "japanese": "はい、とても おいしい です。あついのが きらいじゃなければ、なつが いい です。", "english": "Yes, it is very delicious. If you do not dislike heat, summer is good."}, {"speaker": "ケン", "japanese": "だいじょうぶ です。じゃあ、なつに いきましょう。", "english": "I am fine with that. Then, let''s go in summer."}]}, "cultureNote": {"title": "Okinawa", "body": "Okinawa is Japan''s southernmost prefecture, a chain of subtropical islands with a distinct history as the formerly independent Ryukyu Kingdom before its 19th-century annexation. Its cuisine, music, and dialect differ noticeably from mainland Japan, and it remains one of the most popular domestic beach-vacation destinations for Japanese travelers."}}',
    5
);

INSERT INTO vocabulary_items (id, lesson_id, japanese, reading, meaning, position) VALUES
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'わたし', 'watashi', 'I, me', 0),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'あなた', 'anata', 'you', 1),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', '〜さん', '~san', 'polite title (Mr./Ms., attached after a name)', 2),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'がくせい', 'gakusei', 'student', 3),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'せんせい', 'sensei', 'teacher', 4),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'かいしゃいん', 'kaishain', 'company employee', 5),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'いしゃ', 'isha', 'doctor', 6),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'アメリカ', 'amerika', 'America', 7),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'にほん', 'nihon', 'Japan', 8),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', '〜じん', '~jin', 'person of ~ (nationality suffix)', 9),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'なまえ', 'namae', 'name', 10),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'なんさい', 'nansai', 'how old (polite: おいくつ)', 11),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'はじめまして', 'hajimemashite', 'nice to meet you (first meeting only)', 12),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000001', 'どうぞよろしくおねがいします', 'douzo yoroshiku onegaishimasu', 'please treat me well (closes an introduction)', 13),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'これ', 'kore', 'this (near speaker)', 0),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'それ', 'sore', 'that (near listener)', 1),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'あれ', 'are', 'that over there', 2),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'どれ', 'dore', 'which one', 3),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'ここ', 'koko', 'here', 4),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'そこ', 'soko', 'there', 5),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'あそこ', 'asoko', 'over there', 6),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'どこ', 'doko', 'where', 7),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'いくら', 'ikura', 'how much', 8),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'えん', 'en', 'yen', 9),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'ペン', 'pen', 'pen', 10),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'ほん', 'hon', 'book', 11),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'とけい', 'tokei', 'watch, clock', 12),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'かさ', 'kasa', 'umbrella', 13),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000002', 'だれ', 'dare', 'who', 14),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'いきます', 'ikimasu', 'go', 0),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'たべます', 'tabemasu', 'eat', 1),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'のみます', 'nomimasu', 'drink', 2),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'みます', 'mimasu', 'watch, see', 3),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'ききます', 'kikimasu', 'listen, ask', 4),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'よみます', 'yomimasu', 'read', 5),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'かきます', 'kakimasu', 'write', 6),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'あいます', 'aimasu', 'meet', 7),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'いつも', 'itsumo', 'always', 8),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'たいてい', 'taitei', 'usually', 9),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'ときどき', 'tokidoki', 'sometimes', 10),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'あまり〜ません', 'amari ~masen', 'not very often', 11),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'ぜんぜん〜ません', 'zenzen ~masen', 'not at all', 12),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'まいにち', 'mainichi', 'every day', 13),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000003', 'しゅうまつ', 'shuumatsu', 'weekend', 14),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'あります', 'arimasu', 'there is (inanimate)', 0),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'います', 'imasu', 'there is (animate)', 1),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'うえ', 'ue', 'on top of, above', 2),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'した', 'shita', 'under, below', 3),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'まえ', 'mae', 'in front of', 4),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'うしろ', 'ushiro', 'behind', 5),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'なか', 'naka', 'inside', 6),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'そば', 'soba', 'near', 7),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'でした', 'deshita', 'was (past tense of です)', 8),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'ました', 'mashita', 'past tense verb ending', 9),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'ませんでした', 'masendeshita', 'past negative verb ending', 10),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'いっしょに', 'issho ni', 'together', 11),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'と', 'to', 'and; with', 12),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000004', 'たくさん', 'takusan', 'a lot, many', 13),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'たかい', 'takai', 'expensive; tall', 0),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'やすい', 'yasui', 'cheap', 1),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'おいしい', 'oishii', 'delicious', 2),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'あたらしい', 'atarashii', 'new', 3),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'ふるい', 'furui', 'old (things, not age)', 4),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'きれい（な）', 'kirei (na)', 'pretty, clean', 5),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'しずか（な）', 'shizuka (na)', 'quiet', 6),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'ゆうめい（な）', 'yuumei (na)', 'famous', 7),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'すき（な）', 'suki (na)', 'liked', 8),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'きらい（な）', 'kirai (na)', 'disliked', 9),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'ひとつ', 'hitotsu', 'one (generic counter)', 10),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'ふたつ', 'futatsu', 'two (generic counter)', 11),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'みっつ', 'mittsu', 'three (generic counter)', 12),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'よっつ', 'yottsu', 'four (generic counter)', 13),
(gen_random_uuid(), '0b4f9a12-2222-4a5e-9d3c-000000000005', 'いつつ', 'itsutsu', 'five (generic counter)', 14);
