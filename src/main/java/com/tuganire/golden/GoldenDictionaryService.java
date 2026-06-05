package com.tuganire.golden;

import com.tuganire.translation.TranslationResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Looks up pre-validated native translations from the golden dictionary and manages its entries (admin CRUD). */
public interface GoldenDictionaryService {

    /**
     * Looks up a normalized translation for {@code sourceText} from {@code src} to {@code tgt}.
     *
     * <p>
     * The implementation normalizes the query (lowercase, trim) before matching, so callers do not need to pre-process
     * the text. On a hit the matched entry's {@code usageCount} is incremented atomically.
     *
     * @param sourceText
     *            the text to translate; non-null
     * @param src
     *            the source language locale (e.g. {@code Locale.FRENCH})
     * @param tgt
     *            the target language locale (e.g. {@code new Locale("rw")})
     * @return a present {@link TranslationResponse} with {@code fromGoldenDictionary=true} on hit, or
     *         {@link Optional#empty()} on miss
     */
    Optional<TranslationResponse> lookup(String sourceText, Locale src, Locale tgt);

    /**
     * Returns all golden entries, most recently created first.
     *
     * @return list of entries; empty when none exist
     */
    List<GoldenEntry> findAll();

    /**
     * Creates a new golden entry from the given reference translation pair.
     *
     * @param request
     *            the entry data; non-null
     * @return the persisted entry with its generated id
     */
    GoldenEntry create(GoldenEntryRequest request);

    /**
     * Updates the existing entry identified by {@code id}.
     *
     * @param id
     *            the entry id
     * @param request
     *            the new entry data; non-null
     * @return the updated entry
     * @throws com.tuganire.shared.exception.ResourceNotFoundException
     *             if no entry exists with that id
     */
    GoldenEntry update(Long id, GoldenEntryRequest request);

    /**
     * Deletes the entry identified by {@code id}.
     *
     * @param id
     *            the entry id
     * @throws com.tuganire.shared.exception.ResourceNotFoundException
     *             if no entry exists with that id
     */
    void delete(Long id);
}
