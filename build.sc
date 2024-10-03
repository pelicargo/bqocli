import mill._, scalalib._, scalajslib._, scalafmt._

trait CommonVersions extends ScalaModule {
  override def scalaVersion = "3.5.1"
  override def ammoniteVersion = "2.5.2"
  override def scalacOptions = Seq(
    "-explain",
    // Useful debug aid but often too aggressive
    // "-Xfatal-warnings",
  )
}

object BqoCLI extends ScalaModule with CommonVersions with ScalafmtModule {
  def mainClass = Some("bqocli.Main")

  def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.8.+",
    ivy"com.lihaoyi::pprint:0.7.+",
    ivy"com.lihaoyi::upickle:3.1.+",
    // wtf, not sure why this needs to have the Scala version manually
    // specified
    ivy"com.lihaoyi:ammonite_3.3.1:3.0.0-M0-59-cdeaa580",
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
