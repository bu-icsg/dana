// See LICENSE for license details.

package xfiles

import Chisel._
import cde.{Parameters, Config}

case object PAddrBits extends Field[Int]

object Test {
  def main(args: Array[String]): Unit = {
    val projectName = args(0)
    val topModuleName = args(1)
    val configClassName = args(2)
    // val config = try {
    //   Class.forName(s"$projectName.$configClassName").newInstance.asInstanceOf[Config]
    // } catch {
    //   case e: java.lang.ClassNotFoundException =>
    //     throwException("Unable to find configClassName \"" + configClassName +
    //       "\", did you misspell it?", e)
    // }

    val world = (new XFilesDanaNoRocketConfig).toInstance
    val paramsFromConfig: Parameters = Parameters.root(world)

    println("projectName: " + projectName)
    println("topModuleName: " + topModuleName)
    println("configClassName: " + configClassName)

    val cliArgs = args.slice(1, args.length)
    val res =
      projectName match {
        case "XFilesDana" => chiselMain(cliArgs,
          () => Module(new XFilesDana()(paramsFromConfig)))
        case _ => chiselMain(cliArgs,
          () => Module(new XFilesDana()(paramsFromConfig)))
        case _ => throwException("Unknown top level " + projectName)
      }
  }
}
