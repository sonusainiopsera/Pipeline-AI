package com.opsera.pipelineassistant.analysis;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;

/**
 * Value type returned by PatternMatcher.match().
 * Pairs an ErrorKnowledgeBase entry with the integer count of its keywords
 * that were found in the log text.
 */
public record ScoredMatch(ErrorKnowledgeBase entry, int score) {}
