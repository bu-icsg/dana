package rocketchip

import cde.{Field, Parameters}
import rocket._
import xfiles._
import dana._

class Standalone(implicit p: Parameters) extends RoCC {
  val xfiles = new XFiles
}
