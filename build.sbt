import scala.sys.process._

// Global / scalaJSStage := FullOptStage

ThisBuild / organization := "org.jorolicht"
ThisBuild / scalaVersion := "3.3.3"

val includeAddonSrc: Boolean = sys.env.get("INCLUDE_ADDON_SRC").contains("true")
val appVersion = sys.env.getOrElse("APP_VERSION", "001")
val appDate    = sys.env.getOrElse("APP_DATE", "1970-01-01")
ThisBuild / version  := appVersion
server / maintainer  := sys.env.getOrElse("APP_MAINTAINER", "Joe Doe <joe.doe@example.com>")

//ThisBuild / scalacOptions ++=Seq("-explain")

lazy val root = (project in file("."))
  .aggregate(server, client, shared.jvm, shared.js)

val genMsgFiles = taskKey[Unit]("Generate Message Files")  
lazy val server = project
  .settings(
    commands ++= Seq(hello),
    genMsgFiles := {
      val msgFileDe = baseDirectory.value  / "conf" / "messages.de"
      val msgFileEn = baseDirectory.value  / "conf" / "messages.en"
      val infoDe = baseDirectory.value  / "conf" / "messages" / "de" / "00_info.de"
      val infoEn = baseDirectory.value  / "conf" / "messages" / "en" / "00_info.en"
      val ymd = appDate.split("-")
      val yearMonth = s"${ymd(0)}-${ymd(1)}"
      IO.write(infoDe, s"""
                        |app.version = ${appVersion}DE${yearMonth}
                        |app.date    = ${appDate}
                        |app.lang    = DE
                        |\n""".stripMargin)
      IO.write(infoEn, s"""
                        |app.version = ${appVersion}EN${yearMonth}
                        |app.date    = ${appDate}
                        |app.lang    = EN
                        |\n""".stripMargin)
      val filesDe = (baseDirectory.value / "conf" / "messages" / "de" ** "*.de").get.sortBy(_.getName)
      val filesEn = (baseDirectory.value / "conf" / "messages" / "en" ** "*.en").get.sortBy(_.getName)
      IO.write(msgFileDe, filesDe.map(IO.read(_)).reduceLeft(_ ++ _))
      IO.write(msgFileEn, filesEn.map(IO.read(_)).reduceLeft(_ ++ _))
      println(s"Message files generated")
    },
    scalaJSProjects := Seq(client),
    Assets / pipelineStages  := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline when using compile or continuous compilation
    Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
    Compile / compile := ((Compile / compile) dependsOn genMsgFiles).value,
    
    libraryDependencies += guice,
    libraryDependencies += jdbc,
    libraryDependencies += ws,
    libraryDependencies += evolutions,
    libraryDependencies += "com.mysql" % "mysql-connector-j" % "8.3.0",
    libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.7.0",
    libraryDependencies += "com.vmunier" %% "scalajs-scripts" % "1.3.0",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "3.3.1",
    libraryDependencies += "com.google.api-client" % "google-api-client" % "2.4.0",
    libraryDependencies += "org.playframework" %% "play-mailer" % "10.0.0",
    libraryDependencies += "org.playframework" %% "play-mailer-guice" % "10.0.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.12.0",
    libraryDependencies += "org.apache.pekko" %% "pekko-stream-typed" % "1.0.2"
  )
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(shared.jvm)


lazy val client = project
  .settings(
    (Compile / unmanagedSources / excludeFilter) := {         
      val baseFilter = HiddenFileFilter || "*~" || "*.tmp"
    
      if (includeAddonSrc) {
        // Nichts zusätzlich ausschließen
        baseFilter
      } else {
        // Bestimmtes src-Verzeichnis ausschließen, z.B. src/main/extra
        baseFilter || new SimpleFileFilter(file =>
          file.getAbsolutePath.contains("src/main/scala/addon")
        )
      }
    },

    scalaJSUseMainModuleInitializer := false,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "3.3.1",
    libraryDependencies += "org.rogach"  %%% "scallop" % "5.1.0",
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.12.0",
    libraryDependencies += "com.lihaoyi" %%% "utest" % "0.9.0" % "test",
    // Playwright JVM binding for browser testing
    libraryDependencies += "com.microsoft.playwright" %%% "playwright" % "1.49.0" % "test",    
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb, SbtTwirl)
  .dependsOn(shared.js)
  .settings(
    (Compile / unmanagedSources / excludeFilter) := {         
      val baseFilter = HiddenFileFilter || "*~" || "*.tmp"
    
      if (includeAddonSrc) {
        // Nichts zusätzlich ausschließen
        baseFilter
      } else {
        // Bestimmtes src-Verzeichnis ausschließen, z.B. src/main/extra
        baseFilter || new SimpleFileFilter(file =>
          file.getAbsolutePath.contains("src/main/scala/addon")
        )
      }
    }
  )


lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared"))
  .settings(
     name := "shared",
     libraryDependencies ++= Seq(
       "com.lihaoyi" %%% "upickle" % "3.3.1",
       "org.typelevel" %%% "shapeless3-deriving" % "3.4.0"
     )
   )
  .jsConfigure(_.enablePlugins(ScalaJSWeb))


// Add the following line to build.sbt if you wish to load the server project at sbt startup
// otherwise you have to switch to sbt> project server 

Global / onLoad := (Global / onLoad).value.andThen(state => "project server" :: state)

// clean will only delete the server's generated files (in the server/target directory). 
// Call root/clean to delete the generated files for all the projects.
// sbt 'set Global / scalaJSStage := FullOptStage' Universal/packageBin

//detailed error description
//set ThisBuild/scalacOptions ++=Seq("-explain")

//To run the task: sbt root/copyFileTask
lazy val copyFileTask = taskKey[Int]("Copies javascript files to wordpress directory.")
copyFileTask := {
  val log = streams.value.log
  //val source = sourceFilePath.value
  val source = file("client/target/scala-3.3.3/client-fastopt") 
  val destination = file("server/wpdata/wp-content/plugins/playdemo/js")

  val sourceCss = file("server/public/css") 
  val destinationCss = file("server/wpdata/wp-content/plugins/playdemo/css")  

  try {
    IO.copyDirectory(source, destination)
    IO.copyDirectory(sourceCss, destinationCss)
    log.info(s"Copied javascript files from ${source.getAbsolutePath} to ${destination.getAbsolutePath}")
    log.info(s"Copied css files from ${sourceCss.getAbsolutePath} to ${destinationCss.getAbsolutePath}")
  } catch {
    case e: Exception =>
      log.error(s"Error copying files: ${e.getMessage}")
  }
  42
}


// A simple, no-argument command that prints "Hello",
// leaving the current state unchanged.
def hello = Command.command("hello") { state =>
  // val extracted = Project.extract(state)
  // import extracted._

  println(s"Hello")
  state
}

