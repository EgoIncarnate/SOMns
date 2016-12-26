package som.interpreter;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MethodBuilder;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SInvokable;


public abstract class Invokable extends RootNode {
  protected final String name;

  @Child protected ExpressionNode  expressionOrSequence;

  protected final ExpressionNode uninitializedBody;

  public Invokable(final String name,
      final SourceSection sourceSection,
      final FrameDescriptor frameDescriptor,
      final ExpressionNode expressionOrSequence,
      final ExpressionNode uninitialized) {
    super(SomLanguage.class, sourceSection, frameDescriptor);
    this.name = name;
    this.expressionOrSequence = expressionOrSequence;
    this.uninitializedBody    = uninitialized;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    return expressionOrSequence.executeGeneric(frame);
  }

  public abstract ExpressionNode inline(final MethodBuilder builder, SInvokable outer);

  @Override
  public final boolean isCloningAllowed() {
    return true;
  }

  public final RootCallTarget createCallTarget() {
    return Truffle.getRuntime().createCallTarget(this);
  }

  public abstract void propagateLoopCountThroughoutMethodScope(final long count);
}
