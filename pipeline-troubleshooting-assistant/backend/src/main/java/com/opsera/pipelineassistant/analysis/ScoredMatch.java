package com.opsera.pipelineassistant.analysis;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;

import java.util.List;

/**
 * Value type returned by PatternMatcher.match().
 * Carries the ErrorKnowledgeBase entry, the count of its keywords found in the
 * log text (score), the total count of non-blank keywords in the pattern
 * (totalPatterns), and the list of keyword strings that actually matched.
 */
public record ScoredMatch(ErrorKnowledgeBase entry, int score, int totalPatterns, List<String> matchedPatterns) {}
