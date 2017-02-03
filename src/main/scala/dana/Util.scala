// See LICENSE for license details.

package dana.util

import chisel3._

object divUp {
  def apply(dividend: Int, divisor: Int): Int = {
    (dividend + divisor - 1) / divisor}
}

object packInfo {
  def apply(epb: Int, pes: Int, cache: Int): Int = {
    var x = epb << (6 + 4);
    x = x | pes << 4;
    x = x | cache;
    x}
}
