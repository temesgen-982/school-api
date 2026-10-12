# AGENTS.md

## Commands

- Start the database first (tests do NOT start containers): `docker compose up -d`
- Full test suite: `./mvnw test` (~40s, 91 tests)
- Single test class: `./mvnw test -Dtest=SignupIntegrationTest`
- Run the app: `./mvnw spring-boot:run` (Docker Compose dep auto-starts Postgres for the app, but tests disable it via `src/test/resources/application.yml`)
- No lint/format/typecheck plugins exist — passing `./mvnw test` is the verification step

## Stack & layout

Spring Boot 4.1.1 (servlet MVC), Java 21, Maven single module, PostgreSQL, Flyway, JWT auth. Package root `com.school.api` with feature packages: `auth/`, `school/`, `branch/`, `user/`, `security/`, `common/`. Controllers are the routes; path constants live in `common/web/ApiPaths.java` (base `/api/v1`).

## Data layer

- Schema is Flyway-only (`ddl-auto: never`): add a new `V(N+1)__.sql` in `src/main/resources/db/migration`; never edit applied migrations
- Relationships are UUID scalar columns + DB-level FKs, NOT JPA `@ManyToOne`; repositories are plain `JpaRepository`s
- JWTs are self-issued HS256 (`security/JwtConfig.java`), no external IdP. Tests use an ephemeral signing key, so tokens die on restart
- The first platform admin is seeded by `security/AdminUserSeeder` only while `users` is empty (local: `admin`/`admin`)
- Authorization rules live in `security/SecurityConfig.java` matcher order; new public endpoints need an explicit `permitAll` matcher. `SchoolScopeInterceptor` confines school-scoped tokens to `/schools/{their-id}`

## Conventions

- Errors: RFC-7807 `ProblemDetail` via `common/exception/GlobalExceptionHandler`; throw `NotFoundException`/`ConflictException`/etc. Uniqueness is checked in-code (`existsBy...` → `ConflictException` 409), not by catching constraint violations
- Create endpoints return `201` + `Location`; soft delete = `status=INACTIVE` + `deletedAt/deletedBy` + `204`
- Request DTOs are validated records using `message = "must not be blank"`; response records have a static `from(entity)` mapper
- Multi-entity writes happen in one `@Transactional` service method (see `SchoolService.transferOwner`, `SignupService.signup`)
- Normalize codes with `TextNormalizer.normalizeCode` (trim + lowercase) before uniqueness checks
- Update the Bruno collection in `api-collection/` when adding endpoints

## Testing gotchas

- Integration tests hit the real local DB (`school`/`school`); tests run sequentially but rows persist — generate unique usernames/codes (`UUID.randomUUID()` suffix) and delete created rows in `@AfterEach`
- Circular FKs: `schools.owner_id → users.id` and `users.school_id → schools.id`. To delete a signup-style cluster: ownership history → branches → null `users.school_id` → school → user
- Bean-validation errors on nested record fields are keyed with dots (`"owner.username"`); in MockMvc assert with `$.errors['owner.username']`, not `$.errors.owner.username`
- Test annotations pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` for integration; `@WebMvcTest(controllers = ...)` + `@Import({SecurityConfig.class, JwtConfig.class})` + `@MockitoBean` for controller tests
