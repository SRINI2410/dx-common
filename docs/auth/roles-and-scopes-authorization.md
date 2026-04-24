# Roles & Scopes Authorization — Developer Guide

**Scope of this document:** System roles and scopes only. Custom roles, supplementary scope grants, and delegation flows are intentionally out of scope — they will be added in a future phase.

---

## 1. Core Concepts

### 1.1 Role vs. Scope

| Term | What it is | Example |
|---|---|---|
| **Role** | An identity label assigned to a user. Roles are bundles of scopes. | `cos_admin`, `org_admin`, `provider` |
| **Scope** | A named capability — "what actions this principal may perform." | `user-management`, `own-asset-management` |
| **Effective scopes** | The union of scopes derived from all roles a user holds. Resolved at runtime. | A user with roles `[provider, consumer]` has effective scopes `{own-asset-management, data-access}` |

**Key principle:** authorize on *capabilities* (scopes), not on *identity* (roles). Roles exist for assignment and administration; scopes exist for enforcement.

### 1.2 The Five System Roles

These are defined in code in `dx-common` (`DxRole` enum) and cannot be created or deprecated through any API. They are pre-configured in Keycloak.

| Role | Authority | Purpose |
|---|---|---|
| `CONSUMER` | Base | Browse and download published assets |
| `PROVIDER` | Asset | Manage own assets and approve access requests |
| `ORG_ADMIN` | Organisation | Manage users, assets, and publishers within one org |
| `COS_ADMIN` | Platform | Manage orgs, users, assets, and roles platform-wide |
| `COMPUTE` | Service | Access compute resources and credit workloads |

### 1.3 System Scopes

All 13 system scopes defined in `Scopes` constants class:

| Scope | Assigned to | Grants |
|---|---|---|
| `data-access` | Consumer | Read catalogue, download authorised assets |
| `own-asset-management` | Provider, OrgAdmin | Create/update/delete own assets, approve own access requests |
| `org-user-management` | OrgAdmin | Manage users within an org |
| `org-asset-management` | OrgAdmin | Manage all assets within an org |
| `org-asset-publish` | OrgAdmin | Publish/unpublish org assets |
| `org-publisher-management` | OrgAdmin | Approve publisher role grants within org |
| `org-management` | CosAdmin | Create/suspend/delete organisations |
| `asset-publish` | CosAdmin | Publish/unpublish any asset platform-wide |
| `asset-management` | CosAdmin | Manage any asset on the platform |
| `user-management` | CosAdmin | Manage users platform-wide |
| `publisher-management` | CosAdmin | Approve publisher grants platform-wide |
| `role-management` | CosAdmin | Manage the role and scope schema |
| `compute-access` | Compute | Request credits, invoke compute workloads |

### 1.4 Role → Scope Mapping

Defined in `SystemRoleScopeMap` in `dx-common`. This is the **authoritative** mapping and the only place it should be changed.

```
CONSUMER   → { data-access }
PROVIDER   → { own-asset-management }
ORG_ADMIN  → { org-user-management, org-asset-management, org-asset-publish,
               own-asset-management, org-publisher-management }
COS_ADMIN  → { org-management, asset-publish, asset-management,
               user-management, publisher-management, role-management }
COMPUTE    → { compute-access }
```

---

## 2. Authorization Flow

Every request that hits a protected endpoint goes through this chain:

```
Request → JWT Auth Handler → DxUser attached → Authorization Handler → Controller
```

### 2.1 Step-by-step

1. **JWT validation.** `KeycloakJwtAuthHandler` validates the bearer token against JWKS, rejects expired/tampered tokens.
2. **DxUser construction.** `RoutingContextHelper.fromPrincipal()` parses the JWT claims:
   - `sub` → `userId`
   - `realm_access.roles` → `Set<DxRole>`
   - `organisation_id` → `organisationId`
   - `kyc_verified` → `kycVerified`
3. **DxUser attached** to `RoutingContext` under key `"dxUser"`.
4. **Authorization handler** runs. It:
   - Reads `DxUser` from context
   - Resolves effective scopes via `RoleScopeRegistry.resolveEffectiveScopes(user)`
   - Checks whether effective scopes intersect with the required scopes for this endpoint
   - On pass: `ctx.next()` → controller runs
   - On fail: `ctx.fail(403)`
5. **Controller** executes the business logic, trusting that the caller is authorised.

### 2.2 Effective scope resolution

```java
public Set<String> resolveEffectiveScopes(DxUser user) {
    Set<String> effective = new HashSet<>();
    for (DxRole role : user.getRoles()) {
        effective.addAll(SystemRoleScopeMap.getScopes(role));
    }
    return effective;
}
```

For a user with roles `[provider, consumer]`:
```
provider → {own-asset-management}
consumer → {data-access}
─────────────────────────────────
effective: {own-asset-management, data-access}
```

---

## 3. The Authorization Handler API

A single `AuthorizationHandler` class in `dx-common` exposes these methods. It is instantiated once with a `RoleScopeRegistry` and injected into route setup.

```java
public class AuthorizationHandler {
    private final RoleScopeRegistry registry;

    public AuthorizationHandler(RoleScopeRegistry registry) {
        this.registry = registry;
    }

    public Handler<RoutingContext> forScopes(String... required);
    public Handler<RoutingContext> forScopesWithContext(ScopeRule... rules);
    public Handler<RoutingContext> forRoles(DxRole... required);
}
```

### 3.1 `forScopes(String... required)` — The default

Use for the vast majority of endpoints. Passes if the user's effective scopes contain **any** of the required scopes.

**Semantics:** "Caller must have at least one of these capabilities."

**When to use:**
- Single-scope endpoint (only one role/scope can reach it)
- Multi-scope endpoint where the data boundary is the same regardless of caller

**When NOT to use:**
- Endpoint where the data boundary changes with the caller → use `forScopesWithContext`
- Endpoint gated on identity rather than capability → use `forRoles`

### 3.2 `forScopesWithContext(ScopeRule... rules)` — For tiered endpoints

Use when the **same endpoint serves multiple authority tiers with different data boundaries**. The handler walks rules in priority order, and on first match, sets an `AuthorizationContext` on the routing context that the service layer reads to apply the correct filter.

**Semantics:** "Caller may be at tier A or tier B; tell the service which one so it can filter accordingly."

**Building blocks:**

```java
public enum AuthLevel { PLATFORM, ORG, SELF }

public final class ScopeRule {
    public static ScopeRule platform(String scope);  // no data filter
    public static ScopeRule org(String scope);       // filter by caller's org_id
    public static ScopeRule self(String scope);      // filter by caller's user_id
}

public final class AuthorizationContext {
    AuthLevel getLevel();
    String getScope();       // the matched scope
    String getOrgId();       // set when level = ORG
    String getUserId();      // set when level = SELF
}
```

### 3.3 `forRoles(DxRole... required)` — For identity gates

Use sparingly. Only for endpoints that genuinely gate on *who the user is*, not *what they can do*. During the migration, this method also serves as a backward-compatibility escape hatch for endpoints that haven't been converted yet.

**Semantics:** "Caller must be one of these roles, regardless of scopes."

**Legitimate use cases:**
- Service-account-only endpoints (e.g., endpoints meaningful only for `COMPUTE`)
- Admin-panel landing pages where identity itself is the gate
- Transitional code during migration

**After migration is complete, `forRoles` should appear in fewer than 5 places in the codebase.** If you're reaching for it more often than that, the endpoint probably wants `forScopes` instead.

---

## 4. Choosing the Right Handler

Use this decision tree for every endpoint:

```
Is the endpoint reachable by more than one role/scope?
│
├─ No → forScopes(SINGLE_SCOPE)
│
└─ Yes → Does the data boundary change based on who called it?
         (i.e., does the SQL query differ between callers?)
         │
         ├─ No  → forScopes(SCOPE_A, SCOPE_B, ...)
         │
         └─ Yes → forScopesWithContext(
                    ScopeRule.platform(...),
                    ScopeRule.org(...),
                    ScopeRule.self(...)
                  )

Exception: Is this gated on identity, not capability?
└─ Yes → forRoles(ROLE) — rare, document why
```

### The mental test

> **"If I wrote the SQL query for this endpoint, would it be the same query regardless of who called it?"**
>
> - **Same query for everyone** → `forScopes`
> - **Query changes based on caller** → `forScopesWithContext`

---

## 5. Examples

### 5.1 Simple single-scope endpoint

**Endpoint:** `POST /asset-request` — A Provider creates a new asset.

Only Providers (with `own-asset-management`) reach this endpoint. No branching, no tiering.

**Route:**
```java
router.post("/asset-request")
    .handler(authHandler.forScopes(Scopes.OWN_ASSET_MANAGEMENT))
    .handler(assetController::createAsset);
```

**Controller:**
```java
public void createAsset(RoutingContext ctx) {
    DxUser user = ctx.get("dxUser");
    AssetRequest req = ctx.body().asPojo(AssetRequest.class);

    assetService.create(user.getUserId(), req)
        .onSuccess(asset -> ctx.json(asset))
        .onFailure(ctx::fail);
}
```

The controller reads `userId` from `DxUser` as a normal business concern — that's not authorization, it's just "whose asset are we creating."

### 5.2 Multiple scopes, same data boundary

**Endpoint:** `GET /public-catalogue` — Anyone with any management-level scope can see the full public catalogue.

Both CosAdmin and OrgAdmin see the same rows (all published assets). No filtering difference.

**Route:**
```java
router.get("/public-catalogue")
    .handler(authHandler.forScopes(
        Scopes.ASSET_MANAGEMENT,
        Scopes.ORG_ASSET_MANAGEMENT
    ))
    .handler(catalogueController::listPublic);
```

**Controller:**
```java
public void listPublic(RoutingContext ctx) {
    catalogueService.findAllPublished()
        .onSuccess(assets -> ctx.json(assets))
        .onFailure(ctx::fail);
}
```

Either scope lets the caller through; the query is the same either way.

### 5.3 Tiered endpoint — different data boundaries

**Endpoint:** `DELETE /organisations/{id}`

- **CosAdmin** (`org-management`) can delete any organisation
- **OrgAdmin** (`org-user-management`) can delete only their own organisation

**Route:**
```java
router.delete("/organisations/:id")
    .handler(authHandler.forScopesWithContext(
        ScopeRule.platform(Scopes.ORG_MANAGEMENT),       // priority 1
        ScopeRule.org(Scopes.ORG_USER_MANAGEMENT)        // priority 2
    ))
    .handler(orgController::deleteOrg);
```

**Controller — thin, passes context down:**
```java
public void deleteOrg(RoutingContext ctx) {
    String targetOrgId = ctx.pathParam("id");
    AuthorizationContext authCtx = ctx.get("authContext");

    orgService.delete(targetOrgId, authCtx)
        .onSuccess(v -> ctx.response().setStatusCode(204).end())
        .onFailure(ctx::fail);
}
```

**Service — applies the boundary:**
```java
public Future<Void> delete(String targetOrgId, AuthorizationContext authCtx) {
    return switch (authCtx.getLevel()) {
        case PLATFORM ->
            // CosAdmin: no restriction
            orgRepository.delete(targetOrgId);

        case ORG -> {
            // OrgAdmin: must match their own org
            if (!targetOrgId.equals(authCtx.getOrgId())) {
                yield Future.failedFuture(
                    new ForbiddenException("Can only delete your own organisation"));
            }
            yield orgRepository.delete(targetOrgId);
        }

        case SELF ->
            Future.failedFuture(
                new IllegalStateException("SELF tier not applicable here"));
    };
}
```

### 5.4 Walkthrough: three requests to the tiered endpoint

**Request A — CosAdmin deletes `org-xyz`:**

| Step | What happens |
|---|---|
| JWT | `realm_access.roles: ["cos_admin"]` |
| DxUser | `{roles: [COS_ADMIN], orgId: "cos-home"}` |
| Effective scopes | `{org-management, asset-publish, asset-management, user-management, publisher-management, role-management}` |
| Rule 1 (`platform(org-management)`) | Has scope? **yes** → match |
| AuthorizationContext | `{level: PLATFORM, scope: "org-management"}` |
| Service | No filter → deletes `org-xyz` ✓ |

**Request B — OrgAdmin of `org-abc` tries to delete `org-xyz`:**

| Step | What happens |
|---|---|
| JWT | `realm_access.roles: ["org_admin"]`, `organisation_id: "org-abc"` |
| DxUser | `{roles: [ORG_ADMIN], orgId: "org-abc"}` |
| Effective scopes | `{org-user-management, org-asset-management, org-asset-publish, own-asset-management, org-publisher-management}` |
| Rule 1 (`platform(org-management)`) | Has scope? **no** → skip |
| Rule 2 (`org(org-user-management)`) | Has scope? **yes** → match |
| AuthorizationContext | `{level: ORG, scope: "org-user-management", orgId: "org-abc"}` |
| Service | `"org-xyz" ≠ "org-abc"` → **403 Forbidden** ✓ |

**Request C — Same OrgAdmin deletes `org-abc` (their own):**

Same as Request B until the service:

| Step | What happens |
|---|---|
| Service | `"org-abc" == "org-abc"` → deletes `org-abc` ✓ |

### 5.5 Why priority order matters

Consider this **wrong** ordering:

```java
// ❌ WRONG — org rule before platform rule
authHandler.forScopesWithContext(
    ScopeRule.org(Scopes.ORG_USER_MANAGEMENT),
    ScopeRule.platform(Scopes.ORG_MANAGEMENT)
)
```

If a CosAdmin also happened to hold `org-user-management` (say, because they were assigned org-admin in one org for testing), the handler would match the ORG rule first, downgrade them to ORG tier, and prevent them from deleting any org other than that one — even though as CosAdmin they should be able to delete anything.

**Rule:** always list rules from highest to lowest authority. PLATFORM first, ORG second, SELF third.

---

## 6. Endpoint Migration Map

The following tables map every endpoint to its target handler. From Section 7 of the SRS, annotated with handler choice.

### 6.1 AssetController

| Endpoint | Target Scope | Handler |
|---|---|---|
| `POST /asset-request` | `own-asset-management` | `forScopes` |
| `GET /asset-request` (consumer) | `data-access` | `forScopes` |
| `GET /asset-request` (admin) | `asset-management` | `forScopes` |
| `PUT /asset-request` | `asset-management` | `forScopes` |

### 6.2 AccessRequestController

| Endpoint | Target Scope | Handler |
|---|---|---|
| `GET /access-request-for-org-admin` | `org-asset-management` | `forScopes` |
| `GET /access-request-provider` | `own-asset-management` | `forScopes` |
| `PUT /access-request` | `own-asset-management` | `forScopes` |

### 6.3 OrganizationController

| Endpoint | Target Scope | Handler |
|---|---|---|
| `GET /organisations-request` | `org-management` | `forScopes` |
| `POST /approve-create-org` | `org-management` | `forScopes` |
| `GET /organisations-join-requests` | `org-user-management` | `forScopes` |
| `PUT /organisations-join-requests` | `org-user-management` | `forScopes` |
| `DELETE /organisations/{id}` | `org-management` / `org-user-management` | **`forScopesWithContext`** |
| `PUT /organisations/{id}` | `org-management` | `forScopes` |
| `GET /org-users` | `org-user-management` | `forScopes` |
| `GET /organisations/{id}/users/{uid}` | `org-user-management` | `forScopes` |
| `DELETE /organisations-users/{id}` | `org-user-management` | `forScopes` |
| `PUT /organization-users-role` | `org-user-management` | `forScopes` |
| `GET /user-roles` | `org-user-management` | `forScopes` |
| `PUT /user-roles` | `org-user-management` | `forScopes` |
| `POST /organization-user-provider` | `org-publisher-management` | `forScopes` |

### 6.4 AdminController

| Endpoint | Target Scope | Handler |
|---|---|---|
| `GET /user-id-admin` | `user-management` | `forScopes` |
| `GET /admin-user` | `user-management` | `forScopes` |
| `POST /admin-id-update` | `user-management` | `forScopes` |

### 6.5 CreditController

| Endpoint | Target Scope | Handler |
|---|---|---|
| `POST /credit-request` | `compute-access` | `forScopes` |
| `GET /credit` | `user-management` | `forScopes` |
| `PUT /credit-request` | `user-management` | `forScopes` |
| `PUT /user-credit` | `user-management` | `forScopes` |
| `PUT /user-credit-add` | `user-management` | `forScopes` |
| `GET /compute-role-request` | `role-management` | `forScopes` |
| `PUT /compute-role-request` | `role-management` | `forScopes` |
| `GET /admin-user-credit-balance` | `user-management` | `forScopes` |
| `GET /user-credit-balance` | `compute-access` | `forScopes` |

### 6.6 Other Controllers

| Endpoint | Target Scope | Handler |
|---|---|---|
| `PATCH /item` | `org-asset-management` | `forScopes` |
| `GET /items` | `org-asset-management` | `forScopes` |
| `GET /access-request-report` | `own-asset-management` | `forScopes` |
| `GET /access-request-report-org-admin` | `org-asset-management` | `forScopes` |

**Summary:** of 34 endpoints, **33 use plain `forScopes`** and exactly **1 uses `forScopesWithContext`**. This is the typical distribution — tiered endpoints are the exception, not the rule.

---

## 7. Testing Authorization

For every endpoint migration, add integration tests with these three JWT fixtures at minimum:

```java
@Test
void shouldAllowCorrectScope() {
    String jwt = testJwt().withRoles("provider").build();
    given().header("Authorization", "Bearer " + jwt)
           .when().post("/asset-request")
           .then().statusCode(200);
}

@Test
void shouldRejectWrongScope() {
    String jwt = testJwt().withRoles("consumer").build();
    given().header("Authorization", "Bearer " + jwt)
           .when().post("/asset-request")
           .then().statusCode(403);
}

@Test
void shouldRejectMissingToken() {
    given().when().post("/asset-request")
           .then().statusCode(401);
}
```

For tiered endpoints, add tests for each tier:

```java
@Test
void cosAdminCanDeleteAnyOrg() {
    String jwt = testJwt().withRoles("cos_admin").build();
    given().header("Authorization", "Bearer " + jwt)
           .when().delete("/organisations/some-other-org")
           .then().statusCode(204);
}

@Test
void orgAdminCanDeleteOwnOrg() {
    String jwt = testJwt().withRoles("org_admin").withOrgId("org-abc").build();
    given().header("Authorization", "Bearer " + jwt)
           .when().delete("/organisations/org-abc")
           .then().statusCode(204);
}

@Test
void orgAdminCannotDeleteOtherOrg() {
    String jwt = testJwt().withRoles("org_admin").withOrgId("org-abc").build();
    given().header("Authorization", "Bearer " + jwt)
           .when().delete("/organisations/org-xyz")
           .then().statusCode(403);
}
```

---

## 8. FAQ & Gotchas

**Q: Why not check the role directly instead of resolving scopes every request?**
Because it decouples the endpoint from the role. If `ORG_ADMIN` later gains a new scope, every endpoint using that scope automatically picks it up — no code changes. Scope-first is how the SRS (and most modern RBAC systems) are designed to evolve.

**Q: Does scope resolution hit the database?**
No. The `InMemoryRoleScopeRegistry` is a singleton backed by `SystemRoleScopeMap`, which is a static code-level mapping. Lookup is O(1) and involves no I/O. That's why `forScopes` is cheap enough to run on every request.

**Q: What about the top-level `scope` claim in the JWT (SRS §1.2.1)?**
Deferred. For now we only resolve scopes from roles via `realm_access.roles`. The `scope` claim is for supplementary grants (e.g., `own-asset-publish`), which are out of scope for this phase. When we add them, `resolveEffectiveScopes` will union direct scopes with role-flattened scopes — the rest of the architecture won't change.

**Q: A user has two roles. How are scopes combined?**
Union. A user with `[provider, consumer]` has all scopes from both roles. This is handled automatically by `resolveEffectiveScopes`.

**Q: What if a controller currently branches on role to decide the data filter?**
That's the signal the endpoint is tiered. Migrate it to `forScopesWithContext` and push the branching down to the service layer via `AuthorizationContext`. See §5.3.

**Q: Can I use `forRoles` just because it's simpler?**
No. Use `forScopes` by default. `forRoles` is only for genuine identity gates (service accounts, admin panel landing pages) or transitional backward-compatibility code. If you find yourself writing `forRoles(COS_ADMIN)`, ask: "would `forScopes(USER_MANAGEMENT)` work just as well?" It almost always does.

**Q: What happens if the JWT has no roles?**
`DxUser.getRoles()` returns an empty set. `resolveEffectiveScopes` returns an empty set. Any `forScopes` or `forRoles` check fails with 403. There's no "anonymous" bypass path.

**Q: How do I add a new scope?**
1. Add the constant to `Scopes` in `dx-common`.
2. Map it to the appropriate role(s) in `SystemRoleScopeMap`.
3. Use it in route definitions via `forScopes(Scopes.MY_NEW_SCOPE)`.
4. Publish a new `dx-common` version and bump the dependency in both controlplane and dataplane.

**Q: How do I add a new system role?**
1. Add the enum value to `DxRole` in `dx-common`.
2. Add its scope set to `SystemRoleScopeMap`.
3. Create the role in Keycloak (realm-level role with the matching name).
4. Publish a new `dx-common` version.

New system roles are a rare operation and should be reviewed carefully — prefer adding a scope to an existing role if possible.

---

## 9. Quick Reference Card

```
┌─────────────────────────────────────────────────────────────┐
│  CHOOSING A HANDLER                                         │
│                                                             │
│  Single scope, no tiering        → forScopes(ONE)           │
│  Multiple scopes, same boundary  → forScopes(A, B, ...)     │
│  Multiple tiers, diff boundaries → forScopesWithContext()   │
│  Identity gate (rare)            → forRoles(ROLE)           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  MENTAL TEST                                                │
│                                                             │
│  "Would the SQL for this endpoint be the same               │
│   regardless of who called it?"                             │
│                                                             │
│  YES → forScopes                                            │
│  NO  → forScopesWithContext                                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  PRIORITY ORDER (forScopesWithContext)                      │
│                                                             │
│  PLATFORM first → ORG second → SELF third                   │
│  (highest authority to lowest; first match wins)            │
└─────────────────────────────────────────────────────────────┘
```
