#!/bin/sh
java -jar ./ccpay-scheduled-jobs-1.2.4.jar $SLOT $WEBJOB_S2S_CLIENT_SECRET $AUTH_PROVIDER_SERVICE_CLIENT_BASEURL $WEBJOB_S2S_CLIENT_ID $PAYMENT_SERVER_URL status-update
