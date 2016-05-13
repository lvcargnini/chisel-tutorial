package hello

import Chisel._
import Chisel.iotesters._
import java.io._

object genCpp {
  def apply(dutGen: ()=> Chisel.Module, cppHarnessFilePath: String, testDirPath: String) = {
    val dutFirrtlIR = Chisel.Driver.emit(dutGen)
    val dutCircuit = Chisel.Driver.elaborate(dutGen)
    val dutName = dutCircuit.name
    val verilogFilePath = s"${testDirPath}/${dutName}.v"

    // Parse circuit into FIRRTL
    val circuit = firrtl.Parser.parse(dutFirrtlIR.split("\n"))

    val writer = new PrintWriter(new File(verilogFilePath))
    // Compile to verilog
    firrtl.VerilogCompiler.run(circuit, writer)
    writer.close()

    val verilogFileName = verilogFilePath.split("/").last
    val cppBinaryPath = s"${testDirPath}/V${dutName}"
    val vcdFilePath = s"${testDirPath}/${dutName}.vcd"

    copyCppEmulatorHeaderFiles(testDirPath)

    Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), new File(testDirPath), Seq(), new File(cppHarnessFilePath)).!
    Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!
    cppBinaryPath
  }
}

object Launcher {
  def main(args: Array[String]): Unit = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val cppHarnessFileName = "classic_tester_top.cpp"
    val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
    println("Generating device under test specific Cpp harness")
    genCppHarness(() => new Hello(), "Hello.v", cppHarnessFilePath, s"${testDirPath}/Hello.vcd") //Generates required module specific top level cpp harness file and writes it to cppHarnessFilePath
    println("Generating cpp emulator given the DUT Cpp harness")
    val cppFilePath = genCpp(() => new Hello(), cppHarnessFilePath, testDirPath) //Runs the Chisel3 flow and generates the cpp emulator binary; can be replaced by external makefile/build scripts if so desired
    println("Running ClassicTester with independently provided Cpp binary")
    runClassicTester(() => new Hello(), cppFilePath) { (c, p) => new HelloTests(c, emulBinPath = p) } //Runs the classic tester given a pre-generated cpp emulator binary
  }
}
