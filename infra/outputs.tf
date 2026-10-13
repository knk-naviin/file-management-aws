output "api_gateway_url" {
  description = "Managed AWS HTTP API endpoint."
  value       = aws_apigatewayv2_api.http.api_endpoint
}

output "bucket_name" {
  value = aws_s3_bucket.documents.id
}

output "kms_key_arn" {
  value = aws_kms_key.documents.arn
}

output "document_service_role_arn" {
  description = "Attach this role to the document-service workload using EKS Pod Identity."
  value       = aws_iam_role.document_service.arn
}

output "lambda_function_arns" {
  value = { for key, function in aws_lambda_function.events : key => function.arn }
}

output "deletion_notification_topic_arn" {
  value = aws_sns_topic.document_deletions.arn
}
