package contracts;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * FrenOfClaw — Lightweight snippet ledger. Paw-friendly limits and lower fees than full claw stacks.
 * Tracks code snippets, hint requests, tips, and reputation. Single-file; no external DB.
 */

// ─── FOC constants (cheaper than ClawCodeMax) ─────────────────────────────────

final class FOCConfig {
    static final int FOC_MAX_SNIPPET_BYTES = 2048;
    static final int FOC_MAX_TITLE_BYTES = 64;
    static final int FOC_MIN_TIP_WEI = 10;
    static final int FOC_MAX_SNIPPETS_PER_AUTHOR = 64;
    static final int FOC_MAX_HINT_REQUESTS_PER_USER = 24;
    static final int FOC_TREASURY_FEE_BPS = 25;
    static final int FOC_BPS_DENOM = 10000;
    static final int FOC_REPUTATION_UP_DELTA = 1;
    static final int FOC_REPUTATION_DOWN_DELTA = 1;
    static final int FOC_BADGE_SLOTS = 8;
    static final int FOC_RECENT_QUEUE_SIZE = 64;
    static final long FOC_DOMAIN_SALT = 0x5E7a9C1d3F6b8e0A2c4D7f9B1e3E6a8C0d2F5L;
    static final String FOC_CURATOR_ADDR = "0x2F5a8C1e4B7d0A3f6C9b2E5d8a1F4c7B0e3A6d9F";
    static final String FOC_TREASURY_ADDR = "0x8D1f4A7c0B3e6D9a2F5c8E1b4A7d0C3f6E9a2B5";
    static final String FOC_FULFILLER_ADDR = "0xE3b6D9a2C5f8E1b4A7d0C3f6E9a2B5d8F1c4A7";
    static final int FOC_VERSION = 1;

    private FOCConfig() {}
}

// ─── FOC exceptions (unique names) ─────────────────────────────────────────────

final class FocCuratorOnlyException extends RuntimeException {
    FocCuratorOnlyException() { super("FOC: curator only"); }
}

final class FocTreasuryOnlyException extends RuntimeException {
    FocTreasuryOnlyException() { super("FOC: treasury only"); }
}

final class FocFulfillerOnlyException extends RuntimeException {
    FocFulfillerOnlyException() { super("FOC: fulfiller only"); }
}

final class FocPausedException extends RuntimeException {
    FocPausedException() { super("FOC: paused"); }
}

final class FocSnippetTooLongException extends RuntimeException {
    FocSnippetTooLongException() { super("FOC: snippet too long"); }
}

final class FocTitleTooLongException extends RuntimeException {
    FocTitleTooLongException() { super("FOC: title too long"); }
}

final class FocInvalidSnippetIdException extends RuntimeException {
    FocInvalidSnippetIdException() { super("FOC: invalid snippet id"); }
}

final class FocSnippetDeletedException extends RuntimeException {
    FocSnippetDeletedException() { super("FOC: snippet deleted"); }
}

final class FocNotAuthorException extends RuntimeException {
    FocNotAuthorException() { super("FOC: not author"); }
}

final class FocAuthorSnippetCapException extends RuntimeException {
    FocAuthorSnippetCapException() { super("FOC: author snippet cap"); }
}

final class FocHintRequestCapException extends RuntimeException {
    FocHintRequestCapException() { super("FOC: hint request cap"); }
}

final class FocInvalidHintIdException extends RuntimeException {
    FocInvalidHintIdException() { super("FOC: invalid hint id"); }
}

final class FocHintAlreadyFulfilledException extends RuntimeException {
    FocHintAlreadyFulfilledException() { super("FOC: hint already fulfilled"); }
}

final class FocTipTooSmallException extends RuntimeException {
    FocTipTooSmallException() { super("FOC: tip too small"); }
}

final class FocInsufficientBalanceException extends RuntimeException {
    FocInsufficientBalanceException() { super("FOC: insufficient balance"); }
}

final class FocLanguageAlreadyRegisteredException extends RuntimeException {
    FocLanguageAlreadyRegisteredException() { super("FOC: language already registered"); }
}

final class FocAlreadyUpvotedException extends RuntimeException {
    FocAlreadyUpvotedException() { super("FOC: already upvoted"); }
}

final class FocAlreadyDownvotedException extends RuntimeException {
    FocAlreadyDownvotedException() { super("FOC: already downvoted"); }
}

final class FocCannotVoteOwnException extends RuntimeException {
    FocCannotVoteOwnException() { super("FOC: cannot vote own"); }
}

final class FocZeroAddressException extends RuntimeException {
    FocZeroAddressException() { super("FOC: zero address"); }
}

// ─── Event payloads (FOC event names) ──────────────────────────────────────────

final class FocSnippetSubmittedEvent {
    final long snippetId;
    final String author;
    final String contentHashHex;
    final String languageId;
    final long createdAt;

    FocSnippetSubmittedEvent(long snippetId, String author, String contentHashHex, String languageId, long createdAt) {
        this.snippetId = snippetId;
        this.author = author;
        this.contentHashHex = contentHashHex;
        this.languageId = languageId;
        this.createdAt = createdAt;
    }
}

final class FocSnippetUpdatedEvent {
    final long snippetId;
    final String author;
    final String newContentHashHex;
    final long updatedAt;

    FocSnippetUpdatedEvent(long snippetId, String author, String newContentHashHex, long updatedAt) {
        this.snippetId = snippetId;
        this.author = author;
        this.newContentHashHex = newContentHashHex;
        this.updatedAt = updatedAt;
    }
}

final class FocSnippetDeletedEvent {
    final long snippetId;
