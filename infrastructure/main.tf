provider "azurerm" {
  features {}
}

provider "azurerm" {
  alias = "sendgrid"
  features {}
  subscription_id = var.env != "prod" ? local.sendgrid_subscription.nonprod : local.sendgrid_subscription.prod
}

locals {

  vaultName = join("-", [var.core_product, var.env])

  s2sUrl = "http://rpe-service-auth-provider-${var.env}.service.core-compute-${var.env}.internal"

  #region API gateway
  thumbprints_in_quotes = "${formatlist("&quot;%s&quot;", var.telephony_api_gateway_certificate_thumbprints)}"
  thumbprints_in_quotes_str = "${join(",", local.thumbprints_in_quotes)}"
  api_policy = "${replace(file("template/api-policy.xml"), "ALLOWED_CERTIFICATE_THUMBPRINTS", local.thumbprints_in_quotes_str)}"
  api_base_path = "telephony-api"
  # endregion

  sendgrid_subscription = {
    prod = "8999dec3-0104-4a27-94ee-6588559729d1"
    nonprod = "1c4f0704-a29e-403d-b719-b90c34ef14c9"
  }
}

data "azurerm_key_vault" "payment_key_vault" {
  name = "${local.vaultName}"
  resource_group_name = join("-", [var.core_product, var.env])
}

data "azurerm_key_vault_secret" "gov_pay_keys_cmc" {
  name = "gov-pay-keys-cmc"
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

//created seperate kv for cmc claim store and copied claim store value
resource "azurerm_key_vault_secret" "gov-pay-keys-cmc-claim-store" {
  name = "gov-pay-keys-cmc-claim-store"
  value = data.azurerm_key_vault_secret.gov_pay_keys_cmc.value
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

module "payment-database-v11" {
  source = "git@github.com:hmcts/cnp-module-postgres?ref=master"
  product = var.product
  component = var.component
  name = join("-", [var.product, "postgres-db-v11"])
  location = var.location
  env = var.env
  postgresql_user = var.postgresql_user
  database_name = var.database_name
  sku_name = var.sku_name
  sku_capacity = var.sku_capacity
  sku_tier = "GeneralPurpose"
  common_tags = var.common_tags
  subscription = var.subscription
  postgresql_version = var.postgresql_version
}

# Populate Vault with DB info

resource "azurerm_key_vault_secret" "POSTGRES-USER" {
  name      = join("-", [var.component, "POSTGRES-USER"])
  value     = module.payment-database-v11.user_name
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS" {
  name      = join("-", [var.component, "POSTGRES-PASS"])
  value     = module.payment-database-v11.postgresql_password
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_HOST" {
  name      = join("-", [var.component, "POSTGRES-HOST"])
  value     = module.payment-database-v11.host_name
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_PORT" {
  name      = join("-", [var.component, "POSTGRES-PORT"])
  value     = module.payment-database-v11.postgresql_listen_port
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

resource "azurerm_key_vault_secret" "POSTGRES_DATABASE" {
  name      = join("-", [var.component, "POSTGRES-DATABASE"])
  value     = module.payment-database-v11.postgresql_database
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

# Populate Vault with SendGrid API token

data "azurerm_key_vault" "sendgrid" {
  provider = azurerm.sendgrid

  name                = var.env != "prod" ? "sendgridnonprod" : "sendgridprod"
  resource_group_name = var.env != "prod" ? "SendGrid-nonprod" : "SendGrid-prod"
}

data "azurerm_key_vault_secret" "sendgrid-api-key" {
  provider = azurerm.sendgrid
  
  name         = "hmcts-payment-api-key"
  key_vault_id = data.azurerm_key_vault.sendgrid.id
}

resource "azurerm_key_vault_secret" "spring-mail-password" {
  name         = "spring-mail-password"
  value        = data.azurerm_key_vault_secret.sendgrid-api-key.value
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}


# region API (gateway)

data "azurerm_key_vault_secret" "s2s_client_secret" {
  name = "gateway-s2s-client-secret"
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

data "azurerm_key_vault_secret" "s2s_client_id" {
  name = "gateway-s2s-client-id"
  key_vault_id = data.azurerm_key_vault.payment_key_vault.id
}

data "template_file" "policy_template" {
  template = "${file("${path.module}/template/api-policy.xml")}"

  vars = {
    allowed_certificate_thumbprints = "${local.thumbprints_in_quotes_str}"
    s2s_client_id = "${data.azurerm_key_vault_secret.s2s_client_id.value}"
    s2s_client_secret = "${data.azurerm_key_vault_secret.s2s_client_secret.value}"
    s2s_base_url = "${local.s2sUrl}"
  }
}

data "template_file" "api_template" {
  template = "${file("${path.module}/template/api.json")}"
}

resource "azurerm_template_deployment" "telephony_api" {
  template_body       = data.template_file.api_template.rendered
  name                = "telephony-api-${var.env}"
  deployment_mode     = "Incremental"
  resource_group_name = "core-infra-${var.env}"
  count               = var.env != "preview" ? 1: 0

  parameters = {
    apiManagementServiceName  = "core-api-mgmt-${var.env}"
    apiName                   = "telephony-api"
    apiProductName            = "telephony"
    serviceUrl                = "http://payment-api-${var.env}.service.core-compute-${var.env}.internal"
    apiBasePath               = local.api_base_path
    policy                    = data.template_file.policy_template.rendered
  }
}

# endregion
