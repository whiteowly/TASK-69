-- Slice 9: resource file storage metadata for downloadable resources

ALTER TABLE resource_item
    ADD COLUMN file_path VARCHAR(500) NULL AFTER file_version,
    ADD COLUMN file_size BIGINT NULL AFTER file_path,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER file_size;
