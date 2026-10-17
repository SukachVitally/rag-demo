package org.example.ragdemo.controller;

import lombok.RequiredArgsConstructor;
import org.example.ragdemo.dto.QuestionRequest;
import org.example.ragdemo.service.IngestService;
import org.example.ragdemo.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RagController {
    private final IngestService ingest;
    private final RagService rag;

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Map.of("chunks", ingest.ingest(file.getOriginalFilename(), text));
    }


    @PostMapping("/ask")
    public RagService.Answer ask(@RequestBody QuestionRequest q) { return rag.ask(q.question()); }

}
