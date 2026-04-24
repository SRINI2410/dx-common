# Auth v2 — Implementation Plan

**Repo under change (primary):** `dx-common` on branch `dev-clone`
**Consumer repos:** `dx-controlplane` (dev-clone), `dx-dataplane-rs`, `acl-apd`
**Design source of truth:** `authentication-and-authorization-guide.md`, `roles-and-scopes-authorization.md`
**Status:** Planning — no code written yet. To be reviewed before implementation starts.

---

## 1. Goal

Deliver the foundation (Phase 1 from the design doc) for the new authentication & authorization model:

- A uniform `DxPrincipal` produced by three authentication paths (plain user JWT, JWT + `X-Delegator-Id` header, `X-App-Id` + `X-App-Secret` headers).
- Scope-based authorization that runs identically across all three paths, with `forScopes` / `forScopesWithContext` / `forRoles` handlers.
- A transport-agnostic core in `dx-common`, with pluggable lookup interfaces so that:
  - `dx-controlplane` resolves delegation / app / user data via **in-process services** (no network hop).
  - `dx-dataplane-rs` and `acl-apd` resolve the same data via **gRPC** against `dx-controlplane` (reusing the existing `AppIdVerificationService` and extending it with two new RPCs).

## 2. Non-goals for this phase

- No endpoint migration yet — Phase 2+ (the design doc's "Phase 2: Plain user path in dx-controlplane", "Phase 3: App credentials", "Phase 4: Delegation", "Phase 5: Dataplane integration") is separate work.
- No removal or mutation of the existing legacy classes in `org.cdpg.dx.auth.authorization` (old `DxRole`, `DxScope`, static `AuthorizationHandler`) or `org.cdpg.dx.auth.appid` (existing `AppIdAuthHandler`, `AppIdVerificationClient`, `CombinedAuthHandler`). These stay in place and working until all consumers migrate.
- No changes to the existing `appid_verification.proto` in Phase 1. Additive proto changes (`VerifyDelegation`, `GetUser`) are flagged here but implemented in Phase 4.
- No `forRoles`-heavy migration plan — `forScopes` is the default; `forRoles` is a rare escape hatch.

## 3. Verified current state

(Verified against `dx-common:dev-clone`, `dx-controlplane:dev-clone`, `dx-dataplane-rs`.)

### 3.1 dx-common

| Piece | Status |
|---|---|
| `org.cdpg.dx.auth.authorization.model.DxRole` | Exists with 8 values (includes `DELEGATE`, `CONSUMER_DELEGATE`, `PROVIDER_DELEGATE`). Design wants 5. **Left as-is.** |
| `org.cdpg.dx.auth.authorization.model.DxScope` | Exists with 8 values (`USER_MANAGEMENT`, `ASSET_MANAGEMENT`, `WILDCARD`, …). Design wants 13 kebab-case. **Left as-is.** |
| `org.cdpg.dx.auth.authorization.handler.AuthorizationHandler` (static) | `forRoles`, `forDelegationScopes`, `KycVerification`. No `forScopes`, no `forScopesWithContext`. **Left as-is.** |
| `org.cdpg.dx.auth.appid.handler.AppIdAuthHandler` | Path 3 handler. Hardcoded to take `AppIdVerificationClient` (gRPC). Dataplane uses this. |
| `org.cdpg.dx.auth.authentication.handler.CombinedAuthHandler` | Basic vs Bearer dispatcher. No `X-Delegator-Id` handling. |
| `MultiIssuerJwtAuthHandler`, `OptionalMultiIssuerJwtAuthHandler`, `JwksResolver`, `BearerTokenExtractor` | JWT validation plumbing. Reusable as-is. |
| `src/main/proto/appid_verification.proto` | `VerifyAppId`, `CheckItemAccess(userId, entityId, did)`. No `VerifyDelegation`, no `GetUser`. |
| No `DxPrincipal`, no `SystemRoleScopeMap`, no `RoleScopeRegistry`, no `JwtPrincipalResolver`, no `DelegationResolver`, no lookup interfaces. | — |

### 3.2 dx-controlplane (dev-clone)

| Piece | Status |
|---|---|
| `aaa/grpc/GrpcServerVerticle` | Boots gRPC server on `grpcPort` (default 9090). |
| `aaa/grpc/AppIdVerificationGrpcService` | Implements `VerifyAppId` + `CheckItemAccess`. Backed by `AppCredentialsService`, `ItemService`, `DelegationService`. |
| `aaa/apiserver/ApiServerVerticle` | Extends `AbstractApiServerVerticle`. Does **not** override `getAppIdAuthHandler()`. HTTP-side is JWT-only today. |
| `acl/apiserver/ApdApiServerVerticle` | Same as above. |
| `aaa/appCredentials/...` | `AppCredentialsDAO`, `AppCredentialsService`, `AppCredentialsServiceImpl` — source of truth for app credentials. |
| `aaa/delegation/service/DelegationService` | Delegation CRUD + `checkItemAccess(userId, did)`. Used by gRPC service. |
| `keycloak/service/KeycloakUserService` | User data (roles, org, status) — backs the gRPC service too. |

### 3.3 dx-dataplane-rs

- `ApiServerVerticle`, `ProxyApiServerVerticle`: build `new AppIdVerificationClient(host, port)`, pass into `new AppIdAuthHandler(...)` via `getAppIdAuthHandler()`. HTTP JWT + Basic works today.
- `AppIdItemAccessHandler`: per-resource check after auth.
- Talks to `dx-controlplane`'s gRPC server on the `VerifyAppId` / `CheckItemAccess` RPCs.

### 3.4 Key findings that shape the plan

1. **Controlplane HTTP doesn't do AppId auth yet.** It needs to start, and it must not call its own gRPC server to do so. → SPI is required, not nice-to-have.
2. **Delegation header-resolver doesn't exist anywhere.** Controlplane has delegation CRUD + `checkItemAccess`, but no "`X-Delegator-Id` header → build a delegated `DxPrincipal`" code. Phase 1 introduces this in dx-common; the lookup side needs new gRPC RPCs in Phase 4 for non-controlplane consumers.
3. **Existing `AppIdAuthHandler` is too tightly bound to gRPC.** Phase 1 refactors it to depend on an `AppCredentialLookup` SPI; the existing constructor stays (wrapping the gRPC client in a default adapter) so dataplane keeps working unchanged.

## 4. Target architecture

### 4.1 Packages

```
dx-common/src/main/java/org/cdpg/dx/auth/v2/
  model/
    DxRole.java                       — enum, 5 values
    Scopes.java                       — String constants, 13 scopes
    DxPrincipal.java                  — immutable, builder, effective getters
    AppPrincipal.java                 — DTO returned by AppCredentialLookup
    DelegationRecord.java             — DTO returned by DelegationLookup
    UserSnapshot.java                 — DTO returned by UserLookup
  registry/
    SystemRoleScopeMap.java           — static DxRole → Set<String>
    RoleScopeRegistry.java            — interface
    InMemoryRoleScopeRegistry.java    — only impl
  lookup/
    AppCredentialLookup.java          — interface
    DelegationLookup.java             — interface
    UserLookup.java                   — interface
  handler/
    AuthorizationHandler.java         — forScopes / forScopesWithContext / forRoles
    ScopeRule.java                    — platform(scope) / org(scope) / self(scope)
    AuthorizationContext.java         — attached to ctx after forScopesWithContext match
    AuthLevel.java                    — enum { PLATFORM, ORG, SELF }
    AuthenticationHandler.java        — top-level dispatcher
  resolver/
    JwtPrincipalResolver.java         — JWT User → DxPrincipal (no lookup deps)
    AppCredentialsResolver.java       — X-App-Id/Secret → DxPrincipal (uses AppCredentialLookup + UserLookup)
    DelegationResolver.java           — JWT + X-Delegator-Id → DxPrincipal (uses DelegationLookup + UserLookup)
```

### 4.2 Transport-agnostic core

Resolvers depend only on lookup **interfaces**. No imports from `org.cdpg.dx.auth.appid.client.AppIdVerificationClient`, no imports from any controlplane repository. Same compiled classes load and run in every DX server; only the lookup implementations differ.

### 4.3 Per-server wiring

| Server | `AppCredentialLookup` | `DelegationLookup` | `UserLookup` |
|---|---|---|---|
| `dx-controlplane` | `LocalAppCredentialLookup` (calls `AppCredentialsService` in-process) | `LocalDelegationLookup` (calls `DelegationService`) | `LocalUserLookup` (calls `KeycloakUserService`) |
| `dx-dataplane-rs` | `GrpcAppCredentialLookup` (wraps existing `AppIdVerificationClient`) | `GrpcDelegationLookup` (new RPC — **Phase 4**) | `GrpcUserLookup` (new RPC — **Phase 4**) |
| `acl-apd` | same gRPC impls | same | same |

The `Grpc*Lookup` classes live in `dx-common` (package `org.cdpg.dx.auth.v2.lookup.grpc`) so that dataplane and acl-apd can pull them directly. The `Local*Lookup` classes live in each consumer repo (they depend on that repo's services and cannot live in dx-common).

### 4.4 Authentication dispatch

```
┌─────────── AuthenticationHandler.handle(ctx) ───────────┐
│                                                        │
│  Read headers: Authorization, X-App-Id, X-Delegator-Id │
│                                                        │
│  if (X-App-Id present && Authorization present)        │
│      → 400 Ambiguous credentials                        │
│                                                        │
│  if (X-App-Id present)                                 │
│      → AppCredentialsResolver.resolve(ctx)              │
│                                                        │
│  if (Authorization: Bearer …)                          │
│      if (X-Delegator-Id present)                       │
│          → DelegationResolver.resolve(ctx)              │
│      else                                              │
│          → JwtPrincipalResolver.resolve(ctx)            │
│                                                        │
│  else → 401 Missing credentials                        │
│                                                        │
└────────────────────────────────────────────────────────┘
```

Each resolver, on success, puts the `DxPrincipal` onto the routing context under a well-known key (`"dxPrincipal"`) and calls `ctx.next()`.

### 4.5 How `AppIdAuthHandler` refactor stays backward-compatible

**Step 1 (this phase):** add a new constructor `AppIdAuthHandler(AppIdCacheService, AppCredentialLookup)`. Keep the existing `AppIdAuthHandler(AppIdCacheService, AppIdVerificationClient)` as-is; internally it wraps the client in a `GrpcAppCredentialLookup` adapter (which lives in `dx-common/lookup/grpc`). Dataplane's current wiring keeps compiling and working.

**Step 2 (Phase 2+):** migrate consumers to the new constructor explicitly; delete the legacy constructor.

Same pattern applies once `DelegationResolver` is used: dataplane wires `GrpcDelegationLookup`, controlplane wires `LocalDelegationLookup`. No branching in the resolver itself.

## 5. Phase 1 deliverables — file-by-file

All new files under `org.cdpg.dx.auth.v2.*`. Nothing renamed or deleted. Public API only; no `internal` subpackage needed yet.

### 5.1 `model/`

| File | Shape |
|---|---|
| `DxRole.java` | Enum: `CONSUMER("consumer")`, `PROVIDER("provider")`, `ORG_ADMIN("org_admin")`, `COS_ADMIN("cos_admin")`, `COMPUTE("compute")`. `keycloakName()` getter. `fromKeycloakName(String) → Optional<DxRole>` (case-insensitive). |
| `Scopes.java` | Final class, private ctor. Public `static final String` constants for the 13 scopes (kebab-case). Public `Set<String> ALL` of all 13. |
| `DxPrincipal.java` | Immutable. Fields: `authenticatedSub`, `authenticatedOrgId` (both NonNull), `delegatorSub`, `delegatorOrgId` (nullable, must be both-or-neither), `authorizationRoles` (`Set<DxRole>`, defensive-copy unmodifiable), `directScopes` (`Set<String>`, defensive-copy unmodifiable), `auditRoles` (`Set<DxRole>`), `appId` (nullable). Builder. Invariant: `delegatorSub != null && appId != null` → throw. `getSub()`/`getOrganisationId()` return effective; `getAuthenticatedSub()`/`getAuthenticatedOrgId()` return raw. `isDelegation()`, `isApp()`, `isDirectUser()`. |
| `AppPrincipal.java` | Record: `appId`, `userId` (owner), `roles` (`List<String>`), `scopes` (`List<String>`), `expiresAtEpoch`, `active` (boolean). Exactly mirrors the fields returned by `VerifyAppId` plus a status flag — so both local and gRPC lookups produce the same shape. |
| `DelegationRecord.java` | Record: `delegatorSub`, `delegateeSub`, `scopes` (`Set<String>`, empty for "full"), `fullDelegation` (boolean), `active` (boolean), `expiresAtEpoch` (0 = no expiry). |
| `UserSnapshot.java` | Record: `sub`, `organisationId`, `roles` (`Set<DxRole>`), `disabled` (boolean). |

### 5.2 `registry/`

| File | Shape |
|---|---|
| `SystemRoleScopeMap.java` | Final class, private ctor. `static Set<String> getScopes(DxRole)` backed by a `Map.copyOf(EnumMap)`. Role → scopes exactly per Section 2.3 of `roles-and-scopes-authorization.md`. |
| `RoleScopeRegistry.java` | Interface: `Set<String> resolveEffectiveScopes(DxPrincipal principal)`. |
| `InMemoryRoleScopeRegistry.java` | Implements interface: flatten `authorizationRoles` via `SystemRoleScopeMap`, union with `directScopes`. |

### 5.3 `lookup/`

| File | Shape (all methods return `io.vertx.core.Future<...>`) |
|---|---|
| `AppCredentialLookup.java` | `Future<Optional<AppPrincipal>> verify(String appId, String appSecret)` — returns `Optional.empty()` for "not found / invalid", success holds the principal. (Exception channel reserved for transport failures.) |
| `DelegationLookup.java` | `Future<Optional<DelegationRecord>> findActive(String delegatorSub, String delegateeSub)`. |
| `UserLookup.java` | `Future<Optional<UserSnapshot>> findBySub(String sub)`. |

Rationale for `Optional` rather than failing the future on "not found": lets resolvers cleanly distinguish "no such delegation" (403 at HTTP) from "delegation DB is down" (500) without `instanceof` checks on exceptions.

### 5.4 `handler/`

| File | Shape |
|---|---|
| `AuthLevel.java` | Enum `{ PLATFORM, ORG, SELF }`. |
| `ScopeRule.java` | Static factories: `ScopeRule.platform(String scope)`, `org(String scope)`, `self(String scope)`. Holds `level` + `scope`. |
| `AuthorizationContext.java` | Immutable. `AuthLevel getLevel()`, `String getScope()`, `String getOrgId()` (set when ORG), `String getSub()` (set when SELF). |
| `AuthorizationHandler.java` | Instance-based (not static). Takes a `RoleScopeRegistry`. Methods: `Handler<RoutingContext> forScopes(String... required)`, `forScopesWithContext(ScopeRule... rules)`, `forRoles(DxRole... required)`. Fails 401/403 via exceptions matching the existing `DxUnauthorizedException` / `DxForbiddenException`. |
| `AuthenticationHandler.java` | Dispatcher per §4.4. Takes the three resolvers by constructor. Implements `Handler<RoutingContext>`. |

### 5.5 `resolver/`

Resolvers write their result with `ctx.put("dxPrincipal", principal)` and call `ctx.next()`. On failure: `ctx.fail(new DxUnauthorizedException(...))` or `DxForbiddenException`.

| File | Dependencies | Behavior |
|---|---|---|
| `JwtPrincipalResolver.java` | None (reads `ctx.user()` set by `MultiIssuerJwtAuthHandler`) | Parse `sub`, `organisation_id`, `realm_access.roles`, `kyc_verified`. Roles → `Set<DxRole>` via `DxRole.fromKeycloakName` (skip unknown). Build `DxPrincipal` with `authorizationRoles = parsed`, `auditRoles = parsed`, `directScopes = empty`, no delegator, no appId. |
| `AppCredentialsResolver.java` | `AppCredentialLookup`, `UserLookup` | `X-App-Id` / `X-App-Secret` (or `Authorization: Basic base64(appId:secret)` for back-compat with current `AppIdAuthHandler` — decide in review) → `verify()`; on success load owner via `UserLookup`; cap `app.scopes ∩ flatten(owner.roles)`; build `DxPrincipal` with `authorizationRoles = empty`, `directScopes = capped`, `auditRoles = owner.roles`, `appId = app.appId`, `authenticatedSub = owner.sub`. |
| `DelegationResolver.java` | `DelegationLookup`, `UserLookup` | JWT already validated; read `sub` (delegatee), `organisation_id` (delegatee's org), `X-Delegator-Id` header. `findActive(delegator, delegatee)`; if expired or missing → 403. Load delegator via `UserLookup`; if disabled → 403. Cap scopes: delegation is "full" → `flatten(delegator.roles)`; else `delegation.scopes ∩ flatten(delegator.roles)`. Build `DxPrincipal` with `authenticatedSub = delegatee`, `delegatorSub = delegator`, `authorizationRoles = empty`, `directScopes = capped`, `auditRoles = delegator.roles`. |

### 5.6 Tests (JUnit 5, same style as `URNGeneratorTest`)

Per-class tests with `@DisplayName` + `@Nested`. No Vert.x integration spin-up for unit tests — use fakes for `AppCredentialLookup` / `DelegationLookup` / `UserLookup`.

- `DxRoleTest` — mapping round-trip, case-insensitive lookup, unknown returns empty.
- `DxPrincipalTest` — invariant enforcement, effective getters for each path, copy defensiveness (callers can't mutate the stored sets).
- `SystemRoleScopeMapTest` — each role's scope bundle matches the design table exactly.
- `InMemoryRoleScopeRegistryTest` — plain user flattening, delegation/app (empty roles + directScopes), multi-role union.
- `AuthorizationHandlerTest` — `forScopes` any-match, `forScopesWithContext` priority order (regression for §5.5 of the guide's warning), `forRoles` gate, 401 when no principal.
- `JwtPrincipalResolverTest` — fake Vert.x `RoutingContext` with a User holding JWT claims JSON; verify the principal shape.
- `AppCredentialsResolverTest` — fake lookups; happy, invalid secret, revoked, scope cap shrinks when owner loses a role.
- `DelegationResolverTest` — fake lookups; happy, no delegation → 403, expired → 403, delegator disabled → 403, partial delegation cap, full delegation tracks delegator's current scopes.
- `AuthenticationHandlerTest` — header dispatch matrix (plain JWT, JWT + delegator, AppId, ambiguous, missing).

## 6. Proto changes (flagged, not in this phase)

Deferred to **Phase 4** (delegation). Additive, no client break.

```proto
// In appid_verification.proto (or a new auth_lookup.proto if we prefer separation)

service AuthLookupService {
  rpc VerifyDelegation(VerifyDelegationRequest) returns (VerifyDelegationResponse);
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
}

message VerifyDelegationRequest  { string delegator_sub = 1; string delegatee_sub = 2; }
message VerifyDelegationResponse { bool active = 1; bool full_delegation = 2;
                                   repeated string scopes = 3; int64 expires_at_epoch = 4;
                                   string error_code = 5; }

message GetUserRequest  { string sub = 1; }
message GetUserResponse { string sub = 1; string organisation_id = 2;
                          repeated string roles = 3; bool disabled = 4;
                          bool found = 5; }
```

Server-side handlers will slot into the existing `GrpcServerVerticle`, backed by the already-injected `DelegationService` and `KeycloakUserService`.

## 7. Per-phase sequencing

| Phase | Repo | Deliverable | Blocking? |
|---|---|---|---|
| 1 | `dx-common` | This plan's Section 5 (foundation + resolvers + handlers + tests). No consumers wired yet. | — |
| 2 | `dx-controlplane` | `Local*Lookup` implementations. Override `getAppIdAuthHandler()` on `ApiServerVerticle` + `ApdApiServerVerticle`. Wire `AuthenticationHandler` v2. | Depends on Phase 1 merged. |
| 3 | `dx-common` proto | Add `VerifyDelegation` + `GetUser` RPCs. Regenerate stubs. Ship `dx-common` version. | — |
| 3 | `dx-controlplane` | Implement server-side methods in `GrpcServerVerticle`. | Depends on new proto. |
| 4 | `dx-common` | `Grpc*Lookup` adapters around the new stubs. | Depends on Phase 3. |
| 5 | `dx-dataplane-rs`, `acl-apd` | Wire `Grpc*Lookup` + new `AuthenticationHandler`. Migrate endpoints to `forScopes`. | Depends on Phase 4. |

(The design doc's original Phases 2–5 map onto our Phases 2–5 here; reordered because controlplane needs only local lookups and can land ahead of the proto extension.)

## 8. Open questions for review

1. **Package name.** Current plan: `org.cdpg.dx.auth.v2`. Alternatives: `org.cdpg.dx.auth.core`, `org.cdpg.dx.security`. The `v2` suffix is temporary — once legacy is removed, we'd rename. Is this acceptable?
2. **Principal context key.** Plan: `"dxPrincipal"` on `RoutingContext`. Legacy code uses `"dxUser"`. These coexist during migration. OK?
3. **AppId header scheme.** Design doc mentions `X-App-Id` + `X-App-Secret`. Current `AppIdAuthHandler` uses `Authorization: Basic base64(appId:secret)`. Should the new `AppCredentialsResolver`:
   - (a) accept only `X-App-Id` / `X-App-Secret` (matches design, breaks dataplane unless client changes too),
   - (b) accept only Basic auth (matches existing dataplane wire format),
   - (c) accept both during migration?
   **Recommendation: (c).** Easy to enforce (a) later by removing one branch.
4. **Legacy handler retirement.** We're leaving `org.cdpg.dx.auth.authorization.handler.AuthorizationHandler` and `org.cdpg.dx.auth.authorization.model.{DxRole,DxScope}` untouched. Once all three consumer repos migrate and no callers remain, these get deleted in one sweep. Agreed?
5. **Caching.** The design doc mandates a ~30–60s cache for delegation + app lookups, with MQ-based invalidation. This adds an `AppIdCacheService`-shaped component for delegations. Proposal: **deliver caching in Phase 2 (controlplane) and Phase 5 (dataplane), NOT Phase 1.** Phase 1's resolvers call the lookup every time; caching slots in as a decorator around the lookup interface without touching the resolvers. Acceptable?
6. **`forRoles` — keep in v2 or not?** The design says "fewer than 5 uses after migration." If we're certain we only need it for backward-compatibility during migration, we could omit it from v2 and force callers to use `forScopes`. **Recommendation: include it** — identity gates for `COMPUTE` service accounts are real, and the design itself calls this out.
7. **Kotlin / Java style.** Current code is Java 17+ with records used in `AppIdPrincipal`. Plan uses records for the three DTOs. Any objection?

## 9. What lands in this commit (pre-implementation)

Just this doc. No Java code. Review comments go on this file; once resolved, Phase 1 implementation starts and each slice is a separate commit:

- Slice 1 — model + registry + tests
- Slice 2 — lookup interfaces + DTOs (actually merged into Slice 1 if small)
- Slice 3 — `AuthorizationHandler` API + tests
- Slice 4 — resolvers + fake-lookup tests
- Slice 5 — `AuthenticationHandler` dispatcher + tests

---

**Reviewers:** please focus on Section 4 (architecture), Section 5 (class shapes), and Section 8 (open questions). The goal is to settle all seven open questions before any `.java` file is created.