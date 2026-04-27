# Java → Go Migration: [Service Name]

## Project Context
- Source: Java 25 + GraalVM native image
- Target: Go 1.24+
- Service purpose: [what it does]
- Key dependencies: [Spring Boot? Quarkus? Micronaut?]

## Migration Rules
- Preserve all existing API contracts (endpoints, request/response shapes)
- Match GraalVM startup time goals with Go's native speed
- Replace Java annotations with Go struct tags

## Architecture Mapping
| Java Concept         | Go Equivalent              |
|----------------------|----------------------------|
| @RestController      | net/http or chi/gin router |
| @Service             | plain struct + interface   |
| @Repository          | repository struct          |
| Optional<T>          | (T, error) or *T           |
| CompletableFuture    | goroutine + channel        |
| application.yml      | envconfig / viper          |

## File Structure (target)
cmd/server/main.go
internal/handler/
internal/service/
internal/repository/
internal/model/
pkg/

## Testing Requirements
- Every migrated function needs a Go test
- Benchmark critical paths that relied on GraalVM AOT

## Do NOT
- Use code generation frameworks that hide complexity
- Add unnecessary abstractions mimicking Java patterns
- Use panic() where errors should be returned

## Resume Protocol (IMPORTANT)

At the START of every session:
1. Read MIGRATION_STATUS.md
2. Announce current progress to the user
3. Continue from the "In Progress" item, never restart completed work

At the END of every task unit:
1. Update MIGRATION_STATUS.md before moving to the next file
2. Commit: `git commit -m "migrate: <file> - [done|in-progress]"`
3. Only then proceed to the next module

## Granularity Rule
One commit per migrated file. Never batch multiple files in one commit.
This ensures git history = migration checkpoint log.

## Resuming a Session
User will say: "resume migration"
Claude must respond by:
1. Reading MIGRATION_STATUS.md
2. Reading the last git commit message
3. Summarizing progress
4. Asking: "Continue with [next task]?"