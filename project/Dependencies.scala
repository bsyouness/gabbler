import sbt._

object Version {
  val akka      = "2.3.4"
  val logback   = "1.1.2"
  val scala     = "2.11.1"
  val scalaTest = "2.2.0"
  val spray     = "1.3.1"
  val sprayJson = "1.2.6"
}

object Library {
  val akkaActor      = "com.typesafe.akka" %% "akka-actor"      % Version.akka
  val akkaSlf4j      = "com.typesafe.akka" %% "akka-slf4j"      % Version.akka
  val akkaTestkit    = "com.typesafe.akka" %% "akka-testkit"    % Version.akka
  val logbackClassic = "ch.qos.logback"    %  "logback-classic" % Version.logback
  val scalaTest      = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val sprayCan       = "io.spray"          %% "spray-can"       % Version.spray
  val sprayJson      = "io.spray"          %% "spray-json"      % Version.sprayJson
  val sprayRouting   = "io.spray"          %% "spray-routing"   % Version.spray
}

object Dependencies {

  import Library._

  val gabbler = List(
    akkaActor,
    akkaSlf4j,
    logbackClassic,
    sprayCan,
    sprayJson,
    sprayRouting,
    akkaTestkit % "test",
    scalaTest % "test"
  )
}
