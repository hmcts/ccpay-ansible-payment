variable "product" {
  type    = string
  default = "payment"
}

variable "component" {
  type    = string
}

variable "location" {
  type    = string
  default = "UK South"
}

variable "env" {
  type = string
}
variable "subscription" {
  type = string
}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  type                        = string
  description                 = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "microservice" {
  type = string
  default = "payment-app"
}

variable "database_name" {
  type    = string
  default = "payment"
}

variable "postgresql_user" {
  type    = string
  default = "payment"
}

variable "common_tags" {
  type = map(string)
}

variable "core_product" {
  type    = string
  default = "ccpay"
}

# thumbprint of the SSL certificate for API gateway tests
variable telephony_api_gateway_certificate_thumbprints {
  type = list(string)
  default = []
}

variable "postgresql_version" {
  default = "11"
}

variable sku_name {
  default = "GP_Gen5_2"
}

variable "sku_capacity" {
  default = "2"
}
