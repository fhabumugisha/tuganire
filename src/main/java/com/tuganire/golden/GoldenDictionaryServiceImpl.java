package com.tuganire.golden;

import com.tuganire.shared.exception.ResourceNotFoundException;
import com.tuganire.translation.TranslationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up pre-validated native translations stored in the golden dictionary. On a cache hit the entry's
 * {@code usageCount} is incremented so analytics can track the dictionary hit rate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class GoldenDictionaryServiceImpl implements GoldenDictionaryService {

    private final GoldenDictionaryRepo goldenDictionaryRepo;

    /**
     * {@inheritDoc}
     *
     * <p>
     * The query is normalized (lowercase, trimmed) before the database lookup so that case/whitespace differences do
     * not cause misses against seed entries.
     */
    @Override
    @Transactional
    public Optional<TranslationResponse> lookup(String sourceText, Locale src, Locale tgt) {
        String normalized = normalize(sourceText);
        String sourceLang = src.getLanguage();
        String targetLang = tgt.getLanguage();

        return goldenDictionaryRepo
                .findFirstBySourceLangAndTargetLangAndSourceTextIgnoreCase(sourceLang, targetLang, normalized)
                .map(entry -> {
                    entry.setUsageCount(entry.getUsageCount() + 1);
                    log.debug("Golden dictionary hit: [{}/{}] \"{}\" -> usageCount={}", sourceLang, targetLang,
                            normalized, entry.getUsageCount());
                    return TranslationResponse.fromGoldenDictionary(sourceText, entry.getTargetText(), List.of());
                });
    }

    // -------------------------------------------------------------------------
    // Admin CRUD
    // -------------------------------------------------------------------------

    private static final String RESOURCE_NAME = "GoldenEntry";

    @Override
    @Transactional(readOnly = true)
    public List<GoldenEntry> findAll() {
        return goldenDictionaryRepo.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public GoldenEntry create(GoldenEntryRequest request) {
        GoldenEntry entry = new GoldenEntry();
        apply(entry, request);
        entry.setValidatedAt(Instant.now());
        GoldenEntry saved = goldenDictionaryRepo.save(entry);
        log.info("Created golden entry id={} [{}→{}] \"{}\"", saved.getId(), saved.getSourceLang(),
                saved.getTargetLang(), saved.getSourceText());
        return saved;
    }

    @Override
    @Transactional
    public GoldenEntry update(Long id, GoldenEntryRequest request) {
        GoldenEntry entry = goldenDictionaryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
        apply(entry, request);
        log.info("Updated golden entry id={}", id);
        return goldenDictionaryRepo.save(entry);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!goldenDictionaryRepo.existsById(id)) {
            throw new ResourceNotFoundException(RESOURCE_NAME, id);
        }
        goldenDictionaryRepo.deleteById(id);
        log.info("Deleted golden entry id={}", id);
    }

    /** Copies request fields onto the entity (shared by create + update). */
    private static void apply(GoldenEntry entry, GoldenEntryRequest request) {
        entry.setSourceText(request.sourceText().trim());
        entry.setSourceLang(request.sourceLang());
        entry.setTargetText(request.targetText().trim());
        entry.setTargetLang(request.targetLang());
        entry.setContext(request.context() == null || request.context().isBlank() ? null : request.context().trim());
        entry.setValidatedBy(request.validatedBy().trim());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Normalizes a lookup key the same way the seed data is stored: lowercase + trim. */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase();
    }
}
