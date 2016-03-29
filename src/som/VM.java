package som;

import java.io.IOException;

import som.compiler.MixinDefinition;
import som.compiler.SourcecodeCompiler;
import som.interpreter.SomLanguage;
import som.interpreter.TruffleCompiler;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.ObjectSystem;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.tools.TruffleProfiler;

import dym.DynamicMetrics;
import dym.profiles.StructuralProbe;


public final class VM {

  @CompilationFinal private static PolyglotEngine engine;
  @CompilationFinal private static VM vm;
  @CompilationFinal private static StructuralProbe structuralProbes;

  private final boolean avoidExitForTesting;
  private final ObjectSystem objectSystem;

  private int lastExitCode = 0;
  private volatile boolean shouldExit = false;
  private final VMOptions options;
  private boolean usesActors;
  private Thread mainThread;

  @CompilationFinal
  private SObjectWithoutFields vmMirror;
  @CompilationFinal
  private Actor mainActor;

  public static final boolean DebugMode = false;
  public static final boolean FailOnMissingOptimizations = false;

  public static void thisMethodNeedsToBeOptimized(final String msg) {
    if (FailOnMissingOptimizations) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static void callerNeedsToBeOptimized(final String msg) {
    if (FailOnMissingOptimizations) {
      CompilerAsserts.neverPartOfCompilation(msg);
    }
  }

  public static boolean instrumentationEnabled() {
    if (vm == null || vm.options == null) { // TODO: remove work around and make this work properly
      return false;
    }
    return vm.options.enableInstrumentation;
  }

  public static boolean enabledDynamicMetricsTool() {
    // TODO: this should use some type of configuration flag/command-line parameter
    // and it needs to depend on VM.instrumentationEnabled(), but this is a problem with initialization order
    return true;
  }

  public static void insertInstrumentationWrapper(final Node node) {
    if (VM.instrumentationEnabled()) {
      assert node.getSourceSection() != null : "Node needs source section";
      String[] tags = node.getSourceSection().getTags();
      if (tags != null && tags.length > 0) {
        InstrumentationHandler.insertInstrumentationWrapper(node);
      }
    }
  }

  public static StructuralProbe getStructuralProbe() {
    return structuralProbes;
  }

  public static void reportNewMixin(final MixinDefinition m) {
    structuralProbes.recordNewClass(m);
  }

  public static void reportNewMethod(final SInvokable m) {
    structuralProbes.recordNewMethod(m);
  }

  public VM(final String[] args, final boolean avoidExitForTesting) throws IOException {
    vm = this;

    // TODO: fix hack, we need this early, and we want tool/polyglot engine support for the events...
    structuralProbes = new StructuralProbe();

    this.avoidExitForTesting = avoidExitForTesting;
    options = new VMOptions(args);
    objectSystem = new ObjectSystem(options.platformFile, options.kernelFile);

    if (options.showUsage) {
      VMOptions.printUsageAndExit();
    }
  }

  public VM(final String[] args) throws IOException {
    this(args, false);
  }

  public static boolean shouldExit() {
    return vm.shouldExit;
  }

  public int lastExitCode() {
    return lastExitCode;
  }

  public static boolean isUsingActors() {
    return vm.usesActors;
  }

  public static void hasSendMessages() {
    vm.usesActors = true;
  }

  public static String[] getArguments() {
    return vm.options.args;
  }

  public static void exit(final int errorCode) {
    vm.exitVM(errorCode);
  }

  private void exitVM(final int errorCode) {
    TruffleCompiler.transferToInterpreter("exit");
    // Exit from the Java system
    if (!avoidExitForTesting) {
      engine.dispose();
      System.exit(errorCode);
    } else {
      lastExitCode = errorCode;
      shouldExit = true;
    }
  }

  public static void errorExit(final String message) {
    TruffleCompiler.transferToInterpreter("errorExit");
    errorPrintln("Runtime Error: " + message);
    exit(1);
  }

  @TruffleBoundary
  public static void errorPrint(final String msg) {
    // Checkstyle: stop
    System.err.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln(final String msg) {
    // Checkstyle: stop
    System.err.println(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void errorPrintln() {
    // Checkstyle: stop
    System.err.println();
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void print(final String msg) {
    // Checkstyle: stop
    System.out.print(msg);
    // Checkstyle: resume
  }

  @TruffleBoundary
  public static void println(final String msg) {
    // Checkstyle: stop
    System.out.println(msg);
    // Checkstyle: resume
  }

  public static boolean isAvoidingExit() {
    return vm.avoidExitForTesting;
  }

  public static void setMainThread(final Thread t) {
    vm.mainThread = t;
  }

  public static Thread getMainThread() {
    return vm.mainThread;
  }

  public void initalize() {
    assert vmMirror  == null : "VM seems to be initialized already";
    assert mainActor == null : "VM seems to be initialized already";

    mainActor = Actor.initializeActorSystem();
    vmMirror  = objectSystem.initialize();
  }

  public Object execute(final String selector) {
    return objectSystem.execute(selector);
  }

  public void execute() {
    objectSystem.executeApplication(vmMirror, mainActor);
  }

  public static void main(final String[] args) {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    engine = builder.build();
    Instrument truffleProfiler = engine.getInstruments().get(TruffleProfiler.ID);
    if (truffleProfiler != null) {
      truffleProfiler.setEnabled(false); // we don't want it at the moment
    }

    if (enabledDynamicMetricsTool()) {
      engine.getInstruments().get(DynamicMetrics.ID).setEnabled(true);
    }
    try {
      engine.eval(SomLanguage.START);
    } catch (IOException e) {
      throw new RuntimeException("This should never happen", e);
    }
    engine.dispose();
    System.exit(vm.lastExitCode);
  }

  public static MixinDefinition loadModule(final String filename) throws IOException {
    return vm.objectSystem.loadModule(filename);
  }

  /** This is only meant to be used in unit tests. */
  public static void resetClassReferences(final boolean callFromUnitTest) {
    assert callFromUnitTest;
    SFarReference.setSOMClass(null);
    SPromise.setPairClass(null);
    SPromise.setSOMClass(null);
    SResolver.setSOMClass(null);
    SourcecodeCompiler.resetSyntaxSections();
  }
}
