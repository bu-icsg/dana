package dana

import Chisel._

class TopInterface extends DanaBundle with XFilesParameters {
  val arbiter = Vec.fill(numCores){(new XFilesArbiterInterface).flip}
}

class XFilesArbiterReq extends DanaBundle {
  val tid = UInt(width = tidWidth)
  val readOrWrite = Bool()
  val countFeedback = UInt(width = feedbackWidth)
  val isNew = Bool()
  val isLast = Bool()
  val data = UInt(width = elementWidth)
}

class XFilesArbiterResp extends DanaBundle {
  val tid = UInt(width = tidWidth)
  val data = UInt(width = elementWidth)
}

class XFilesArbiterInterface extends DanaBundle {
  val req = Decoupled(new XFilesArbiterReq)
  val resp = Decoupled(new XFilesArbiterResp).flip
}

class Top extends Module {
  val io = new TopInterface

  val xFilesArbiter = Module(new XFilesArbiter)
  val dana = Module(new Dana)

  io.arbiter <> xFilesArbiter.io.core
  xFilesArbiter.io.dana <> dana.io
}
