#!/bin/sh
set -eu

KEY_ID="$(awslocal kms create-key --description 'Local secure documents development key' --query 'KeyMetadata.KeyId' --output text)"
awslocal kms create-alias --alias-name alias/secure-docs --target-key-id "$KEY_ID"
awslocal s3 mb s3://secure-document-storage
awslocal s3api put-bucket-versioning --bucket secure-document-storage --versioning-configuration Status=Enabled
awslocal s3api put-bucket-encryption --bucket secure-document-storage --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms","KMSMasterKeyID":"alias/secure-docs"},"BucketKeyEnabled":true}]}'
