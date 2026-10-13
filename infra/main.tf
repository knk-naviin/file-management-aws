provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  bucket_arn = "arn:${data.aws_partition.current.partition}:s3:::${var.bucket_name}"
  lambda_functions = {
    thumbnail = {
      name    = "GenerateThumbnailLambda"
      handler = "thumbnail.handler"
    }
    audit = {
      name    = "AuditLambda"
      handler = "audit.handler"
    }
    notification = {
      name    = "NotificationLambda"
      handler = "notification.handler"
    }
  }
  api_routes = [
    "POST /auth/login",
    "POST /auth/logout",
    "POST /auth/register",
    "POST /auth/refresh",
    "GET /auth/profile",
    "GET /users/me",
    "POST /documents/upload",
    "GET /documents",
    "GET /documents/{id}",
    "DELETE /documents/{id}",
  ]
}

resource "aws_kms_key" "documents" {
  description             = "Customer-managed key for secure document data and metadata"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy                  = data.aws_iam_policy_document.kms_key.json

  tags = {
    Application = "secure-document-management"
    DataClass   = "confidential"
  }
}

resource "aws_kms_alias" "documents" {
  name          = "alias/secure-docs"
  target_key_id = aws_kms_key.documents.key_id
}

resource "aws_s3_bucket" "documents" {
  bucket = var.bucket_name

  tags = {
    Application = "secure-document-management"
  }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_iam_policy_document" "bucket_transport" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.documents.arn,
      "${aws_s3_bucket.documents.arn}/*"
    ]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "documents" {
  bucket = aws_s3_bucket.documents.id
  policy = data.aws_iam_policy_document.bucket_transport.json
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.documents.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_ownership_controls" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"
    filter {}
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
  rule {
    id     = "expire-noncurrent-object-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

resource "aws_sns_topic" "document_deletions" {
  name              = "secure-documents-deleted"
  kms_master_key_id = aws_kms_key.documents.arn
}

resource "aws_sns_topic_subscription" "deletion_email" {
  count     = var.notification_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.document_deletions.arn
  protocol  = "email"
  endpoint  = var.notification_email
}

resource "aws_cloudwatch_log_group" "lambda" {
  for_each          = local.lambda_functions
  name              = "/aws/lambda/${each.value.name}"
  retention_in_days = 90
  kms_key_id        = aws_kms_key.documents.arn
}

resource "aws_iam_role" "lambda" {
  for_each = local.lambda_functions
  name     = "${each.value.name}Role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

data "aws_iam_policy_document" "lambda" {
  for_each = local.lambda_functions
  statement {
    sid       = "WriteOwnCloudWatchLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.lambda[each.key].arn}:*"]
  }
  dynamic "statement" {
    for_each = each.key == "thumbnail" ? [1] : []
    content {
      sid       = "ReadEncryptedDocumentsAndWritePreviews"
      effect    = "Allow"
      actions   = ["s3:GetObject", "s3:PutObject"]
      resources = ["${local.bucket_arn}/documents/*", "${local.bucket_arn}/thumbnails/*"]
    }
  }
  dynamic "statement" {
    for_each = each.key == "audit" ? [1] : []
    content {
      sid       = "ReadUploadEventAndWriteAuditRecord"
      effect    = "Allow"
      actions   = ["s3:PutObject"]
      resources = ["${local.bucket_arn}/archives/audit/*"]
    }
  }
  dynamic "statement" {
    for_each = each.key == "notification" ? [1] : []
    content {
      sid       = "PublishDeletionNotifications"
      effect    = "Allow"
      actions   = ["sns:Publish"]
      resources = [aws_sns_topic.document_deletions.arn]
    }
  }
  dynamic "statement" {
    for_each = contains(["thumbnail", "audit"], each.key) ? [1] : []
    content {
      sid       = "UseCustomerManagedKeyForEventProcessing"
      effect    = "Allow"
      actions   = each.key == "thumbnail" ? ["kms:Decrypt", "kms:GenerateDataKey"] : ["kms:Encrypt", "kms:GenerateDataKey"]
      resources = [aws_kms_key.documents.arn]
    }
  }
}

resource "aws_iam_role_policy" "lambda" {
  for_each = local.lambda_functions
  name     = "${each.value.name}LeastPrivilege"
  role     = aws_iam_role.lambda[each.key].id
  policy   = data.aws_iam_policy_document.lambda[each.key].json
}

resource "aws_lambda_function" "events" {
  for_each         = local.lambda_functions
  function_name    = each.value.name
  role             = aws_iam_role.lambda[each.key].arn
  runtime          = "nodejs22.x"
  handler          = each.value.handler
  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds
  architectures    = ["arm64"]
  depends_on       = [aws_cloudwatch_log_group.lambda]

  environment {
    variables = {
      BUCKET_NAME   = aws_s3_bucket.documents.id
      KMS_KEY_ARN   = aws_kms_key.documents.arn
      SNS_TOPIC_ARN = aws_sns_topic.document_deletions.arn
    }
  }
}

resource "aws_lambda_permission" "s3" {
  for_each      = local.lambda_functions
  statement_id  = "AllowEventBridgeInvoke${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.events[each.key].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.s3_events[each.key].arn
}

resource "aws_s3_bucket_notification" "events" {
  bucket      = aws_s3_bucket.documents.id
  eventbridge = true
}

resource "aws_cloudwatch_event_rule" "s3_events" {
  for_each = {
    thumbnail    = "Object Created"
    audit        = "Object Created"
    notification = "Object Deleted"
  }
  name        = "secure-docs-${each.key}-s3-event"
  description = "Route document object events to ${local.lambda_functions[each.key].name}"
  event_pattern = jsonencode({
    source        = ["aws.s3"]
    "detail-type" = [each.value]
    detail = {
      bucket = { name = [aws_s3_bucket.documents.id] }
      object = { key = [{ prefix = "documents/" }] }
    }
  })
  depends_on = [aws_s3_bucket_notification.events]
}

resource "aws_cloudwatch_event_target" "s3_events" {
  for_each  = local.lambda_functions
  rule      = aws_cloudwatch_event_rule.s3_events[each.key].name
  target_id = local.lambda_functions[each.key].name
  arn       = aws_lambda_function.events[each.key].arn
}

resource "aws_sns_topic_policy" "document_deletions" {
  arn = aws_sns_topic.document_deletions.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowNotificationFunctionPublish"
        Effect    = "Allow"
        Principal = { AWS = aws_iam_role.lambda["notification"].arn }
        Action    = ["sns:Publish"]
        Resource  = aws_sns_topic.document_deletions.arn
      },
      {
        Sid       = "AllowAccountOwnerTopicAdministration"
        Effect    = "Allow"
        Principal = { AWS = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = ["sns:GetTopicAttributes", "sns:SetTopicAttributes", "sns:Subscribe", "sns:ListSubscriptionsByTopic"]
        Resource  = aws_sns_topic.document_deletions.arn
      }
    ]
  })
}

resource "aws_iam_role" "document_service" {
  name = var.document_service_role_name
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "pods.eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        ArnLike      = { "aws:SourceArn" = "arn:${data.aws_partition.current.partition}:eks:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster/*" }
      }
    }]
  })
}

data "aws_iam_policy_document" "document_service" {
  statement {
    sid       = "ListOnlyApplicationPrefixes"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.documents.arn]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["documents/*", "archives/*", "users/*", "thumbnails/*"]
    }
  }
  statement {
    sid       = "ManageOnlyApplicationObjects"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.documents.arn}/documents/*", "${aws_s3_bucket.documents.arn}/archives/*", "${aws_s3_bucket.documents.arn}/users/*"]
  }
  statement {
    sid       = "UseDocumentKey"
    effect    = "Allow"
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey", "kms:DescribeKey"]
    resources = [aws_kms_key.documents.arn]
  }
}

resource "aws_iam_role_policy" "document_service" {
  name   = "SecureDocumentStorageAndKms"
  role   = aws_iam_role.document_service.id
  policy = data.aws_iam_policy_document.document_service.json
}

resource "aws_apigatewayv2_api" "http" {
  name          = "secure-document-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = var.allowed_origins
    allow_methods = ["GET", "POST", "DELETE", "OPTIONS"]
    allow_headers = ["authorization", "content-type", "x-request-id"]
    max_age       = 3600
  }
}

resource "aws_apigatewayv2_integration" "gateway" {
  api_id                 = aws_apigatewayv2_api.http.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = var.backend_url
  payload_format_version = "1.0"
  request_parameters = {
    "overwrite:path" = "$request.path"
  }
}

resource "aws_apigatewayv2_route" "routes" {
  for_each  = toset(local.api_routes)
  api_id    = aws_apigatewayv2_api.http.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.gateway.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.http.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}

data "aws_iam_policy_document" "kms_key" {
  statement {
    sid       = "EnableAccountAdministration"
    effect    = "Allow"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
  statement {
    sid       = "AllowDocumentWorkloadAndEventHandlers"
    effect    = "Allow"
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey", "kms:DescribeKey"]
    resources = ["*"]
    principals {
      type = "AWS"
      identifiers = concat(
        [aws_iam_role.document_service.arn],
        [for role in aws_iam_role.lambda : role.arn]
      )
    }
  }
  statement {
    sid       = "AllowSnsToUseKeyForEncryptedNotifications"
    effect    = "Allow"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["sns.${var.aws_region}.amazonaws.com"]
    }
  }
  statement {
    sid       = "AllowCloudWatchLogsToUseLogGroupKey"
    effect    = "Allow"
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["logs.${var.aws_region}.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/lambda/*"]
    }
  }
}
