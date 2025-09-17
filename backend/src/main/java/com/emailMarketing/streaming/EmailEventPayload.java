package com.emailMarketing.streaming;

import java.time.Instant;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailEventPayload {
    private String eventId;
    private Long userId;
    private Long campaignId;
    private String type;
    private Instant occurredAt;
    private String metadataJson;
}
