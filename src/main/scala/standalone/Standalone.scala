package rocketchip

import chisel3._
import chisel3.testers.BasicTester
import cde.{Field, Parameters}
import rocket._
import xfiles._
import dana._

class Standalone(implicit p: Parameters) extends BasicTester {
  val xfiles = Module(new XFiles)
}
