package ch.nullprofile.service;

import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.repository.RedirectUriRepository;
import ch.nullprofile.repository.RelyingPartyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RelyingPartyService {

    private final RelyingPartyRepository relyingPartyRepository;
    private final RedirectUriRepository redirectUriRepository;

    public RelyingPartyService(
            RelyingPartyRepository relyingPartyRepository,
            RedirectUriRepository redirectUriRepository) {
        this.relyingPartyRepository = relyingPartyRepository;
        this.redirectUriRepository = redirectUriRepository;
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
}
