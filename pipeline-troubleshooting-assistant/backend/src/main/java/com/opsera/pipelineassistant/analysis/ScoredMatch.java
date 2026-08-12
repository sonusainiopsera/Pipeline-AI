package com.opsera.pipelineassistant.analysis;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;

/**
 * Value type returned by PatternMatcher.match().
 * Carries the ErrorKnowledgeBase entry, the count of its keywords found in the
 * log text (score), and the total count of non-blank keywords in the pattern
 * (totalPatterns). Both counts are computed by PatternMatcher so callers never
 * need to re-parse the errorPattern string.
 */
public record ScoredMatch(ErrorKnowledgeBase entry, int score, int totalPatterns) {}
