import mill._, scalalib._, scalafmt._, publish._
import mill.define.{Segment, Segments}
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`

val `2.12` = "2.12.10"
val `2.13` = "2.13.1"

object Dep {
  val slf4jSimple = ivy"org.slf4j:slf4j-simple:1.7.28"
  val paho = ivy"org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.2"
  val akkaStream = ivy"com.typesafe.akka::akka-stream:2.5.25"
  val akkaHttp = ivy"com.typesafe.akka::akka-http:10.1.10"
  val cats = ivy"org.typelevel::cats-core:2.0.0"
  val scalaLogging = ivy"com.typesafe.scala-logging::scala-logging:3.9.2"
  val collectionCompat = ivy"org.scala-lang.modules::scala-collection-compat:2.1.2"
  val playJson = ivy"com.typesafe.play::play-json:2.7.4"
}

val developers = Seq(Developer("kag0", "Nathan Fischer", "https://github.com/kag0", Some("lightform"), Some("https://github.com/blackdoor")))

trait SampleModule extends ScalaModule {
  def scalaVersion = "2.13.1"

  def scalacOptions = Seq(
    "-Xfatal-warnings", "-feature", "-unchecked", "-deprecation",
    "-Ywarn-macros:after", "-Ywarn-unused", "-language:higherKinds"
  )
}

trait BaseModule extends CrossScalaModule with ScalafmtModule {

  def artifactName = T(Segments(millModuleSegments.value.filterNot(_.isInstanceOf[Segment.Cross]):_*).parts.mkString("-"))

  def publishVersion = T.input(T.ctx().env("PUBLISH_VERSION"))

  def scalacOptions = Seq(
    "-Xfatal-warnings", "-feature", "-unchecked", "-deprecation",
    "-Ywarn-macros:after", "-Ywarn-unused", "-language:higherKinds"
  )

  trait TestBase extends Tests {
    def ivyDeps = super.ivyDeps() ++ Agg(ivy"org.scalatest::scalatest:3.0.8", Dep.slf4jSimple)
    def testFrameworks = List("org.scalatest.tools.Framework")
  }
}

class CommonCross[T](implicit ci : mill.define.Cross.Factory[T], ctx : mill.define.Ctx) extends Cross[T](`2.12`, `2.13`)

object mercury extends CommonCross[MercuryModule]
class MercuryModule(val crossScalaVersion: String) extends BaseModule with PublishModule {

  def ivyDeps = Agg(
    Dep.cats,
    Dep.scalaLogging,
    Dep.collectionCompat
  )

  def pomSettings = PomSettings(
    "Mercury - modular JSON-RPC for Scala",
    "com.lightform",
    "https://lightform-oss.github.io/mercury",
    Seq(License.MIT),
    VersionControl.github("lightform-oss", "mercury"),
    developers
  )

  object test extends TestBase
}

object `play-json` extends Cross[PlayJsonModule](`2.12`, `2.13`)
class PlayJsonModule(val crossScalaVersion: String) extends BaseModule with PublishModule {

  def artifactName = s"${mercury(crossScalaVersion).artifactName()}-${super.artifactName()}"
  def pomSettings = mercury(crossScalaVersion)
    .pomSettings()
    .copy(description = "Play JSON support for Lightform mercury")

  def moduleDeps = Seq(mercury(crossScalaVersion))
  def ivyDeps = Agg(Dep.playJson)

  object test extends TestBase {
    def moduleDeps = super.moduleDeps :+ mercury(crossScalaVersion).test
  }
}

object paho extends Cross[PahoModule](`2.12`, `2.13`)
class PahoModule(val crossScalaVersion: String) extends BaseModule with PublishModule {

  def artifactName = s"${mercury(crossScalaVersion).artifactName()}-${super.artifactName()}"
  def pomSettings = mercury(crossScalaVersion)
    .pomSettings()
    .copy(description = "MQTT transport for Lightform mercury with Apache Paho")


  def moduleDeps = Seq(mercury(crossScalaVersion))
  def ivyDeps = Agg(Dep.paho)

  object sample extends SampleModule {

    def moduleDeps = Seq(sampleRef)
    def ivyDeps = Agg(Dep.slf4jSimple)
  }
}

object `akka-stream` extends Cross[AkkaStreamModule](`2.12`, `2.13`)
class AkkaStreamModule(val crossScalaVersion: String) extends BaseModule with PublishModule {

  def artifactName = s"${mercury(crossScalaVersion).artifactName()}-${super.artifactName()}"
  def pomSettings = mercury(crossScalaVersion)
    .pomSettings()
    .copy(description = "Akka Stream transport for Lightform mercury")


  def moduleDeps = Seq(mercury(crossScalaVersion))
  def compileIvyDeps = Agg(Dep.akkaStream)

  object sample extends SampleModule {
    def moduleDeps = Seq(`akka-stream`(`2.13`), sampleRef)
    def ivyDeps = Agg(
      Dep.akkaStream,
      Dep.akkaHttp
    )
  }
}

object `akka-http` extends Cross[AkkaHttpModule](`2.12`, `2.13`)
class AkkaHttpModule(val crossScalaVersion: String) extends BaseModule with PublishModule {

  def artifactName = s"${mercury(crossScalaVersion).artifactName()}-${super.artifactName()}"
  def pomSettings = mercury(crossScalaVersion)
    .pomSettings()
    .copy(description = "HTTP transport for Lightform mercury with Akka HTTP")

  def moduleDeps = Seq(mercury(crossScalaVersion))
  def compileIvyDeps = Agg(Dep.akkaStream)
  def ivyDeps = Agg(Dep.akkaHttp)

  object sample extends SampleModule {
    def moduleDeps = Seq(`akka-http`(`2.13`), sampleRef)
    def ivyDeps = Agg(Dep.akkaStream)
  }
}

def sampleRef = sample
object sample extends SampleModule {

  def moduleDeps = Seq(paho(`2.13`), `play-json`(`2.13`))
  def ivyDeps = Agg(Dep.slf4jSimple)
}
