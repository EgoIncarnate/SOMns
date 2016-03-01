package som.primitives.reflection;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  public PerformInSuperclassPrim(final SourceSection source) { super(source); }

  @Specialization
  public final Object doSAbstractObject(final VirtualFrame frame,
      final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
    VM.thisMethodNeedsToBeOptimized("PerformInSuperclassPrim");
    Dispatchable invokable = clazz.lookupMessage(selector, AccessModifier.PUBLIC);
    return invokable.invoke(call, frame, new Object[] {receiver});
  }
}
