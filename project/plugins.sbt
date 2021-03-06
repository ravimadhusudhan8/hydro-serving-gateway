logLevel := Level.Info

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

libraryDependencies ++= Seq(
  "com.spotify" % "docker-client" % "8.8.0",
  "com.thesamet.scalapb" %% "compilerplugin" % "0.7.4"
)