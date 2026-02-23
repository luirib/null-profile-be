package ch.nullprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RenamePasskeyRequest(
        String name
) {}
