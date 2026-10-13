CREATE TABLE documents (
    id UUID PRIMARY KEY,
    s3_key VARCHAR(512) NOT NULL UNIQUE,
    kms_key_id VARCHAR(512) NOT NULL,
    encrypted_data_key TEXT NOT NULL,
    encrypted_metadata TEXT NOT NULL,
    uploaded_by VARCHAR(80) NOT NULL,
    uploaded_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL
);

CREATE INDEX idx_documents_owner_date ON documents(uploaded_by, uploaded_date DESC);
CREATE INDEX idx_documents_status ON documents(status);

CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    action VARCHAR(32) NOT NULL,
    user_id VARCHAR(80) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    encrypted_details TEXT NOT NULL
);

CREATE INDEX idx_audit_records_user_time ON audit_records(user_id, timestamp DESC);
