package com.ngs.analytics.ai;

import com.ngs.analytics.ai.AiDtos.AskRequest;
import com.ngs.analytics.ai.AiDtos.AskResponse;
import com.ngs.analytics.ai.AiDtos.InteractionSummary;
import com.ngs.analytics.auth.SecurityUtils;
import com.ngs.analytics.domain.UserAccount;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAnalystService analystService;

    public AiController(AiAnalystService analystService) {
        this.analystService = analystService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        UserAccount user = SecurityUtils.currentUser();
        return analystService.ask(request, user);
    }

    @GetMapping("/interactions")
    public List<InteractionSummary> history() {
        return analystService.history(SecurityUtils.currentUser().getId());
    }
}
