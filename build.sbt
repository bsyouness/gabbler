lazy val gabbler = project in file(".")

name := "gabbler"

Common.settings

libraryDependencies ++= Dependencies.gabbler

initialCommands := """|import de.heikoseeberger.gabbler._""".stripMargin
