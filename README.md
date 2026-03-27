# RentalOps Backend

RentalOps Backend is the Spring Boot API for RentalOps, a multi-tenant B2B platform built to organize day-to-day operations for short-term rental teams. It centralizes task management, operator assignments, property data, and issue reporting inside isolated workspaces for each property manager.

This repository provides the application services and REST endpoints that power the full workflow between admins and operators.

## Project Repositories

- Backend repository: [github.com/NamelessKing/rentalops-backend](https://github.com/NamelessKing/rentalops-backend)
- Frontend repository: [github.com/NamelessKing/rentalops-frontend](https://github.com/NamelessKing/rentalops-frontend)

## What The Backend Covers

The backend supports the main MVP business flow of the project:

- admin registration and authentication with JWT
- tenant-aware access control and role-based authorization
- operator management inside the current workspace
- property management
- task creation with `POOL` and `DIRECT_ASSIGNMENT` modes
- operator claim, start, and completion actions
- issue report creation by operators
- admin review, dismissal, or conversion of issue reports into tasks
- admin dashboard aggregates for properties, operators, tasks, and issue reports

The system is built around two roles:

- `ADMIN`, who configures the workspace and supervises operations
- `OPERATOR`, who executes field work and reports newly discovered problems

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT
- OpenAPI / Swagger
- Maven Wrapper
- Testcontainers

## Local Setup

### Prerequisites

- Java 21
- Docker

### Start PostgreSQL

The repository includes a local PostgreSQL setup:

```bash
docker compose up -d
```

### Create Local Configuration

Copy the example file:

Windows PowerShell:

```powershell
Copy-Item env.properties.example env.properties
```

macOS / Linux:

```bash
cp env.properties.example env.properties
```

Set a local JWT secret in `env.properties` and keep the default database values unless you are using a different PostgreSQL instance.

### Run The Application

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080` by default.

## Useful Commands

Windows:

```bash
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

macOS / Linux:

```bash
./mvnw test
./mvnw clean verify
```

Integration tests require Docker because they use Testcontainers.

## API Documentation

When the backend is running locally, Swagger UI is available at:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

## Frontend Companion App

This repository contains only the API layer. The user interface lives in the companion frontend repository:

[https://github.com/NamelessKing/rentalops-frontend](https://github.com/NamelessKing/rentalops-frontend)
