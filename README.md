# API Gateway with Example Microservice

This project contains an API Gateway (port 8080) and an example microservice (port 8081) to demonstrate microservice architecture with Spring Cloud Gateway.

## Architecture

- **API Gateway**: Runs on port 8080, routes requests to microservices
- **Example Microservice**: Runs on port 8081, provides REST API endpoints

## Project Structure

```
gatewayauth/
├── src/                          # API Gateway source code
├── build.gradle                  # API Gateway build configuration
├── application.yml               # API Gateway configuration
├── example-service/              # Example microservice
│   ├── src/                      # Microservice source code
│   ├── build.gradle              # Microservice build configuration
│   ├── application.yml           # Microservice configuration (port 8081)
│   └── README.md                 # Microservice documentation
└── README.md                     # This file
```

## Quick Start

### 1. Start the API Gateway

```bash
# Build and run the API Gateway
./gradlew bootRun
```

The API Gateway will start on port 8080.

### 2. Start the Example Microservice

```bash
# Navigate to the example service directory
cd example-service

# Build and run the microservice
./gradlew bootRun
```

The example microservice will start on port 8081.

### 3. Test the Setup

Once both services are running, you can test the integration:

```bash
# Test direct access to microservice
curl http://localhost:8081/example/hello

# Test access through API Gateway
curl http://localhost:8080/example/hello
```

Both should return the same response, but the second request goes through the API Gateway.

## API Gateway Configuration

The API Gateway is configured in `src/main/resources/application.yml`:

- **Port**: 8080
- **Routes**: `/example/**` → `http://localhost:8081`
- **CORS**: Enabled for cross-origin requests

## Example Microservice Endpoints

- `GET /example/hello` - Hello message with service info
- `GET /example/data` - Sample data
- `POST /example/echo` - Echo request body
- `GET /example/health` - Health check

## Running Both Services

### Option 1: Separate Terminals

1. Terminal 1 - API Gateway:
   ```bash
   ./gradlew bootRun
   ```

2. Terminal 2 - Example Service:
   ```bash
   cd example-service
   ./gradlew bootRun
   ```

### Option 2: Background Processes

1. Start API Gateway in background:
   ```bash
   ./gradlew bootRun &
   ```

2. Start Example Service in background:
   ```bash
   cd example-service
   ./gradlew bootRun &
   ```

## Testing the Complete Setup

```bash
# Test microservice directly
curl http://localhost:8081/example/hello

# Test through API Gateway
curl http://localhost:8080/example/hello

# Test data endpoint
curl http://localhost:8080/example/data

# Test echo endpoint
curl -X POST -H "Content-Type: application/json" \
  -d '{"message":"Hello from Gateway"}' \
  http://localhost:8080/example/echo
```

## Troubleshooting

1. **Port already in use**: Make sure ports 8080 and 8081 are available
2. **Service not found**: Ensure both services are running
3. **Connection refused**: Check if the services are started on the correct ports

## Development

- API Gateway: Spring Cloud Gateway with JWT authentication
- Example Service: Spring Boot Web with REST endpoints
- Both use Java 17 and Gradle 