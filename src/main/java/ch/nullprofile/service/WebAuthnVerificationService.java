package ch.nullprofile.service;

import ch.nullprofile.config.WebAuthnProperties;
import ch.nullprofile.entity.User;
import ch.nullprofile.entity.WebAuthnCredential;
import ch.nullprofile.repository.UserRepository;
import ch.nullprofile.repository.WebAuthnCredentialRepository;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import com.webauthn4j.validator.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Service
public class WebAuthnVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnVerificationService.class);

    private final WebAuthnProperties properties;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private WebAuthnManager webAuthnManager;
    private ObjectConverter objectConverter;

    public WebAuthnVerificationService(
            WebAuthnProperties properties,
            UserRepository userRepository,
            WebAuthnCredentialRepository credentialRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
    }

    @PostConstruct
    public void init() {
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        this.objectConverter = new ObjectConverter();
    }

    /**
     * Verify registration attestation and create user + credential
     */
    @Transactional
    public User verifyRegistrationAndCreateUser(
            String challenge,
            String name,
            String clientDataJSON,
            String attestationObject,
            String origin) throws ValidationException {

        // Decode base64url inputs
        byte[] clientDataJSONBytes = Base64UrlUtil.decode(clientDataJSON);
        byte[] attestationObjectBytes = Base64UrlUtil.decode(attestationObject);

        // Create challenge
        Challenge challengeObj = new DefaultChallenge(Base64UrlUtil.decode(challenge));

        // Create server property
        ServerProperty serverProperty = new ServerProperty(
                Origin.create(origin),
                properties.getRp().getId(),
                challengeObj,
                null
        );

        // Verify registration
        RegistrationRequest registrationRequest = new RegistrationRequest(
                attestationObjectBytes,
                clientDataJSONBytes
        );

        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty,
                null, // pubKeyCredParams - accept all
                false, // userVerificationRequired
                true  // userPresenceRequired
        );

        RegistrationData registrationData;
        try {
            registrationData = webAuthnManager.validate(registrationRequest, registrationParameters);
        } catch (ValidationException e) {
            logger.error("WebAuthn registration verification failed", e);
            throw e;
        }

        // Create new user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        // Create credential
        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setId(UUID.randomUUID());
        credential.setUserId(user.getId());
        credential.setCredentialId(Base64UrlUtil.encodeToString(registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData().getCredentialId()));
        
        // Store public key in COSE format
        byte[] publicKeyCose = objectConverter.getCborConverter().writeValueAsBytes(
                registrationData.getAttestationObject()
                        .getAuthenticatorData()
                        .getAttestedCredentialData()
                        .getCOSEKey()
        );
        credential.setPublicKeyCose(Base64.getEncoder().encodeToString(publicKeyCose));
        
        credential.setSignCount(registrationData.getAttestationObject().getAuthenticatorData().getSignCount());
        
        // Store AAGUID if available
        if (registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData().getAaguid() != null) {
            credential.setAaguid(registrationData.getAttestationObject()
                    .getAuthenticatorData()
                    .getAttestedCredentialData()
                    .getAaguid()
                    .toString());
        }
        
        credential.setName(name);
        credential.setCreatedAt(Instant.now());
        credential.setLastUsedAt(Instant.now());
        credentialRepository.save(credential);

        logger.info("Successfully registered new user with credential: userId={}, credentialId={}", 
                user.getId(), credential.getCredentialId());

        return user;
    }

    /**
     * Verify authentication assertion
     */
    @Transactional
    public User verifyAuthentication(
            String challenge,
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature,
            String origin) {

        // Find credential
        WebAuthnCredential credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        // Find user
        User user = userRepository.findById(credential.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Decode base64url inputs
        byte[] clientDataJSONBytes = Base64UrlUtil.decode(clientDataJSON);
        byte[] authenticatorDataBytes = Base64UrlUtil.decode(authenticatorData);
        byte[] signatureBytes = Base64UrlUtil.decode(signature);

        // Create challenge
        Challenge challengeObj = new DefaultChallenge(Base64UrlUtil.decode(challenge));

        // Create server property
        ServerProperty serverProperty = new ServerProperty(
                Origin.create(origin),
                properties.getRp().getId(),
                challengeObj,
                null
        );

        // Recreate authenticator from stored credential
        byte[] credentialIdBytes = Base64UrlUtil.decode(credential.getCredentialId());
        byte[] publicKeyCoseBytes = Base64.getDecoder().decode(credential.getPublicKeyCose());
        
        // Parse the public key COSE
        com.webauthn4j.data.attestation.authenticator.COSEKey coseKey = 
                objectConverter.getCborConverter().readValue(publicKeyCoseBytes, com.webauthn4j.data.attestation.authenticator.COSEKey.class);
        
        // Recreate AttestedCredentialData
        com.webauthn4j.data.attestation.authenticator.AAGUID aaguid = credential.getAaguid() != null 
                ? new com.webauthn4j.data.attestation.authenticator.AAGUID(credential.getAaguid())
                : com.webauthn4j.data.attestation.authenticator.AAGUID.ZERO;
        
        com.webauthn4j.data.attestation.authenticator.AttestedCredentialData attestedCredentialData = 
                new com.webauthn4j.data.attestation.authenticator.AttestedCredentialData(
                        aaguid,
                        credentialIdBytes,
                        coseKey
                );
        
        Authenticator authenticator = new AuthenticatorImpl(
                attestedCredentialData,
                null, // attestationStatement
                credential.getSignCount()
        );

        // Verify authentication
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                credentialIdBytes,
                authenticatorDataBytes,
                clientDataJSONBytes,
                signatureBytes
        );

        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                serverProperty,
                authenticator,
                null, // expectedCredentialIds
                false, // userVerificationRequired
                true   // userPresenceRequired
        );

        AuthenticationData authenticationData;
        try {
            authenticationData = webAuthnManager.validate(authenticationRequest, authenticationParameters);
        } catch (ValidationException e) {
            logger.error("WebAuthn authentication verification failed for credentialId={}", credentialId, e);
            throw e;
        }

        // Verify and update sign count
        long newSignCount = authenticationData.getAuthenticatorData().getSignCount();
        if (newSignCount <= credential.getSignCount()) {
            logger.warn("Sign count did not increase for credentialId={}: old={}, new={}", 
                    credentialId, credential.getSignCount(), newSignCount);
            // In production, you might want to reject or flag this credential
        }

        // Update credential
        credential.setSignCount(newSignCount);
        credential.setLastUsedAt(Instant.now());
        credentialRepository.save(credential);

        // Update user
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        logger.info("Successfully authenticated user: userId={}, credentialId={}", user.getId(), credentialId);

        return user;
    }
}
