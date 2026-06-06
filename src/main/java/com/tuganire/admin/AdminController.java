package com.tuganire.admin;

import com.tuganire.golden.GoldenDictionaryService;
import com.tuganire.golden.GoldenEntryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Thymeleaf controller for the {@code /admin} back-office dashboard.
 *
 * <p>
 * The dashboard has read-only feedback metrics, golden-dictionary CRUD, and LLM-usage analytics. {@code GET /admin}
 * renders the full page; the {@code /admin/golden/**} endpoints are HTMX partials that re-render the golden-entries
 * table fragment after a create / delete operation. There is no authentication in the MVP, so these routes are open
 * like the rest of the app.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private static final String VIEW_DASHBOARD = "admin/dashboard";
    private static final String FRAGMENT_GOLDEN_TABLE = "admin/fragments/golden :: table";

    private static final String ATTR_FEEDBACK = "feedbackStats";
    private static final String ATTR_USAGE = "usageStats";
    private static final String ATTR_GOLDEN_ENTRIES = "goldenEntries";

    private final AdminStatsService adminStatsService;
    private final GoldenDictionaryService goldenService;

    /**
     * Renders the full admin dashboard (all sections).
     *
     * @param model
     *            the Spring MVC model
     * @return the dashboard view name
     */
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute(ATTR_FEEDBACK, adminStatsService.feedbackStats());
        model.addAttribute(ATTR_USAGE, adminStatsService.llmUsageStats());
        model.addAttribute(ATTR_GOLDEN_ENTRIES, goldenService.findAll());
        return VIEW_DASHBOARD;
    }

    /**
     * HTMX endpoint: creates a golden entry and returns the refreshed entries-table fragment.
     *
     * @param request
     *            the validated form payload
     * @param model
     *            the Spring MVC model
     * @return the golden-table fragment view name
     */
    @PostMapping("/golden")
    public String createGolden(@Valid @ModelAttribute GoldenEntryRequest request, Model model) {
        goldenService.create(request);
        model.addAttribute(ATTR_GOLDEN_ENTRIES, goldenService.findAll());
        return FRAGMENT_GOLDEN_TABLE;
    }

    /**
     * HTMX endpoint: updates a golden entry and returns the refreshed entries-table fragment.
     *
     * @param id
     *            the entry id
     * @param request
     *            the validated form payload
     * @param model
     *            the Spring MVC model
     * @return the golden-table fragment view name
     */
    @PostMapping("/golden/{id}")
    public String updateGolden(@PathVariable Long id, @Valid @ModelAttribute GoldenEntryRequest request, Model model) {
        goldenService.update(id, request);
        model.addAttribute(ATTR_GOLDEN_ENTRIES, goldenService.findAll());
        return FRAGMENT_GOLDEN_TABLE;
    }

    /**
     * HTMX endpoint: deletes a golden entry and returns the refreshed entries-table fragment.
     *
     * @param id
     *            the entry id
     * @param model
     *            the Spring MVC model
     * @return the golden-table fragment view name
     */
    @PostMapping("/golden/{id}/delete")
    public String deleteGolden(@PathVariable Long id, Model model) {
        goldenService.delete(id);
        model.addAttribute(ATTR_GOLDEN_ENTRIES, goldenService.findAll());
        return FRAGMENT_GOLDEN_TABLE;
    }
}
