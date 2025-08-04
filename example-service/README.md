# Example Microservice

This is a simple Spring Boot microservice that runs on port 8081 and provides example REST endpoints.

## Features

- REST API endpoints for demonstration
- Runs on port 8081
- CORS enabled for cross-origin requests
- Health check endpoint

## Available Endpoints

- `GET /example/hello` - Returns a hello message with service info
- `GET /example/data` - Returns sample data
- `POST /example/echo` - Echoes back the request body
- `GET /example/health` - Health check endpoint

## Running the Service

### Prerequisites
- Java 17 or higher
- Gradle (or use the included wrapper)

### Build and Run

1. Navigate to the example-service directory:
   ```bash
   cd example-service
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Run the service:
   ```bash
   ./gradlew bootRun
   ```

   Or on Windows:
   ```bash
   gradlew.bat bootRun
   ```

The service will start on port 8081.

## Testing the Service

Once running, you can test the endpoints:

```bash
# Hello endpoint
curl http://localhost:8081/example/hello

# Data endpoint
curl http://localhost:8081/example/data

# Health check
curl http://localhost:8081/example/health

# Echo endpoint (POST)
curl -X POST -H "Content-Type: application/json" \
  -d '{"message":"Hello World"}' \
  http://localhost:8081/example/echo
```

## Integration with API Gateway

This service is configured to work with the API Gateway running on port 8080. The gateway routes requests with path `/example/**` to this service on port 8081. 