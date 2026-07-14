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

export interface Lesson {
  id: string
  courseId: string
  title: string
  content: string
  position: number
  createdAt: string
}

export interface CourseProgress {
  completed: number
  total: number
}

export interface UserProfile {
  username: string
  nativeLanguage: string
  startingLevel: JlptLevel | null
}
