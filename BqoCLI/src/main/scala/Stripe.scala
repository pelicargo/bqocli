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
 * Every Stripe list endpoint caps `limit` at 100, defaulting to 10. Larger
 * values are rejected, so anything bigger has to be paginated with
 * `starting_after`.
 * https://docs.stripe.com/api/pagination
 */
val MAX_LIST_LIMIT = 100

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
 * A date interval filter, as accepted by Stripe's list endpoints.
 * All values are measured in seconds since the Unix epoch.
 *
 * @param gt Minimum value to filter by (exclusive)
 * @param gte Minimum value to filter by (inclusive)
 * @param lt Maximum value to filter by (exclusive)
 * @param lte Maximum value to filter by (inclusive)
 */
case class DateFilter(
    gt: Option[Long] = None,
    gte: Option[Long] = None,
    lt: Option[Long] = None,
    lte: Option[Long] = None,
) {

  /**
   * Render into Stripe's bracketed parameters, e.g. `created[gte]`.
   */
  def toParams(name: String): Map[String, String] =
    Map[String, String]()
      ++ gt.map(x => (s"${name}[gt]" -> x.toString))
      ++ gte.map(x => (s"${name}[gte]" -> x.toString))
      ++ lt.map(x => (s"${name}[lt]" -> x.toString))
      ++ lte.map(x => (s"${name}[lte]" -> x.toString))
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
    invoice_settings: CustomerInvoiceSettings,
)

case class CustomerInvoiceSettings(
    custom_fields: ujson.Value,
    default_payment_method: Option[String],
    footer: Option[String],
    rendering_options: ujson.Value,
)

object CustomerInvoiceSettings {
  def fromJsonDict(json: ujson.Obj): CustomerInvoiceSettings = {
    CustomerInvoiceSettings(
      custom_fields = json("custom_fields"),
      default_payment_method = json("default_payment_method").strOpt,
      footer = json("footer").strOpt,
      rendering_options = json("rendering_options"),
    )
  }
}

object Customer {

  def fromRawJson(json: ujson.Value): Either[String, Customer] = {
    Right(fromJsonDict(json.obj))
  }

  def fromJsonDict(json: ujson.Obj): Customer = {
    // Only used to identify the customer in error messages, so tolerate a
    // missing id rather than throwing while building the message.
    val idStr = json.value.get("id").flatMap(_.strOpt).getOrElse("<unknown id>")

    // Stripe leaves these null when they're unset. Plain `.str` turns that
    // into an opaque `ujson.Value$InvalidData: Expected ujson.Str (data:
    // null)`, so say which customer and which field instead.
    def requiredStr(field: String): String = {
      val value = json.value.getOrElse(field, ujson.Null)
      require(
        value != ujson.Null,
        s"Stripe customer ${idStr} has no '${field}' set. Either set one in " +
          s"the Stripe dashboard, or change Customer.${field} to " +
          "Option[String] so it can be missing."
      )
      value.str
    }

    Customer(
      rawJson = Some(json),
      id = json("id").str,
      name = requiredStr("name"),
      email = requiredStr("email"),
      invoice_prefix = json("invoice_prefix").str,
      invoice_settings =
        CustomerInvoiceSettings.fromJsonDict(json("invoice_settings").obj),
    )
  }

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

  /**
   * Transaction types described by the balance transaction itself, even
   * though Stripe does give them a source.
   */
  private val describedByTransaction =
    Set("adjustment", "payment_refund", "refund")

  /**
   * Transaction types that legitimately have no source at all.
   */
  private val sourcelessTypes = Set("stripe_fee", "adjustment")

  /**
   * Render every balance transaction making up a payout as CSV, for import
   * into accounting software. Stripe's fees are split onto their own rows so
   * that gross amounts and fees can be booked separately.
   *
   * Charges are labelled with their invoice number where there is one, which
   * costs two extra API calls per charge.
   */
  def toCsv(payoutId: String): String = {
    val rows = BalanceTransaction
      .listAll(payout = Some(payoutId))
      .flatMap { txn =>
        val date = Utils.utcToDate(txn.created)
        val fee = Utils.intCentsToString(-txn.fee.abs)

        val source: Option[String] =
          if (describedByTransaction(txn.typ)) None
          else {
            require(
              txn.source.isDefined || sourcelessTypes(txn.typ),
              s"balance transaction ${txn.id} of type '${txn.typ}' has no " +
                "source, so there's nothing to describe it with"
            )
            txn.source
          }

        source match {
          case None =>
            Seq(
              Utils.csvRow(
                txn.description,
                date,
                Utils.intCentsToString(txn.amount)
              ),
              Utils.csvRow(s"${txn.description} - Fee", date, fee),
            )

          case Some(payout) if payout.startsWith("po_") =>
            Seq(
              Utils.csvRow(
                s"STRIPE PAYOUT ${date}",
                date,
                Utils.intCentsToString(txn.amount)
              )
            )

          case Some(chargeId) =>
            val charge = Charge.retrieve(chargeId) match {
              case Right(x) => x
              case Left(err) =>
                throw new RuntimeException(
                  s"could not retrieve charge ${chargeId}: ${err}"
                )
            }
            val description = charge.invoice match {
              case Some(invoiceId) =>
                val invoice = Invoice.retrieve(invoiceId) match {
                  case Right(x) => x
                  case Left(err) =>
                    throw new RuntimeException(
                      s"could not retrieve invoice ${invoiceId}: ${err}"
                    )
                }
                s"Invoice ${invoice.number.getOrElse(invoiceId)}"
              case None => charge.description
            }
            Seq(
              Utils.csvRow(
                description,
                date,
                Utils.intCentsToString(charge.amount)
              ),
              Utils.csvRow(s"STRIPE FEES ${date}", date, fee),
            )
        }
      }

    (Utils.csvRow("Desc", "Date", "Amount") +: rows).mkString("\n") + "\n"
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
    confirm: Boolean,
    currency: String,
    customer: Option[String],
    payment_method: Option[String],
    description: String,
    statement_descriptor: String,
    statement_descriptor_suffix: String,
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
    confirm = json.value.get("confirm").map(_.bool).getOrElse(false),
    currency = json("currency").str,
    customer = json.value.get("customer").flatMap(v => Utils.nullableString(v)),
    payment_method =
      json.value.get("payment_method").flatMap(v => Utils.nullableString(v)),
    description = json("description").str,
    statement_descriptor = json.value
      .get("statement_descriptor")
      .flatMap(v => Utils.nullableString(v))
      .getOrElse(""),
    statement_descriptor_suffix = json.value
      .get("statement_descriptor_suffix")
      .flatMap(v => Utils.nullableString(v))
      .getOrElse(""),
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

  /**
   * Simple creation of USD invoices, auto-picking the default payment method.
   */
  def createDefault(
      amount: Long,
      customer: String,
      description: String,
      statementDescriptor: String,
      statementDescriptorSuffix: String,
  ): Either[String, PaymentIntent] = {
    val paymentMethod = stripe.Customer
      .retrieve(customer)
      .right
      .get
      .invoice_settings
      .default_payment_method
      .get
    create(
      amount = amount,
      customer = Some(customer),
      description = description,
      currency = "usd",
      confirm = true,
      paymentMethod = paymentMethod,
      offSession = Some(true),
      statementDescriptor = statementDescriptor,
      statementDescriptorSuffix = statementDescriptorSuffix,
    )
  }

  /**
   * Create a PaymentIntent
   * https://docs.stripe.com/api/payment_intents/create
   */
  def create(
      amount: Long,
      currency: String,
      paymentMethod: String,
      confirm: Boolean = false,
      customer: Option[String] = None,
      description: String = "",
      offSession: Option[Boolean] = None,
      statementDescriptor: String = "",
      statementDescriptorSuffix: String = ""
  ): Either[String, PaymentIntent] = {

    require(amount > 0, "Amount must be greater than 0")
    require(
      offSession.isDefined == confirm,
      "offSession should be present only when confirm=true"
    )

    // The actual limit is 22, including the 2-character separator
    require(
      statementDescriptor.length + statementDescriptorSuffix.length + 2 <= 22,
      "Total statement descriptor (prefix + suffix + separator) exceeds 22 characters"
    )

    val formData: Map[String, String] = Map(
      "amount" -> amount.toString,
      "currency" -> currency,
      "payment_method" -> paymentMethod,
      "confirm" -> confirm.toString,
      "description" -> description,
      "statement_descriptor" -> statementDescriptor,
      "statement_descriptor_suffix" -> statementDescriptorSuffix,
      // We don't support redirect-only methods for now
      "automatic_payment_methods[enabled]" -> "true",
      "automatic_payment_methods[allow_redirects]" -> "never",
    ) ++ customer.toSeq.map("customer" -> _)

    Requests
      .postGetJson(
        "/v1/payment_intents",
        formData
      )
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
   * A single page of balance transactions.
   * https://docs.stripe.com/api/balance_transactions/list
   *
   * @param limit Between 1 and 100. Stripe defaults to 10.
   * @param startingAfter Cursor: the id of the last item of the previous page
   */
  def list(
      payout: Option[String] = None,
      typ: Option[String] = None,
      limit: Option[Int] = None,
      startingAfter: Option[String] = None
  ): Seq[BalanceTransaction] = {
    require(
      limit.forall(x => x >= 1 && x <= MAX_LIST_LIMIT),
      s"limit must be between 1 and ${MAX_LIST_LIMIT}, got ${limit}"
    )

    val map = Map[String, String]()
      ++ payout.map(x => ("payout" -> x))
      ++ typ.map(x => ("type" -> x))
      ++ limit.map(x => ("limit" -> x.toString))
      ++ startingAfter.map(x => ("starting_after" -> x))

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

  /**
   * Every balance transaction matching the filters, paging past Stripe's
   * 100-per-request cap.
   */
  def listAll(
      payout: Option[String] = None,
      typ: Option[String] = None
  ): Seq[BalanceTransaction] = {
    @annotation.tailrec
    def go(
        after: Option[String],
        acc: Seq[BalanceTransaction]
    ): Seq[BalanceTransaction] = {
      val page = list(
        payout = payout,
        typ = typ,
        limit = Some(MAX_LIST_LIMIT),
        startingAfter = after
      )
      val soFar = acc ++ page
      // A short page means there's nothing after it
      if (page.size < MAX_LIST_LIMIT) soFar
      else go(Some(page.last.id), soFar)
    }

    go(None, Seq.empty)
  }
}

/**
 * The status of an invoice.
 * https://docs.stripe.com/billing/invoices/workflow#workflow-overview
 */
enum InvoiceStatus(val value: String) {
  case Draft extends InvoiceStatus("draft")
  case Open extends InvoiceStatus("open")
  case Paid extends InvoiceStatus("paid")
  case Uncollectible extends InvoiceStatus("uncollectible")
  case Void extends InvoiceStatus("void")
}

object InvoiceStatus {

  /**
   * Parse a status. Returns None for anything unrecognised, so that a status
   * newly added by Stripe doesn't break parsing of the whole invoice. The
   * original value stays available via `Invoice.rawJson`.
   */
  def fromString(s: String): Option[InvoiceStatus] =
    InvoiceStatus.values.find(_.value == s)
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
    // None if the invoice has no status, or one Stripe added after this was written
    status: Option[InvoiceStatus],
    // Final amount due at this time (in integer cents)
    amount_due: Long,
)

/**
 * An invoice flattened into one row, for exporting to a spreadsheet.
 */
case class InvoiceSummary(
    number: String,
    customerName: String,
    amount: String,
    customFields: String,
) {
  def toCsv: String = Utils.csvRow(number, customerName, amount, customFields)
}

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
    status =
      Utils.nullableString(json("status")).flatMap(InvoiceStatus.fromString),
    amount_due = json("amount_due").num.toLong,
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
   * List all invoices, or the invoices for a specific customer.
   * Sorted by creation date, most recently created first.
   * https://docs.stripe.com/api/invoices/list
   *
   * @param created Only return invoices created during the given date interval
   * @param status Only return invoices with this status
   * @param customer Only return invoices for the customer with this ID
   * @param limit Between 1 and 100. Stripe defaults to 10.
   * @param startingAfter Cursor: the id of the last item of the previous page
   */
  def list(
      created: Option[DateFilter] = None,
      status: Option[InvoiceStatus] = None,
      customer: Option[String] = None,
      limit: Option[Int] = None,
      startingAfter: Option[String] = None
  ): Seq[Invoice] = {
    require(
      limit.forall(x => x >= 1 && x <= MAX_LIST_LIMIT),
      s"limit must be between 1 and ${MAX_LIST_LIMIT}, got ${limit}"
    )

    val map = Map[String, String]()
      ++ created.map(_.toParams("created")).getOrElse(Map.empty[String, String])
      ++ status.map(x => ("status" -> x.value))
      ++ customer.map(x => ("customer" -> x))
      ++ limit.map(x => ("limit" -> x.toString))
      ++ startingAfter.map(x => ("starting_after" -> x))

    val rawResp = Requests
      .getJson(
        "/v1/invoices",
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

  private val isoDatePattern = """\d{4}-\d{2}-\d{2}""".r

  /**
   * Every invoice created between two `YYYY-MM-DD` dates, inclusive of both
   * ends, restricted to the given statuses. Defaults to the statuses that
   * represent real money owed or received, i.e. skipping drafts and voids.
   *
   * Sorted most recently created first, matching the Stripe API's own order.
   */
  def listCreatedBetween(
      start: String,
      end: String,
      statuses: Seq[InvoiceStatus] = Seq(InvoiceStatus.Open, InvoiceStatus.Paid)
  ): Seq[Invoice] = {
    def parseDate(name: String, value: String): java.time.LocalDate = {
      require(
        isoDatePattern.matches(value),
        s"${name} must be YYYY-MM-DD, got '${value}'"
      )
      java.time.LocalDate.parse(value)
    }

    val startDate = parseDate("start", start)
    val endDate = parseDate("end", end)
    require(
      !endDate.isBefore(startDate),
      s"start ${start} must not be after end ${end}"
    )

    def utcMidnight(d: java.time.LocalDate): Long =
      d.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond

    // [00:00 UTC on `start`, 00:00 UTC the day after `end`), which includes
    // everything on both endpoint days
    val created = DateFilter(
      gte = Some(utcMidnight(startDate)),
      lt = Some(utcMidnight(endDate.plusDays(1))),
    )

    // Stripe's `status` is a single enum rather than a list, so this needs
    // one pass per status
    statuses
      .flatMap { status =>
        @annotation.tailrec
        def go(after: Option[String], acc: Seq[Invoice]): Seq[Invoice] = {
          val page = list(
            created = Some(created),
            status = Some(status),
            limit = Some(MAX_LIST_LIMIT),
            startingAfter = after
          )
          val soFar = acc ++ page
          // A short page means there's nothing after it
          if (page.size < MAX_LIST_LIMIT) soFar
          else go(Some(page.last.id), soFar)
        }

        go(None, Seq.empty)
      }
      .sortBy(-_.created)
  }

  /**
   * Invoice numbers, e.g. "FOOBAR-0001", for [[listCreatedBetween]].
   * Invoices without a number are skipped.
   */
  def numbersCreatedBetween(
      start: String,
      end: String,
      statuses: Seq[InvoiceStatus] = Seq(InvoiceStatus.Open, InvoiceStatus.Paid)
  ): Seq[String] =
    listCreatedBetween(start, end, statuses).flatMap(_.number)

  /**
   * Look up an invoice by number and flatten it into a single exportable row.
   */
  def summarize(number: String): Either[String, InvoiceSummary] =
    for {
      invoice <- searchByNumber(number)
      customer <- Customer.retrieve(invoice.customer)
    } yield InvoiceSummary(
      number = number,
      customerName = customer.name,
      amount = Utils.intCentsToString(invoice.total),
      customFields = invoice.rawJson
        .flatMap(_.obj.get("custom_fields"))
        .map(_.toString)
        .getOrElse(""),
    )

  private val descriptionDateFormat = java.time.format.DateTimeFormatter
    .ofPattern("MMM d, yyyy", java.util.Locale.US)

  /**
   * Helper function to render an invoice's line items and totals the way the Stripe dashboard
   * shows them.
   *
   * Reads `lines` straight off the raw JSON, since the Invoice case class
   * doesn't model line items. Purely formatting: fetch the invoice with
   * `retrieve` or `searchByNumber` first.
   */
  def describe(invoice: Invoice): String = {
    val json = invoice.rawJson match {
      case Some(x) => x.obj
      case None =>
        throw new IllegalArgumentException(
          s"invoice ${invoice.id} has no rawJson to describe"
        )
    }

    def fmtDate(ts: Long): String = java.time.LocalDateTime
      .ofEpochSecond(ts, 0, java.time.ZoneOffset.UTC)
      .format(descriptionDateFormat)

    def money(cents: Long): String = "$" + Utils.intCentsToString(cents)

    // Only the first page of line items is embedded in the invoice object
    require(
      !json("lines")("has_more").bool,
      s"invoice ${invoice.id} has more than one page of line items; fetch " +
        s"/v1/invoices/${invoice.id}/lines instead"
    )

    val lines = json("lines")("data").arr.toSeq.map { line =>
      val description =
        line.obj.get("description").flatMap(_.strOpt).getOrElse("")
      val quantity = line.obj.get("quantity").flatMap(_.numOpt).map(_.toLong)
      val amount = line("amount").num.toLong
      val period = line("period")

      Seq(
        description,
        s"${fmtDate(period("start").num.toLong)} - " +
          s"${fmtDate(period("end").num.toLong)}",
        quantity.map(_.toString).getOrElse("-"),
        // Stripe doesn't always send a unit amount, so derive it
        quantity.filter(_ != 0).map(q => money(amount / q)).getOrElse("-"),
        money(amount),
      ).mkString("\n")
    }

    def optionalCents(field: String): Option[Long] =
      json.get(field).flatMap(_.numOpt).map(_.toLong)

    // `tax` was replaced by `total_taxes` in newer API versions
    val tax = json
      .get("total_taxes")
      .map(_.arr.map(_("amount").num.toLong).sum)
      .orElse(optionalCents("tax"))

    val totals = Seq(
      "Subtotal" -> money(json("subtotal").num.toLong),
      "Total excluding tax" ->
        optionalCents("total_excluding_tax").map(money).getOrElse("-"),
      "Tax" -> tax.filter(_ != 0).map(money).getOrElse("-"),
      "Total" -> money(json("total").num.toLong),
    ).map((label, value) => s"${label}\n\t${value}")

    (lines ++ totals).mkString("\n\t\n")
  }
}
