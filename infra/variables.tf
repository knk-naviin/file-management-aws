variable "aws_region" {
  description = "AWS region for the document platform."
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Globally unique private S3 bucket name."
  type        = string
  default     = "secure-document-storage"
}

variable "backend_url" {
  description = "Reachable base URL of the Spring Cloud Gateway integration, without a trailing slash."
  type        = string
  default     = "https://replace-with-reachable-gateway.example.com"
}

variable "allowed_origins" {
  description = "Exact browser origins allowed by the managed HTTP API."
  type        = list(string)
  default     = ["https://replace-with-your-frontend.example.com"]
}

variable "notification_email" {
  description = "Optional verified email subscriber for document-deletion notifications."
  type        = string
  default     = ""
}

variable "lambda_package_path" {
  description = "Path to a zip containing the files in lambda/ (build with scripts/build-lambdas.ps1)."
  type        = string
  default     = "../lambda/build/secure-docs-lambdas.zip"
}

variable "document_service_role_name" {
  description = "IAM role name for the document-service workload identity."
  type        = string
  default     = "SecureDocsDocumentServiceRole"
}

variable "lambda_memory_mb" {
  type    = number
  default = 512
}

variable "lambda_timeout_seconds" {
  type    = number
  default = 30
}
