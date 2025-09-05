package com.emailMarketing.controller;

import org.springframework.web.bind.annotation.*; import org.springframework.http.ResponseEntity; import lombok.RequiredArgsConstructor; import com.emailMarketing.streaming.EmailEventPayload; import com.emailMarketing.streaming.EmailEventProducer;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventIngestController {
    private final EmailEventProducer producer;
    @PostMapping
    public ResponseEntity<String> ingest(@RequestBody EmailEventPayload payload){ producer.publish(payload); return ResponseEntity.accepted().body(payload.getEventId()); }
}
