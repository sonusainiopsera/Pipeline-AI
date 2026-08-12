package com.opsera.pipelineassistant.analysis;

import com.opsera.pipelineassistant.fixtures.TestFixtures;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMatcherTest {

    private PatternMatcher patternMatcher;

    @BeforeEach
    void setUp() {
        patternMatcher = new PatternMatcher();
    }

    private ErrorKnowledgeBase entry(String pattern) {
        return ErrorKnowledgeBase.builder()
                .id(99L)
                .errorPattern(pattern)
                .category("Test")
                .rootCause("test cause")
                .solution("test solution")
                .severity("LOW")
                .build();
    }

    // ── Null / empty inputs ───────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyListWhenEntriesIsNull() {
        List<ScoredMatch> result = patternMatcher.match("some log text", null);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenEntriesIsEmpty() {
        List<ScoredMatch> result = patternMatcher.match("some log text", Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldScoreZeroForAllEntriesWhenLogTextIsNull() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OutOfMemoryError,heap"));
        List<ScoredMatch> result = patternMatcher.match(null, entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    @Test
    void shouldScoreZeroForAllEntriesWhenLogTextIsEmpty() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OutOfMemoryError,heap"));
        List<ScoredMatch> result = patternMatcher.match("", entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    // ── Empty / blank pattern ─────────────────────────────────────────────────

    @Test
    void shouldScoreZeroWhenErrorPatternIsEmpty() {
        List<ErrorKnowledgeBase> entries = List.of(entry(""));
        List<ScoredMatch> result = patternMatcher.match("some log text with content", entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    @Test
    void shouldScoreZeroWhenErrorPatternIsBlank() {
        List<ErrorKnowledgeBase> entries = List.of(entry("   "));
        List<ScoredMatch> result = patternMatcher.match("some log text with content", entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    @Test
    void shouldScoreZeroWhenErrorPatternIsNull() {
        ErrorKnowledgeBase entryWithNullPattern = ErrorKnowledgeBase.builder()
                .id(1L)
                .errorPattern(null)
                .category("Test")
                .rootCause("cause")
                .solution("fix")
                .severity("LOW")
                .build();
        List<ScoredMatch> result = patternMatcher.match("some log text", List.of(entryWithNullPattern));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    // ── Single keyword ────────────────────────────────────────────────────────

    @Test
    void shouldScore1ForSingleKeywordMatch() {
        List<ErrorKnowledgeBase> entries = List.of(entry("timeout"));
        List<ScoredMatch> result = patternMatcher.match("connection timeout occurred", entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(1);
    }

    @Test
    void shouldScore0ForSingleKeywordNoMatch() {
        List<ErrorKnowledgeBase> entries = List.of(entry("timeout"));
        List<ScoredMatch> result = patternMatcher.match("connection refused error", entries);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    // ── Multiple keywords ─────────────────────────────────────────────────────

    @Test
    void shouldScoreAllMatchingKeywords() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OutOfMemoryError,heap space,java.lang"));
        List<ScoredMatch> result = patternMatcher.match(
                "java.lang.OutOfMemoryError: Java heap space exhausted", entries);
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    @Test
    void shouldScorePartialKeywordMatches() {
        List<ErrorKnowledgeBase> entries = List.of(entry("alpha,beta,gamma,delta"));
        List<ScoredMatch> result = patternMatcher.match("alpha and beta are present", entries);
        assertThat(result.get(0).score()).isEqualTo(2);
    }

    @Test
    void shouldScoreZeroWhenNoKeywordsMatch() {
        List<ErrorKnowledgeBase> entries = List.of(entry("alpha,beta,gamma"));
        List<ScoredMatch> result = patternMatcher.match("completely unrelated log message", entries);
        assertThat(result.get(0).score()).isEqualTo(0);
    }

    // ── Case insensitivity ────────────────────────────────────────────────────

    @Test
    void shouldMatchCaseInsensitivelyWhenPatternIsUpperCase() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OUTOFMEMORYERROR,HEAP"));
        List<ScoredMatch> result = patternMatcher.match("outofmemoryerror heap space", entries);
        assertThat(result.get(0).score()).isEqualTo(2);
    }

    @Test
    void shouldMatchCaseInsensitivelyWhenLogIsMixedCase() {
        List<ErrorKnowledgeBase> entries = List.of(entry("connection refused,timeout"));
        List<ScoredMatch> result = patternMatcher.match("Connection Refused: TIMEOUT after 30s", entries);
        assertThat(result.get(0).score()).isEqualTo(2);
    }

    // ── Whitespace trimming ───────────────────────────────────────────────────

    @Test
    void shouldTrimWhitespaceAroundCommaDelimitedKeywords() {
        List<ErrorKnowledgeBase> entries = List.of(entry(" alpha , beta , gamma "));
        List<ScoredMatch> result = patternMatcher.match("alpha beta gamma present in log", entries);
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    @Test
    void shouldIgnoreWhitespaceOnlyKeywordsAfterTrim() {
        // Three commas produce 4 tokens: "alpha", " ", " ", "beta"
        List<ErrorKnowledgeBase> entries = List.of(entry("alpha, , ,beta"));
        List<ScoredMatch> result = patternMatcher.match("alpha and beta are here", entries);
        // Only "alpha" and "beta" contribute; blanks after trim are skipped
        assertThat(result.get(0).score()).isEqualTo(2);
    }

    // ── Mixed delimiters (comma and newline) ──────────────────────────────────

    @Test
    void shouldSplitOnNewlineAsWellAsComma() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OutOfMemoryError\nheap space\njava.lang"));
        List<ScoredMatch> result = patternMatcher.match(
                "java.lang.OutOfMemoryError: Java heap space exhausted", entries);
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    @Test
    void shouldSplitOnMixedCommaAndNewlineDelimiters() {
        List<ErrorKnowledgeBase> entries = List.of(entry("OutOfMemoryError,heap space\njava.lang"));
        List<ScoredMatch> result = patternMatcher.match(
                "java.lang.OutOfMemoryError: Java heap space exhausted", entries);
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    // ── Multiple entries ──────────────────────────────────────────────────────

    @Test
    void shouldScoreEachEntryIndependently() {
        List<ErrorKnowledgeBase> entries = List.of(
                entry("alpha,beta"),
                entry("gamma,delta"),
                entry("epsilon")
        );
        List<ScoredMatch> result = patternMatcher.match("alpha beta gamma", entries);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).score()).isEqualTo(2); // alpha, beta
        assertThat(result.get(1).score()).isEqualTo(1); // gamma only
        assertThat(result.get(2).score()).isEqualTo(0); // epsilon not present
    }

    @Test
    void shouldPreserveEntryOrderInResults() {
        ErrorKnowledgeBase first  = entry("alpha");
        ErrorKnowledgeBase second = entry("beta");
        ErrorKnowledgeBase third  = entry("gamma");
        List<ScoredMatch> result = patternMatcher.match("alpha beta gamma", List.of(first, second, third));
        assertThat(result.get(0).entry()).isSameAs(first);
        assertThat(result.get(1).entry()).isSameAs(second);
        assertThat(result.get(2).entry()).isSameAs(third);
    }

    // ── TestFixtures integration ──────────────────────────────────────────────

    @Test
    void shouldScoreMemorySeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.memoryLog(), List.of(TestFixtures.memoryEntry()));
        // memoryEntry has 3 keywords: "OutOfMemoryError", "heap space", "java.lang.OutOfMemoryError"
        // memoryLog contains all three
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    @Test
    void shouldScoreNetworkSeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.networkLog(), List.of(TestFixtures.networkEntry()));
        // networkEntry has 3 keywords: "connection refused", "ECONNREFUSED", "connection timeout"
        assertThat(result.get(0).score()).isEqualTo(3);
    }

    @Test
    void shouldScorePermissionsSeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.permissionsLog(), List.of(TestFixtures.permissionsEntry()));
        // permissionsEntry has 4 keywords
        assertThat(result.get(0).score()).isEqualTo(4);
    }

    @Test
    void shouldScoreDependencySeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.dependencyLog(), List.of(TestFixtures.dependencyEntry()));
        // dependencyEntry has 4 keywords
        assertThat(result.get(0).score()).isEqualTo(4);
    }

    @Test
    void shouldScoreDockerSeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.dockerLog(), List.of(TestFixtures.dockerEntry()));
        // dockerEntry has 4 keywords
        assertThat(result.get(0).score()).isEqualTo(4);
    }

    @Test
    void shouldScoreTestingSeedEntryCorrectlyWithMatchingLog() {
        List<ScoredMatch> result = patternMatcher.match(
                TestFixtures.testingLog(), List.of(TestFixtures.testingEntry()));
        // testingEntry has 4 keywords
        assertThat(result.get(0).score()).isEqualTo(4);
    }

    @Test
    void shouldFindBestMatchAcrossAllSeedEntries() {
        List<ScoredMatch> results = patternMatcher.match(
                TestFixtures.dockerLog(), TestFixtures.allSeedEntries());
        assertThat(results).hasSize(6);

        // The docker entry (index 4) should have the highest score
        ScoredMatch best = results.stream()
                .max(java.util.Comparator.comparingInt(ScoredMatch::score))
                .orElseThrow();
        assertThat(best.entry().getCategory()).isEqualTo("Docker");
    }

    @Test
    void shouldScoreZeroForNonMatchingLogAcrossAllSeedEntries() {
        List<ScoredMatch> results = patternMatcher.match(
                "completely irrelevant log that matches nothing", TestFixtures.allSeedEntries());
        assertThat(results).hasSize(6);
        results.forEach(sm -> assertThat(sm.score()).isEqualTo(0));
    }
}
