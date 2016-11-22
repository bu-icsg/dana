package xfiles.standalone

// import chisel3._
import chisel3.internal.firrtl.Circuit
// import java.io._
import _root_.util.GeneratorApp

object Standalone extends GeneratorApp {
  val longName = names.topModuleProject + "." + names.configs
  generateFirrtl
}
