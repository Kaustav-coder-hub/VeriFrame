-- V1__init_schema.sql
-- Media Provenance initial database schema

CREATE TABLE media_asset (
    id UUID PRIMARY KEY,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    cloudinary_asset_id VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE media_version (
    id UUID PRIMARY KEY,
    media_asset_id UUID NOT NULL REFERENCES media_asset(id) ON DELETE CASCADE,
    version_type VARCHAR(50) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    parent_version_id UUID REFERENCES media_version(id),
    sha256_hash VARCHAR(66) NOT NULL,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    cloudinary_url VARCHAR(2048) NOT NULL,
    optimized_url VARCHAR(2048) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_media_version_asset_id ON media_version(media_asset_id);
CREATE UNIQUE INDEX idx_media_version_sha256_hash ON media_version(sha256_hash);

CREATE TABLE ai_result (
    id UUID PRIMARY KEY,
    media_version_id UUID NOT NULL REFERENCES media_version(id) ON DELETE CASCADE,
    provider VARCHAR(100) NOT NULL,
    tags_json VARCHAR(16000),
    raw_json VARCHAR(16000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_result_version_id ON ai_result(media_version_id);

CREATE TABLE provenance_record (
    id UUID PRIMARY KEY,
    media_version_id UUID NOT NULL UNIQUE REFERENCES media_version(id) ON DELETE CASCADE,
    previous_hash VARCHAR(66),
    operation VARCHAR(100) NOT NULL,
    cloudinary_asset_id VARCHAR(255) NOT NULL,
    chain_status VARCHAR(50) NOT NULL,
    tx_hash VARCHAR(255),
    record_id BIGINT,
    explorer_url VARCHAR(2048),
    error_message VARCHAR(16000),
    attempts INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_provenance_record_version_id ON provenance_record(media_version_id);
CREATE INDEX idx_provenance_record_chain_status ON provenance_record(chain_status);
CREATE INDEX idx_provenance_record_tx_hash ON provenance_record(tx_hash);
