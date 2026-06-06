
import scala.sys.process._ 

// Global / scalaJSStage := FullOptStage

val includeAddon: Boolean  = sys.env.get("APP_INCLUDE_ADDON").contains("true")
val appOrganization        = sys.env.getOrElse("APP_ORGANIZATION","org.jorolicht")
val appVersion             = sys.env.getOrElse("APP_VERSION", "001")
val appDate                = sys.env.getOrElse("APP_DATE", "1970-01-01")
val appMaintainer          = sys.env.getOrElse("APP_MAINTAINER", "Joe Doe <joe.doe@example.com>")

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := appOrganization
ThisBuild / version      := appVersion
server / maintainer      := appMaintainer

//ThisBuild / scalacOptions ++=Seq("-explain")

lazy val root = (project in file("."))
  .aggregate(server, client, shared.jvm, shared.js)

val genMsgFiles = taskKey[Unit]("Generate Message Files")  
val convertMessagesToJson = taskKey[Seq[File]]("Converts message files to JSON")

lazy val syncClientWpFiles = taskKey[Unit]("Copies main.js and main.js.map to wordpress")

lazy val server = project
  .settings(
    Universal / stage := {
      val stageDir = (Universal / stage).value   // erzeugt target/universal/stage

      val log = streams.value.log
      val targetDir = baseDirectory.value / "docker" / "playdemo" / "stage"
      // Zielverzeichnis sauber neu anlegen
      IO.delete(targetDir)
      IO.createDirectory(targetDir)

      // komplettes stage-Verzeichnis kopieren
      IO.copyDirectory(stageDir, targetDir, overwrite = true, preserveLastModified = true)

      log.info(s"Copied staged distribution to ${targetDir.getAbsolutePath}")

      // Start docker build
      val dockerDir = baseDirectory.value / "docker"
      log.info(s"Starting docker build in ${dockerDir.getAbsolutePath}...")
      val exitCode = Process("./build.sh", dockerDir).!
      if (exitCode != 0) sys.error(s"Docker build failed with exit code $exitCode")

      stageDir
    },
    commands ++= Seq(hello, buildMsg),
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
        val targetFileSrv  = baseDirectory.value / ".." / "server" / "public" / "data" / ("msgs_" + lang + ".json")
        val targetFileWp   = baseDirectory.value / ".." / "wp-plugin" / "data" / ("msgs_" + lang + ".json")

        IO.copyFile(generatedFile, targetFileWp)
        IO.copyFile(generatedFile, targetFileSrv)
        log.info(s"Generated ${targetFileWp.getAbsolutePath} und ${targetFileSrv.getAbsolutePath}")
        targetFileSrv
      }
    },
    Compile / resourceGenerators += convertMessagesToJson.taskValue,
    scalaJSProjects := Seq(client),
    Assets / pipelineStages  := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline and syncClientWpFiles when using compile or continuous compilation
    Compile / compile := ((Compile / compile) dependsOn (scalaJSPipeline, syncClientWpFiles)).value,
    
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
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.4.2",
    syncClientWpFiles := {
      // make sure that main.js and main.js.map are created before copying
      (client / Compile / fastLinkJS).value
      (client / Compile / fullLinkJS).value

      val log = streams.value.log
      val clientTargetDir = (client / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      val clientCssSource = file("server/public/css") 

      val wpJsDestination = file("wp-plugin/js")
      val wpCssDestination = file("wp-plugin/css")
      // 2. Zielverzeichnisse definieren
      val wpJsDestination2 = file("server/docker/wp_data/wp-content/plugins/playdemo/js")
      val wpCssDestination2 = file("server/docker/wp_data/wp-content/plugins/playdemo/css")

      val wpPluginDir = baseDirectory.value / ".." / "wp-plugin"
      val dockerWpPluginDir = baseDirectory.value / "docker" / "wp_data" / "wp-content" / "plugins" / "playdemo"

      IO.copyDirectory(clientTargetDir, wpJsDestination)
      IO.copyDirectory(clientCssSource, wpCssDestination)
      IO.copyDirectory(clientTargetDir, wpJsDestination2)
      IO.copyDirectory(clientCssSource, wpCssDestination2)

      // Copy playdemo.php and includes to docker
      IO.copyFile(wpPluginDir / "playdemo.php", dockerWpPluginDir / "playdemo.php")
      IO.copyDirectory(wpPluginDir / "includes", dockerWpPluginDir / "includes")

      log.info(s"Copied files to wordpress (including php and includes)")
    },
    Universal / dist := {
      // Build ZIP explicitly
      val zipFile = (Universal / packageBin).value

      val log = streams.value.log
      val targetDir = baseDirectory.value / "docker" / "playdemo" 
      IO.createDirectory(targetDir)

      val targetFile = targetDir / zipFile.getName
      IO.copyFile(zipFile, targetFile)

      log.info(s"Copied ${zipFile.getName} to ${targetDir}")
      zipFile
    }

  )
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(shared.jvm)

lazy val client = project
  .settings(
    (Compile / unmanagedSources / excludeFilter) := {         
      val baseFilter = HiddenFileFilter || "*~" || "*.tmp"
    
      if (includeAddon) {
        // Nichts zusätzlich ausschließen
        baseFilter
      } else {
        // Bestimmtes src-Verzeichnis ausschließen, z.B. src/main/extra
        baseFilter || new SimpleFileFilter(file =>
          file.getAbsolutePath.contains("src/main/scala/addon")
        )
      }
    },

    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "AddonConfig.scala"
      val code =
        if (includeAddon)
          """package addon
            |object Console { 
            |  val enabled = true
            |  def start() = DebugConsole.start()
            |}
            |""".stripMargin          

        else
          """package addon
            |object Console { 
            |  val enabled = false
            |  def start() = println("debug console not available")
            |}
            |""".stripMargin          

      IO.write(out, code)
      Seq(out)
    },

    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }, 
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "3.3.1",
    libraryDependencies += "org.rogach"  %%% "scallop" % "5.1.0",
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.12.0",
    libraryDependencies += "com.lihaoyi" %%% "sourcecode" % "0.4.2"
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
       "org.typelevel" %%% "shapeless3-deriving" % "3.4.0",
       "com.lihaoyi" %%% "sourcecode" % "0.4.2",
       "com.lihaoyi" %% "sourcecode" % "0.4.2",
       "org.typelevel" %%% "cats-core" % "2.13.0"
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


def buildMsg = Command.command("buildMsg") { state =>
  println("--- Starte Nachrichten-Generierung ---")
  
  // Führt die Tasks nacheinander aus
  val state1 = Command.process("genMsgFiles", state)
  val state2 = Command.process("convertMessagesToJson", state1)
  
  println("--- Nachrichten-Generierung abgeschlossen ---")
  state2
}
