# Secure User Management System

A Spring Boot project focused on **authentication and authorization** with Spring Security. It supports two ways to log in: a local username/password form backed by MySQL, and Google login through OAuth2 / OpenID Connect.

This is a learning project. The goal was to understand how Spring Security's pieces fit together (`UserDetailsService`, `AuthenticationProvider`, password encoding, `OidcUserService`, the filter chain), not to build a full user management platform. The [Limitations](#limitations) section lists what is missing.

---

## Overview

- **What it is:** a small Spring Boot 4.1.0 / Java 17 web app with a login page and one protected home page.
- **Main objective:** implement and study local authentication and Google (OIDC) authentication side by side, with users persisted in a database.
- **Authentication:** form login (username/password, BCrypt-verified) and Google OAuth2 / OIDC.
- **Authorization:** a simple gate: every route except the login page and a few static paths requires an authenticated user. There is one hardcoded authority (`USER`) and no roles.
- **User storage:** a single `users` table in MySQL, accessed via Spring Data JPA. Each user records where it came from (`LOCAL` or `GOOGLE`).
- **Session model:** standard server-side sessions. There is no JWT.

---

## Key Features

- Username/password login using Spring Security form login
- BCrypt password verification (default strength 10)
- Google login via OAuth2 Authorization Code flow with OpenID Connect
- Custom `OidcUserService` that finds or creates a database user from the Google profile
- MySQL persistence with Spring Data JPA
- `AuthProvider` enum (`LOCAL`, `GOOGLE`) and `providerId` field to record the login source
- Route protection with `.anyRequest().authenticated()`
- Logout with session invalidation and authentication clearing
- Thymeleaf login page and home page

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0, Spring MVC |
| Security | Spring Security, Spring Security OAuth2 Client, BCrypt |
| Persistence | Spring Data JPA, Hibernate, MySQL |
| Templates | Thymeleaf |
| Build | Maven (wrapper included) |
| Other | Lombok, MySQL Connector/J |

---

## Project Structure

```text
src/main/java/com/practice/springboot/
├── SpringbootApplication.java
├── configrations/
│   └── ApplicationSecurityConfiguration.java   # filter chain, auth provider, BCrypt
├── controllers/
│   └── UserController.java                     # /, /login, /logout, /user
├── entities/
│   └── User.java                               # JPA entity (users table)
├── enums/
│   └── AuthProvider.java                       # LOCAL, GOOGLE
├── repositories/
│   └── UserRepository.java                     # Spring Data JPA repository
├── security/
│   └── UserPrincipal.java                      # UserDetails implementation
└── services/
    ├── MyUserDetailsService.java               # local login user lookup
    └── CustomOidcUserService.java              # Google/OIDC user handling

src/main/resources/
├── application.properties                      # local config (do not commit secrets)
├── application-example.properties              # configuration template
└── templates/
    ├── login.html                              # login form + "Login with Google" link
    └── index.html                              # protected home page
```

> The package name `configrations` is spelled as it appears in the repository.

### Key classes

| Class | Responsibility |
|---|---|
| `ApplicationSecurityConfiguration` | Defines the `SecurityFilterChain` (route rules, form login, logout, OAuth2 login) and the `DaoAuthenticationProvider` with `BCryptPasswordEncoder` |
| `MyUserDetailsService` | Loads a user by username for local login |
| `UserPrincipal` | Wraps the `User` entity as `UserDetails`; grants a single `USER` authority |
| `CustomOidcUserService` | Extends `OidcUserService`; looks up or creates the user after Google login |
| `User` | JPA entity mapped to the `users` table |
| `UserRepository` | `findByUsername`, `findByEmail` |
| `AuthProvider` | Enum marking the account source |
| `UserController` | Serves `/`, `/login`, `/logout`, and `/user` (returns the `Principal`) |

---

## Authentication

### 1. Username and Password Authentication

Login is handled by Spring Security's form login. The login page is at `/login` and the form posts back to `/login`.

```text
User
 ↓
Login Form (POST /login)
 ↓
Spring Security filter chain
 ↓
DaoAuthenticationProvider
 ↓
MyUserDetailsService.loadUserByUsername()
 ↓
UserRepository.findByUsername()
 ↓
MySQL (users table)
 ↓
UserPrincipal (wraps User, grants USER authority)
 ↓
BCryptPasswordEncoder.matches(raw, storedHash)
 ↓
Success → authenticated session → redirect to /
Failure → redirect to /login?error
```

Details:

- `DaoAuthenticationProvider` is configured with `MyUserDetailsService` and a `BCryptPasswordEncoder`. It is declared as a bean and picked up by Spring Boot's auto-configuration as the only `AuthenticationProvider`.
- The encoder is created inline inside that bean, not exposed as a separate `@Bean`.
- On success the user is always redirected to `/` (`defaultSuccessUrl("/", true)`).
- Account status methods on `UserPrincipal` (enabled, locked, expired) always return `true`.

### 2. Google OAuth2 / OpenID Connect Authentication

Google login uses Spring Security's OAuth2 client with the standard endpoints:

| Purpose | URL |
|---|---|
| Start login | `/oauth2/authorization/google` |
| Callback (redirect URI) | `/login/oauth2/code/google` |

```text
User clicks "Login with Google"
 ↓
/oauth2/authorization/google
 ↓
Google consent screen
 ↓
Google redirects to /login/oauth2/code/google?code=...
 ↓
Spring Security exchanges the code for tokens
 ↓
CustomOidcUserService.loadUser()
 ↓
Read email, full name, and subject (sub) from the OIDC user
 ↓
UserRepository.findByEmail(email)
 ↓
   ├─ not found → save new User (GOOGLE, providerId = sub, password = null)
   └─ found     → nothing is updated
 ↓
Return Google's OidcUser as the principal
 ↓
Authenticated session → redirect to /
```

`CustomOidcUserService` returns the original `OidcUser` from Google, **not** a `UserPrincipal` built from the local `User` row. See [Limitations](#limitations) for what this means.

### 3. Logout

- Endpoint: `/logout`
- Invalidates the HTTP session and clears the authentication
- Redirects to `/login`

The home page does not currently have a logout link or button.

---

## Authorization

Authorization is intentionally basic.

| Rule | Paths |
|---|---|
| `permitAll` | `/login`, `/register`, `/css/**`, `/js/**` |
| `authenticated` | everything else (`.anyRequest().authenticated()`) |

- The only authority is `USER`, hardcoded in `UserPrincipal.getAuthorities()` and therefore applied to local-login users.
- There are no roles, no admin authorization, no role hierarchy, and no method-level security (`@PreAuthorize` / `@EnableMethodSecurity` are not used).
- Once logged in, by either method, a user can reach everything the app has.
- `/register` is listed as public, but no registration controller or page exists yet.

---

## Database and User Model

Single table: `users`.

| Field | Type | Notes |
|---|---|---|
| `id` | `long` | primary key, auto-generated (`IDENTITY`) |
| `username` | `String` | |
| `password` | `String` | BCrypt hash for local users, `null` for Google users |
| `email` | `String` | |
| `provider` | `AuthProvider` | stored as a string: `LOCAL` or `GOOGLE` |
| `providerId` | `String` | Google `sub` value for Google users, `null` for local users |

All fields other than `id` are nullable (no `@Column(nullable = false)` constraints).

| | Local user | Google user |
|---|---|---|
| `provider` | `LOCAL` | `GOOGLE` |
| `providerId` | `null` | Google subject |
| `password` | BCrypt hash | `null` |
| `username` | user-chosen | Google full name |
| Looked up by | `username` | `email` |

---

## Security Notes

Implemented:

- Passwords for local users are compared against BCrypt hashes, never stored or checked in plaintext.
- Sessions are invalidated and the security context cleared on logout.
- Google login relies on Spring Security's standard OAuth2/OIDC handling.

Not implemented or weak (see below): CSRF protection is disabled, there is no password policy, no rate limiting or account locking, and no custom security headers configuration.

---

## Getting Started

### Prerequisites

- Java 17+
- Maven (or the included `./mvnw`)
- MySQL running locally
- A Google OAuth2 client (Google Cloud Console → APIs & Services → Credentials)

### 1. Create the database

```sql
CREATE DATABASE springboot;
```

The `users` table is mapped by the `User` entity. The configuration shown in the project does not set a schema-generation option, so if the table is not created automatically in your setup, add `spring.jpa.hibernate.ddl-auto=update` while developing.

### 2. Configure the application

Copy `application-example.properties` to `application.properties` (or edit the existing file) and use your own values. **Never commit real credentials.**

```properties
spring.application.name=springboot

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/springboot
spring.datasource.username=YOUR_DATABASE_USERNAME
spring.datasource.password=YOUR_DATABASE_PASSWORD
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Google OAuth2
spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
```

Environment variables also work with Spring Boot's relaxed binding, e.g. `SPRING_DATASOURCE_PASSWORD` and `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`, which keeps secrets out of the file entirely.

In the Google Cloud Console, add this as an authorized redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

### 3. Run

```bash
./mvnw spring-boot:run
# or
mvn spring-boot:run
```

The app starts on the default port 8080. Open <http://localhost:8080/login>.

### Trying the two login methods

- **Google:** click "Login with Google". A new `users` row is created on first login.
- **Local:** there is no registration page, so a local user has to be inserted into the database manually with a **BCrypt-hashed** password and `provider = 'LOCAL'`. Inserting a plaintext password will not work, because the login flow always compares against a BCrypt hash.

---

## Limitations

This project covers the core authentication flows but is not a complete user management system. Known gaps:

**Authentication**
- **No registration.** Local accounts cannot be created through the app. `/register` is whitelisted but has no controller or template.
- **Google principal mismatch.** `CustomOidcUserService` returns Google's `OidcUser`, so Google users do not get the local `USER` authority, and the saved `User` entity is not used as the principal.
- **No account linking.** If a local user already has the same email, Google login finds that row but does not update `provider`/`providerId` or link the accounts.
- No password reset, email verification, two-factor authentication, or remember-me.

**Authorization**
- Single hardcoded `USER` authority. No RBAC, no admin role, no method-level security.

**Security hardening**
- **CSRF protection is disabled** (`csrf.disable()`), and the login form has no CSRF token.
- No password policy (length or complexity), no rate limiting or account locking, and no secure-header configuration.
- The `PasswordEncoder` is not a standalone bean, so it cannot be injected elsewhere (for example, into a future registration service).
- No JWT or stateless API authentication; only session-based login.

**Code quality**
- `UserRepository` contains a faulty `email(String)` method that returns `String` instead of `User`.
- `application.properties` contains legacy `security.oauth2.client.*` properties that Spring Boot 3+ does not use.
- The only test is the default empty context-load test.
- The UI is minimal: no error messages on the login page, no logout link, no registration or profile pages, and a typo in the home page text.

---

## Possible Next Steps

These are ideas, **not** current features:

- User registration with validation and a shared `PasswordEncoder` bean
- Return a `UserPrincipal` (or map authorities) for Google users so both login types share one principal model
- Account linking between local and Google identities
- Roles (e.g. `USER`, `ADMIN`) with method-level security
- Re-enable CSRF and add tokens to the Thymeleaf forms
- Password reset and email verification
- Rate limiting for failed logins, secure headers, and authentication audit logging
- Proper tests for both login flows