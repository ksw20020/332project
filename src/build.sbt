ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.7"

lazy val common = project.in(file("common"))

lazy val master = project
  .in(file("master"))
  .dependsOn(common)
  .settings(
    mainClass := Some("master.MasterMain")
  )

lazy val worker = project
  .in(file("worker"))
  .dependsOn(common)
  .settings(
    mainClass := Some("worker.WorkerMain")
  )
