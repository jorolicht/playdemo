import scala.sys.process._ 

// Global / scalaJSStage := FullOptStage

ThisBuild / organization := "org.jorolicht"
ThisBuild / scalaVersion := "3.3.7"

val includeAddonSrc: Boolean = sys.env.get("INCLUDE_ADDON_SRC").contains("true")
val appVersion = sys.env.getOrElse("APP_VERSION", "001")
val appDate    = sys.env.getOrElse("APP_DATE", "1970-01-01")
ThisBuild / version  := appVersion
server / maintainer  := sys.env.getOrElse("APP_MAINTAINER", "Joe Doe <joe.doe@example.com>")

//ThisBuild / scalacOptions ++=Seq("-explain")

lazy val root = (project in file("."))
  .aggregate(server, client, shared.jvm, shared.js)

val genMsgFiles = taskKey[Unit]("Generate Message Files")  
val convertMessagesToJson = taskKey[Seq[File]]("Converts message files to JSON")

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
    convertMessagesToJson := {
      genMsgFiles.value // run genMsgFiles task first
      val log = streams.value.log
      val confDir = baseDirectory.value / "conf"
      val targetDir = (Compile / resourceManaged).value / "messages"
      IO.createDirectory(targetDir)
      
      val msgFiles = Seq(confDir / "messages.de", confDir / "messages.en")
      
      msgFiles.map { msgFile =>
        log.info(s"Converting ${msgFile.getAbsolutePath} to JSON...")
        if (s"msgConverter ${msgFile.getAbsolutePath}".! != 0) {
          sys.error(s"msgConverter failed for $msgFile")
        }
        
        val generatedFile = new File(msgFile.getAbsolutePath + "_json")
        val lang = msgFile.name.split('.').last
        val targetFileVite = baseDirectory.value / ".." / "client" / "vite" / "assets" / "data" / ("msgs_" + lang + ".json")
        val targetFileSrv = baseDirectory.value / ".." / "server" / "public" / "data" / ("msgs_" + lang + ".json")
        
        IO.copyFile(generatedFile, targetFileSrv)
        IO.move(generatedFile, targetFileVite)
        log.info(s"Generated ${targetFileVite.getAbsolutePath} und ${targetFileSrv.getAbsolutePath}")
        targetFileSrv
      }
    },
    Compile / resourceGenerators += convertMessagesToJson.taskValue,
    scalaJSProjects := Seq(client),
    Assets / pipelineStages  := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline and copyClientViteFiles when using compile or continuous compilation
    Compile / compile := ((Compile / compile) dependsOn (scalaJSPipeline, copyClientViteFiles)).value,
    
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
    libraryDependencies += "org.apache.pekko" %% "pekko-stream-typed" % "1.0.2",
    libraryDependencies += "com.yubico" % "webauthn-server-core" % "2.6.0",
    copyClientViteFiles := {
      // Copy main.js and main.js.map to client/vite
      // Also copy to wordpress plugin directory
      val log = streams.value.log
      val clientTargetDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      val clientCssSource = file("server/public/css") 

      val wpJsDestination = file("server/wpdata/wp-content/plugins/playdemo/js")
      val wpCssDestination = file("server/wpdata/wp-content/plugins/playdemo/css")
      val viteDestinationDir = baseDirectory.value / ".." / "client" / "vite"

      IO.copyDirectory(clientTargetDir, viteDestinationDir)
      IO.copyDirectory(clientTargetDir, wpJsDestination)
      IO.copyDirectory(clientCssSource, wpCssDestination)

      log.info(s"Copied files to vite and wordpress")
    }
  )
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(shared.jvm)

lazy val copyClientViteFiles = taskKey[Unit]("Copies main.js and main.js.map to client/vite")

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
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }, 








    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "3.3.1",
    libraryDependencies += "org.rogach"  %%% "scallop" % "5.1.0",
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.12.0"
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(shared.js)
  .enablePlugins(SbtTwirl)


lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared"))
  .settings(
     name := "shared",
     libraryDependencies ++= Seq(
       "com.lihaoyi" %%% "upickle" % "3.3.1",
       "com.lihaoyi" %% "upickle" % "3.3.1",
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


// A simple, no-argument command that prints "Hello",
// leaving the current state unchanged.
def hello = Command.command("hello") { state =>
  // val extracted = Project.extract(state)
  // import extracted._

  println(s"Hello")
  state
}

