# Smart Study

Smart Study turns previous exam papers into generated practice questions, supports adaptive quizzes and written-answer feedback, and provides voice-enabled interview practice for academic subjects and engineering roles.

## Run with Docker Desktop on Windows

Prerequisites:

- Docker Desktop running with the Linux containers engine
- An Ollama Cloud API key (or a reachable Ollama endpoint configured in `.env`)
- Git, if cloning this repository

1. Clone the repository:

   ```powershell
   git clone https://github.com/Shravikajain/smartstudy.git
   cd smartstudy
   ```

2. Create your own local environment file and set fresh secret values:

   ```powershell
   Copy-Item .env.example .env
   ```

   Generate a JWT secret and service key in PowerShell:

   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```

   Use separate generated values for `JWT_SECRET` and `ML_INTERNAL_KEY`. Set `POSTGRES_PASSWORD` to a new database password and `OLLAMA_API_KEY` to your own provider key. Do not commit `.env` or share credentials in chat.

3. Start the application:

   ```powershell
   Set-Location Frontend
   docker compose --env-file ..\.env up --build -d
   ```

4. Open [http://localhost:3001](http://localhost:3001). Change `FRONTEND_PORT` in `.env` if that port is already in use.

View container status and logs:

```powershell
docker compose --env-file ..\.env ps
docker compose --env-file ..\.env logs -f backend ml-service frontend
```

Stop the containers while preserving the database and uploaded data:

```powershell
docker compose --env-file ..\.env down
```

To delete the local database and uploads as well, use `docker compose --env-file ..\.env down -v`; this permanently removes those local Docker volumes.

## Services

- `Frontend`: static browser app served by Nginx.
- `backend`: Spring Boot API and PostgreSQL access.
- `ml-service`: FastAPI question generation, grading, interview, and writing-style assessment.
- `db`: PostgreSQL data store.

Docker Compose builds the backend and ML service images from the adjacent `backend` and `ml_services` folders. Each teammate should provide their own `.env`; provider keys and signing secrets are not included in this repository.

Browser microphone access and speech recognition depend on the browser. Text entry remains available when speech recognition is unavailable.
