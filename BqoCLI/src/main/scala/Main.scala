package bqocli

import sttp.model.StatusCode
import sttp.model.Uri

/**
 * QuickBooks Online API baseURL
 * https://developer.intuit.com/app/developer/qbo/docs/get-started/create-a-request
 */
val BASE_URL_QBO =
  sys.env.getOrElse("BASE_URL_QBO", "https://quickbooks.api.intuit.com")

/**
 * Realm ID must be set and doesn't change.
 */
val REALM_ID = sys.env.get("REALM_ID") match {
  case Some(x) => x
  case None =>
    throw new IllegalArgumentException("$REALM_ID must be set! See the README.")
}

// These are required to refresh access tokens.
val CLIENT_ID: String = sys.env.get("CLIENT_ID").get
val CLIENT_SECRET: String = sys.env.get("CLIENT_SECRET").get

/**
 * User-agent to use for API calls.
 */
val USER_AGENT = "APIExplorer"

/**
 * Refresh token.
 * See README.
 */
object RefreshToken {
  def apply(): String = os.read(os.pwd / "refresh_token.txt").trim
}

/**
 * Access token.
 * See README.
 */
object AccessToken {

  def apply(): String = os.read(os.pwd / "access_token.txt").trim

  /**
   * Try to update the token using the refresh token.
   * https://developer.intuit.com/app/developer/qbo/docs/develop/authentication-and-authorization/oauth-2.0#refresh-tokens
   */
  def update(): Either[String, Unit] = {
    val request = Requests
      .rawPost(
        "/oauth2/v1/tokens/bearer",
        baseUrl = Some("https://oauth.platform.intuit.com")
      )
      .auth
      .basic(CLIENT_ID, CLIENT_SECRET)
      .body(
        Map("grant_type" -> "refresh_token", "refresh_token" -> RefreshToken())
      )
    val response = request.send(Requests.backend) match {
      case scala.util.Success(x) => x
    }

    if (response.code == StatusCode.Ok) {
      val newAccessToken = ujson.read(response.body)("access_token").str.trim
      val newRefreshToken = ujson.read(response.body)("refresh_token").str.trim
      os.write.over(os.pwd / "access_token.txt", newAccessToken)
      os.write.over(os.pwd / "refresh_token.txt", newRefreshToken)
      Right(())
    } else {
      Left(
        s"AccessToken.update: got unknown response ${response} to request ${request}"
      )
    }
  }
}

/**
 * Making requests to the QBO API.
 */
object Requests {

  /**
   * sttp backend.
   * See https://sttp.softwaremill.com/en/latest/backends/summary.html
   */
  val backend =
    sttp.client4.wrappers.TryBackend(sttp.client4.DefaultSyncBackend())

  def rawGet(endpoint: String): sttp.client4.Request[String] =
    sttp.client4.quick.quickRequest
      .get(Uri.parse(BASE_URL_QBO + endpoint).right.get)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "application/json")

  def rawPost(
      endpoint: String,
      baseUrl: Option[String] = None
  ): sttp.client4.Request[String] =
    sttp.client4.quick.quickRequest
      .post(Uri.parse(baseUrl.getOrElse(BASE_URL_QBO) + endpoint).right.get)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "application/json")

  /**
   * Make a GET request.
   * @param endpoint e.g. "/v3/company/${REALM_ID}/companyinfo/${REALM_ID}"
   */
  def get(
      endpoint: String,
      reTry: Boolean = true
  ): Either[String, ujson.Value] = {
    // Construct the base request in case we need to re-try
    val baseRequest = rawGet(endpoint)

    val request = baseRequest.auth
      .bearer(AccessToken())
    val response = request
      .send(backend) match {
      case scala.util.Success(x) => x
    }

    if (response.code == StatusCode.Ok) {
      // probably OK
      Right(ujson.read(response.body))
    } else if (response.code == StatusCode.Unauthorized) {
      // Possibly expired access token, may need to try again.
      // But don't re-try infinitely many times.
      AccessToken.update() match {
        case Left(x) => Left(x)
        case Right(_) => {
          // Try again
          get(endpoint, reTry = false)
        }
      }
    } else {
      Left(
        s"Requests.get: got unknown response ${response} to request ${request}"
      )
    }
  }

}

/**
 * The CompanyInfo object.
 * https://developer.intuit.com/app/developer/qbo/docs/api/accounting/all-entities/companyinfo
 */
case class CompanyInfo(
    rawJson: Option[ujson.Value],
    companyName: String,
    legalName: String,
    companyAddrRaw: ujson.Value,
    customerCommunicationAddrRaw: ujson.Value,
    legalAddrRaw: ujson.Value,
    customerCommunicationEmailAddrRaw: ujson.Value,
    primaryPhoneRaw: ujson.Value,
    companyStartDate: String,
    fiscalYearStartMonth: String,
    country: String,
    emailRaw: ujson.Value,
    webAddrRaw: ujson.Value,
    supportedLanguages: String,
    nameValueRaw: ujson.Value,
    domain: String,
    sparse: Boolean,
    id: Int,
    syncToken: Int,
    metaDataRaw: ujson.Value,
)

object CompanyInfo {
  def read(): CompanyInfo = {
    val rawJson =
      Requests.get(s"/v3/company/${REALM_ID}/companyinfo/${REALM_ID}")
    val json = rawJson.flatMap(j =>
      j.obj
        .get("CompanyInfo")
        .toRight(s"CompanyInfo.read: malformed structure ${j}")
    ) match {
      case Right(x) => x
    }

    CompanyInfo(
      rawJson = Some(json),
      companyName = json("CompanyName").str,
      legalName = json("LegalName").str,
      companyAddrRaw = json("CompanyAddr"),
      customerCommunicationAddrRaw = json("CustomerCommunicationAddr"),
      legalAddrRaw = json("LegalAddr"),
      customerCommunicationEmailAddrRaw =
        json("CustomerCommunicationEmailAddr"),
      primaryPhoneRaw = json("PrimaryPhone"),
      companyStartDate = json("CompanyStartDate").str,
      fiscalYearStartMonth = json("FiscalYearStartMonth").str,
      country = json("Country").str,
      emailRaw = json("Email"),
      webAddrRaw = json("WebAddr"),
      supportedLanguages = json("SupportedLanguages").str,
      nameValueRaw = json("NameValue"),
      domain = json("domain").str,
      sparse = json("sparse").bool,
      id = json("Id").str.toInt,
      syncToken = json("SyncToken").str.toInt,
      metaDataRaw = json("MetaData"),
    )
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    println("Hello world!")
    println("Reading CompanyInfo as a sanity test.")
    pprint.pprintln(CompanyInfo.read())
  }
}
