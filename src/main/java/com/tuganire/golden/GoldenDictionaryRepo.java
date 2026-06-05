package com.tuganire.golden;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldenDictionaryRepo extends JpaRepository<GoldenEntry, Long> {

    Optional<GoldenEntry> findFirstBySourceLangAndTargetLangAndSourceTextIgnoreCase(String sourceLang,
            String targetLang, String sourceText);

    List<GoldenEntry> findAllByOrderByCreatedAtDesc();
}
