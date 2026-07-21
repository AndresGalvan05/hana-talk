package events

const (
	TopicUserRegistered    = "user.registered"
	TopicExerciseCompleted = "exercise.completed"
)

// UserRegistered mirrors EventPublisher.publishUserRegistered's payload in core-api.
type UserRegistered struct {
	UserID         string `json:"userId"`
	Username       string `json:"username"`
	NativeLanguage string `json:"nativeLanguage"`
	OccurredAt     string `json:"occurredAt"`
}

// ExerciseCompleted mirrors EventPublisher.publishExerciseCompleted's payload
// in core-api. Despite the topic name, this fires for every lesson
// completion (CompletionSource.MANUAL, AUTO, or EXERCISE), not only
// exercise-graded ones.
type ExerciseCompleted struct {
	UserID     string `json:"userId"`
	LessonID   string `json:"lessonId"`
	CourseID   string `json:"courseId"`
	JlptLevel  string `json:"jlptLevel"`
	Source     string `json:"source"`
	OccurredAt string `json:"occurredAt"`
}
