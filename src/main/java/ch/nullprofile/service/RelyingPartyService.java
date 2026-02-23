package ch.nullprofile.service;

import ch.nullprofile.dto.*;
import ch.nullprofile.entity.RedirectUri;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.repository.RedirectUriRepository;
import ch.nullprofile.repository.RelyingPartyRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RelyingPartyService {

    private final RelyingPartyRepository relyingPartyRepository;
    private final RedirectUriRepository redirectUriRepository;
    private final EntityManager entityManager;

    public RelyingPartyService(
            RelyingPartyRepository relyingPartyRepository,
            RedirectUriRepository redirectUriRepository,
            EntityManager entityManager) {
        this.relyingPartyRepository = relyingPartyRepository;
        this.redirectUriRepository = redirectUriRepository;
        this.entityManager = entityManager;
    }

    /**
     * Lookup relying party by rpId (client_id)
     */
    public Optional<RelyingParty> findByRpId(String rpId) {
        return relyingPartyRepository.findByRpId(rpId);
    }

    /**
     * Validate redirect URI with exact match
     */
    public boolean isRedirectUriValid(RelyingParty relyingParty, String redirectUri) {
        return redirectUriRepository
                .findByRelyingPartyIdAndUri(relyingParty.getId(), redirectUri)
                .isPresent();
    }

    /**
     * Get all relying parties for a user (summary view)
     */
    public List<RelyingPartySummary> getAllRelyingParties(UUID userId) {
        return relyingPartyRepository.findAll().stream()
                .filter(rp -> rp.getCreatedByUserId().equals(userId))
                .map(rp -> new RelyingPartySummary(
                        rp.getId(),
                        rp.getRpName(),
                        rp.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get single relying party by ID with full details
     */
    public Optional<RelyingPartyDetail> getRelyingPartyById(UUID id, UUID userId) {
        return relyingPartyRepository.findById(id)
                .filter(rp -> rp.getCreatedByUserId().equals(userId))
                .map(rp -> {
                    List<String> uris = redirectUriRepository.findByRelyingPartyId(rp.getId())
                            .stream()
                            .map(RedirectUri::getUri)
                            .collect(Collectors.toList());
                    
                    return new RelyingPartyDetail(
                            rp.getId(),
                            rp.getRpName(),
                            rp.getSectorId(),
                            uris,
                            new RelyingPartyDetail.BrandingInfo(
                                    rp.getBrandingLogoUrl(),
                                    rp.getBrandingPrimaryColor(),
                                    rp.getBrandingSecondaryColor()
                            )
                    );
                });
    }

    /**
     * Create a new relying party
     */
    @Transactional
    public RelyingPartyDetail createRelyingParty(CreateRelyingPartyRequest request, UUID userId) {
        // Generate unique RP ID
        String rpId = "rp_" + UUID.randomUUID().toString().replace("-", "");

        // Create relying party entity
        RelyingParty rp = new RelyingParty();
        rp.setRpId(rpId);
        rp.setRpName(request.name());
        rp.setSectorId(request.sectorId() != null && !request.sectorId().isBlank() 
                ? request.sectorId() 
                : rpId); // Use rpId as default sector
        rp.setBrandingLogoUrl(request.logoUrl());
        rp.setBrandingPrimaryColor(request.primaryColor());
        rp.setBrandingSecondaryColor(request.secondaryColor());
        rp.setCreatedByUserId(userId);
        rp.setStatus("ACTIVE");
        rp.setPlanTier("FREE");

        rp = relyingPartyRepository.save(rp);

        // Create redirect URIs
        for (String uri : request.redirectUris()) {
            RedirectUri redirectUri = new RedirectUri();
            redirectUri.setRelyingPartyId(rp.getId());
            redirectUri.setUri(uri);
            redirectUriRepository.save(redirectUri);
        }

        return new RelyingPartyDetail(
                rp.getId(),
                rp.getRpName(),
                rp.getSectorId(),
                request.redirectUris(),
                new RelyingPartyDetail.BrandingInfo(
                        rp.getBrandingLogoUrl(),
                        rp.getBrandingPrimaryColor(),
                        rp.getBrandingSecondaryColor()
                )
        );
    }

    /**
     * Update an existing relying party
     */
    @Transactional
    public Optional<RelyingPartyDetail> updateRelyingParty(UUID id, UpdateRelyingPartyRequest request, UUID userId) {
        return relyingPartyRepository.findById(id)
                .filter(rp -> rp.getCreatedByUserId().equals(userId))
                .map(rp -> {
                    // Update fields
                    if (request.name() != null) {
                        rp.setRpName(request.name());
                    }
                    if (request.sectorId() != null) {
                        rp.setSectorId(request.sectorId());
                    }
                    if (request.logoUrl() != null) {
                        rp.setBrandingLogoUrl(request.logoUrl());
                    }
                    if (request.primaryColor() != null) {
                        rp.setBrandingPrimaryColor(request.primaryColor());
                    }
                    if (request.secondaryColor() != null) {
                        rp.setBrandingSecondaryColor(request.secondaryColor());
                    }

                    rp = relyingPartyRepository.save(rp);

                    // Update redirect URIs if provided
                    if (request.redirectUris() != null) {
                        // Delete existing URIs
                        redirectUriRepository.deleteByRelyingPartyId(rp.getId());
                        entityManager.flush(); // Flush to ensure deletes are committed
                        
                        // Create new URIs
                        for (String uri : request.redirectUris()) {
                            RedirectUri redirectUri = new RedirectUri();
                            redirectUri.setRelyingPartyId(rp.getId());
                            redirectUri.setUri(uri);
                            redirectUriRepository.save(redirectUri);
                        }
                    }

                    // Fetch current URIs
                    List<String> uris = redirectUriRepository.findByRelyingPartyId(rp.getId())
                            .stream()
                            .map(RedirectUri::getUri)
                            .collect(Collectors.toList());

                    return new RelyingPartyDetail(
                            rp.getId(),
                            rp.getRpName(),
                            rp.getSectorId(),
                            uris,
                            new RelyingPartyDetail.BrandingInfo(
                                    rp.getBrandingLogoUrl(),
                                    rp.getBrandingPrimaryColor(),
                                    rp.getBrandingSecondaryColor()
                            )
                    );
                });
    }

    /**
     * Delete a relying party
     */
    @Transactional
    public boolean deleteRelyingParty(UUID id, UUID userId) {
        return relyingPartyRepository.findById(id)
                .filter(rp -> rp.getCreatedByUserId().equals(userId))
                .map(rp -> {
                    // Delete associated redirect URIs
                    redirectUriRepository.deleteByRelyingPartyId(rp.getId());
                    entityManager.flush(); // Flush to ensure deletes are committed
                    
                    // Delete relying party
                    relyingPartyRepository.delete(rp);
                    
                    return true;
                })
                .orElse(false);
    }
}
