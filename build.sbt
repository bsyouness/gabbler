organization := "name.heikoseeberger"

name := "gabbler"

version := "2.0.0"

scalaVersion := Version.scala

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Dependencies.gabbler

unmanagedSourceDirectories in Compile := List((scalaSource in Compile).value)

unmanagedSourceDirectories in Test := List((scalaSource in Test).value)

scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

initialCommands := "import name.heikoseeberger.gabbler._"
