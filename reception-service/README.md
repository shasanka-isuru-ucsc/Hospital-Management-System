# HMS Reception Service

This repository contains the Spring Boot microservice for the **Reception Service**, managing patient registration, queue tokens, and appointment bookings.

## Prerequisites
- Docker & Docker Compose
- Java 21
- Maven (or use the provided Maven wrapper/installation)

## Setup Instructions

### 1. Start Infrastructure Dependencies
The service depends on PostgreSQL, RabbitMQ, and MinIO. These are configured in the `docker-compose.yml` file located in `D:\service 1`.

Open a terminal in the root folder (`D:\service 1`) and run:
\`\`\`bash
docker-compose up -d
\`\`\`

Wait a few seconds for all containers to initialize.

### 2. Run the Spring Boot Application
Navigate into the `reception-service` directory and run the Spring Boot application using Maven.

Open a terminal in `D:\service 1\reception-service` and run:
\`\`\`bash
mvn spring-boot:run
# Or if you don't have Maven installed globally, run the packaged jar:
# java -jar target/reception-0.0.1-SNAPSHOT.jar
\`\`\`

The application will start on port `3001` and automatically create the required database tables via Spring Data JPA mappings.

### 3. API Usage
You can now start making API calls to `http://localhost:3001`. Ensure that you include the necessary Keycloak simulated headers in your requests:
- `X-User-Id: <uuid>`
- `X-User-Role: <role>` (e.g. `receptionist`, `doctor`)

**Example (Patient Registration):**
\`\`\`bash
curl.exe -s -X POST "http://localhost:3001/patients" ^
  -F "first_name=John" ^
  -F "last_name=Doe" ^
  -F "mobile=+94771234568" ^
  -F "date_of_birth=1990-01-01" ^
  -F "gender=male" ^
  -H "X-User-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890" ^
  -H "X-User-Role: receptionist"
\`\`\`
