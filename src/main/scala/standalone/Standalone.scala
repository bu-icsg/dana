package rocketchip

import chisel3._
import cde.{Field, Parameters}
import rocket._
import xfiles._
import dana._

class Standalone(implicit p: Parameters) extends RoCC {
  val xfiles = Module(new XFiles)
}
