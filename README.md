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
import bqocli._

// Get all open invoices associated with customer ID 91 and send a reminder.
Invoice.invoicesByCustomerId(91, onlyOpen = true).foreach(Invoice.send(_))

// Get open invoices and associated invoice numbers.
Invoice.invoicesByCustomerId(91, onlyOpen = true).map(x => (x, Invoice.read(x).right.get.docNumber))

// Get last invoice of company.
Invoice.read(Invoice.invoicesByCustomerId(91, onlyOpen = false).toSeq.sorted.last)

// Mark an invoice as paid on Stripe
stripe.Invoice.pay(stripe.Invoice.searchByNumber("FOOBAR-0001").right.get.id, true)
```

# Notes

If you get `stty` errors like this, this is a [bug in Ubuntu/rust-coreutils](https://bugs.launchpad.net/ubuntu/+source/rust-coreutils/+bug/2127106/comments/9).

```
/bin/stty: invalid argument '6506:5:f00bf:8a3b:3:1c:7f:15:4:0:1:0:11:13:1a:0:12:f:17:16:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0'
Exception in thread "main" java.lang.RuntimeException: Nonzero exit value: 1
	at scala.sys.process.ProcessBuilderImpl$AbstractBuilder.slurp(ProcessBuilderImpl.scala:164)
	at scala.sys.process.ProcessBuilderImpl$AbstractBuilder.$bang$bang(ProcessBuilderImpl.scala:121)
	at ammonite.terminal.TTY$.stty(Utils.scala:106)
	at ammonite.terminal.TTY$.withSttyOverride(Utils.scala:122)
	at ammonite.terminal.Terminal$.readLine(Terminal.scala:38)
	at ammonite.repl.AmmoniteFrontEnd.readLine(AmmoniteFrontEnd.scala:137)
	at ammonite.repl.AmmoniteFrontEnd.action(AmmoniteFrontEnd.scala:30)
	at ammonite.repl.Repl.action$$anonfun$2$$anonfun$2(Repl.scala:201)
```
