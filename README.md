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

3. Write the access token (`accessToken`) into `access_token.txt` and the refresh token (`refreshToken`) into `refresh_token.txt`.
4. Note that the refresh token value [changes every 24 hours](https://developer.intuit.com/app/developer/qbo/docs/develop/authentication-and-authorization/faq) so make sure to keep it up to date.

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
