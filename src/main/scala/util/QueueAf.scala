// See LICENSE.BU for license details.

package xfiles

import chisel3._
import chisel3.util._

class QueueIOAf[T <: Data](gen: T, entries: Int) extends QueueIO[T](gen, entries) {
  val almostFull = Output(Bool())
  override def cloneType = new QueueIOAf(gen, entries).asInstanceOf[this.type]
}

class QueueAf[T <: Data](gen: T, entries: Int, almostFullEntries: Int,
  pipe: Boolean = false, flow: Boolean = false,
  override_reset: Option[Bool] = None)
    extends Module(override_reset = override_reset) {

  val io = IO(new QueueIOAf(gen, entries))
  val queue = Module(new Queue(gen, entries, pipe, flow, override_reset))

  io.enq <> queue.io.enq
  io.deq <> queue.io.deq
  io.count := queue.io.count
  io.almostFull := queue.io.count >= almostFullEntries.U
}
