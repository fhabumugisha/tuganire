package com.tuganire.conversation;

import java.util.Locale;

public sealed interface ConversationEvent
        permits ConversationEvent.SpeechStarted, ConversationEvent.PartialTranscript, ConversationEvent.FinalTranscript,
        ConversationEvent.TranslationReady, ConversationEvent.AudioReady, ConversationEvent.ErrorOccurred {

    record SpeechStarted(String sessionId, Locale language) implements ConversationEvent {
    }

    record PartialTranscript(String sessionId, String text) implements ConversationEvent {
    }

    record FinalTranscript(String sessionId, String text, Locale language) implements ConversationEvent {
    }

    record TranslationReady(String sessionId, String text, Locale targetLang) implements ConversationEvent {
    }

    record AudioReady(String sessionId, String audioUrl, long durationMs) implements ConversationEvent {
    }

    record ErrorOccurred(String sessionId, String code, String message) implements ConversationEvent {
    }

    static String describe(ConversationEvent event) {
        return switch (event) {
            case SpeechStarted s -> "Speech started in " + s.language();
            case PartialTranscript p -> "Partial: " + p.text();
            case FinalTranscript f -> "Final: " + f.text();
            case TranslationReady t -> "Translated to " + t.targetLang();
            case AudioReady a -> "Audio ready (" + a.durationMs() + "ms)";
            case ErrorOccurred e -> "Error: " + e.message();
        };
    }
}
