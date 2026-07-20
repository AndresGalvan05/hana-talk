from fastapi import FastAPI

from app.routes import router

app = FastAPI(title="ai-exercise-svc")
app.include_router(router)
