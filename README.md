# null-profile Backend

Spring Boot 3.2+ backend application with Java 21 and PostgreSQL.

## Prerequisites

- Docker Desktop with WSL 2 integration enabled
- Maven (for initial setup only)

## Initial Setup

### 1. Generate Maven Wrapper

If you haven't already, generate the Maven wrapper (needed for Docker build):

```bash
cd /mnt/c/Users/luis.ribeiro/Documents/sources/null-profile/null-profile-be/null-profile-be
mvn wrapper:wrapper
```

### 2. Configure Environment (Optional)

Copy the example environment file and customize if needed:

```bash
cp .env.example .env
```

Edit `.env` to change default values like database credentials, ports, etc.

## Running with Docker Compose

### Start the Application

From WSL, navigate to the backend directory and run:

```bash
cd /mnt/c/Users/luis.ribeiro/Documents/sources/null-profile/null-profile-be/null-profile-be
docker compose up --build
```

This will:
- Build the Spring Boot application
- Start PostgreSQL database
- Run database migrations with Flyway
- Start the backend service

### Stop the Application

```bash
docker compose down
```

To also remove volumes (database data):

```bash
docker compose down -v
```

## Endpoints

Once running, the application will be available at:

- **API Base**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/actuator/health
- **Example Endpoint**: http://localhost:8080/api/public/hello

## Development

### Viewing Logs

```bash
# All services
docker compose logs -f

# Backend only
docker compose logs -f backend

# PostgreSQL only
docker compose logs -f postgres
```

### Rebuilding After Code Changes

```bash
docker compose up --build
```

### Accessing the Database

```bash
docker exec -it nullprofile-postgres psql -U profile_user -d profile
```

## Configuration

The application uses environment variables for configuration. Default values are:

- **Server Port**: 8080
- **Database**: profile
- **Database User**: profile_user
- **Database Password**: profile_pass
- **Database Port**: 5432

See `.env.example` for all available configuration options.

## Package Structure

```
ch.nullprofile
├── Application.java           # Main Spring Boot application
└── controller
    └── PublicController.java  # Example REST controller
```

## Database Migrations

Flyway migrations should be placed in:
```
src/main/resources/db/migration/
```

Example naming: `V1__initial_schema.sql`
