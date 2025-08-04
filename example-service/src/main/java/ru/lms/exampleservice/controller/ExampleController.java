package ru.lms.exampleservice.controller;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/example")
@CrossOrigin(origins = "*")
public class ExampleController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from Example Service!");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "example-service");
        response.put("port", "8081");
        return response;
    }

    @GetMapping("/data")
    public Map<String, Object> getData() {
        Map<String, Object> response = new HashMap<>();
        response.put("id", 1);
        response.put("name", "Example Data");
        response.put("description", "This is sample data from the example microservice");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("echo", request);
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "example-service");
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "example-service");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
} 