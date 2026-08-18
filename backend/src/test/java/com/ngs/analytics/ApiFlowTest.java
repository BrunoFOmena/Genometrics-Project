package com.ngs.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void uploadFixturesThenReportsAndRejectsForeignAccess() throws Exception {
        String token = register("owner" + System.nanoTime() + "@example.com");
        String otherToken = register("other" + System.nanoTime() + "@example.com");

        String projectId = createProject(token, "Cohort");
        String sampleId = createSample(token, projectId, "S1");

        mockMvc.perform(get("/api/reports/" + sampleId).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        upload(token, sampleId, "sample.fastq", Files.readAllBytes(fixture("sample.fastq")));
        upload(token, sampleId, "sample.vcf", Files.readAllBytes(fixture("sample.vcf")));
        awaitDone(token, sampleId);

        mockMvc.perform(get("/api/samples/" + sampleId + "/metrics/fastq").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readCount").value(org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(get("/api/samples/" + sampleId + "/metrics/vcf").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantCount").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/reports/" + sampleId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleName").value("S1"))
                .andExpect(jsonPath("$.fastq.readCount").exists())
                .andExpect(jsonPath("$.vcf.variantCount").exists());

        byte[] csv = mockMvc.perform(get("/api/reports/" + sampleId + "/csv").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertTrue(new String(csv).startsWith("section,metric,value"));

        byte[] pdf = mockMvc.perform(get("/api/reports/" + sampleId + "/pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertTrue(new String(pdf, 0, 4).startsWith("%PDF"));

        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/samples/" + sampleId + "/files").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyAndUnknownUploadsAreRejected() throws Exception {
        String token = register("up" + System.nanoTime() + "@example.com");
        String sampleId = createSample(token, createProject(token, "P"), "S");

        mockMvc.perform(multipart("/api/samples/" + sampleId + "/files")
                        .file(new MockMultipartFile("file", "empty.fastq", "text/plain", new byte[0]))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is required"));

        mockMvc.perform(multipart("/api/samples/" + sampleId + "/files")
                        .file(new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported file type. Use FASTQ/VCF/FASTA (+ .gz)"));
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secret12","displayName":"Researcher"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"test"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createSample(String token, String projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/samples")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","notes":""}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void upload(String token, String sampleId, String filename, byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/samples/" + sampleId + "/files")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.status").exists());
    }

    private void awaitDone(String token, String sampleId) throws Exception {
        for (int i = 0; i < 50; i++) {
            String body = mockMvc.perform(get("/api/samples/" + sampleId + "/analyses")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode analyses = objectMapper.readTree(body);
            boolean allDone = analyses.size() > 0;
            boolean failed = false;
            for (JsonNode analysis : analyses) {
                String status = analysis.get("status").asText();
                if ("FAILED".equals(status)) {
                    failed = true;
                }
                if (!"DONE".equals(status)) {
                    allDone = false;
                }
            }
            if (failed) {
                fail("analysis failed: " + body);
            }
            if (allDone) {
                return;
            }
            Thread.sleep(200);
        }
        fail("timed out waiting for analyses to complete");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Path fixture(String name) {
        Path fromModule = Path.of("..", "datasets", name);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        Path fromRoot = Path.of("datasets", name);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("Missing fixture datasets/" + name);
    }
}
