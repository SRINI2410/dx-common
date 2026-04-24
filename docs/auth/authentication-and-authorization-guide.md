# Authentication & Authorization — Development Guide

**Audience:** Backend engineers working on `dx-controlplane`, `dx-dataplane-rs`, and `dx-common`.

**Scope:** Full authentication and authorization architecture covering three authentication paths (plain user JWT, delegation, app credentials) and scope-based authorization with five system roles.

**Status:** Design reference for the migration from role-based to scope-based authorization.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Concepts](#2-core-concepts)
3. [The DxPrincipal Model](#3-the-dxprincipal-model)
4. [Authentication Flow](#4-authentication-flow)
5. [Authentication Path 1 — Plain User](#5-authentication-path-1--plain-user)
6. [Authentication Path 2 — Delegation](#6-authentication-path-2--delegation)
7. [Authentication Path 3 — App Credentials](#7-authentication-path-3--app-credentials)
8. [Authorization — Roles & Scopes](#8-authorization--roles--scopes)
9. [Authorization Handler API](#9-authorization-handler-api)
10. [End-to-End Request Walkthroughs](#10-end-to-end-request-walkthroughs)
11. [Data Model](#11-data-model)
12. [Audit Logging](#12-audit-logging)
13. [Testing](#13-testing)
14. [Development Phases](#14-development-phases)
15. [FAQ](#15-faq)

---

## 1. Overview

The platform uses a two-layer security model:

1. **Authentication** — determines *who is making the request*. There are three supported paths:
   - **Plain user:** a user logs in, gets a JWT from Keycloak, and calls APIs directly.
   - **Delegation:** user A grants user B permission to act on A's behalf. B calls APIs with B's own JWT plus an `X-Delegator-Id` header naming A.
   - **App credentials:** a user creates an "app" with a scoped set of permissions. The app calls APIs with `X-App-Id` and `X-App-Secret` headers (no JWT).

2. **Authorization** — determines *what the authenticated principal is allowed to do*. Authorization is scope-based: each endpoint requires one or more scopes, and the authorization handler checks whether the principal's effective scope set satisfies the requirement.

**The key architectural property:** authentication paths are plural, but authorization is singular. All three authentication paths converge on a uniform `DxPrincipal` object with a resolved scope set. The authorization handler doesn't know or care which path the caller came in through — it just checks scopes.

```
┌──────────────────┐
│  Plain user JWT  │──┐
└──────────────────┘  │
                      │    ┌─────────────────┐    ┌──────────────────┐    ┌────────────┐
┌──────────────────┐  ├───>│  DxPrincipal    │───>│  Authorization   │───>│ Controller │
│  JWT + Delegator │──┤    │  (uniform)      │    │  forScopes(...)  │    │            │
└──────────────────┘  │    └─────────────────┘    └──────────────────┘    └────────────┘
                      │
┌──────────────────┐  │
│ AppId + Secret   │──┘
└──────────────────┘
```

---

## 2. Core Concepts

### 2.1 Role vs. Scope

| Term | What it is | Example |
|---|---|---|
| **Role** | An identity label. Roles are bundles of scopes. | `cos_admin`, `org_admin`, `provider` |
| **Scope** | A named capability — "what the principal may do." | `user-management`, `own-asset-management` |
| **Effective scopes** | The final scope set used for authorization, resolved per request. | `{data-access, own-asset-management}` |

**Guiding principle:** authorize on *capabilities* (scopes), not on *identity* (roles). Roles exist for assignment; scopes exist for enforcement.

### 2.2 The Five System Roles

Defined in `dx-common` as the `DxRole` enum. Pre-configured in Keycloak. Immutable — cannot be created, deprecated, or modified through any API.

| Role | Authority | Purpose |
|---|---|---|
| `CONSUMER` | Base | Browse and download published assets |
| `PROVIDER` | Asset | Manage own assets and approve access requests |
| `ORG_ADMIN` | Organisation | Manage users, assets, and publishers within one org |
| `COS_ADMIN` | Platform | Manage orgs, users, assets, and roles platform-wide |
| `COMPUTE` | Service | Access compute resources and credit workloads |

### 2.3 The System Scopes

All 13 scopes defined in the `Scopes` constants class.

| Scope | Assigned to role(s) | Grants |
|---|---|---|
| `data-access` | Consumer | Read catalogue, download authorised assets |
| `own-asset-management` | Provider, OrgAdmin | Create/update/delete own assets |
| `org-user-management` | OrgAdmin | Manage users within an org |
| `org-asset-management` | OrgAdmin | Manage all assets within an org |
| `org-asset-publish` | OrgAdmin | Publish/unpublish org assets |
| `org-publisher-management` | OrgAdmin | Approve publisher grants within org |
| `org-management` | CosAdmin | Create/suspend/delete organisations |
| `asset-publish` | CosAdmin | Publish/unpublish any asset platform-wide |
| `asset-management` | CosAdmin | Manage any asset on the platform |
| `user-management` | CosAdmin | Manage users platform-wide |
| `publisher-management` | CosAdmin | Approve publisher grants platform-wide |
| `role-management` | CosAdmin | Manage the role and scope schema |
| `compute-access` | Compute | Request credits, invoke compute workloads |

### 2.4 The Role → Scope Mapping

Defined in `SystemRoleScopeMap` in `dx-common`. Authoritative and code-level — not in the database.

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

## 3. The DxPrincipal Model

`DxPrincipal` is the uniform object representing the authenticated caller. All three authentication paths produce a `DxPrincipal`, and all downstream code reads it without caring which path built it.

### 3.1 Class shape

```java
public class DxPrincipal {
    
    // ─── Raw authenticated identity (from JWT or app credentials) ───
    
    private String authenticatedSub;       // user sub from JWT, or app owner's sub
    private String authenticatedOrgId;     // org from JWT, or app owner's org
    
    // ─── Delegation context (present only when acting as a delegator) ───
    
    private String delegatorSub;           // null unless delegation
    private String delegatorOrgId;         // null unless delegation
    
    // ─── Authorization ───
    
    private Set<DxRole> authorizationRoles;  // USER only; empty for DELEGATED_USER, APP
    private Set<String> directScopes;        // final resolved scopes (capped for delegation/app)
    
    // ─── Audit context (never read by authorization) ───
    
    private Set<DxRole> auditRoles;          // sub's roles, for log enrichment
    private String appId;                    // null unless APP
    
    // ─── Effective getters (what downstream code uses) ───
    
    /**
     * The effective identity whose data and authority applies to this request.
     * For delegation: the delegator. For apps: the owner. For plain users: themselves.
     * Use this in services, repositories, and business queries.
     */
    public String getSub() {
        return delegatorSub != null ? delegatorSub : authenticatedSub;
    }
    
    /**
     * The effective organisation whose boundary applies to this request.
     * Services filtering by "my org" should use this.
     */
    public String getOrganisationId() {
        return delegatorOrgId != null ? delegatorOrgId : authenticatedOrgId;
    }
    
    // ─── Raw getters (audit only) ───
    
    /** The user who actually presented credentials. For audit logging only. */
    public String getAuthenticatedSub() { return authenticatedSub; }
    
    /** The actual org of the authenticating user. For audit logging only. */
    public String getAuthenticatedOrgId() { return authenticatedOrgId; }
    
    // ─── Type detection ───
    
    public boolean isDelegation() { return delegatorSub != null; }
    public boolean isApp()        { return appId != null; }
    public boolean isDirectUser() { return !isDelegation() && !isApp(); }
    
    // ─── Audit field getters ───
    
    public Set<DxRole> getAuditRoles() { return auditRoles; }
    public String getAppId() { return appId; }
    
    // ─── Authorization accessors (used only by RoleScopeRegistry) ───
    
    public Set<DxRole> getAuthorizationRoles() { return authorizationRoles; }
    public Set<String> getDirectScopes() { return directScopes; }
}
```

### 3.2 Design principles

**One request, one identity.** Every request produces exactly one `DxPrincipal` with exactly one effective sub and one effective orgId. There is no merging of identities, no "acting as multiple people," no ambiguity.

**Raw vs. effective identity is explicit.** `authenticatedSub` / `authenticatedOrgId` hold the raw credentials-presenter. `getSub()` / `getOrganisationId()` return the effective identity (delegator if delegation, else authenticated). Downstream code calls the effective getters; audit code can reach either.

**Roles only flatten for plain users.** `authorizationRoles` is populated only for the `USER` path. For delegation and apps, scopes are pre-computed and stored in `directScopes`. The authorization handler runs one uniform algorithm that produces the right answer for all three paths.

**`auditRoles` is separate from `authorizationRoles`.** This prevents accidentally flattening an audit-only role set into the effective scopes. Authorization logic never touches `auditRoles`.

---

## 4. Authentication Flow

```
Request
   │
   ▼
┌──────────────────────────────────┐
│  AuthenticationHandler           │   ◄── Dispatches based on request headers
│  (entry point)                   │
└──────────────────────────────────┘
   │
   ├──► JwtPrincipalResolver       ── Path 1 (plain user)
   ├──► DelegationResolver          ── Path 2 (delegation)
   └──► AppCredentialsResolver      ── Path 3 (app)
        │
        ▼
┌──────────────────────────────────┐
│  DxPrincipal on RoutingContext   │   ◄── Uniform output
└──────────────────────────────────┘
   │
   ▼
┌──────────────────────────────────┐
│  AuthorizationHandler            │   ◄── forScopes / forScopesWithContext
│  (path-agnostic)                 │
└──────────────────────────────────┘
   │
   ▼
Controller → Service → Repository
```

### 4.1 The dispatcher

`AuthenticationHandler` inspects incoming request headers and picks exactly one resolver:

```java
public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    String appIdHeader = ctx.request().getHeader("X-App-Id");
    String delegatorHeader = ctx.request().getHeader("X-Delegator-Id");
    
    // Reject ambiguous credentials
    if (appIdHeader != null && authHeader != null) {
        ctx.fail(400, "Ambiguous credentials: send either JWT or X-App-Id, not both");
        return;
    }
    
    // App path
    if (appIdHeader != null) {
        appResolver.resolve(ctx);
        return;
    }
    
    // JWT-based paths
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        if (delegatorHeader != null) {
            delegationResolver.resolve(ctx);   // Path 2
        } else {
            jwtResolver.resolve(ctx);          // Path 1
        }
        return;
    }
    
    ctx.fail(401, "Missing credentials");
}
```

### 4.2 Resolver responsibilities

Each resolver does three things:

1. **Validate credentials.** Cryptographic (JWT signature, secret hash) and logical (delegation active, app not revoked).
2. **Compute the effective scope set.** For delegation and apps, this includes capping against the delegator's/owner's current scopes.
3. **Build a `DxPrincipal`.** Set all fields, attach to routing context, call `next()`.

If any step fails, the resolver fails the request with 401 or 403 as appropriate and does not call `next()`.

---

## 5. Authentication Path 1 — Plain User

The simplest path. A user logs in through Keycloak, receives a JWT, and calls APIs with it.

### 5.1 JWT structure

Tokens follow RFC 9068 (JWT Profile for OAuth 2.0 Access Tokens). Relevant claims:

```json
{
  "sub": "user-uuid",
  "iss": "https://keycloak.example.com/realms/dx",
  "realm_access": { "roles": ["org_admin", "provider"] },
  "organisation_id": "org-uuid",
  "kyc_verified": true,
  "exp": 1700000000
}
```

The `scope` claim (for supplementary direct grants) is deferred to a later phase.

### 5.2 Resolver logic

```java
public class JwtPrincipalResolver {
    
    public void resolve(RoutingContext ctx) {
        // JWT signature was already validated by KeycloakJwtAuthHandler
        User jwtUser = ctx.user();
        JsonObject claims = jwtUser.principal();
        
        String sub = claims.getString("sub");
        String orgId = claims.getString("organisation_id");
        Set<DxRole> roles = parseRoles(claims.getJsonObject("realm_access"));
        
        DxPrincipal principal = DxPrincipal.builder()
            .authenticatedSub(sub)
            .authenticatedOrgId(orgId)
            .delegatorSub(null)
            .delegatorOrgId(null)
            .authorizationRoles(roles)
            .directScopes(Set.of())
            .auditRoles(roles)
            .appId(null)
            .build();
        
        ctx.put("principal", principal);
        ctx.next();
    }
    
    private Set<DxRole> parseRoles(JsonObject realmAccess) {
        if (realmAccess == null) return Set.of();
        JsonArray rolesArray = realmAccess.getJsonArray("roles");
        if (rolesArray == null) return Set.of();
        return rolesArray.stream()
            .map(Object::toString)
            .map(DxRole::fromKeycloakName)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }
}
```

### 5.3 What the principal looks like

```java
// Example: Alice, an OrgAdmin in org-a, logs in and calls GET /org-users
principal.getSub()                  // "alice-uuid"
principal.getOrganisationId()       // "org-a-uuid"
principal.getAuthorizationRoles()   // {ORG_ADMIN}
principal.getDirectScopes()         // {} (empty — scopes come from roles)
principal.getAuditRoles()           // {ORG_ADMIN}
principal.isDirectUser()            // true
principal.isDelegation()            // false
principal.isApp()                   // false
```

When the authorization handler calls `resolveEffectiveScopes(principal)`, it flattens `{ORG_ADMIN}` to `{org-user-management, org-asset-management, org-asset-publish, own-asset-management, org-publisher-management}` and checks whether the endpoint's required scopes are present.

---

## 6. Authentication Path 2 — Delegation

A user (delegator) grants another user (delegatee) permission to act on their behalf with a chosen subset of scopes. The delegatee uses their own JWT to authenticate and includes an `X-Delegator-Id` header to signal "I am acting as this delegator for this request."

### 6.1 Core rules

1. **Delegation is per-request.** The delegatee is only treated as acting for the delegator when they send the `X-Delegator-Id` header. Without the header, they're acting as themselves.
2. **One request = one identity.** The delegatee is never both themselves and the delegator simultaneously. Each request is either "as self" or "as delegator X" — never a union.
3. **Delegated scopes are capped by the delegator's current scopes.** If the delegator loses a role after the delegation is created, the delegation's effective scopes shrink immediately.
4. **`sub` switches to the delegator.** Queries filter by the delegator's id and org. The delegatee's id is preserved only in audit fields.
5. **No sub-delegation.** A delegatee cannot further delegate. Delegations are one level deep.

### 6.2 Delegation scope types

When a delegation is created, the delegator chooses one of:

- **Full delegation:** the delegatee receives all of the delegator's current scopes. If the delegator later gains or loses roles, the delegation's effective scopes follow automatically.
- **Partial delegation:** the delegator picks a specific subset of scopes to grant. The delegatee cannot exceed this subset even if the delegator has more scopes.

In both cases, the final effective scopes are capped by the delegator's current role-derived scopes at request time.

### 6.3 Resolver logic

```java
public class DelegationResolver {
    
    private final DelegationRepository delegationRepo;
    private final UserRepository userRepo;
    private final RoleScopeRegistry registry;
    
    public void resolve(RoutingContext ctx) {
        // 1. Validate the delegatee's JWT
        User jwtUser = ctx.user();
        JsonObject claims = jwtUser.principal();
        String delegateeSub = claims.getString("sub");
        String delegateeOrgId = claims.getString("organisation_id");
        
        // 2. Read the delegator header
        String delegatorSub = ctx.request().getHeader("X-Delegator-Id");
        
        // 3. Look up the active delegation
        delegationRepo.findActive(delegatorSub, delegateeSub)
            .onFailure(err -> ctx.fail(500, err))
            .onSuccess(delegation -> {
                if (delegation == null) {
                    ctx.fail(403, "No active delegation from " + delegatorSub);
                    return;
                }
                if (delegation.isExpired()) {
                    ctx.fail(403, "Delegation has expired");
                    return;
                }
                
                // 4. Load the delegator to get their current roles and org
                userRepo.findById(delegatorSub)
                    .onFailure(err -> ctx.fail(500, err))
                    .onSuccess(delegator -> {
                        if (delegator == null || delegator.isDisabled()) {
                            ctx.fail(403, "Delegator is no longer active");
                            return;
                        }
                        
                        // 5. Compute effective scopes (capped)
                        Set<String> delegatorCurrentScopes = 
                            flatten(delegator.getRoles());
                        
                        Set<String> requestedScopes = delegation.isFullDelegation()
                            ? delegatorCurrentScopes
                            : new HashSet<>(delegation.getScopes());
                        
                        Set<String> effective = intersect(
                            requestedScopes, 
                            delegatorCurrentScopes
                        );
                        
                        // 6. Build the principal
                        DxPrincipal principal = DxPrincipal.builder()
                            .authenticatedSub(delegateeSub)       // delegatee
                            .authenticatedOrgId(delegateeOrgId)   // delegatee's real org
                            .delegatorSub(delegatorSub)            // delegator
                            .delegatorOrgId(delegator.getOrganisationId())
                            .authorizationRoles(Set.of())          // empty
                            .directScopes(effective)               // capped set
                            .auditRoles(delegator.getRoles())      // delegator's roles
                            .appId(null)
                            .build();
                        
                        ctx.put("principal", principal);
                        ctx.next();
                    });
            });
    }
    
    private Set<String> flatten(Set<DxRole> roles) {
        Set<String> result = new HashSet<>();
        for (DxRole role : roles) {
            result.addAll(SystemRoleScopeMap.getScopes(role));
        }
        return result;
    }
    
    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
```

### 6.4 What the principal looks like

```java
// Example: Bob (in org-b) has a partial delegation from Alice (in org-a)
// granting only {data-access}. Bob calls GET /assets with X-Delegator-Id: alice.

principal.getSub()                  // "alice-uuid"     ← delegator
principal.getOrganisationId()       // "org-a-uuid"     ← delegator's org
principal.getAuthenticatedSub()     // "bob-uuid"       ← actual authenticator
principal.getAuthenticatedOrgId()   // "org-b-uuid"     ← Bob's actual org
principal.getAuthorizationRoles()   // {}
principal.getDirectScopes()         // {data-access}    ← capped
principal.getAuditRoles()           // {ORG_ADMIN, PROVIDER}  ← Alice's roles
principal.isDirectUser()            // false
principal.isDelegation()            // true
principal.isApp()                   // false
```

Queries downstream use `principal.getSub()` and `principal.getOrganisationId()`, which return Alice's identity. Bob is acting as Alice for this request — he sees Alice's data in Alice's org. Bob's identity is preserved only in the audit fields.

### 6.5 Caching

Delegation lookups will be on the hot path. Cache `(delegatorSub, delegateeSub) → DelegationRecord` in an in-memory cache with a short TTL (30–60 seconds).

On delegation revocation, publish a cache invalidation event (via RabbitMQ, which is already in the stack) to evict the entry. This keeps per-request auth overhead low while keeping revocation latency bounded.

**Acceptable revocation window:** 30 seconds default. Document this in operator docs.

### 6.6 Cross-org delegation

A delegatee in org X can be delegated to by a delegator in org Y. This is supported and works naturally:

- Without the header: delegatee is in org X, sees org X data
- With the header: delegatee is acting as delegator in org Y, sees org Y data

The delegatee never sees both orgs in a single request. Per the "one request, one identity" rule, they're operating in exactly one org context per call, and which one is determined by whether they sent the header.

---

## 7. Authentication Path 3 — App Credentials

A user creates an app (a non-human service account) with a scoped set of permissions. The app authenticates with `X-App-Id` and `X-App-Secret` headers. No JWT is involved.

### 7.1 Core rules

1. **Apps authenticate AS their owner.** `getSub()` returns the owner's id. Queries run as the owner. The app is just a credential pair; the owner is the identity.
2. **App scopes are capped by the owner's current scopes.** Same freshness rule as delegation.
3. **Apps have their own identifier in audit.** `appId` is preserved in the audit field so logs can distinguish "ananjay (user session)" from "ananjay (via analytics-worker app)".
4. **Secret is hashed, never stored plaintext.** Standard bcrypt/argon2 hygiene.
5. **Apps are revocable independently.** Revoking an app doesn't affect the owner's user session or other apps.

### 7.2 Resolver logic

```java
public class AppCredentialsResolver {
    
    private final AppRepository appRepo;
    private final AppScopeRepository appScopeRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    
    public void resolve(RoutingContext ctx) {
        String appId = ctx.request().getHeader("X-App-Id");
        String secret = ctx.request().getHeader("X-App-Secret");
        
        if (secret == null || secret.isEmpty()) {
            ctx.fail(401, "Missing X-App-Secret");
            return;
        }
        
        // 1. Look up the app
        appRepo.findByAppId(appId)
            .onFailure(err -> ctx.fail(500, err))
            .onSuccess(app -> {
                if (app == null || app.isRevoked()) {
                    ctx.fail(401, "Invalid credentials");
                    return;
                }
                
                // 2. Verify the secret
                if (!passwordEncoder.matches(secret, app.getSecretHash())) {
                    // TODO: rate-limit failed attempts per appId and IP
                    ctx.fail(401, "Invalid credentials");
                    return;
                }
                
                // 3. Load the owner
                userRepo.findById(app.getOwnerUserId())
                    .onFailure(err -> ctx.fail(500, err))
                    .onSuccess(owner -> {
                        if (owner == null || owner.isDisabled()) {
                            ctx.fail(403, "App owner is no longer active");
                            return;
                        }
                        
                        // 4. Load app's stored scopes and cap against owner's current scopes
                        appScopeRepo.findByAppId(app.getId())
                            .onSuccess(appStoredScopes -> {
                                Set<String> ownerCurrentScopes = 
                                    flatten(owner.getRoles());
                                
                                Set<String> effective = intersect(
                                    appStoredScopes,
                                    ownerCurrentScopes
                                );
                                
                                // 5. Build the principal
                                DxPrincipal principal = DxPrincipal.builder()
                                    .authenticatedSub(owner.getSub())     // acts AS owner
                                    .authenticatedOrgId(owner.getOrganisationId())
                                    .delegatorSub(null)
                                    .delegatorOrgId(null)
                                    .authorizationRoles(Set.of())          // empty
                                    .directScopes(effective)
                                    .auditRoles(owner.getRoles())
                                    .appId(app.getAppId())                  // audit
                                    .build();
                                
                                ctx.put("principal", principal);
                                ctx.next();
                            });
                    });
            });
    }
}
```

### 7.3 What the principal looks like

```java
// Example: App "analytics-worker" owned by Ananjay calls GET /own-assets

principal.getSub()                  // "ananjay-uuid"   ← owner
principal.getOrganisationId()       // "org-a-uuid"     ← owner's org
principal.getAuthenticatedSub()     // "ananjay-uuid"   ← same as sub
principal.getAuthenticatedOrgId()   // "org-a-uuid"     ← same
principal.getAuthorizationRoles()   // {}
principal.getDirectScopes()         // {own-asset-management}  ← capped
principal.getAuditRoles()           // {PROVIDER}       ← owner's roles
principal.getAppId()                // "analytics-worker"
principal.isDirectUser()            // false
principal.isDelegation()            // false
principal.isApp()                   // true
```

Queries run as Ananjay. The app is just a credential wrapper around Ananjay's identity with a reduced scope set. The `appId` field lets audit logs distinguish this from Ananjay's personal usage.

### 7.4 Caching

Cache `appId → (App, Set<scope>)` with a short TTL (30–60 seconds). Invalidate on revoke via a message-queue event. Same pattern as delegation.

### 7.5 Rate limiting

Failed secret attempts must be rate-limited per `appId` AND per source IP. Apps are attractive brute-force targets because no human is watching for lockout notifications. A typical policy: lock an app after 10 consecutive failures in 5 minutes, log the event, alert the owner via email.

### 7.6 Secret lifecycle

- On creation: the platform generates a cryptographically random secret (at least 32 bytes), returns it to the creator **once**, and stores only the hash. The plaintext is never persisted.
- On rotation: the creator calls a rotation endpoint, receives a new plaintext once, and the old hash is replaced.
- On revoke: the app record is marked revoked, the cache is invalidated, and subsequent requests fail with 401.

---

## 8. Authorization — Roles & Scopes

Authorization runs after authentication. The principal is already built; the authorization handler just checks scopes.

### 8.1 Effective scope resolution

The `RoleScopeRegistry` provides a single method that produces the effective scope set for any principal:

```java
public class InMemoryRoleScopeRegistry implements RoleScopeRegistry {
    
    public Set<String> resolveEffectiveScopes(DxPrincipal principal) {
        Set<String> effective = new HashSet<>();
        
        // Flatten roles (only populated for USER path)
        for (DxRole role : principal.getAuthorizationRoles()) {
            effective.addAll(SystemRoleScopeMap.getScopes(role));
        }
        
        // Add direct scopes (pre-computed for DELEGATED_USER and APP)
        effective.addAll(principal.getDirectScopes());
        
        return effective;
    }
}
```

**How this handles each path uniformly:**

| Path | `authorizationRoles` | `directScopes` | Effective scopes |
|---|---|---|---|
| USER | `{PROVIDER}` | `{}` | `{own-asset-management}` (from flattening) |
| DELEGATED_USER | `{}` | `{data-access}` | `{data-access}` (capped by resolver) |
| APP | `{}` | `{own-asset-management}` | `{own-asset-management}` (capped by resolver) |

One function, one line of flattening, correct answer for all three paths. There is no type branching in the authorization hot path.

### 8.2 Why roles are empty for delegation and apps

This is a critical invariant. If the delegator's or owner's roles were copied into `authorizationRoles` for delegation/app principals, flattening them would re-grant *all* of their scopes, silently bypassing the delegation/app scope cap. That would be a security bug.

The resolvers explicitly pass `authorizationRoles = Set.of()` for delegation and app principals. The capped scope set lives in `directScopes`. The auditing story is handled by the separate `auditRoles` field, which the authorization handler never reads.

### 8.3 Why this is fast

- JWT parsing: already done by `KeycloakJwtAuthHandler` (for JWT paths)
- Role → scope lookup: O(1) in an in-memory `HashMap`
- Set union: O(n) where n is the number of scopes (small, typically < 10)
- Set intersection (for scope check): O(m) where m is the number of required scopes

No database queries. No network calls. Authorization adds microseconds, not milliseconds.

---

## 9. Authorization Handler API

A single `AuthorizationHandler` class in `dx-common` exposes the handler factory methods used at route declaration time.

### 9.1 The class

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

### 9.2 `forScopes(String... required)` — the default

Passes if the principal has **any** of the required scopes.

**Semantics:** "Caller must have at least one of these capabilities."

**When to use:** single-scope endpoints, or multi-scope endpoints where the data boundary is the same regardless of caller.

```java
public Handler<RoutingContext> forScopes(String... required) {
    return ctx -> {
        DxPrincipal principal = ctx.get("principal");
        Set<String> effective = registry.resolveEffectiveScopes(principal);
        
        for (String req : required) {
            if (effective.contains(req)) {
                ctx.next();
                return;
            }
        }
        ctx.fail(403, "Insufficient scope");
    };
}
```

**Example:**
```java
router.post("/asset-request")
    .handler(authHandler.forScopes(Scopes.OWN_ASSET_MANAGEMENT))
    .handler(assetController::create);
```

### 9.3 `forScopesWithContext(ScopeRule... rules)` — for tiered endpoints

Use when a **single endpoint serves multiple authority tiers with different data boundaries**. The handler walks rules in priority order; on first match, it sets an `AuthorizationContext` on the routing context that the service reads to apply the correct filter.

**Semantics:** "Caller may be at tier A or tier B; tell the service which tier so it can filter accordingly."

```java
public enum AuthLevel { PLATFORM, ORG, SELF }

public final class ScopeRule {
    public static ScopeRule platform(String scope);  // no data filter
    public static ScopeRule org(String scope);       // filter by caller's orgId
    public static ScopeRule self(String scope);      // filter by caller's sub
}

public final class AuthorizationContext {
    AuthLevel getLevel();
    String getScope();
    String getOrgId();
    String getSub();
}
```

**Priority order matters:** list rules from highest to lowest authority (PLATFORM → ORG → SELF). The first matching rule wins. Putting ORG before PLATFORM would let a CosAdmin who also holds an org-level scope be accidentally downgraded to ORG tier.

**Example:**
```java
router.delete("/organisations/:id")
    .handler(authHandler.forScopesWithContext(
        ScopeRule.platform(Scopes.ORG_MANAGEMENT),       // priority 1
        ScopeRule.org(Scopes.ORG_USER_MANAGEMENT)         // priority 2
    ))
    .handler(orgController::delete);
```

The controller reads `AuthorizationContext` from the routing context and passes it to the service. The service applies the appropriate filter based on `authCtx.getLevel()`.

### 9.4 `forRoles(DxRole... required)` — rare

Use sparingly. Only for genuine identity gates or during backward-compatibility migration.

**Semantics:** "Caller must hold one of these specific roles, regardless of scopes."

**Legitimate use cases:**
- Service-account-only endpoints (e.g., meaningful only for `COMPUTE`)
- Admin-panel identity gates where "you must be a CosAdmin" is the actual requirement
- Transitional code during migration

**After migration, `forRoles` should appear in fewer than 5 places.** If you're reaching for it frequently, you probably want `forScopes` instead.

### 9.5 Decision tree

```
Is the endpoint reachable by more than one role/scope?
│
├─ No → forScopes(SINGLE_SCOPE)
│
└─ Yes → Does the data boundary change based on caller?
         (Does the SQL query differ between callers?)
         │
         ├─ No  → forScopes(SCOPE_A, SCOPE_B, ...)
         │
         └─ Yes → forScopesWithContext(
                    ScopeRule.platform(...),
                    ScopeRule.org(...),
                    ScopeRule.self(...)
                  )

Exception: identity gate (rare)
└─ forRoles(ROLE) — document why
```

**Mental test:** *"Would the SQL for this endpoint be the same regardless of who called it?"*
- **Yes** → `forScopes`
- **No** → `forScopesWithContext`

---

## 10. End-to-End Request Walkthroughs

### 10.1 Walkthrough: Plain user request

**Request:** Alice (OrgAdmin in org-a) calls `GET /org-users`

```
Authorization: Bearer <alice-jwt>
```

**Flow:**

1. `AuthenticationHandler` sees JWT, no `X-App-Id`, no `X-Delegator-Id` → dispatches to `JwtPrincipalResolver`
2. `JwtPrincipalResolver` parses the JWT, builds principal:
   ```
   authenticatedSub = alice
   authenticatedOrgId = org-a
   authorizationRoles = {ORG_ADMIN}
   directScopes = {}
   auditRoles = {ORG_ADMIN}
   ```
3. `AuthorizationHandler.forScopes(ORG_USER_MANAGEMENT)` runs:
   - `resolveEffectiveScopes` flattens `{ORG_ADMIN}` → `{org-user-management, org-asset-management, org-asset-publish, own-asset-management, org-publisher-management}`
   - `org-user-management` is present → pass
4. Controller runs: `orgUserService.listByOrg(principal.getOrganisationId())`
5. Service queries `SELECT * FROM users WHERE organisation_id = 'org-a'`
6. Returns Alice's org users

### 10.2 Walkthrough: Delegation request

**Setup:** Alice (in org-a, OrgAdmin) delegated `{data-access}` to Bob (in org-b, Provider).

**Request:** Bob calls `GET /assets` with `X-Delegator-Id: alice`

```
Authorization: Bearer <bob-jwt>
X-Delegator-Id: alice
```

**Flow:**

1. `AuthenticationHandler` sees JWT + `X-Delegator-Id` → dispatches to `DelegationResolver`
2. `DelegationResolver`:
   - Validates Bob's JWT → `delegateeSub = bob`, `delegateeOrgId = org-b`
   - Looks up delegation `(alice → bob)` in Postgres → found, active, partial with scopes `{data-access}`
   - Loads Alice from `userRepo` → `aliceRoles = {ORG_ADMIN}`, `aliceOrgId = org-a`
   - Computes Alice's current scopes: flatten `{ORG_ADMIN}` → `{org-user-management, org-asset-management, ...}`
   - Caps: `{data-access} ∩ {alice's current} = {data-access}` (data-access is NOT in Alice's current set actually — OrgAdmin doesn't have data-access!)

**Wait — this example exposes an important subtlety.** Let me fix the setup: Alice is actually a Consumer + OrgAdmin, so `aliceRoles = {CONSUMER, ORG_ADMIN}`, giving her `{data-access, org-user-management, ...}`. The delegation of `{data-access}` then intersects cleanly.

3. Builds principal:
   ```
   authenticatedSub = bob           (from JWT)
   authenticatedOrgId = org-b       (Bob's real org)
   delegatorSub = alice
   delegatorOrgId = org-a
   authorizationRoles = {}           (empty!)
   directScopes = {data-access}     (capped)
   auditRoles = {CONSUMER, ORG_ADMIN}  (Alice's roles)
   ```
4. `forScopes(DATA_ACCESS)`:
   - `resolveEffectiveScopes` = `flatten({}) ∪ {data-access}` = `{data-access}`
   - `data-access` is present → pass
5. Controller runs: `assetService.list(principal.getSub(), principal.getOrganisationId())`
   - `getSub()` returns `alice`
   - `getOrganisationId()` returns `org-a`
6. Service queries `SELECT * FROM assets WHERE visible_to = 'alice' AND organisation_id = 'org-a'`
7. Returns Alice's accessible assets (not Bob's)

Bob sees Alice's data in org-a, not his own data in org-b. His identity is preserved only in `authenticatedSub` and `authenticatedOrgId` for audit.

### 10.3 Walkthrough: Same user, same endpoint, no delegator header

**Request:** Bob calls `GET /assets` **without** `X-Delegator-Id`

```
Authorization: Bearer <bob-jwt>
```

**Flow:**

1. `AuthenticationHandler` sees JWT, no header → dispatches to `JwtPrincipalResolver`
2. Bob's plain principal:
   ```
   authenticatedSub = bob
   authenticatedOrgId = org-b
   authorizationRoles = {PROVIDER}
   directScopes = {}
   ```
3. `forScopes(DATA_ACCESS)`:
   - Flattens `{PROVIDER}` → `{own-asset-management}`
   - `data-access` NOT present → **403 Forbidden**

Bob, as himself, is a Provider — he doesn't have `data-access`, so he can't list assets via this endpoint. Only his delegation from Alice grants him that scope, and only for requests where he explicitly invokes the delegation via the header.

**Key takeaway:** the delegation from Alice is dormant unless Bob sends the header. Bob operates as Bob by default, and switches to "acting as Alice" only when he explicitly requests it per-call.

### 10.4 Walkthrough: App request

**Setup:** Ananjay creates app `analytics-worker` with scopes `{own-asset-management}`. Ananjay is a Provider.

**Request:**

```
X-App-Id: analytics-worker
X-App-Secret: <secret>
```

**Flow:**

1. `AuthenticationHandler` sees `X-App-Id` → dispatches to `AppCredentialsResolver`
2. Resolver:
   - Looks up app → found, not revoked
   - Verifies secret → matches
   - Loads owner Ananjay → `ownerRoles = {PROVIDER}`, `ownerOrgId = org-a`
   - Computes owner's current scopes: `{own-asset-management}`
   - Caps app scopes: `{own-asset-management} ∩ {own-asset-management} = {own-asset-management}`
3. Builds principal:
   ```
   authenticatedSub = ananjay       (acts as owner)
   authenticatedOrgId = org-a
   authorizationRoles = {}
   directScopes = {own-asset-management}
   auditRoles = {PROVIDER}
   appId = analytics-worker
   ```
4. `forScopes(OWN_ASSET_MANAGEMENT)` → pass
5. Controller runs as `principal.getSub() = ananjay`
6. Service queries Ananjay's assets
7. Returns assets

Downstream is identical to Ananjay calling the endpoint himself. The only difference is `principal.getAppId()` is populated, which the audit logger picks up to distinguish "Ananjay via analytics-worker" from "Ananjay's direct session."

---

## 11. Data Model

### 11.1 Tables used by authentication

**`delegations`** — stores delegation records

```sql
CREATE TABLE delegations (
    id UUID PRIMARY KEY,
    delegator_id UUID NOT NULL,
    delegatee_id UUID NOT NULL,
    delegated_scopes TEXT[] NOT NULL,         -- NULL array means "full delegation"
    is_full_delegation BOOLEAN NOT NULL,
    status VARCHAR NOT NULL CHECK (status IN ('active','revoked','expired')),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP NULL,
    UNIQUE (delegator_id, delegatee_id, status) 
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_delegations_delegatee_active 
    ON delegations(delegatee_id, delegator_id) 
    WHERE status = 'active';
```

**`apps`** — app records

```sql
CREATE TABLE apps (
    id UUID PRIMARY KEY,
    app_id VARCHAR UNIQUE NOT NULL,
    secret_hash VARCHAR NOT NULL,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    organisation_id UUID NOT NULL,
    name VARCHAR NOT NULL,
    description TEXT,
    status VARCHAR NOT NULL CHECK (status IN ('active','revoked')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP NULL
);

CREATE INDEX idx_apps_app_id_active 
    ON apps(app_id) 
    WHERE status = 'active';

CREATE INDEX idx_apps_owner 
    ON apps(owner_user_id);
```

**`app_scopes`** — app-to-scope mapping

```sql
CREATE TABLE app_scopes (
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    scope_name VARCHAR NOT NULL,
    PRIMARY KEY (app_id, scope_name)
);
```

### 11.2 No tables for role-scope mapping

System role-to-scope mappings live in code (`SystemRoleScopeMap`), not in the database. This is intentional:

- Authoritative source is the compiled jar, versioned with the codebase
- No database round-trip for authorization checks
- Zero chance of drift between deployed code and database state
- The dataplane (`dx-dataplane-rs`) needs no database access for authorization

---

## 12. Audit Logging

Every authenticated request should emit a structured log line at the end of request processing. The log line must include enough context to answer "who did what" for any of the three authentication paths.

### 12.1 Required fields

```java
log.info("request.processed",
    // Effective identity (used for queries)
    kv("sub", principal.getSub()),
    kv("orgId", principal.getOrganisationId()),
    
    // Raw authenticated identity (may differ from effective)
    kv("authenticatedSub", principal.getAuthenticatedSub()),
    kv("authenticatedOrgId", principal.getAuthenticatedOrgId()),
    
    // Authorization context
    kv("auditRoles", principal.getAuditRoles()),
    kv("effectiveScopes", effective),
    
    // Path distinguishers (one may be populated)
    kv("isDelegation", principal.isDelegation()),
    kv("appId", principal.getAppId()),
    
    // Request context
    kv("endpoint", ctx.request().path()),
    kv("method", ctx.request().method()),
    kv("statusCode", ctx.response().getStatusCode()),
    kv("requestId", ctx.get("requestId")));
```

### 12.2 Example log lines

**Plain user:**
```
sub=alice orgId=org-a 
authenticatedSub=alice authenticatedOrgId=org-a 
auditRoles=[org_admin] effectiveScopes=[org-user-management,...] 
isDelegation=false appId=null 
endpoint=/org-users method=GET status=200
```

**Delegation:**
```
sub=alice orgId=org-a 
authenticatedSub=bob authenticatedOrgId=org-b 
auditRoles=[consumer,org_admin] effectiveScopes=[data-access] 
isDelegation=true appId=null 
endpoint=/assets method=GET status=200
```

Notice that `sub ≠ authenticatedSub` — instantly visible that this was a delegated call, and you can see exactly which user acted as which.

**App:**
```
sub=ananjay orgId=org-a 
authenticatedSub=ananjay authenticatedOrgId=org-a 
auditRoles=[provider] effectiveScopes=[own-asset-management] 
isDelegation=false appId=analytics-worker 
endpoint=/assets method=POST status=201
```

The `appId` field distinguishes this from Ananjay's direct session usage.

### 12.3 Failed authentication

Authentication failures should also be logged, at WARN level, with whatever identifying information is available:

```java
log.warn("auth.failed",
    kv("reason", "invalid_secret"),
    kv("appId", appIdHeader),          // if available
    kv("sourceIp", ctx.request().remoteAddress()),
    kv("endpoint", ctx.request().path()));
```

This is important for detecting brute-force attacks against apps.

---

## 13. Testing

### 13.1 Test categories

For each authentication path and endpoint, write tests covering:

1. **Happy path** — correct credentials + correct scope → 200
2. **Wrong scope** — correct credentials + insufficient scope → 403
3. **Invalid credentials** — wrong secret / bad signature → 401
4. **Missing credentials** — no headers → 401
5. **Revoked credentials** — revoked delegation / revoked app → 403 or 401

### 13.2 Delegation-specific tests

**Critical tests that catch design bugs:**

```java
@Test
void delegationWithoutHeader_behavesAsPlainUser() {
    // Bob has a delegation from Alice, but doesn't send the header
    String bobJwt = testJwt().subject("bob").roles("provider").build();
    given().header("Authorization", "Bearer " + bobJwt)
           .when().get("/assets")
           .then().statusCode(403);  // Bob as Provider can't list assets
}

@Test
void delegationWithHeader_actsAsDelegator() {
    givenDelegation("alice", "bob", Set.of("data-access"));
    
    String bobJwt = testJwt().subject("bob").roles("provider").build();
    given().header("Authorization", "Bearer " + bobJwt)
           .header("X-Delegator-Id", "alice")
           .when().get("/assets")
           .then().statusCode(200)
           .body("orgId", equalTo("alice-org"));  // Alice's org, not Bob's
}

@Test
void partialDelegation_doesNotGrantDelegatorsOtherScopes() {
    // Alice has roles {PROVIDER, CONSUMER}
    // Alice delegates ONLY {data-access} to Bob
    givenDelegation("alice", "bob", Set.of("data-access"));
    
    String bobJwt = testJwt().subject("bob").roles("provider").build();
    
    // Bob acting as Alice should be able to read data
    given().header("Authorization", "Bearer " + bobJwt)
           .header("X-Delegator-Id", "alice")
           .when().get("/assets")
           .then().statusCode(200);
    
    // But should NOT be able to create assets (own-asset-management)
    // even though Alice herself has that scope
    given().header("Authorization", "Bearer " + bobJwt)
           .header("X-Delegator-Id", "alice")
           .when().post("/asset-request")
           .then().statusCode(403);
}

@Test
void delegationCapped_whenDelegatorLosesRole() {
    // Alice has {CONSUMER}, delegates full to Bob
    givenUser("alice", Set.of("consumer"));
    givenDelegation("alice", "bob", /* full */);
    
    // Alice loses consumer role
    updateUserRoles("alice", Set.of());
    
    String bobJwt = testJwt().subject("bob").build();
    given().header("Authorization", "Bearer " + bobJwt)
           .header("X-Delegator-Id", "alice")
           .when().get("/assets")
           .then().statusCode(403);  // Delegation shrinks with delegator's scopes
}
```

### 13.3 App-specific tests

```java
@Test
void app_actsAsOwner() {
    givenApp("analytics-worker", "ananjay", Set.of("own-asset-management"));
    
    given().header("X-App-Id", "analytics-worker")
           .header("X-App-Secret", "correct-secret")
           .when().get("/my-assets")
           .then().statusCode(200)
           .body("ownerId", equalTo("ananjay"));
}

@Test
void appScopes_cappedByOwnersCurrentScopes() {
    givenUser("ananjay", Set.of("provider"));  // has own-asset-management
    givenApp("worker", "ananjay", Set.of("own-asset-management"));
    
    // Ananjay loses provider role
    updateUserRoles("ananjay", Set.of());
    
    given().header("X-App-Id", "worker")
           .header("X-App-Secret", "correct-secret")
           .when().post("/asset-request")
           .then().statusCode(403);
}

@Test
void app_wrongSecret_returns401() {
    givenApp("worker", "ananjay", Set.of("own-asset-management"));
    
    given().header("X-App-Id", "worker")
           .header("X-App-Secret", "wrong")
           .when().get("/my-assets")
           .then().statusCode(401);
}
```

### 13.4 Test fixture helper

Consider building a small DSL for test JWT construction:

```java
public class TestJwtBuilder {
    public TestJwtBuilder subject(String sub);
    public TestJwtBuilder orgId(String orgId);
    public TestJwtBuilder roles(String... roles);
    public TestJwtBuilder expiresIn(Duration d);
    public String build();  // signs with test key
}
```

This makes auth tests readable and reduces boilerplate.

---

## 14. Development Phases

Implement in this order. Each phase is independently testable and deployable.

### Phase 1 — Foundation in `dx-common`

**Scope:** build the core auth module without wiring it to controllers yet.

**Deliverables:**
- `DxRole` enum, `Scopes` constants, `SystemRoleScopeMap`, `RoleScopeRegistry` (in-memory impl)
- `DxPrincipal` with builder, effective getters, type detection
- `AuthorizationHandler` with `forScopes`, `forScopesWithContext`, `forRoles`
- `ScopeRule`, `AuthorizationContext`, `AuthLevel`
- JWT parsing utilities (`BearerTokenExtractor`, `JwtClaimsParser`)
- Full unit test coverage for all of the above
- Fix the `dx-common` v1.0.1 packaging issue; publish v1.0.2 with verified jar contents

**Exit criteria:** `dx-common` v1.0.2 published, all tests green, jar contains all expected packages.

### Phase 2 — Plain user path in `dx-controlplane`

**Scope:** migrate all 34 existing endpoints to scope-based authorization using the plain user path only.

**Deliverables:**
- Replace duplicated `DxRole`/`AuthHandler`/JWT code with `dx-common` dependency
- Rename `SecurityHandler` to `JwtPrincipalResolver`, update to build `DxPrincipal`
- Add `AuthenticationHandler` dispatcher (only JWT branch for now)
- Migrate endpoints one controller at a time (see order below)
- Integration tests per endpoint with correct/wrong/missing JWT

**Controller migration order:**
1. AdminController (3 endpoints) — smallest, safest
2. AssetController (4 endpoints)
3. AccessRequestController (3 endpoints)
4. CreditController (9 endpoints)
5. OrganizationController (14 endpoints) — includes the one `forScopesWithContext` case
6. ItemController, SearchController, AccessReportController (4 endpoints)

**Exit criteria:** all 34 endpoints using scope-based handlers, full test suite green, backward-compatible with existing JWTs.

### Phase 3 — App credentials path

**Scope:** add the app authentication path and the app management APIs.

**Deliverables:**
- Database tables: `apps`, `app_scopes`
- `AppCredentialsResolver`
- App management endpoints: create, rotate secret, list, revoke
- Secret hashing, rate limiting, cache invalidation plumbing
- Integration tests for all app scenarios

**Exit criteria:** apps can be created, used to call APIs, rotated, and revoked; cache and rate limiting work as specified.

### Phase 4 — Delegation path

**Scope:** add the delegation authentication path and delegation management APIs.

**Deliverables:**
- Database table: `delegations`
- `DelegationResolver` with caching and scope capping
- Delegation management endpoints: create, list own delegations, list received delegations, revoke
- Cache invalidation on revoke
- Integration tests for cross-org delegation, partial delegation, capping, revocation

**Exit criteria:** delegations work end-to-end with all semantics documented in section 6; partial delegation cap is verified by tests.

### Phase 5 — Dataplane integration

**Scope:** migrate `dx-dataplane-rs` to use `dx-common`.

**Deliverables:**
- Replace duplicated auth code with `dx-common` dependency
- Build `DxPrincipal` in `RoutingContextHelper.fromPrincipal()`
- Enable `forScopes` on `LatestController` (currently commented out)
- Re-enable `ResourcePolicyAuthorizationHandler` alongside scope handlers
- Dataplane only supports plain user JWT (no delegation, no apps in dataplane)

**Exit criteria:** dataplane runs on `dx-common`, all endpoints protected by scope handlers, resource policy enforcement restored.

---

## 15. FAQ

**Q: Why is `sub` sometimes the authenticated user and sometimes the delegator?**

Because "sub" in the principal represents the *effective* identity for this request — whose data is being operated on. For plain users, that's themselves. For delegations, that's the delegator (the delegatee is borrowing the delegator's authority). For apps, that's the owner (apps act as their owner). Making `sub` the effective identity means downstream services call `principal.getSub()` and always get the right id for queries without knowing about auth paths.

**Q: Does scope resolution hit the database?**

Never. `SystemRoleScopeMap` is a code-level constant, and the registry is in-memory. Delegation and app resolvers do hit the DB (to look up the delegation or app record), but that's during *authentication*, not *authorization*, and the results are cached.

**Q: What happens if a user has two system roles?**

Union of scopes. A user with `[provider, consumer]` has all scopes from both roles (`{own-asset-management, data-access}`). This is automatic in `resolveEffectiveScopes`.

**Q: Can a delegatee sub-delegate?**

No. Delegations are one level deep. Enforce this at delegation creation time: you can only create a delegation based on scopes you have directly (via your own roles), not via scopes you received from another delegation.

**Q: What if a request has both a JWT and `X-App-Id`?**

Reject with 400 "Ambiguous credentials." Clients must present exactly one credential type per request.

**Q: How fast is delegation revocation?**

With a 30-second cache TTL and cache invalidation events, revocation is effective within 30 seconds worst case, faster if the invalidation event propagates quickly. Document this as the guaranteed maximum latency for operators.

**Q: What if the delegator loses a role after creating a delegation?**

The delegation's effective scopes shrink automatically at the next request. The resolver intersects the delegation's stored scopes with the delegator's *current* role-derived scopes. Stale grants are impossible.

**Q: Can an app be owned by more than one user?**

Not in this design. One app, one owner. If you need multi-owner apps later, that's a future enhancement — probably implemented as an "organization-owned app" concept.

**Q: Why not put roles on the delegated/app principal so scopes can be computed at auth time?**

Because flattening roles would re-grant *all* of the delegator's/owner's scopes, bypassing the cap set by the delegation/app scope bundle. Storing only the pre-computed `directScopes` (with an empty `authorizationRoles`) guarantees the cap is respected.

**Q: Can I use `forRoles` because it's simpler than `forScopes`?**

No. Use `forScopes` by default. `forRoles` is only for genuine identity gates. If you're about to write `forRoles(COS_ADMIN)`, ask "would `forScopes(USER_MANAGEMENT)` work?" — it almost always does, and it's the correct abstraction.

**Q: What about the top-level `scope` claim in the JWT (RFC 9068)?**

Deferred. In this phase, all authorization scopes come from roles. When supplementary direct scope grants are added in a future phase, the JWT's `scope` claim will be parsed into `directScopes` for plain users, and `resolveEffectiveScopes` will union them with flattened roles. The rest of the architecture doesn't change.

**Q: What's the maximum recommended length of `authorizationRoles` / `directScopes`?**

Small. System roles are 5, system scopes are 13. A plain user will typically have 1–2 roles, yielding 1–6 effective scopes. Delegation and app scope sets are typically 1–5. Performance is a non-issue at these sizes.

**Q: Does this support multi-tenancy?**

Yes, via `organisationId`. Each principal carries an effective org, and services filter by it for org-scoped queries. Cross-org access requires CosAdmin scopes (`org-management` etc.), which are platform-wide.

---

## Appendix A — Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────┐
│  AUTHENTICATION PATHS                                           │
│                                                                 │
│  Plain user:   Authorization: Bearer <jwt>                      │
│  Delegation:   Authorization: Bearer <jwt>                      │
│                X-Delegator-Id: <delegator-sub>                  │
│  App:          X-App-Id: <app-id>                               │
│                X-App-Secret: <secret>                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  PRINCIPAL GETTERS                                              │
│                                                                 │
│  getSub()              → effective identity (for queries)      │
│  getOrganisationId()   → effective org (for queries)           │
│  getAuthenticatedSub() → raw authenticator (audit only)        │
│  isDelegation()        → boolean                               │
│  isApp()               → boolean                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  CHOOSING A HANDLER                                             │
│                                                                 │
│  Single scope, no tiering          → forScopes(ONE)             │
│  Multiple scopes, same boundary    → forScopes(A, B, ...)       │
│  Multiple tiers, diff boundaries   → forScopesWithContext()     │
│  Identity gate (rare)              → forRoles(ROLE)             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  MENTAL TEST                                                    │
│                                                                 │
│  "Would the SQL for this endpoint be the same                   │
│   regardless of who called it?"                                 │
│                                                                 │
│  YES → forScopes                                                │
│  NO  → forScopesWithContext                                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  PRIORITY ORDER (forScopesWithContext)                          │
│                                                                 │
│  PLATFORM first → ORG second → SELF third                       │
│  (highest authority to lowest; first match wins)                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  DELEGATION INVARIANTS                                          │
│                                                                 │
│  • Per-request (controlled by X-Delegator-Id header)            │
│  • One request = one identity (no union)                        │
│  • Scopes capped by delegator's current scopes                  │
│  • sub and orgId switch to delegator                            │
│  • Delegatee id preserved in authenticatedSub for audit         │
│  • No sub-delegation                                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  APP INVARIANTS                                                 │
│                                                                 │
│  • App authenticates AS its owner (sub = owner)                 │
│  • Scopes capped by owner's current scopes                      │
│  • Secret hashed (bcrypt/argon2), never plaintext               │
│  • appId preserved in audit for per-app attribution             │
│  • Rate-limited by appId and source IP                          │
└─────────────────────────────────────────────────────────────────┘
```
