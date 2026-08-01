import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireAdmin } from './components/RequireAdmin'
import { RequireAuth } from './components/RequireAuth'
import { AchievementsPage } from './pages/AchievementsPage'
import { AdminCourseFormPage } from './pages/AdminCourseFormPage'
import { AdminCoursesPage } from './pages/AdminCoursesPage'
import { AdminLessonFormPage } from './pages/AdminLessonFormPage'
import { AdminLessonsPage } from './pages/AdminLessonsPage'
import { ChatPage } from './pages/ChatPage'
import { CourseDetailPage } from './pages/CourseDetailPage'
import { CoursesPage } from './pages/CoursesPage'
import { FlashcardsPage } from './pages/FlashcardsPage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { LessonPage } from './pages/LessonPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/courses" replace />} />
          <Route path="/courses" element={<CoursesPage />} />
          <Route path="/courses/:courseId" element={<CourseDetailPage />} />
          <Route path="/courses/:courseId/lessons/:lessonId" element={<LessonPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/flashcards" element={<FlashcardsPage />} />
          <Route path="/achievements" element={<AchievementsPage />} />
          <Route element={<RequireAdmin />}>
            <Route path="/admin/courses" element={<AdminCoursesPage />} />
            <Route path="/admin/courses/new" element={<AdminCourseFormPage />} />
            <Route path="/admin/courses/:id/edit" element={<AdminCourseFormPage />} />
            <Route path="/admin/courses/:courseId/lessons" element={<AdminLessonsPage />} />
            <Route path="/admin/courses/:courseId/lessons/new" element={<AdminLessonFormPage />} />
            <Route path="/admin/courses/:courseId/lessons/:lessonId/edit" element={<AdminLessonFormPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
