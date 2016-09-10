package som.interpreter.nodes.specialized.whileloops;

import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileFalseSplzr;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileTrueSplzr;
import som.primitives.Primitive;
import som.vm.NotYetImplementedException;
import som.vm.Primitives.Specializer;
import som.vmobjects.SBlock;


@Primitive(selector = "whileTrue:",  noWrapper = true, specializer = WhileTrueSplzr.class)
@Primitive(selector = "whileFalse:", noWrapper = true, specializer = WhileFalseSplzr.class)
public final class WhileWithStaticBlocksNode extends AbstractWhileNode {
  public abstract static class WhileSplzr extends Specializer {
    private final boolean whileTrueOrFalse;
    protected WhileSplzr(final boolean whileTrueOrFalse) {
      this.whileTrueOrFalse = whileTrueOrFalse;
    }

    @Override
    public boolean matches(final Primitive prim, final Object receiver,
        final ExpressionNode[] args) {
      return unwrapIfNecessary(args[1]) instanceof BlockNode &&
          unwrapIfNecessary(args[0]) instanceof BlockNode;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      assert !eagerWrapper;
      BlockNode argBlockNode = (BlockNode) unwrapIfNecessary(argNodes[1]);
      SBlock    argBlock     = (SBlock)    arguments[1];
      return (T) new WhileWithStaticBlocksNode(
          (BlockNode) unwrapIfNecessary(argNodes[0]), argBlockNode,
          (SBlock) arguments[0], argBlock, whileTrueOrFalse, section);
    }
  }

  public static final class WhileTrueSplzr extends WhileSplzr {
    public WhileTrueSplzr() { super(true); }
  }

  public static final class WhileFalseSplzr extends WhileSplzr {
    public WhileFalseSplzr() { super(false); }
  }

  @Child protected BlockNode receiver;
  @Child protected BlockNode argument;

  private WhileWithStaticBlocksNode(final BlockNode receiver,
      final BlockNode argument, final SBlock rcvr, final SBlock arg,
      final boolean predicateBool, final SourceSection source) {
    super(rcvr, arg, predicateBool, source);
    this.receiver = receiver;
    this.argument = argument;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    SBlock rcvr = receiver.executeSBlock(frame);
    SBlock arg  = argument.executeSBlock(frame);
    return executeEvaluated(frame, rcvr, arg);
  }

  @Override
  protected Object doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition,
      final SBlock loopBody) {
    return doWhileUnconditionally(frame, loopCondition, loopBody);
  }

  public static final class WhileWithStaticBlocksNodeFactory implements NodeFactory<WhileWithStaticBlocksNode> {

    @Override
    public WhileWithStaticBlocksNode createNode(final Object... args) {
      return new WhileWithStaticBlocksNode((BlockNode) args[0],
          (BlockNode) args[1], (SBlock) args[2], (SBlock) args[3],
          (Boolean) args[4], (SourceSection) args[5]);
    }

    @Override
    public Class<WhileWithStaticBlocksNode> getNodeClass() {
      return WhileWithStaticBlocksNode.class;
    }

    @Override
    public List<List<Class<?>>> getNodeSignatures() {
      throw new NotYetImplementedException();
    }

    @Override
    public List<Class<? extends Node>> getExecutionSignature() {
      throw new NotYetImplementedException();
    }
  }
}
