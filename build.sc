import mill._, scalalib._, scalajslib._, scalafmt._

trait CommonVersions extends ScalaModule {
  override def scalaVersion = "3.5.1"
  override def ammoniteVersion = "3.0.0"
  override def scalacOptions = Seq(
    "-explain",
    // Useful debug aid but often too aggressive
    // "-Xfatal-warnings",
  )
}

object BqoCLI extends ScalaModule with CommonVersions with ScalafmtModule {
  def mainClass = Some("bqocli.Main")

  def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.10.+",
    ivy"com.lihaoyi::pprint:0.9.+",
    ivy"com.lihaoyi::upickle:4.0.+",
    // wtf, not sure why this needs to have the version manually
    // specified
    ivy"com.lihaoyi:ammonite_3.5.1:3.0.+",
    ivy"com.softwaremill.sttp.client4::core:4.0.0-M18",
  )

  object test extends ScalaTests with TestModule.ScalaTest with ScalafmtModule {
    def ammoniteVersion = BqoCLI.ammoniteVersion
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.9"
    )
    def testOne(args: String*) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }
  }
}
