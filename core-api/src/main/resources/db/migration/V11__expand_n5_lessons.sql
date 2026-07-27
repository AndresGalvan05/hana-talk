-- Deepens the five original N5 lessons and extends the course through
-- Genki I's next block of core grammar (existence, time, daily-routine
-- verbs, past tense, adjectives/likes-dislikes). Existing lesson rows are
-- UPDATEd in place, not replaced, so user_lesson_progress rows and V9's
-- seeded exercises (which quote specific phrases from lessons 1 and 4
-- verbatim) both remain valid. New lessons at positions 6-10 intentionally
-- have no seeded exercises -- ai-exercise-svc generates and caches them on
-- first request, exactly like lessons 2, 3, and 5 already do.

UPDATE lessons SET content =
    E'Basic Japanese greetings and everyday phrases:\n\n'
    || E'- おはようございます (ohayou gozaimasu) — good morning (polite); casual: おはよう (ohayou)\n'
    || E'- こんにちは (konnichiwa) — hello / good afternoon\n'
    || E'- こんばんは (konbanwa) — good evening\n'
    || E'- おやすみなさい (oyasumi nasai) — good night (before sleeping); casual: おやすみ (oyasumi)\n'
    || E'- ありがとうございます (arigatou gozaimasu) — thank you (polite); casual: ありがとう (arigatou)\n'
    || E'- どういたしまして (dou itashimashite) — you are welcome\n'
    || E'- すみません (sumimasen) — excuse me / sorry / used to get someone''s attention (e.g. calling a waiter)\n'
    || E'- ごめんなさい (gomen nasai) — I am sorry (more for an actual apology than すみません)\n'
    || E'- さようなら (sayounara) — goodbye (formal); casual: じゃあね (jaa ne) or またね (mata ne, "see you")\n'
    || E'- いってきます／いってらっしゃい (ittekimasu / itterasshai) — said when leaving, and by whoever stays home\n'
    || E'- ただいま／おかえりなさい (tadaima / okaeri nasai) — "I am home" / "welcome back"\n\n'
    || E'Note: こんにちは and こんばんは end in the particle は, which is written "ha" but pronounced "wa" — one of a small number of particles whose pronunciation differs from their usual reading (へ as "e" is another).\n\n'
    || E'Note on politeness: greetings ending in ございます or なさい are the formal register you would use with strangers, teachers, or in a shop; casual versions are for friends and family. Both are correct — the choice depends on who you are speaking to, not on right vs. wrong grammar.'
WHERE id = '0b4f9a12-2222-4a5e-9d3c-000000000001';

UPDATE lessons SET content =
    E'The standard self-introduction (じこしょうかい) pattern, used the first time you meet someone:\n\n'
    || E'- はじめまして (hajimemashite) — nice to meet you (said only at a first meeting)\n'
    || E'- わたしは アナ です (watashi wa Ana desu) — I am Ana\n'
    || E'- わたしは アメリカじん です (watashi wa amerika-jin desu) — I am American (~じん = "person of ~", attach to a country name)\n'
    || E'- わたしは がくせい です (watashi wa gakusei desu) — I am a student\n'
    || E'- ~さい です (~sai desu) — I am ~ years old (e.g. にじゅっさい です = I am 20 years old)\n'
    || E'- どうぞよろしくおねがいします (douzo yoroshiku onegaishimasu) — "please treat me well" — no direct English equivalent; always closes a self-introduction\n\n'
    || E'Full example:\n'
    || E'はじめまして。わたしは アナ です。アメリカじん です。がくせい です。\n'
    || E'どうぞよろしくおねがいします。\n\n'
    || E'Pattern: わたしは [name/description] です。The particle は marks the topic ("as for me..."), です is the polite copula ("to be" / "is"). Notice the subject (わたし, "I") only needs to be stated once — Japanese freely drops repeated topics, so がくせい です alone (without わたしは) is completely natural in context, just like leaving out "I am" in a list.'
WHERE id = '0b4f9a12-2222-4a5e-9d3c-000000000002';

UPDATE lessons SET content =
    E'Numbers 0–10:\n\n'
    || E'0 ゼロ／れい (zero/rei), 1 いち (ichi), 2 に (ni), 3 さん (san), 4 よん／し (yon/shi), 5 ご (go),\n'
    || E'6 ろく (roku), 7 なな／しち (nana/shichi), 8 はち (hachi), 9 きゅう／く (kyuu/ku), 10 じゅう (juu).\n\n'
    || E'4, 7 and 9 each have two readings; よん, なな and きゅう are the safer defaults in most contexts (し and しち are avoided in some situations because they sound close to the word for "death" and can be confused with いち).\n\n'
    || E'11–99 are compounds built from tens and ones:\n'
    || E'11 = じゅういち (10+1), 20 = にじゅう (2×10), 21 = にじゅういち (2×10+1), 99 = きゅうじゅうきゅう (9×10+9).\n\n'
    || E'Bigger units: 100 = ひゃく (hyaku), 1,000 = せん (sen), 10,000 = まん (man) — Japanese groups numbers in units of 10,000, not 1,000, so 15,000 is いちまんごせん (1×10,000 + 5×1,000), not broken down the way English says "fifteen thousand".\n\n'
    || E'Prices use the counter えん (en, "yen") directly after the number: 100えん (hyaku-en), 1,500えん (sen-gohyaku-en). Asking a price: これは いくら ですか (kore wa ikura desu ka) — "how much is this?".'
WHERE id = '0b4f9a12-2222-4a5e-9d3c-000000000003';

UPDATE lessons SET content =
    E'The most fundamental Japanese sentence pattern: A は B です — "A is B."\n\n'
    || E'- わたしは がくせい です (watashi wa gakusei desu) — I am a student\n'
    || E'- これは ほん です (kore wa hon desu) — this is a book\n'
    || E'- たなかさんは せんせい です (Tanaka-san wa sensei desu) — Mr. Tanaka is a teacher\n'
    || E'- とうきょうは にほんの しゅと です (Tokyo wa nihon no shuto desu) — Tokyo is the capital of Japan\n\n'
    || E'Negative: では ありません (dewa arimasen, formal) ／ じゃ ありません (ja arimasen, casual-polite) — both attach after removing です.\n'
    || E'わたしは がくせい では ありません (watashi wa gakusei dewa arimasen) — I am not a student.\n\n'
    || E'Question: add か at the end (no question mark needed, no word order change) —\n'
    || E'あなたは がくせい ですか (anata wa gakusei desu ka) — are you a student?\n'
    || E'Answer: はい、がくせい です (hai, gakusei desu — yes) ／ いいえ、がくせい では ありません (iie, gakusei dewa arimasen — no).\n\n'
    || E'も ("also/too") replaces は when the same thing is true of another topic:\n'
    || E'わたしは がくせい です。たなかさんも がくせい です。(I am a student. Tanaka is also a student.)\n\n'
    || E'Pattern summary: は marks what you are talking about (the topic), です states what it is, か turns a statement into a question, も says "this too."'
WHERE id = '0b4f9a12-2222-4a5e-9d3c-000000000004';

UPDATE lessons SET content =
    E'Japanese has a three-way demonstrative system based on distance from the speaker and listener:\n\n'
    || E'- これ (kore) — this (near the speaker)\n'
    || E'- それ (sore) — that (near the listener)\n'
    || E'- あれ (are) — that over there (far from both)\n'
    || E'- どれ (dore) — which one?\n\n'
    || E'Examples:\n'
    || E'- これは ペン です — this is a pen\n'
    || E'- それは なん ですか (sore wa nan desu ka) — what is that?\n'
    || E'- あれは がっこう です — that (over there) is a school\n\n'
    || E'これ／それ／あれ／どれ stand alone as the subject or topic of a sentence ("this [thing]"). To describe a noun directly ("this pen", not just "this"), use the related adjective forms この／その／あの／どの before the noun:\n\n'
    || E'- この ペン (kono pen) — this pen\n'
    || E'- その ほん (sono hon) — that book\n'
    || E'- あの がっこう (ano gakkou) — that school over there\n'
    || E'- どの ペン ですか (dono pen desu ka) — which pen?\n\n'
    || E'The same distance system extends to places and people, using こちら／そちら／あちら／どちら (polite) or ここ／そこ／あそこ／どこ (plain, for places only):\n\n'
    || E'- ここは がっこう です (koko wa gakkou desu) — here is a school / this place is a school\n'
    || E'- トイレは どこ ですか (toire wa doko desu ka) — where is the bathroom?\n'
    || E'- こちらは たなかさん です (kochira wa Tanaka-san desu) — this is Mr. Tanaka (polite way to introduce someone)'
WHERE id = '0b4f9a12-2222-4a5e-9d3c-000000000005';

INSERT INTO lessons (id, course_id, title, content, position) VALUES
(
    '0b4f9a12-2222-4a5e-9d3c-000000000006',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Existence: あります／います',
    E'Japanese uses two different verbs for "there is / there are", depending on whether the subject is alive:\n\n'
    || E'- あります (arimasu) — for inanimate things (objects, plants, buildings)\n'
    || E'- います (imasu) — for animate things (people, animals)\n\n'
    || E'Basic pattern: [place] に [thing] が あります／います — "[thing] exists at [place]".\n\n'
    || E'- つくえの うえに ほんが あります (tsukue no ue ni hon ga arimasu) — there is a book on the desk\n'
    || E'- きょうしつに がくせいが います (kyoushitsu ni gakusei ga imasu) — there are students in the classroom\n'
    || E'- こうえんに ねこが います (kouen ni neko ga imasu) — there is a cat in the park\n\n'
    || E'Location words (combine with の + a noun):\n\n'
    || E'- うえ (ue) — on top of / above\n'
    || E'- した (shita) — under / below\n'
    || E'- まえ (mae) — in front of\n'
    || E'- うしろ (ushiro) — behind\n'
    || E'- なか (naka) — inside\n'
    || E'- そば／ちかく (soba / chikaku) — near\n\n'
    || E'Example: いすの したに ねこが います (isu no shita ni neko ga imasu) — there is a cat under the chair.\n\n'
    || E'Note the particle が here, not は — が introduces new information ("a book exists"), while は marks something already established as the topic. This が／は distinction is one of the trickier parts of early Japanese grammar and becomes natural with practice — for now, default to が with あります／います.',
    6
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000007',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Telling time',
    E'Telling time uses two counters: じ (ji) for the hour, ふん／ぷん (fun/pun) for minutes.\n\n'
    || E'Hours (1–12): いちじ、にじ、さんじ、よじ、ごじ、ろくじ、しちじ、はちじ、くじ、じゅうじ、じゅういちじ、じゅうにじ。\n'
    || E'Note: 4 o''clock is よじ (not よんじ) and 7 o''clock is しちじ (not ななじ) — these two hours use their alternate readings.\n\n'
    || E'Minutes change sound depending on the number before them:\n'
    || E'いっぷん (1), にふん (2), さんぷん (3), よんぷん (4), ごふん (5), じゅっぷん (10), etc.\n'
    || E'半 (はん, han) means "half past": さんじはん (sanji-han) — half past three.\n\n'
    || E'Asking and answering the time:\n'
    || E'- いま なんじ ですか (ima nanji desu ka) — what time is it now?\n'
    || E'- いま さんじ です (ima sanji desu) — it is 3 o''clock now.\n\n'
    || E'に marks a specific clock time when something happens (similar to "at" in English):\n'
    || E'- しちじに おきます (shichiji ni okimasu) — I get up at 7 o''clock.\n\n'
    || E'Basic time words need no に — they are relative, not clock times:\n\n'
    || E'- きょう (kyou) — today\n'
    || E'- あした (ashita) — tomorrow\n'
    || E'- きのう (kinou) — yesterday\n'
    || E'- まいあさ (maiasa) — every morning\n'
    || E'- いま (ima) — now',
    7
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000008',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Daily routine verbs and を／に／で',
    E'Japanese verbs in polite speech end in ます (present/future) — this is the ます-form, the first verb conjugation you will use in every polite sentence.\n\n'
    || E'Common daily-routine verbs:\n\n'
    || E'- おきます (okimasu) — get up\n'
    || E'- ねます (nemasu) — go to sleep\n'
    || E'- たべます (tabemasu) — eat\n'
    || E'- のみます (nomimasu) — drink\n'
    || E'- いきます (ikimasu) — go\n'
    || E'- かえります (kaerimasu) — return home\n'
    || E'- べんきょうします (benkyou shimasu) — study\n'
    || E'- はたらきます (hatarakimasu) — work\n\n'
    || E'Three particles mark different roles in a sentence:\n\n'
    || E'- を (wo, pronounced "o") — marks the direct object, the thing being acted on\n'
    || E'  ごはんを たべます (gohan wo tabemasu) — I eat rice/a meal\n'
    || E'- に (ni) — marks a destination or a specific point in time\n'
    || E'  がっこうに いきます (gakkou ni ikimasu) — I go to school\n'
    || E'  しちじに おきます (shichiji ni okimasu) — I get up at 7 o''clock\n'
    || E'- で (de) — marks the place where an action happens (not existence — that is に, from the previous lesson)\n'
    || E'  レストランで たべます (resutoran de tabemasu) — I eat at a restaurant\n\n'
    || E'Putting it together: まいあさ しちじに おきて、レストランで あさごはんを たべます — every morning I get up at 7 and eat breakfast at a restaurant. (This connects two actions with the て-form, covered in a later lesson — for now, notice how を／に／で each answer a different question: what? when/where-to? where-at?)',
    8
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000009',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Past tense: ～ました／～ませんでした',
    E'The polite past tense of a verb is formed by changing ます to ました (affirmative) or ません to ませんでした (negative) — no separate past-tense particle or auxiliary verb needed.\n\n'
    || E'- たべます → たべました (tabemashita) — ate\n'
    || E'- たべません → たべませんでした (tabemasendeshita) — did not eat\n'
    || E'- いきます → いきました (ikimashita) — went\n'
    || E'- みます → みました (mimashita) — watched/saw\n\n'
    || E'Examples:\n'
    || E'- きのう、えいがを みました (kinou, eiga wo mimashita) — yesterday, I watched a movie\n'
    || E'- あさごはんを たべませんでした (asagohan wo tabemasendeshita) — I did not eat breakfast\n'
    || E'- どこに いきましたか (doko ni ikimashita ka) — where did you go?\n\n'
    || E'です also has a past tense: でした (deshita, "was") and じゃ ありませんでした／では ありませんでした (was not).\n\n'
    || E'- がくせい でした (gakusei deshita) — I was a student\n'
    || E'- せんせい じゃ ありませんでした (sensei ja arimasendeshita) — I was not a teacher\n\n'
    || E'Summary for a verb like たべます:\n'
    || E'present affirmative たべます／present negative たべません／past affirmative たべました／past negative たべませんでした。\n\n'
    || E'This four-way pattern (present/past × affirmative/negative) is completely regular for every ます-form verb, which is exactly why the polite form is taught first — irregular exceptions come later with the plain/dictionary form.',
    9
),
(
    '0b4f9a12-2222-4a5e-9d3c-000000000010',
    '0b4f9a12-1111-4a5e-9d3c-000000000001',
    'Adjectives and likes/dislikes: すき／きらい',
    E'Japanese adjectives come in two types, and they conjugate differently.\n\n'
    || E'い-adjectives end in い and conjugate on their own:\n\n'
    || E'- たかい (takai) — expensive/tall — negative: たかく ない (takaku nai)\n'
    || E'- おいしい (oishii) — delicious — past: おいしかった (oishikatta)\n'
    || E'- あたらしい (atarashii) — new\n\n'
    || E'Directly before a noun: たかい ほん (takai hon) — an expensive book.\n\n'
    || E'な-adjectives need な before a noun, and です for a predicate (they behave more like nouns):\n\n'
    || E'- きれい(な) (kirei) — pretty/clean — きれいな はな (kirei na hana, "a pretty flower") but はなは きれい です (hana wa kirei desu, "the flower is pretty" — no な before です)\n'
    || E'- しずか(な) (shizuka) — quiet\n'
    || E'- ゆうめい(な) (yuumei) — famous\n\n'
    || E'Likes and dislikes use すき (suki, "liked") and きらい (kirai, "disliked") — both behave like な-adjectives, and the thing liked/disliked is marked with が, not を:\n\n'
    || E'- わたしは すしが すき です (watashi wa sushi ga suki desu) — I like sushi (literally: "as for me, sushi is likeable")\n'
    || E'- コーヒーが きらい です (koohii ga kirai desu) — I dislike coffee\n'
    || E'- サッカーが だいすき です (sakkaa ga daisuki desu) — I love soccer (だい+すき = "very much like")\n\n'
    || E'Note the が here parallels あります／います from an earlier lesson: すき／きらい describe a quality of the thing itself, so the thing takes が, while わたしは stays the overall topic of the sentence.',
    10
);
