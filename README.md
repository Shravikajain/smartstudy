# Smart Study

Smart Study turns previous exam papers into generated practice questions, supports adaptive quizzes and written-answer feedback, and provides voice-enabled interview practice for academic subjects and engineering roles.

## Run with Docker Desktop on Windows

Prerequisites:

- Docker Desktop running with the Linux containers engine
- An Ollama Cloud API key (or a reachable Ollama endpoint configured for the ML service)
- Git, if cloning this repository

1. Clone the repository:

   ```powershell
   git clone https://github.com/Shravikajain/smartstudy.git
   cd smartstudy
   ```

2. Create your own local environment files from the examples, then set fresh secret values:

   ```powershell
   Copy-Item .env.example .env
   Copy-Item ml_services\.env.example ml_services\.env
   ```

   Generate a JWT secret and service key in PowerShell:

   ```powershell
   $bytes = New-Object byte[] 32
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   ```

   Use separate generated values for `JWT_SECRET` and `ML_INTERNAL_KEY`. Set `POSTGRES_PASSWORD` to a new database password. Do not commit `.env` or share credentials in chat.

   Set your provider key as `OLLAMA_API_KEY` in `ml_services/.env`. The ML container reads this file at startup. It is ignored by Git and is not included in the container image.

3. Start the application:

   ```powershell
   Set-Location Frontend
   docker compose --env-file ..\.env up --build -d
   ```

   The first build downloads Python, Java and PyTorch packages and can take 15–30 minutes on a slow connection.

4. Wait for the ML models. On first start the ML service downloads three local models (about 750 MB in total). The app works meanwhile, but the AI-writing check and voice dictation report "still loading" until they finish. Check progress with:

   ```powershell
   docker compose --env-file ..\.env logs ml-service | Select-String "model loaded|could not be loaded"
   ```

   All three are ready when the log shows `AI detector model loaded`, `Whisper model loaded` and `Sentence embedding model loaded`. They are kept in a Docker volume, so later starts are fast.

5. Open [http://localhost:3001](http://localhost:3001) and create an account. Change `FRONTEND_PORT` in `.env` if that port is already in use.

### Updating an existing copy

If you already have Smart Study running, pull the latest code and rebuild:

```powershell
git pull
Set-Location Frontend
docker compose --env-file ..\.env up --build -d
```

Compare `.env.example` and `ml_services\.env.example` with your own `.env` files and copy over any settings you are missing.

Your database and uploaded papers are kept. The backend adds new database columns and tables automatically on start. Your browser picks up the new interface on the next page load.

### Troubleshooting

- **The backend container exits with `password authentication failed for user "smartstudy"`.** Your database volume was created with an older `POSTGRES_PASSWORD`. Postgres only reads that setting when the volume is first created. To keep your data, set the database password to the one in your current `.env`, then start the backend again:

  ```powershell
  $password = (Select-String '^POSTGRES_PASSWORD=(.*)$' ..\.env).Matches[0].Groups[1].Value
  docker compose --env-file ..\.env exec db psql -U smartstudy -d smartstudy -c "ALTER USER smartstudy PASSWORD '$password'"
  docker compose --env-file ..\.env up -d backend
  ```

- **The build fails with `Read timed out` while installing packages.** This is a network timeout. Run the same `up --build` command again; downloaded packages are cached, so it continues where it stopped.
- **The AI-writing check or voice dictation says the model is still loading.** The first-start download is still running (see step 4). Downloads resume after a restart.
- **`docker` reports that it cannot connect to the Docker engine.** Start Docker Desktop and wait until it shows that the engine is running.

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

## Features

- **Practice sets from past papers**: upload 1–5 PDFs and generate multiple-choice, subjective, or both kinds of questions across the topics found in the papers.
- **Tests from your question bank**: on a subject page, choose multiple choice, subjective or both, and how many questions. Quizzes adapt difficulty to your answers.
- **My tests**: every test is saved. Reviewing one shows each question, your answer, the correct option or model answer and marking guide, the grade and feedback, and the AI-writing check for written answers.
- **AI-writing check on written answers**: each subjective answer gets an estimated probability that it was AI-written, labelled *likely human-written* (below 35%), *uncertain* (35–60%) or *likely AI-written* (60% and above). It never changes the grade.
- **Authenticity check**: paste text or upload a PDF, `.txt` or `.md` file (up to 60,000 characters) to estimate how likely it is that it was AI-written, with a section-by-section breakdown for longer documents. Results are kept in your history; the text itself is not stored.
- **AI interview practice** with optional voice dictation.

AI-writing detection is an estimate, not proof. It can miss AI-written text and can wrongly flag human writing, especially short, heavily edited or non-native-English text.

## Services

- `Frontend`: static browser app served by Nginx.
- `backend`: Spring Boot API and PostgreSQL access.
- `ml-service`: FastAPI question generation, grading, interview, and writing-style assessment.
- `db`: PostgreSQL data store.

Docker Compose builds the backend and ML service images from the adjacent `backend` and `ml_services` folders. Each teammate should provide their own `.env`; provider keys and signing secrets are not included in this repository.

Interview dictation records audio in the browser and transcribes it with the local CPU Whisper model in the ML container, so it also works in Firefox and does not send audio to an external speech provider. The first recording downloads the configured model (`WHISPER_MODEL`, default `base`); Docker Compose persists it in the `whisper-cache` volume. Microphone access requires localhost or HTTPS. Text entry remains available if recording or transcription is unavailable.

The ML service downloads three small local models when it starts: the AI-writing detector, the Whisper speech model, and a sentence-embedding model (`all-MiniLM-L6-v2`) that groups past-paper questions by meaning as well as shared words. Docker Compose keeps them in the `whisper-cache` volume, so later starts reuse them. If the embedding model cannot be downloaded, analysis falls back to word-based grouping. Set `ML_EMBEDDINGS=false` to always use word-based grouping, or `ML_WARMUP=false` to load models only on first use.

Every ML endpoint except `/health` requires the `X-Internal-Key` header, which the backend sends automatically. When you run the backend and ML service outside Docker, set the same `ML_INTERNAL_KEY` for both.
