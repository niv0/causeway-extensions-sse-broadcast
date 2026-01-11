# Apache Causeway Extensions - SSE Broadcast

## Overview

Independent Maven project providing Server-Sent Events (SSE) broadcast functionality for Apache Causeway applications.

This project was extracted from `causeway-extensions-sse-wicket` to provide a standalone, reusable broadcast service that can be used independently of the Wicket viewer.

## Artifact

```xml
<dependency>
    <groupId>org.apache.causeway.extensions</groupId>
    <artifactId>causeway-extensions-sse-broadcast</artifactId>
    <version>3.5.0</version>
</dependency>
```

## Features

- **Named Channel Broadcasting**: Broadcast events to multiple clients via named channels
- **Minimal Dependencies**: Only depends on `causeway-extensions-sse-applib`
- **Servlet-Based**: Provides `/sse/broadcast` servlet endpoint
- **Thread-Safe**: Concurrent client management with ConcurrentHashMap
- **Spring Integration**: Automatic configuration via `@Configuration`

## Components

### SseBroadcastService

Service interface for broadcasting events to named channels.

**Key Methods:**
- `broadcast(String channelName, String payload)` - Broadcast JSON payload to all clients on a channel
- `lookupByChannelName(String channelName)` - Get or create a named channel
- `getClientCount(String channelName)` - Get number of connected clients
- `closeChannel(String channelName)` - Close a specific channel

### BroadcastSseServlet

HTTP servlet that handles SSE connections at `/sse/broadcast?channel=<name>`.

**Features:**
- Supports authentication bypass for development
- CORS configuration from `application.yml`
- Automatic client cleanup on disconnect
- Infinite timeout for long-running connections

### CausewayModuleExtSseBroadcast

Spring `@Configuration` class that enables the broadcast module.

## Dependencies

This project depends on:

- `causeway-extensions-sse-applib` (3.5.0) - For common SSE interfaces (SseChannel, SseSource)
- `causeway-core-config` (3.5.0) - For CausewayConfiguration (including CORS settings) (provided)
- Spring Web - For servlet support (provided)
- Jakarta Servlet API - For servlet functionality (provided)
- Causeway Core Runtime Services - For InteractionService (provided)
- Lombok - For logging annotations (provided)

**Note:** 
- The broadcast project reuses common SSE interfaces (SseChannel, SseSource) from applib but provides its own broadcast-specific service interface (SseBroadcastService).
- CORS configuration is read from Causeway core config (`causeway-core-config`), not a separate CORS extension.
- No dependency on `causeway-extensions-cors` is required.

## Usage

### 1. Add Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.causeway.extensions</groupId>
    <artifactId>causeway-extensions-sse-broadcast</artifactId>
    <version>3.5.0</version>
</dependency>
```

### 2. Enable Module

Import the configuration in your Spring application:

```java
@Configuration
@Import({
    CausewayModuleExtSseBroadcast.class,
    // ... other modules
})
public class AppConfig {
}
```

### 3. Use in Your Code

Inject `SseBroadcastService` and broadcast events:

```java
@Service
public class MyService {
    
    @Inject
    SseBroadcastService sseBroadcast;
    
    public void notifyClients() {
        String payload = "{\"type\":\"UPDATE\",\"data\":\"Hello World\"}";
        sseBroadcast.broadcast("my-channel", payload);
    }
}
```

### 4. Connect from Client

JavaScript example:

```javascript
const eventSource = new EventSource('/sse/broadcast?channel=my-channel');

eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('Received:', data);
};
```

## Configuration

### Application Properties

```yaml
causeway:
  extensions:
    sse:
      broadcast:
        bypass-authentication: true  # Development only - allows unauthenticated connections
    cors:
      # CORS configuration (uses Causeway core config - no CORS extension needed)
      allowed-origins:
        - http://localhost:3000
        - http://localhost:8080
      allow-credentials: true
```

**Notes:**
- CORS configuration is read from Causeway core configuration (no separate CORS extension dependency required)
- If `allowed-origins` is not configured, CORS headers are not set (stricter security)
- Origins must match exactly (including port)
- Never use `*` wildcard when `allow-credentials: true`

## Build

### Compile

```bash
mvn clean compile
```

### Install to Local Repository

```bash
mvn clean install
```

### Skip Tests

```bash
mvn clean install -DskipTests
```

## Architecture

### Independence from Wicket

This module is **independent** of `causeway-extensions-sse-wicket`. It can be used in:

- Wicket-based applications
- Spring MVC applications
- REST-only applications
- Any Apache Causeway application

### Integration with Wicket Module

If you use `causeway-extensions-sse-wicket`, it will transitively include this broadcast module.

The wicket module depends on broadcast module:

```xml
<!-- In causeway-extensions-sse-wicket -->
<dependency>
    <groupId>org.apache.causeway.extensions</groupId>
    <artifactId>causeway-extensions-sse-broadcast</artifactId>
    <version>3.5.0</version>
</dependency>
```

## Package Structure

```
org.apache.causeway.extensions.sse.broadcast
├── CausewayModuleExtSseBroadcast.java
├── services/
│   └── SseBroadcastServiceImpl.java
└── webmodule/
    └── BroadcastSseServlet.java
```

## Version

- **Current Version**: 3.5.0
- **Java**: 17+
- **Apache Causeway**: 3.5.0
- **Spring**: 6.2.12

## License

Licensed under the Apache License, Version 2.0

## Related Projects

- **causeway-extensions-sse-applib**: Service interfaces for SSE functionality
- **causeway-extensions-sse-wicket**: Wicket viewer integration with SSE
- **causeway-extensions-sse-metamodel**: Metamodel support for SSE

## Migration from Embedded Module

If you previously used the broadcast module as part of `causeway-extensions-sse`, no code changes are required. The wicket module now depends on this external project, so the functionality is available transitively.

## Support

For issues and questions, please refer to the Apache Causeway project documentation and community resources.

