# Supply Chain Management

Spring Boot starter project for supply chain management.

## Requirements

- Java 21+
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

The application starts at `http://localhost:8080`.

The development H2 console is available at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:supplychain`, user `sa`, and an empty password.

## Structure

- `controller`: REST endpoints
- `service`: business logic
- `repository`: Spring Data repositories
- `entity`: JPA domain entities
- `dto`: request and response models
- `security`: authentication and authorization
- `exception`: application exceptions and handlers
- `config`: application configuration
- `database`: SQL scripts and database notes
