package com.ssachtleben.sbt.define

//import com.typesafe.sbt.jse.SbtJsTask
//import sbt._
//import com.typesafe.sbt.web.SbtWeb
//import spray.json.{JsBoolean, JsObject}
//import sbt.Keys._
//import com.typesafe.sbt.coffeescript.Import.CoffeeScriptKeys.coffeescript
//
//object Import {
//
//  object DefineKeys {
//    val define = TaskKey[Seq[File]]("assets-define", "Wraps javascript files into a define method block")
//    val testStuff = TaskKey[java.io.File]("assets-define-teststuff", "Wraps javascript files into a define method block")
//  }
//
//}
//
//object DefinePlugin extends AutoPlugin {
//
//  override def requires = SbtJsTask
//
//  override def trigger = AllRequirements
//
//  val autoImport = Import
//
//  import SbtWeb.autoImport._
//  import WebKeys._
//  import SbtJsTask.autoImport.JsTaskKeys._
//  import autoImport.DefineKeys._
//
//  val defineUnscopedSettings = Seq(
//    includeFilter := "*.coffee",
//    //sources in define := (sourceDirectories.value ** ((includeFilter in define).value)).get,
//    sources in define := ((resourceManaged in coffeescript).value ** ((includeFilter in define).value)).get,
//    jsOptions := JsObject(
//        "test" -> JsBoolean(true)
//    ).toString,
//    testStuff in define := {
//      streams.value.log.info("source: " + (resourceManaged in coffeescript).value)
//      webTarget.value / "public" / "main" / "javascripts"
//    }
//  )
//
//  override def projectSettings = inTask(define)(
//    SbtJsTask.jsTaskSpecificUnscopedSettings ++
//      inConfig(Assets)(defineUnscopedSettings) ++
//      inConfig(TestAssets)(defineUnscopedSettings) ++
//      Seq(
//        moduleName := "define",
//        shellFile := getClass.getClassLoader.getResource("define.js"),
//
//        taskMessage in Assets := "Define compiling",
//        taskMessage in TestAssets := "Define test compiling"
//      )
//  ) ++ SbtJsTask.addJsSourceFileTasks(define) ++ Seq(
//    //define in Assets := (define in Assets).dependsOn(coffeescript in Assets).value,
//    //define in Assets := (define in Assets).dependsOn(testStuff in define in Assets).value,
//    define in TestAssets := (define in TestAssets).dependsOn(coffeescript in Assets).value,
//    define in TestAssets := (define in TestAssets).dependsOn(testStuff in define in TestAssets).value
//  )
//
//}

import com.typesafe.sbt.coffeescript.Import.CoffeeScriptKeys.coffeescript
import com.ssachtleben.sbt.handlebars.Import.HandlebarsKeys.handlebars
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.PathMapping
import collection.mutable
import mutable.ListBuffer
import java.io.File
import java.util.regex.Pattern
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils

import sbt._
import Keys._

object Import {

  val define = TaskKey[Pipeline.Stage]("coffee-define", "wraps coffee scripts with a define block")

  object Define {
    val files = SettingKey[sbt.PathFinder]("coffee-define-files", "List of ConcatGroup files")
  }

}

object DefinePlugin extends sbt.AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import Define._

  override def projectSettings: Seq[Setting[_]] = Seq(
    resourceManaged in define in Assets := webTarget.value / define.key.label / "main",
    resourceManaged in define in TestAssets := webTarget.value / define.key.label / "test",
    //unmanagedSourceDirectories in define in Assets += (resourceManaged in coffeescript in Assets).value,
    //unmanagedSourceDirectories in define in TestAssets += (resourceManaged in coffeescript in TestAssets).value,
    //unmanagedSourceDirectories in define in Assets += (resourceManaged in handlebars in Assets).value,
    //unmanagedSourceDirectories in define in TestAssets += (resourceManaged in handlebars in TestAssets).value,
    define in Assets := (define in Assets).dependsOn(coffeescript in Assets).dependsOn(handlebars in Assets).value,
    define in TestAssets := (define in TestAssets).dependsOn(coffeescript in TestAssets).dependsOn(handlebars in TestAssets).value,
    includeFilter in define := "*.js",
    excludeFilter in define := HiddenFileFilter,
    files := (resourceManaged in coffeescript in Assets).value / "javascripts" / "app" ** "*.js" +++
        (resourceManaged in handlebars in Assets).value ** "*.js",
    define := transformFiles.value)

  //  private def toFileNames(logger: Logger, values: Seq[PathFinder],
  //    srcDirs: Seq[File], webModuleDirs: Seq[File]): Seq[Iterable[String]] =
  //    values.map {
  //      case (list) => list match {
  //        case fileNamesPathFinder =>
  //          logger.info(fileNamesPathFinder.get.toString)
  //          val r = fileNamesPathFinder.pair(relativeTo(srcDirs ++ webModuleDirs) | flat)
  //          r.toMap.values
  //      }
  //    }

  private def checkFolder(logger: Logger, inputfolders: Seq[java.io.File], outputfolder: java.io.File, files: PathFinder, mappings: Seq[PathMapping]) : Seq[PathMapping] = {
    var reducedMappings = Seq.empty[PathMapping]
    val items = files.pair(relativeTo(inputfolders) | flat).toMap.values
    logger.info("Define updating " + items.size + " source(s)")
    items.foreach { path =>
      val matchedFile = mappings.filter(f => f._2.equals(path)).head
      val paths = List.fromArray(path.split(Pattern.quote(File.separator)))
      val isHandlebar = matchedFile._1.getAbsolutePath().indexOf("handlebars" + File.separator) > -1
      val defineName = paths.drop(2).mkString("/").dropRight(FilenameUtils.getExtension(path).length() + 1)     
      val fileContent = IO.read(matchedFile._1)
      if (StringUtils.isNotBlank(fileContent) && !fileContent.trim().startsWith("define")) {
        //logger.info("Define " + defineName + " -> " + matchedFile._1.getAbsolutePath())
        IO.write(new java.io.File(outputfolder, path), "define(\"" + defineName + "\", function() { " + (if(isHandlebar) "return " else "") + fileContent.trim() + " });")
        reducedMappings = reducedMappings ++ Seq.apply(matchedFile);
      }
    }
    reducedMappings
  }
    
  def transformFiles: Def.Initialize[Task[Pipeline.Stage]] = Def.task { (mappings: Seq[PathMapping]) =>
    val outputfolder = (resourceManaged in define in Assets).value
    if (outputfolder.exists() && outputfolder.isDirectory()) {
      org.apache.commons.io.FileUtils.cleanDirectory(outputfolder);
    }
    var reducedMappings = Seq.empty[PathMapping]
    //streams.value.log.info("InputFolder: " + inputfolder)
    //streams.value.log.info("OutputFolder: " + outputfolder)
    //streams.value.log.info("Define1: " + coffeeFolder.get)
    //streams.value.log.info("Define2: " + files.value.get)
    //val items : PathFinder = (resourceManaged in coffeescript in Assets).value 
    //val r = items.pair(relativeTo(coffeeFolder) | flat).toMap.values
    //val paths = SbtWeb.syncMappings(streams.value.cacheDirectory, mappings, public.value)
    val inputFolders = Seq.apply((resourceManaged in coffeescript in Assets).value) ++ Seq.apply((resourceManaged in handlebars in Assets).value)
    reducedMappings = reducedMappings ++ checkFolder(streams.value.log, inputFolders, outputfolder, files.value, mappings)

//        val targetDir = webTarget.value / "public" / "main"
//        //val include = GlobFilter(path)
//        val filteredMappings = mappings.filter(f => !f._1.isDirectory())
//        //streams.value.log.info("Found: " + targetDir + " -> " + filteredMappings.toString)
//        filteredMappings.map(mapping => {
//          val sourceFile = mapping._1
//          val fileName = mapping._2
//          val paths = List.fromArray(fileName.split(Pattern.quote(File.separator)))
//          val defineName = paths.drop(2).mkString(File.separator).dropRight(FilenameUtils.getExtension(fileName).length() + 1)
//          val fileContent = IO.read(sourceFile)
//          if (StringUtils.isNotBlank(fileContent) && !fileContent.trim().startsWith("define")) {
//            //streams.value.log.info("Update define: " + defineName + " -> " + sourceFile.getAbsolutePath())
//            //IO.write(sourceFile, "define(\"" + defineName + "\"," + fileContent.trim() + ");")
//          }
//        })

    //    val folders = toFileNames(streams.value.log, source.value,
    //      (sourceDirectories in Assets).value,
    //      (webModuleDirectories in Assets).value)
    //    folders.foreach {
    //      case (folder) =>
    //        //streams.value.log.info(s"DEFINE updating ${folder.size} file(s)")
    //        folder.foreach {
    //          case (fileName) =>
    //            val sourceFile = new java.io.File((sourceDirectory in Assets).value, fileName)
    //            val outputFile = new java.io.File(targetDir, fileName)
    //            val paths = List.fromArray(fileName.split(Pattern.quote(File.separator)))
    //            val defineName = paths.drop(2).mkString(File.separator).dropRight(FilenameUtils.getExtension(fileName).length() + 1)
    //            val fileContent = IO.read(sourceFile)
    //            if (StringUtils.isNotBlank(fileContent) && !fileContent.trim().startsWith("define")) {
    //              streams.value.log.info("Update define: " + defineName + " -> " + outputFile.getAbsolutePath())
    //              IO.write(outputFile, "define(\"" + defineName + "\"," + fileContent.trim() + ");")
    //            }
    //            fileName
    //        }
    //    }
    val compiled = outputfolder.***.get.filter(f => f.isFile && f.getAbsolutePath.startsWith(outputfolder.getAbsolutePath)).pair(relativeTo(outputfolder))
    (mappings.toSet -- reducedMappings.toSet ++ compiled.toSet).toSeq
  }

}