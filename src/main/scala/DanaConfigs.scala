// See LICENSE for license details.

package dana

import Chisel._
import cde.{Parameters, Config, Dump, Knob}
import xfiles.{BuildXFilesBackend, XFilesBackendParameters}

class DefaultDanaConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    def divUp (dividend: Int, divisor: Int): Int = {
      (dividend + divisor - 1) / divisor}
    def packInfo (): Int = {
      var x = site(ElementsPerBlock) << (6 + 4);
      x = x | site(PeTableNumEntries) << 4;
      x = x | site(CacheNumEntries);
      x}
    pname match {
      // ANTW Parameters
      case AntwRobEntries => 32
      // Field widths
      case ElementWidth => Dump("ELEMENT_WIDTH", 32)
      case ElementsPerBlock => Dump(Knob("ELEMENTS_PER_BLOCK"))
      case ActivationFunctionWidth => 5
      case NnidWidth => Dump("NNID_WIDTH", 16)
      case DecimalPointOffset => Dump("DECIMAL_POINT_OFFSET", 7)
      case DecimalPointWidth => Dump("DECIMAL_POINT_WIDTH", 3)
      case SteepnessWidth => 3
      // The steepness offset is the value you subtract from the
      // steepness to get the actual steepness
      case SteepnessOffset => 4
      case ErrorFunctionWidth => 1
      case FeedbackWidth => Dump("FEEDBACK_WIDTH", 12)
      // Processing Element Table
      case PeTableNumEntries => Dump(Knob("NUM_PES"))
      // Configuration Cache
      case CacheNumEntries => Dump(Knob("CACHE_NUM_ENTRIES"))
      case CacheDataSize => 32 * 1024
      // Register File
      case RegisterFileNumElements => Dump(Knob("REGISTER_FILE_NUM_ELEMENTS"))
      // Enables support for in-hardware learning
      case LearningEnabled => true
      case BitsPerBlock => site(ElementsPerBlock) * site(ElementWidth)
      case RegFileNumBlocks => divUp(site(RegisterFileNumElements),
        site(ElementsPerBlock))
      case CacheNumBlocks => divUp(divUp((site(CacheDataSize) * 8),
        site(ElementWidth)), site(ElementsPerBlock))
      case NNConfigNeuronWidth => 64
      case BuildXFilesBackend => XFilesBackendParameters(
          generator = (p: Parameters) => Module(new Dana()(p)),
          info = packInfo())
    }},
  // [TODO] Add constraints
  // topConstraints = List(
  //   { ex => ex(ElementWidth) == 32 },
  //   { ex => ex(ActivationFunctionWidth) <= 5 }
  // ),
  knobValues = {
    case "ELEMENTS_PER_BLOCK" => 4
    case "NUM_PES" => 1
    case "CACHE_NUM_ENTRIES" => 2
    case "REGISTER_FILE_NUM_ELEMENTS" => 10240
  }
)

class DanaNoLearningConfig extends Config (
  topDefinitions = { (pname,site,here) =>
    pname match {
      case LearningEnabled => false
    }}
)
