# Project 2 (Monolith Path): Content Platform
**Stack:** Java, Spring Boot, PostgreSQL, Redis, JWT, GraphQL, RabbitMQ, Docker Compose  
**Prerequisite:** Completed URL Shortener project (Parts 1–6) covering REST APIs, JPA, Redis caching, session auth, soft delete, Testcontainers  
**Goal:** Build a production-style content platform (posts, comments, reactions, notifications) as a single Spring Boot application with clean module boundaries. This project reinforces fundamentals from the URL shortener while introducing JWT, GraphQL, async messaging, complex query design, rate limiting, and idempotency — without the distributed-systems overhead of splitting into services. The same conceptual patterns a senior backend engineer uses daily, in the architectural shape most production Java backends actually ship in.

---

## Architecture Overview

One Spring Boot application with internally-isolated modules, backed by shared infrastructure. Modules communicate via Java method calls (for synchronous reads/writes) or RabbitMQ (for async event handling).

```
                     ┌──────────────────┐
                     │ Client (Web/App) │
                     └────────┬─────────┘
                              │ REST + GraphQL (JWT in Authorization header)
                              │
                     ┌────────▼──────────────────────────┐
                     │     Spring Boot Monolith          │
                     │                                   │
                     │  ┌─────────────────────────────┐  │
                     │  │ JWT filter → Rate limiter → │  │
                     │  │ Idempotency interceptor     │  │
                     │  └─────────────────────────────┘  │
                     │                                   │
                     │   Auth │ Posts │ Comments │       │
                     │        │       │          │       │
                     │   Reactions │ Notifications       │
                     │                    ▲              │
                     │              RabbitMQ events      │
                     │   (Posts/Comments → Notifications)│
                     └───┬──────────┬──────────┬─────────┘
                         │          │          │
                    ┌────▼───┐  ┌──▼──┐   ┌───▼─────┐
                    │Postgres│  │Redis│   │RabbitMQ │
                    └────────┘  └─────┘   └─────────┘
```

**Communication patterns:**
- Client → App: REST (writes, auth) and GraphQL (flexible reads on posts)
- Module → Module (sync): Java method calls on service beans (e.g., CommentService injecting PostService for existence checks)
- Module → Module (async): RabbitMQ events (Posts/Comments publish, Notification module consumes via `@RabbitListener`)
- All modules share one Postgres instance but respect strict package-scoped table ownership

---

## Module Breakdown

### 1. Auth Module
**Tech:** REST, JWT, BCrypt, PostgreSQL, Redis  
**Purpose:** User registration, login, and token management.

**What's different from the URL shortener:** Sessions worked fine when one app handled everything and the client was a browser holding a cookie. Here, JWT is the learning vehicle for stateless auth — the kind of auth that works the same whether you have one service or twenty, and whether the client is a browser, a mobile app, or a background worker. In this project the only verifier happens to be the same app that issued the token, which removes the distributed-verification story but keeps the token mechanics, refresh flow, and filter-chain placement identical to what you'd do in a distributed system.

**Core functionality:**
- `POST /api/v1/auth/register` — register with username/password, BCrypt hashing
- `POST /api/v1/auth/login` — verify credentials, return JWT access token + refresh token
- `POST /api/v1/auth/refresh` — issue new access token from a valid refresh token
- JWT contains userId and roles in the payload, signed with a shared secret (HMAC) or RSA key
- Access token TTL: 15 minutes. Refresh token TTL: 7 days, stored in Redis so it can be revoked on logout
- A Spring Security filter (or custom `OncePerRequestFilter`) validates the JWT on every protected request and populates the security context

**Key concepts to learn:**
- JWT structure (header.payload.signature), signing, and verification
- Access vs refresh token pattern and why access tokens are short-lived
- Stateless auth tradeoffs vs the session approach from the URL shortener (no built-in access-token revocation without a denylist)
- Where JWT validation sits in the filter chain and why order matters (auth filter must run before anything that expects `Authentication` in the security context)
- Token refresh flow and refresh-token rotation

**Database:** `users` table (id, username, password_hash, created_at)

---

### 2. Posts Module
**Tech:** REST (writes), GraphQL (reads), PostgreSQL, Redis  
**Purpose:** Manage posts with flexible read queries for different client views.

**Why GraphQL for reads:** A post has different shapes depending on where it's rendered. The feed page wants `{title, excerpt, author_name, reaction_count, tag_names}`. The detail page wants the full body, the tag objects, the reactions breakdown, and the first page of comments. A mobile client may want only title and excerpt. REST would need multiple endpoints or consistent overfetching. GraphQL lets the client request exactly the fields it needs in one query.

**Why REST for writes:** Creating, editing, publishing, and archiving posts are plain commands. Field selection adds no value to a mutation that returns "created" or "204 No Content." Mixing REST writes with GraphQL reads is a real production pattern, not a compromise — it's effectively CQRS at the API layer.

**Core functionality:**
- `POST /api/v1/posts` — create a post (starts in `DRAFT`)
- `PUT /api/v1/posts/{id}` — edit own post (ownership check)
- `POST /api/v1/posts/{id}/publish` — transition `DRAFT → PUBLISHED`, sets `published_at`, publishes a `PostPublished` event
- `POST /api/v1/posts/{id}/archive` — transition `PUBLISHED → ARCHIVED`
- `DELETE /api/v1/posts/{id}` — soft delete own post
- GraphQL queries: `posts(tag, authorId, sort, cursor, limit)`, `post(id)`, `postsByAuthor(authorId, cursor, limit)`
- Tags are many-to-many via a `post_tags` join table; tag creation is upsert-on-write
- Redis cache-aside on `post(id)` detail lookups, invalidated on update/publish/archive

**Key concepts to learn:**
- GraphQL schema definition language (SDL) and `.graphqls` files
- Queries vs mutations (even though mutations are unused here — know when not to use them)
- Resolver pattern — how GraphQL maps queries to Java methods
- N+1 problem and DataLoader batching (e.g., `posts → authors` and `posts → tag lists`)
- How GraphQL and REST coexist in the same app (separate controllers, shared service layer)
- State machine for post lifecycle and where to enforce it (service layer, not controller)
- Publishing domain events on state transitions (`applicationEventPublisher` or direct RabbitMQ publish)

**Database:**
- `posts` (id, author_id, title, body, excerpt, status, published_at, is_deleted, created_at, updated_at)
- `tags` (id, name, slug) — unique on slug
- `post_tags` (post_id, tag_id) — composite PK
- **Indexes:**
  - `idx_posts_published_at` on (status, published_at DESC, id DESC) — feed queries
  - `idx_posts_author_published` on (author_id, published_at DESC, id DESC) — author page
  - `idx_post_tags_tag` on (tag_id, post_id) — tag filter queries

---

### 3. Comments Module
**Tech:** REST, PostgreSQL, JPA Specifications  
**Purpose:** Comments on posts, with cursor pagination, dynamic filters, and multiple sort orders — the module that pushes SQL and query design the hardest.

**Why this module pushes SQL harder:** A popular post might have 10,000 comments. Offset pagination is O(n) in page depth. Users want to sort by newest, oldest, or most-reacted. Moderators want to filter by date range or author. All of this translates to parameterized queries, composite indexes, and `EXPLAIN ANALYZE`-verified index usage. This is the Review Service pattern from the original microservices spec, applied to comments.

**Core functionality:**

*Write operations:*
- `POST /api/v1/posts/{postId}/comments` — create a comment (authenticated)
- `PUT /api/v1/comments/{id}` — edit own comment (ownership check)
- `DELETE /api/v1/comments/{id}` — soft delete own comment
- Publishes `CommentCreated` event to RabbitMQ for the Notification module

*Read operations (where the complexity lives):*
- `GET /api/v1/posts/{postId}/comments` — paginated comments for a post
  - **Cursor-based pagination:** `?cursor=<encoded>&limit=20`
  - **Sort options:** `sort=newest` (default), `sort=oldest`, `sort=most_reacted`
  - **Filter options:** `?authorId=`, `?since=`, `?until=`
  - Sort + filter + cursor pagination interact in non-trivial ways — this is the core challenge
- `GET /api/v1/users/me/comments` — all comments by the authenticated user, paginated

**Key concepts to learn:**

*Cursor-based pagination:*
- Why offset pagination breaks at scale and why cursor-based is O(1) regardless of page depth
- Encoding cursor values (typically the sort column's value plus the row ID for tiebreaking)
- How cursors interact with sort direction — the `WHERE` clause flips between `<` and `>`
- Keyset pagination in JPA: `WHERE (created_at, id) < (:cursorDate, :cursorId)` instead of `OFFSET`

*Parameterized and dynamic queries:*
- Optional filters: if `authorId` is provided, add `AND author_id = :authorId`; if not, omit
- JPA Specification pattern (`Specification<Comment>`) for composable predicates
- When JPA's abstraction helps and when native SQL is clearer

*Composite indexes and query performance:*
- `(post_id, created_at DESC, id DESC)` for default pagination
- `(post_id, reaction_count DESC, id DESC)` for `sort=most_reacted`
- `(author_id, created_at DESC)` for a user's own comments
- Reading `EXPLAIN ANALYZE` output to verify index usage

**Database:**
- `comments` (id, post_id, author_id, body, reaction_count, is_deleted, created_at, updated_at)
- **Indexes:**
  - `idx_comments_post_created` on (post_id, created_at DESC, id DESC)
  - `idx_comments_post_reactions` on (post_id, reaction_count DESC, id DESC)
  - `idx_comments_author` on (author_id, created_at DESC)

---

### 4. Reactions Module
**Tech:** REST, PostgreSQL, Redis  
**Purpose:** Multiple reaction types on posts and comments, with aggregate queries and denormalized counters.

**Why multiple reaction types:** A single like/unlike doesn't stress aggregation. Dev.to-style reactions (👍 🦄 💡 🔥 🤯) force a real `GROUP BY` query for the summary view, which gives you a natural reason to cache the aggregate in Redis and invalidate it on write. It's the aggregation pattern from the Review Service, cleanly motivated.

**Core functionality:**
- `POST /api/v1/posts/{postId}/reactions` — add reaction (authenticated, body contains `type`)
- `DELETE /api/v1/posts/{postId}/reactions` — remove authenticated user's reaction
- `POST /api/v1/comments/{commentId}/reactions` — same, for comments
- `DELETE /api/v1/comments/{commentId}/reactions` — same, for comments
- `GET /api/v1/posts/{postId}/reactions/summary` — `{ total, by_type: { thumbs_up: 42, unicorn: 3, ... } }`
  - Computed via `SELECT type, COUNT(*) FROM reactions WHERE target_type='POST' AND target_id=? GROUP BY type`
  - Cached in Redis, invalidated on any reaction add/remove for that target
- One reaction per user per target (unique constraint)
- Denormalized counters: `post.reaction_count` and `comment.reaction_count` updated atomically with the `reactions` row insert/delete (single transaction)

**Key concepts to learn:**
- Aggregate queries with `GROUP BY` and `COUNT(*)`
- Caching computed data vs raw entities — same cache-aside pattern, different invalidation rules
- Denormalized counters: the tradeoff between computed-on-read (always correct, slower) and maintained-on-write (fast reads, complexity at write time)
- Atomicity: updating the counter column and the reactions row in one transaction so drift is impossible
- Unique constraints as domain-rule enforcement (DB-level one-per-user, not app-level)

**Database:**
- `reactions` (id, user_id, target_type, target_id, type, created_at)
- **Constraints & Indexes:**
  - `uq_reactions_user_target` unique on (user_id, target_type, target_id) — one reaction per user per target
  - `idx_reactions_target` on (target_type, target_id, type) — summary queries

---

### 5. Notification Module
**Tech:** RabbitMQ (producer-and-consumer, same application), PostgreSQL  
**Purpose:** Decouple notification work from the HTTP request thread via in-process messaging.

**Why RabbitMQ in a monolith:** The naive approach is to write the notification row (and send the email) synchronously inside the comment or post-publish handler. That couples the request's latency and failure modes to the notification system — if the notifications table is slow or the email service is down, your comment POST is slow or fails. Publishing an event and handling it in a separate `@RabbitListener` moves that work off the request thread, gives you retry + dead-letter semantics for free, and prepares you for the day the consumer runs in a separate process (or a separate service). This pattern exists in production monoliths everywhere — the thing you're learning is *async event handling*, not cross-machine communication.

**Core functionality:**
- **Producer side** (inside Posts and Comments modules):
  - On `post.publish`, publish `PostPublished { postId, authorId, title, publishedAt }` to the `post.events` exchange
  - On `comment.create`, publish `CommentCreated { commentId, postId, commentAuthorId }` to the `comment.events` exchange
- **Consumer side** (this module):
  - `@RabbitListener` on `post.published` queue → writes a notification row for users who follow the author (or the tag, if you add follows later)
  - `@RabbitListener` on `comment.created` queue → looks up the post author, writes a notification row, logs a simulated email
  - Manual ack after successful processing
  - Dead-letter queue for messages that fail N retries
  - Idempotent processing: dedupe by message id in a `processed_messages` table (or Redis) so double-delivery never creates duplicate notifications

**Key concepts to learn:**
- RabbitMQ exchanges, queues, bindings, routing keys
- Producer-consumer decoupling within a single app (and why it's still valuable)
- Manual ack vs auto ack
- Dead-letter queues and retry policy design
- Idempotent message processing — "exactly-once delivery" is a myth; the real pattern is at-least-once delivery plus an idempotent consumer
- Running the consumer under a separate Spring profile to simulate a worker process (optional stretch — flip one profile flag and your monolith runs as two processes sharing code)

**Database:**
- `notifications` (id, recipient_user_id, type, payload_json, is_read, created_at)
- `processed_messages` (message_id PRIMARY KEY, processed_at) — idempotency ledger

---

## Cross-Cutting Concerns

### Rate Limiting & Idempotency
**Tech:** Spring MVC filter/interceptor, Redis  
**Purpose:** Protect write endpoints from abuse and make client retries safe.

**Why these together:** Rate limiting prevents floods (same user can't submit 50 comments in a second). Idempotency prevents duplicates when the client retries a request that timed out mid-flight. They're complementary protections — different problems, same Redis backing store.

**Applied to:**
- `POST /api/v1/posts`, `POST /api/v1/comments`, `POST /api/v1/reactions`
- **Rate limits:** per-user sliding window (e.g., 20 posts/hour, 60 comments/hour, 200 reactions/hour). Implemented with Redis sliding-window counter or token bucket. Returns `HTTP 429 Too Many Requests` with `Retry-After` header when exceeded.
- **Idempotency:** client sends an `Idempotency-Key` header on every write. Interceptor checks Redis before the handler runs; if the key has been seen, return the cached response. Otherwise, process the request and cache the response keyed by `Idempotency-Key`. Key TTL: 24 hours.

**Key concepts to learn:**
- Sliding window counter vs token bucket — pick one, understand both
- Why Redis is the right backing store (atomic ops via Lua scripts or `INCR`, native TTL)
- Idempotency key pattern and the inherent race window between "check key" and "store result" — how to close it (SETNX, or DB unique constraint on the key)
- HTTP 429 semantics and `Retry-After`
- Filter-chain ordering: auth → rate limit → idempotency → handler

### Module Boundaries
Each module is a top-level package under `com.example.content` (e.g., `com.example.content.posts`, `com.example.content.comments`). A module's public API is its `*Service` bean; other modules call that, not the repositories directly. Modules must not read each other's tables. This is the *discipline* that microservices enforce by network boundary — here, you enforce it by convention plus code review. Getting this right now makes extracting a module into a real service later a weekend, not a rewrite.

### Docker Compose
One Spring Boot app + PostgreSQL + Redis + RabbitMQ. A single `Dockerfile` for the app. The compose file wires the network, sets environment variables, and defines health checks so the app waits for its dependencies.

### Testing Strategy
Same approach as the URL shortener, applied per module:
- Unit tests with Mockito for service layer logic
- Repository integration tests with `@DataJpaTest` + Testcontainers (real Postgres)
- Controller/endpoint integration tests with `@SpringBootTest` + `MockMvc`
- GraphQL tests using Spring's `GraphQlTester`
- RabbitMQ producer+consumer tests using the Testcontainers RabbitMQ module
- **Query performance tests:** seed 10,000+ comments and assert pagination/sort correctness. Use `EXPLAIN ANALYZE` assertions to verify index usage.
- **Idempotency tests:** issue the same write with the same `Idempotency-Key` twice; assert a single side effect and identical response
- **Rate limiting tests:** issue N+1 requests inside the window; assert the (N+1)th returns 429

---

## Suggested Build Order

Build one module at a time. Each part introduces new technology while reinforcing fundamentals.

**Part 1: Auth Module (JWT)**  
Reinforces: registration, BCrypt, REST, PostgreSQL, Redis, validation, exception handling  
New: JWT signing/verification, access/refresh tokens, stateless auth, JWT filter placement

**Part 2: Posts Module — Writes First (REST + Postgres)**  
Reinforces: REST, JPA relationships (authors, tags), ownership scoping, soft delete, DTOs, validation  
New: Many-to-many (post_tags), state machine (DRAFT → PUBLISHED → ARCHIVED), composite indexes

**Part 3: Posts Module — Reads (GraphQL + Redis caching)**  
Reinforces: cache-aside on post detail  
New: GraphQL schema-first design, resolvers, N+1 problem, DataLoader batching, REST + GraphQL coexistence

**Part 4: Comments Module (cursor pagination + dynamic queries)**  
Reinforces: REST, JPA, ownership scoping, soft delete  
New: Cursor-based pagination, JPA Specifications, composite indexes for sort orders, `EXPLAIN ANALYZE`

**Part 5: Reactions Module (aggregates + denormalized counters)**  
Reinforces: JPA, Redis caching  
New: `GROUP BY` aggregate queries, cached aggregates + invalidation, denormalized counters, one-per-user unique constraints

**Part 6: Rate Limiting & Idempotency**  
Reinforces: Redis, Spring filters/interceptors  
New: Sliding window counters (or token bucket), idempotency key pattern, filter-chain ordering, 429 semantics

**Part 7: Notification Module (RabbitMQ producer + consumer)**  
Reinforces: PostgreSQL, Spring component model  
New: RabbitMQ exchanges/queues/bindings, producer-consumer pattern within one app, manual ack, DLQ, idempotent consumer

**Part 8: Docker Compose & End-to-End Integration**  
Reinforces: Docker Compose, Testcontainers  
New: Multi-container local orchestration, end-to-end flows exercising every module together, optional: running the notification consumer under a separate profile to simulate a worker process

---

## Technology Summary

| Technology | Where It Appears | Why It's Used |
|---|---|---|
| JWT | Auth → all modules | Stateless auth, learnable in a monolith but prepares for multi-client / multi-service future |
| GraphQL | Posts (read side) | Flexible queries for different client views |
| RabbitMQ | Posts/Comments → Notification | Async event-driven work, decoupled even within one app |
| Redis | Auth (refresh tokens), Posts (detail caching), Reactions (aggregate caching), Rate limiting, Idempotency | Multiple patterns: caching, aggregate caching, rate limiting, idempotency store |
| PostgreSQL | All modules (shared DB, module-owned tables) | Single relational store with module discipline |
| Cursor Pagination | Comments | Efficient pagination over large datasets |
| JPA Specifications | Comments | Composable dynamic query predicates |
| Rate Limiting | Write endpoints | Abuse protection |
| Idempotency Keys | Write endpoints | Safe client retries |
| Docker Compose | Full stack | Local orchestration of app + Postgres + Redis + RabbitMQ |
| Testcontainers | All integration tests | Real infrastructure in tests |

---

## What Was Removed and Why

**gRPC + Protocol Buffers:** gRPC is meaningful only across process boundaries. In a monolith, what would have been an `Order → Catalog` gRPC call is a Java method call on a service bean — no wire format, no generated stubs, no `.proto` contract. Learning gRPC on top of a half-understood async + auth + query stack just makes all of them blurrier. Revisit gRPC in a small focused follow-up (maybe a 2–3 service project) once everything here is reflexive.

**Database-per-service:** Collapsed to one Postgres instance with strict module-owned tables enforced by code convention. The *discipline* that matters — modules don't read each other's tables — is preserved. The operational cost of running six databases in Compose is not.

**API Gateway:** Not needed with one application. JWT validation happens in a Spring filter at the entry point; rate limiting and idempotency are interceptors in the same chain.

**Multiple service deployments:** One Docker image, one process. The consumer can optionally run under a separate Spring profile as a second process sharing the same codebase — that gives you a gentle taste of "worker node" deployment without six services to wire.

---

## Student Context

Carlos is building this as the second project in a structured backend engineering curriculum, after recognizing mid-way through the microservices version that too many axes of variation were blurring together (distribution + JWT + GraphQL + gRPC + RabbitMQ + database-per-service + multi-container deploy, all at once, before any of it was muscle memory). This spec keeps every learning concept from the original *except* the distribution axis. He completed the URL Shortener (Parts 1–6) which covered Spring Boot, JPA, REST, Redis caching, session auth, soft delete, and Testcontainers. Key patterns he's comfortable with: cache-aside, filter-based auth, atomic DB operations, DTO validation, global exception handling, repository/service/controller layering.

**Teaching approach that works for Carlos:**
- Socratic method for architecture and design decisions — ask guiding questions, let him reason through tradeoffs before providing answers
- Adjacent problem pattern for new syntax and annotations — provide an 85–95% similar working example in a parallel domain and let him adapt it
- Concrete trace-throughs with specific values when explaining new concepts
- Session wind-down reviews summarizing what was built and why
- Direct and honest feedback — Carlos pushes back when he disagrees and expects the same in return
- Don't over-explain what he already knows from the URL shortener — reference the prior project and build on it

**What he explicitly excluded for this iteration:** gRPC and microservices (saved for a focused follow-up project — likely a small 2–3 service system — once the monolith patterns are reflexive), WebSockets (separate project), SOAP (explore in isolation if curiosity demands).
