Unofficial QuickBooks Online CLI interface
------------------------------------------

Disclaimer: This open source code is independently developed and is not officially endorsed, approved, or supported by Intuit or QuickBooks. Use at your own risk.

# Credential setup

1. Follow steps 2-5 ("Set up your developer account" to "Learn how to get your app’s credentials") on https://developer.intuit.com/app/developer/qbo/docs/get-started/start-developing-your-app
   This should get you the "Client ID" and "Client Secret".
   Make sure to get the production credentials (this will include a 30 minute survey). This app is designed only for private/internal use.
2. Go to the [Intuit OAuth 2.0 Playground](https://developer.intuit.com/app/developer/playground), and get the "Authorization Code" and "Realm ID".
   Then click through to Step 2 to get the "access token" and "refresh token".

```json
{
 "refreshToken": "[...]",
 "accessToken": "[...]",
 "expires_in": 3600,
 "x_refresh_token_expires_in": 8726400,
 "idToken": "[...]"
}
```

3. Write the access token (`accessToken`) into `access_token.txt` and the refresh token (`refreshToken`) into `refresh_token.txt`. The program will change them as needed.
4. Note that the refresh token value [changes every 24 hours](https://developer.intuit.com/app/developer/qbo/docs/develop/authentication-and-authorization/faq) so make sure to keep it up to date.
5. Set the `REALM_ID` environment variable e.g. `export REALM_ID=9123456789012345`.
6. Set the `CLIENT_ID` and `CLIENT_SECRET` variables (this is used to refresh access tokens).

By default, the production baseURL is used (`https://quickbooks.api.intuit.com`). To change it, set the environment variable `BASE_URL_QBO`.

# Quickstart

1. Ensure that the credentials are set up as per the above.
2. Run `setup.sh` to prep.

```shell
./setup.sh
```

3. Run the sanity check.

```shell
./mill -i BqoCLI.run
```

4. Run it in a repl:

```shell
./mill -i BqoCLI.repl
```

# Examples

```scala
// Get all invoices associated with customer ID 91 and send a reminder.
Invoice.invoicesByCustomerId(91).foreach(Invoice.send(_))
```
