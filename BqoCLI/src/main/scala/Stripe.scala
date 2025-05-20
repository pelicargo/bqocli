package bqocli
package stripe

import sttp.model.StatusCode
import sttp.model.Uri

/**
 * https://docs.stripe.com/api/
 */
val BASE_URL_STRIPE =
  sys.env.getOrElse("BASE_URL_STRIPE", "https://api.stripe.com")

/**
 * Stripe API key
 */
val STRIPE_API_KEY = sys.env.get("STRIPE_API_KEY") match {
  case Some(x) => x
  case None =>
    throw new IllegalArgumentException(
      "$STRIPE_API_KEY must be set! See the README."
    )
}

val stripeParams = CommonParams(
  baseUrl = BASE_URL_STRIPE,
  userAgent = "curl/8.5.0",
)

/**
 * Making requests to the Stripe API.
 */
object Requests {

  given CommonParams = stripeParams

  /**
   * Make a GET request.
   * @param endpoint Endpoint
   * @param reqFunc optional function to create the request
   */
  def get[T](
      endpoint: String,
      contentType: String,
      responseSpec: sttp.client4.ResponseAs[T],
      reqFunc: (sttp.client4.Request[T] => sttp.client4.Request[T]) =
        identity[sttp.client4.Request[T]],
  ): Either[String, T] = {
    // Construct the base request in case we need to re-try
    val baseRequest =
      Common.rawGet(endpoint, contentType = contentType).response(responseSpec)

    val request = reqFunc(
      baseRequest.auth
        .bearer(STRIPE_API_KEY)
    )
    val response = request
      .send(Common.backend) match {
      case scala.util.Success(x) => x
    }

    if (response.code == StatusCode.Ok) {
      // probably OK
      Right(response.body)
    } else {
      Left(
        s"Requests.get: got unknown response ${response} to request ${request}"
      )
    }
  }

  def getJson(
      endpoint: String,
      reqFunc: (sttp.client4.Request[String] => sttp.client4.Request[String]) =
        identity[sttp.client4.Request[String]],
  ): Either[String, ujson.Value] =
    get(
      endpoint,
      contentType = "application/json",
      responseSpec = sttp.client4.asStringAlways,
      reqFunc = reqFunc,
    ).map(ujson.read(_))
}

/**
 * The Customer object.
 * https://docs.stripe.com/api/customers/object
 */
case class Customer(
    rawJson: Option[ujson.Value],
    id: String,
    name: String,
    email: String,
    invoice_prefix: String,
)


object Customer {

  def fromRawJson(json: ujson.Value): Either[String, Customer] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): Customer = Customer(
    rawJson = Some(json),
    id = json("id").str,
    name = json("name").str,
    email = json("email").str,
    invoice_prefix = json("invoice_prefix").str,
  )

  /**
   * Retrieve an invoice.
   * https://docs.stripe.com/api/customers/retrieve
   */
  def retrieve(id: String): Either[String, Customer] = {
    Requests
      .getJson(s"/v1/customers/${id}")
      .flatMap(fromRawJson)
  }
}

/**
 * The Invoice object.
 * https://docs.stripe.com/api/invoices
 */
case class Invoice(
    rawJson: Option[ujson.Value],
    id: String,
    customer: String,
    // Time at which the object was created. Measured in seconds since the Unix epoch
    created: Long,
    // Total after discounts and taxes (in integer cents)
    total: Long,
)

object Invoice {

  given CommonParams = stripeParams

  def fromRawJson(json: ujson.Value): Either[String, Invoice] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): Invoice = Invoice(
    rawJson = Some(json),
    id = json("id").str,
    customer = json("customer").str,
    created = json("created").num.toLong,
    total = json("total").num.toLong,
  )

  /**
   * Retrieve an invoice.
   * https://docs.stripe.com/api/invoices/retrieve
   */
  def retrieve(invoiceId: String): Either[String, Invoice] = {
    Requests
      .getJson(s"/v1/invoices/${invoiceId}")
      .flatMap(fromRawJson)
  }

  /**
   * Get invoice by number.
   * https://docs.stripe.com/api/invoices/search
   */
  def searchByNumber(invoiceId: String): Either[String, Invoice] = {
    val rawResp = Requests
      .getJson(
        "/v1/invoices/search",
        reqFunc = (
            x =>
              x.body(
                Map(
                  "query" -> s"number:\"${invoiceId}\"",
                  "limit" -> 1.toString
                )
              )
        )
      )
    val rawJson = rawResp.flatMap(x =>
      scala.util.Try(x.obj("data").arr(0)).toEither.left.map(_.toString)
    )
    rawJson.flatMap(fromRawJson)
  }
}
