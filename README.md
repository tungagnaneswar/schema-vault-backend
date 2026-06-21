# Schema Vault Backend

This is the Spring Boot backend application for Schema Vault.

## 🚀 Prerequisites

- Java 21
- Maven
- PostgreSQL database
- Docker (optional, for containerization)

## 💻 Local Development Setup

1. **Clone the repository:**
   ```bash
   git clone <your-repository-url>
   cd schema-vault-backend
   ```

2. **Configure Environment Variables:**
   Copy the `.env.example` file to `.env`:
   ```bash
   cp .env.example .env
   ```
   *Make sure to fill in your local database credentials and other necessary secrets in the `.env` file.*

3. **Run the application:**
   You can run the Spring Boot application using Maven:
   ```bash
   ./mvnw spring-boot:run
   ```
   The backend should now be running on `http://localhost:8080`.

## 🐳 Docker Setup

If you prefer to run the backend using Docker, a `Dockerfile` and `docker-compose.yml` are provided.

**Build and run using Docker Compose:**
```bash
docker-compose up -d --build
```

## 🔄 CI/CD (GitHub Actions)

This repository is configured with a GitHub Actions workflow that automatically builds and pushes the Docker image to Docker Hub whenever code is pushed to the `main` or `master` branch.

### Setting up Docker Hub Deployment

For the automated deployment to work, you must configure your Docker Hub credentials in GitHub Secrets.

1. Go to your repository on GitHub.
2. Navigate to **Settings** > **Secrets and variables** > **Actions** (on the left sidebar).
3. Click on **New repository secret**.
4. Add the following two secrets:
   - **Name**: `DOCKERHUB_USERNAME`
   - **Secret**: Your Docker Hub username.
5. Click **New repository secret** again.
   - **Name**: `DOCKERHUB_TOKEN`
   - **Secret**: Your Docker Hub password or Personal Access Token (PAT).

Once these are set, GitHub Actions will automatically handle the build and push process for every new commit on the main branch!

## 📄 API Documentation
*(Add details about your Swagger/Springdoc UI here if applicable, normally accessible at `http://localhost:8080/swagger-ui.html`)*
