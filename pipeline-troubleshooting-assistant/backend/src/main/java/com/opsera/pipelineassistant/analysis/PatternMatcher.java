package com.opsera.pipelineassistant.analysis;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring bean responsible for keyword-based pattern matching.
 *
 * <p>Accepts a log text string and a list of ErrorKnowledgeBase entries; returns
 * a ScoredMatch for every entry (including zero-score entries) so the caller can
 * choose its own selection strategy.
 *
 * <p>Splitting rule: errorPattern is split on commas and newlines so patterns stored
 * either as comma-separated or line-separated keyword lists are handled uniformly.
 * Each keyword is trimmed and compared case-insensitively against the log text.
 */
@Service
@Slf4j
public class PatternMatcher {

    private static final String SPLIT_REGEX = "[,\n]";

    /**
     * Scores each entry by counting how many of its keywords appear in {@code logText}.
     *
     * @param logText the (sanitized) log text to search; null or empty yields all-zero scores
     * @param entries the knowledge-base entries to score; null returns an empty list
     * @return list of ScoredMatch in the same order as {@code entries}
     */
    public List<ScoredMatch> match(String logText, List<ErrorKnowledgeBase> entries) {
        if (entries == null) {
            return List.of();
        }

        String normalizedLog = (logText == null || logText.isEmpty())
                ? ""
                : logText.toLowerCase();

        List<ScoredMatch> results = new ArrayList<>(entries.size());
        for (ErrorKnowledgeBase entry : entries) {
            results.add(new ScoredMatch(entry, scoreEntry(normalizedLog, entry)));
        }
        return results;
    }

    private int scoreEntry(String normalizedLog, ErrorKnowledgeBase entry) {
        String errorPattern = entry.getErrorPattern();
        if (errorPattern == null || errorPattern.isBlank()) {
            log.warn("Entry id={} has null/blank errorPattern — scoring as 0", entry.getId());
            return 0;
        }

        String[] keywords = errorPattern.split(SPLIT_REGEX);
        int matchCount = 0;
        for (String keyword : keywords) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && normalizedLog.contains(trimmed.toLowerCase())) {
                matchCount++;
            }
        }
        return matchCount;
    }
}
