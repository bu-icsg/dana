// See LICENSE.IBM for license details.

package dana

import chisel3._
import cde._
import xfiles.{BuildXFilesBackend, XFilesBackendParameters}

import dana.util._
import dana.abi._

class DefaultHardwareConfig extends Config ( topDefinitions = {
  (pname,site,here) => pname match {
    // ANTW Parameters
    case AntwRobEntries            => 32
    // Field widths
    case NnidWidth                 => 16
    // Processing Element Table
    case PeTableNumEntries         => 2
    case PeCooldownWidth           => 8
    // Configuration Cache
    case CacheNumEntries           => 2
    case CacheSizeBytes            => 512 * 1024 // KiB
    case CacheNumBlocks            => divUp(divUp((site(CacheSizeBytes) * 8),
      site(DanaDataBits)), site(ElementsPerBlock))
    case CacheInit                 => Nil
    // Register File
    case ScratchpadBytes           => 8 * 1024  // KiB
    case ScratchpadElements        => divUp(site(ScratchpadBytes) * 8,
      site(DanaDataBits))
    // Enables support for in-hardware learning
    case LearningEnabled           => true
    case BitsPerBlock              => site(ElementsPerBlock) * site(DanaDataBits)
    case BytesPerBlock             => site(BitsPerBlock) / 8
    case RegFileNumBlocks          => divUp(site(ScratchpadElements),
      site(ElementsPerBlock))
    case NNConfigNeuronWidth       => 64
    case BuildXFilesBackend        => XFilesBackendParameters(
      generator = (p: Parameters)  => Module(new Dana()(p)),
      csrFile_gen = (p: Parameters) => Module(new dana.CSRFile()(p)),
      csrStatus_gen = (p: Parameters) => new DanaStatus()(p),
      csrProbes_gen = (p: Parameters) => new DanaProbes()(p),
      info = packInfo(site(ElementsPerBlock), site(PeTableNumEntries),
        site(CacheNumEntries)))
    case _ => throw new CDEMatchError
  }}
)

class DefaultDanaConfig extends Config (new Abi32Bit ++
  new DefaultHardwareConfig)

class DanaNoLearningConfig extends Config ( topDefinitions = {
  (pname,site,here) => pname match {
    case LearningEnabled => false
    case _ => throw new CDEMatchError
  }}
)

class DanaConfig
  (numPes:      Int     = 1,
    epb:        Int     = 4,
    cache:      Int     = 2,
    cacheSize:  Int     = 32 * 1024,
    scratchpad: Int     = 8 * 1024,
    learning:   Boolean = true)
    extends Config( topDefinitions = {
  (pname,site,here) => pname match {
    case LearningEnabled   => learning
    case PeTableNumEntries => numPes
    case ElementsPerBlock  => epb
    case CacheNumEntries   => cache
    case CacheSizeBytes    => cacheSize
    case ScratchpadBytes   => scratchpad
    case _ => throw new CDEMatchError
  }})

case class CacheInitParameters(asid: Int, nnid: Int)

class CacheInitialized extends Config( topDefinitions = {
  (pname,site,here) => pname match {
    case CacheInit => Seq(
      CacheInitParameters(asid = 1, nnid = 0))
    case _ => throw new CDEMatchError
  }})

class DanaAsicConfig extends Config(
  new DanaConfig(numPes=4, cache=1, scratchpad=2048, cacheSize=128*1024))
