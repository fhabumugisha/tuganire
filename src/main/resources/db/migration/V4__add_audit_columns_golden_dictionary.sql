-- V4: Add JPA auditing columns to golden_dictionary.
-- Required by @CreatedDate/@LastModifiedDate on GoldenEntry (ddl-auto=none).
-- created_at defaults to NOW() for existing rows; last_modified_at also defaults to NOW().

ALTER TABLE golden_dictionary
    ADD COLUMN created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN last_modified_at TIMESTAMP NOT NULL DEFAULT NOW();
