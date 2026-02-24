# OIDC Authorization Endpoint Implementation Summary

## Overview
Complete implementation of OAuth 2.0 Authorization Code Flow with PKCE (S256) for the null-profile OIDC provider.

## What Was Implemented

### 1. Data Transfer Objects (DTOs)

#### OidcTransaction.java
- Session-based transaction storage for authorization flows
- Immutable record with methods for state transitions
- Fields: txnId, rpId, redirectUri, scope, state, nonce, codeChallenge, codeChallengeMethod, requestedAt, authnRequired, authenticatedUserId, authCodeHash, authCodeExpiresAt
- Helper methods:
  - `createNew()` - Create new transaction
  - `withAuthenticatedUser()` - Mark transaction as authenticated
  - `withAuthCode()` - Store authorization code hash
  - `consumeAuthCode()` - One-time use code consumption
  - `isAuthenticated()` - Check authentication status

#### OidcAuthorizationRequest.java
- Request parameter holder for /authorize endpoint
- Fields: responseType, clientId, redirectUri, scope, state, nonce, codeChallenge, codeChallengeMethod, prompt
- Helper method: `requiresLogin()` - Check for prompt=login

#### OidcAuthorizationValidationResult.java
- Sealed interface with two variants:
  - `Valid` - Validation succeeded with validated request
  - `Invalid` - Validation failed with error details (error, errorDescription, redirectUri, state)
- Method: `canRedirect()` - Check if error can be safely redirected to RP

### 2. Configuration

#### OidcProperties.java
- Spring Boot configuration properties for OIDC provider
- Prefix: `oidc`
- Security configuration:
  - `allowHttpRedirectUrisForLocalhost` (default: true) - Dev mode HTTP support
  - `authCodeValiditySeconds` (default: 300) - 5 minutes
  - `sessionTimeoutSeconds` (default: 1800) - 30 minutes
  - `maxStateLength` (default: 1024)
  - `maxNonceLength` (default: 1024)
  - `minCodeChallengeLength` (default: 43)
  - `maxCodeChallengeLength` (default: 128)

#### application.yml Updates
- Added comprehensive OIDC security configuration
- All values configurable via environment variables
- Defaults suitable for development

### 3. Services

#### OidcAuthorizationValidationService.java
- Complete validation of authorization requests per OIDC spec
- Validations performed:
  - response_type must be "code"
  - scope must be exactly "openid"
  - nonce is REQUIRED
  - code_challenge and code_challenge_method (S256) are REQUIRED
  - client_id must exist and map to valid RelyingParty
  - redirect_uri must be exactly registered for the RP
  - redirect_uri scheme validation (https required, http allowed for localhost in dev)
  - Parameter length validation (state, nonce, code_challenge)
  - Base64url validation for code_challenge
  - prompt parameter validation (rejects unsupported values)
- Error codes returned:
  - `unsupported_response_type` - response_type != code
  - `invalid_scope` - scope != openid
  - `invalid_request` - Missing/invalid parameters
  - `unauthorized_client` - Unknown client_id

#### OidcSessionTransactionService.java (Updated)
- Complete rewrite to use OidcTransaction model
- Session-based storage (Hazelcast-backed)
- Methods:
  - `createTransaction()` - Create new OIDC transaction
  - `getTransaction()` - Retrieve current transaction
  - `updateTransaction()` - Update transaction state
  - `clearTransaction()` - Remove transaction
  - `authenticateTransaction()` - Mark transaction with authenticated user
  - `generateAndStoreAuthCode()` - Generate 32-byte random code, store SHA-256 hash
  - `validateAndConsumeAuthCode()` - Verify and one-time consume code
  - `validatePkce()` - Verify code_verifier against S256 challenge
  - `getAuthenticatedUserId()` - Get global session user ID
  - `setAuthenticatedUserId()` - Set global session user ID
  - `clearAuthenticatedUser()` - Clear for prompt=login
  - `isAuthenticated()` - Check session authentication
  - `getUserId()` - Backward compatibility for existing controllers
- Security features:
  - Authorization codes: 32-byte random, SHA-256 hashed, 5-minute expiry, one-time use
  - PKCE validation with S256
  - Codes never stored in plaintext or database

### 4. Controllers

#### OidcAuthorizationController.java (Complete Rewrite)
- GET /authorize - Main authorization endpoint
  - Accepts OIDC parameters (response_type, client_id, redirect_uri, scope, state, nonce, code_challenge, code_challenge_method, prompt)
  - Validates all parameters using OidcAuthorizationValidationService
  - Handles validation errors (redirects to RP or returns JSON error)
  - Creates transaction in session
  - Checks authentication status
  - Handles prompt=login (clears authenticated user)
  - If authenticated: issues code immediately and redirects to redirect_uri
  - If not authenticated: redirects to /login?txn={txnId}
  - Security: Never redirects to unvalidated redirect_uri
  - Logging: Logs txnId, rpId, redirect_uri host (not full URI)

- GET /authorize/resume - Resume after authentication
  - Called after user completes WebAuthn login
  - Validates session is authenticated
  - Retrieves transaction from session
  - Validates transaction exists and matches txnId (if provided)
  - Authenticates transaction with user ID
  - Generates authorization code
  - Redirects to redirect_uri with code and state

#### OidcTokenController.java (Updated)
- Updated to use OidcTransaction model
- Retrieves transaction from session instead of individual attributes
- All validation logic remains the same

#### WebAuthnController.java (Updated)
- Updated to use new setAuthenticatedUserId() method
- Checks for OIDC transaction using getTransaction()

### 5. OIDC Discovery Metadata
- Already complete in existing OidcDiscoveryResponse.java
- Advertises:
  - authorization_endpoint
  - token_endpoint
  - jwks_uri
  - response_types_supported: ["code"]
  - scopes_supported: ["openid"]
  - code_challenge_methods_supported: ["S256"]
  - subject_types_supported: ["pairwise"]
  - id_token_signing_alg_values_supported: ["RS256"]
  - claims_supported: ["sub", "iss", "aud", "exp", "iat"]

### 6. Tests

#### OidcAuthorizationControllerTest.java (Complete Rewrite)
- Comprehensive integration tests using MockMvc
- Test coverage:
  - Missing/invalid response_type
  - Invalid scope (only "openid" allowed)
  - Missing nonce
  - Missing code_challenge
  - Wrong code_challenge_method (must be S256)
  - Unknown client_id
  - Invalid redirect_uri (not registered)
  - Unauthenticated user redirects to login
  - Authenticated user gets code immediately
  - prompt=login forces re-authentication
  - /authorize/resume without authentication
  - /authorize/resume without transaction
  - /authorize/resume with valid session issues code
  - State preservation in all responses
  - Parameter length validation (nonce too long)
- Uses H2 in-memory database for tests
- Creates test relying party and redirect URI
- Uses real session management

## Security Features Implemented

1. **PKCE S256 Required**: Plain challenge method rejected
2. **Exact Redirect URI Matching**: Prevents open redirects
3. **Authorization Code Security**:
   - 32-byte random (256-bit entropy)
   - SHA-256 hashed storage
   - 5-minute expiry
   - One-time use (consumed after validation)
   - Never logged or stored in database
4. **Redirect URI Scheme Validation**:
   - HTTPS required in production
   - HTTP allowed only for localhost in dev mode
   - Configurable via `allowHttpRedirectUrisForLocalhost`
5. **Parameter Validation**:
   - Length limits on state, nonce, code_challenge
   - Base64url format validation
   - Required parameter enforcement
6. **Session-Based State**:
   - All transaction state in Hazelcast-backed session
   - 30-minute session timeout
   - No sensitive data in database
7. **prompt=login Support**: Forces re-authentication
8. **Error Handling**:
   - Safe error responses (don't redirect to unvalidated URIs)
   - Proper OIDC error codes
   - Detailed logging (no sensitive data)

## OIDC Compliance

✅ Authorization Code Flow (response_type=code)
✅ PKCE S256 required
✅ scope=openid exact match
✅ nonce required
✅ state preserved in all responses
✅ Exact redirect_uri matching
✅ Standard error codes (invalid_request, unsupported_response_type, etc.)
✅ prompt=login support
✅ Pairwise subject identifiers
✅ ID token signing (RS256)
✅ Discovery endpoint (/.well-known/openid-configuration)

## Testing with oidcdebugger.com

The implementation is ready to test with oidcdebugger.com:

1. **Configuration**:
   - Authorization URL: `http://localhost:8080/authorize`
   - Token URL: `http://localhost:8080/token`
   - Client ID: Your RP's rpId (from database)
   - Redirect URI: Must be registered in redirect_uris table
   - Scope: `openid`
   - Response type: `code`
   - Response mode: `form_post` or `query`
   - Use PKCE: Yes (S256)

2. **Flow**:
   - Click "Send Request" on oidcdebugger.com
   - System validates request and redirects to /login
   - Complete WebAuthn authentication
   - System redirects back to /authorize/resume
   - Authorization code issued
   - User-agent redirected to oidcdebugger.com with code
   - Exchange code for ID token at /token endpoint

## Files Created

- `src/main/java/ch/nullprofile/dto/OidcTransaction.java`
- `src/main/java/ch/nullprofile/dto/OidcAuthorizationRequest.java`
- `src/main/java/ch/nullprofile/dto/OidcAuthorizationValidationResult.java`
- `src/main/java/ch/nullprofile/config/OidcProperties.java`
- `src/main/java/ch/nullprofile/service/OidcAuthorizationValidationService.java`

## Files Modified

- `src/main/java/ch/nullprofile/service/OidcSessionTransactionService.java` - Complete rewrite
- `src/main/java/ch/nullprofile/controller/OidcAuthorizationController.java` - Complete rewrite
- `src/main/java/ch/nullprofile/controller/OidcTokenController.java` - Updated for new transaction model
- `src/main/java/ch/nullprofile/controller/WebAuthnController.java` - Updated method calls
- `src/main/resources/application.yml` - Added OIDC configuration
- `src/test/java/ch/nullprofile/controller/OidcAuthorizationControllerTest.java` - Complete rewrite

## Configuration Required

Add to `.env.local` or environment variables (optional, defaults work for dev):

```properties
# OIDC Configuration (optional)
OIDC_ISSUER=http://localhost:8080
OIDC_ALLOW_HTTP_LOCALHOST=true
OIDC_AUTH_CODE_VALIDITY=300
OIDC_SESSION_TIMEOUT=1800
```

## Next Steps

1. **Build and Test**:
   ```bash
   cd null-profile-be
   mvn clean test
   ```

2. **Start Application**:
   ```bash
   mvn spring-boot:run
   ```

3. **Test with oidcdebugger.com**:
   - Create a relying party via API
   - Register redirect URI: `https://oidcdebugger.com/debug`
   - Use the Authorization URL: `http://localhost:8080/authorize`
   - Complete the flow

4. **Production Deployment**:
   - Set `OIDC_ALLOW_HTTP_LOCALHOST=false` in production
   - Use HTTPS for issuer URL
   - Configure proper session clustering

## Limitations & Future Enhancements

Current implementation (as specified):
- Single active transaction per session (simplest MVP)
- No persistent authorization code storage
- No client_secret support (public client only)
- No refresh tokens
- No userinfo endpoint
- No additional scopes beyond "openid"
- No dynamic client registration

These are intentional for the MVP. The architecture supports adding these features later.
