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

  /**
   * Make a POST request.
   * @param endpoint Endpoint
   * @param reqFunc optional function to create the request
   */
  def post[T](
      endpoint: String,
      contentType: String,
      responseSpec: sttp.client4.ResponseAs[T],
      reqFunc: (sttp.client4.Request[T] => sttp.client4.Request[T]) =
        identity[sttp.client4.Request[T]],
  ): Either[String, T] = {
    // Construct the base request in case we need to re-try
    val baseRequest =
      Common.rawPost(endpoint, contentType = contentType).response(responseSpec)

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
        s"Requests.post: got unknown response ${response} to request ${request}"
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

  def postGetJson(
      endpoint: String,
      data: Map[String, String],
      reqFunc: (sttp.client4.Request[String] => sttp.client4.Request[String]) =
        identity[sttp.client4.Request[String]],
  ): Either[String, ujson.Value] =
    post(
      endpoint,
      contentType = "application/json",
      responseSpec = sttp.client4.asStringAlways,
      reqFunc = (r: sttp.client4.Request[String]) => {
        reqFunc(r.body(data))
      },
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
 * The Payout object.
 * https://docs.stripe.com/api/payouts/object
 */
case class Payout(
    rawJson: Option[ujson.Value],
    id: String,
    // Amount in cents
    amount: Long,
    statement_descriptor: Option[String],
    status: String,
)

object Payout {

  given CommonParams = stripeParams

  def fromRawJson(json: ujson.Value): Either[String, Payout] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): Payout = Payout(
    rawJson = Some(json),
    id = json("id").str,
    amount = json("amount").num.toLong,
    statement_descriptor = Utils.nullableString(json("statement_descriptor")),
    status = json("status").str,
  )

  /**
   * Retrieve
   * https://docs.stripe.com/api/payouts/retrieve
   */
  def retrieve(id: String): Either[String, Payout] = {
    Requests
      .getJson(s"/v1/payouts/${id}")
      .flatMap(fromRawJson)
  }
}

/**
 * The Charge object.
 * https://docs.stripe.com/api/charges
 */
case class Charge(
    rawJson: Option[ujson.Value],
    id: String,
    description: String,
    // Amount in cents
    amount: Long,
    created: Long,
    invoice: Option[String],
)

object Charge {

  given CommonParams = stripeParams

  def fromRawJson(json: ujson.Value): Either[String, Charge] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): Charge = Charge(
    rawJson = Some(json),
    id = json("id").str,
    description = json("description").str,
    amount = json("amount").num.toLong,
    created = json("created").num.toLong,
    invoice = json.value.get("invoice") match {
      case Some(ujson.Null) => None
      case Some(x)          => Some(x.str)
      case None             => None
    }
  )

  /**
   * Retrieve
   * https://docs.stripe.com/api/charges/retrieve
   */
  def retrieve(id: String): Either[String, Charge] = {
    Requests
      .getJson(s"/v1/charges/${id}")
      .flatMap(fromRawJson)
  }
}

/**
 * The PaymentIntent object.
 * https://docs.stripe.com/api/payment_intents/object
 */
case class PaymentIntent(
    rawJson: Option[ujson.Value],
    id: String,
    // Amount in cents
    amount: Long,
)

object PaymentIntent {

  given CommonParams = stripeParams

  def fromRawJson(json: ujson.Value): Either[String, PaymentIntent] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): PaymentIntent = PaymentIntent(
    rawJson = Some(json),
    id = json("id").str,
    amount = json("amount").num.toLong,
  )

  /**
   * Retrieve
   *   https://docs.stripe.com/api/payment_intents/retrieve
   */
  def retrieve(id: String): Either[String, PaymentIntent] = {
    Requests
      .getJson(s"/v1/payment_intents/${id}")
      .flatMap(fromRawJson)
  }
}

/**
 * The BalanceTransaction object.
 * https://docs.stripe.com/api/balance_transactions
 */
case class BalanceTransaction(
    rawJson: Option[ujson.Value],
    id: String,
    // Amount in cents
    amount: Long,
    // Fee in cents
    fee: Long,
    created: Long,
    typ: String,
    description: String,
    source: Option[String],
)

object BalanceTransaction {

  given CommonParams = stripeParams

  def fromRawJson(json: ujson.Value): Either[String, BalanceTransaction] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): BalanceTransaction = BalanceTransaction(
    rawJson = Some(json),
    id = json("id").str,
    amount = json("amount").num.toLong,
    fee = json("fee").num.toLong,
    created = json("created").num.toLong,
    typ = json("type").str,
    description = json("description").str,
    source = Utils.nullableString(json("source")),
  )

  /**
   * Retrieve
   * https://docs.stripe.com/api/payouts/retrieve
   */
  def retrieve(id: String): Either[String, BalanceTransaction] = {
    Requests
      .getJson(s"/v1/balance_transactions/${id}")
      .flatMap(fromRawJson)
  }

  /**
   * https://docs.stripe.com/api/balance_transactions/list
   */
  def list(
      payout: Option[String] = None,
      typ: Option[String] = None,
      limit: Option[Int] = None
  ): Seq[BalanceTransaction] = {
    val map = Map[String, String]()
      ++ payout.map(x => ("payout" -> x))
      ++ typ.map(x => ("type" -> x))
      ++ limit.map(x => ("limit" -> x.toString))

    val rawResp = Requests
      .getJson(
        "/v1/balance_transactions",
        reqFunc = (
            x => x.body(map)
        )
      )

    rawResp match {
      case Right(x) =>
        x.obj("data")
          .arr
          .toSeq
          .map(r =>
            fromRawJson(r) match {
              case Right(b) => b
            }
          )
    }
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
    number: Option[String],
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
    number = Utils.nullableString(json("number")),
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
   * Pay an invoice.
   * Only out of band supported for now!
   * https://docs.stripe.com/api/invoices/pay
   */
  def pay(
      invoiceId: String,
      paidOutOfBand: Boolean
  ): Either[String, Invoice] = {
    require(paidOutOfBand, "Only out of band supported for now")
    Requests
      .postGetJson(
        s"/v1/invoices/${invoiceId}/pay",
        Map("paid_out_of_band" -> paidOutOfBand.toString)
      )
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
