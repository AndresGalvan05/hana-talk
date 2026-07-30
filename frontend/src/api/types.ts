export type JlptLevel = 'N5' | 'N4' | 'N3' | 'N2' | 'N1'

export interface AuthResponse {
  token: string
  username: string
}

export interface Course {
  id: string
  title: string
  jlptLevel: JlptLevel
  description: string | null
  createdAt: string
}

export interface ExampleSentence {
  japanese: string
  romaji: string
  english: string
}

export interface GrammarPoint {
  title: string
  explanation: string
  examples: ExampleSentence[]
}

export interface DialogueLine {
  speaker: string
  japanese: string
  english: string
}

export interface Dialogue {
  title: string
  lines: DialogueLine[]
}

export interface CultureNote {
  title: string
  body: string
}

export interface LessonContent {
  grammarPoints: GrammarPoint[]
  dialogue: Dialogue
  cultureNote: CultureNote
}

export interface Lesson {
  id: string
  courseId: string
  title: string
  content: LessonContent
  position: number
  createdAt: string
}

export interface VocabularyItem {
  id: string
  japanese: string
  reading: string
  meaning: string
}

export interface CourseProgress {
  completed: number
  total: number
  completedLessonIds: string[]
}

export type ExerciseType = 'MCQ' | 'FILL_IN_BLANK' | 'TRANSLATION' | 'SENTENCE_ORDERING'

export interface Exercise {
  id: string
  lessonId: string
  type: ExerciseType
  prompt: string
  options: string[] | null
}

export interface AttemptResult {
  correct: boolean
}

export interface UserProfile {
  username: string
  nativeLanguage: string
  startingLevel: JlptLevel | null
}

export interface Streak {
  userId: string
  currentStreak: number
  lastActiveDate: string | null
}

export interface LeaderboardEntry {
  userId: string
  username: string
  currentStreak: number
}

export interface ChatMessage {
  speaker: 'user' | 'tutor'
  japanese: string
}

export interface ChatReply {
  japanese: string
  english: string
  correction: string | null
}
