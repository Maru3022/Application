package com.healthlife.common.dto.user;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GDPR Article 20 — Right to data portability.
 *
 * <p>Contains all personal data held for the user in a machine-readable format. The {@code
 * dataJson} field is a JSON string containing the full export payload so that the structure can
 * evolve without changing this DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprExportDto {

    private UUID userId;
    private String email;
    private OffsetDateTime exportedAt;

    /**
     * Full JSON export of all user data. Clients should treat this as an opaque blob and offer it
     * as a downloadable file (e.g. {@code healthlife-export-{userId}.json}).
     */
    private String dataJson;
}
