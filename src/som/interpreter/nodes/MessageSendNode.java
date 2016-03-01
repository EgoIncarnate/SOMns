package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;
import som.VM;
import som.compiler.AccessModifier;
import som.compiler.Tags;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.actors.SPromise;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.IfMessageNodeGen;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeGen;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeGen;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeGen;
import som.interpreter.nodes.specialized.IntToDoMessageNodeGen;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeGen;
import som.interpreter.nodes.specialized.OrMessageNodeGen.OrBoolMessageNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileWithDynamicBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;
import som.primitives.DoublePrimsFactory.PositiveInfinityPrimFactory;
import som.primitives.DoublePrimsFactory.RoundPrimFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.IntegerPrimsFactory.AbsPrimNodeGen;
import som.primitives.IntegerPrimsFactory.As32BitSignedValueFactory;
import som.primitives.IntegerPrimsFactory.As32BitUnsignedValueFactory;
import som.primitives.IntegerPrimsFactory.LeftShiftPrimFactory;
import som.primitives.IntegerPrimsFactory.MaxIntPrimNodeGen;
import som.primitives.IntegerPrimsFactory.ToPrimNodeGen;
import som.primitives.IntegerPrimsFactory.UnsignedRightShiftPrimFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.ObjectPrimsFactory.IsNilNodeGen;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.ObjectPrimsFactory.NotNilNodeGen;
import som.primitives.SizeAndLengthPrimFactory;
import som.primitives.StringPrimsFactory.SubstringPrimFactory;
import som.primitives.SystemPrims;
import som.primitives.SystemPrimsFactory.TicksPrimFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.actors.ActorClasses;
import som.primitives.actors.CreateActorPrimFactory;
import som.primitives.actors.PromisePrimsFactory.CreatePromisePairPrimFactory;
import som.primitives.actors.PromisePrimsFactory.WhenResolvedPrimFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.ExpPrimFactory;
import som.primitives.arithmetic.GreaterThanOrEqualPrimNodeGen;
import som.primitives.arithmetic.GreaterThanPrimNodeGen;
import som.primitives.arithmetic.LessThanOrEqualPrimNodeGen;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.RemainderPrimFactory;
import som.primitives.arithmetic.SinPrimFactory;
import som.primitives.arithmetic.SqrtPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.primitives.arrays.AtPrimFactory;
import som.primitives.arrays.AtPutPrimFactory;
import som.primitives.arrays.CopyPrimNodeGen;
import som.primitives.arrays.DoIndexesPrimFactory;
import som.primitives.arrays.DoPrimFactory;
import som.primitives.arrays.NewImmutableArrayNodeGen;
import som.primitives.arrays.NewPrimFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.vm.NotYetImplementedException;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


public final class MessageSendNode {

  public static AbstractMessageSendNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    return new UninitializedMessageSendNode(selector, arguments, source);
  }

  public static AbstractMessageSendNode adaptSymbol(final SSymbol newSelector,
      final AbstractMessageSendNode node) {
    assert node instanceof UninitializedMessageSendNode;
    return new UninitializedMessageSendNode(newSelector, node.argumentNodes,
        node.getSourceSection());
  }

  public static AbstractMessageSendNode createForPerformNodes(
      final SSymbol selector, final SourceSection source) {
    return new UninitializedSymbolSendNode(selector, source);
  }

  public static GenericMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source) {
    if (argumentNodes != null &&
        unwrapIfNecessary(argumentNodes[0]) instanceof ISpecialSend) {
      throw new NotYetImplementedException();
    } else {
      return new GenericMessageSendNode(selector, argumentNodes,
          UninitializedDispatchNode.createRcvrSend(source, selector, AccessModifier.PUBLIC),
          source);
    }
  }

  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments,
        final SourceSection source) {
      super(source);
      this.argumentNodes = arguments;
    }

    protected AbstractMessageSendNode(final SourceSection source) {
      super(source);
      // default constructor for instrumentation wrapper nodes
      this.argumentNodes = null;
    }

    public boolean isSpecialSend() {
      return unwrapIfNecessary(argumentNodes[0]) instanceof ISpecialSend;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = new Object[argumentNodes.length];
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
        assert arguments[i] != null;
      }
      return arguments;
    }
  }

  public abstract static class AbstractUninitializedMessageSendNode
      extends AbstractMessageSendNode {

    protected final SSymbol selector;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
    }

    @Override
    public String toString() {
      return "UninitMsgSend(" + selector.toString() + ")";
    }

    public SSymbol getSelector() {
      return selector;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreter();
      return super.executeGeneric(frame);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreter();
      return specialize(arguments).
          doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      // let's organize the specializations by number of arguments
      // perhaps not the best, but simple
      switch (argumentNodes.length) {
        case  1: return specializeUnary(arguments);
        case  2: return specializeBinary(arguments);
        case  3: return specializeTernary(arguments);
        case  4: return specializeQuaternary(arguments);
      }
      return makeSend();
    }

    protected PreevaluatedExpression makeSend() {
      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (isSpecialSend()) {
        return makeSpecialSend();
      }
      return makeOrdenarySend();
    }

    protected abstract PreevaluatedExpression makeSpecialSend();

    private GenericMessageSendNode makeOrdenarySend() {
      VM.insertInstrumentationWrapper(this);

      Tagging.addTags(argumentNodes[0], Tags.VIRTUAL_INVOKE_RECEIVER);
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          UninitializedDispatchNode.createRcvrSend(
              getSourceSection(), selector, AccessModifier.PUBLIC),
          getSourceSection());
      replace(send);
      VM.insertInstrumentationWrapper(send);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return send;
    }

    private PreevaluatedExpression makeEagerUnaryPrim(final UnaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      Tagging.addTags(argumentNodes[0], Tags.PRIMITIVE_ARGUMENT);

      PreevaluatedExpression result = replace(new EagerUnaryPrimitiveNode(
          prim.getSourceSection(), selector, argumentNodes[0], prim));
      Tagging.addTags(prim, Tags.EAGERLY_WRAPPED);
      VM.insertInstrumentationWrapper((Node) result);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return result;
    }

    protected PreevaluatedExpression specializeUnary(final Object[] args) {
      Object receiver = args[0];
      switch (selector.getString()) {
        // eagerly but cautious:
        case "size":
          if (receiver instanceof SArray) {
            return makeEagerUnaryPrim(SizeAndLengthPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "length":
          if (receiver instanceof String) {
            return makeEagerUnaryPrim(SizeAndLengthPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "value":
          if (receiver instanceof SBlock || receiver instanceof Boolean) {
            return makeEagerUnaryPrim(ValueNonePrimFactory.create(getSourceSection(), null));
          }
          break;
        case "not":
          if (receiver instanceof Boolean) {
            return makeEagerUnaryPrim(NotMessageNodeFactory.create(getSourceSection(), null));
          }
          break;
        case "abs":
          if (receiver instanceof Long) {
            return makeEagerUnaryPrim(AbsPrimNodeGen.create(getSourceSection(), null));
          }
          break;
        case "copy":
          if (receiver instanceof SArray) {
            return makeEagerUnaryPrim(CopyPrimNodeGen.create(getSourceSection(), null));
          }
          break;
        case "PositiveInfinity":
          if (receiver == Classes.doubleClass) {
            // don't need to protect this with an eager wrapper
            return replace(PositiveInfinityPrimFactory.create(getSourceSection(), argumentNodes[0]));
          }
          break;
        case "round":
          if (receiver instanceof Double) {
            return makeEagerUnaryPrim(RoundPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "as32BitSignedValue":
          if (receiver instanceof Long) {
            return makeEagerUnaryPrim(As32BitSignedValueFactory.create(getSourceSection(), null));
          }
          break;
        case "as32BitUnsignedValue":
          if (receiver instanceof Long) {
            return makeEagerUnaryPrim(As32BitUnsignedValueFactory.create(getSourceSection(), null));
          }
          break;
        case "sin":
          if (receiver instanceof Double) {
            return makeEagerUnaryPrim(SinPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "exp":
          if (receiver instanceof Double) {
            return makeEagerUnaryPrim(ExpPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "log":
          if (receiver instanceof Double) {
            return makeEagerUnaryPrim(LogPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "sqrt":
          if (receiver instanceof Number) {
            return makeEagerUnaryPrim(SqrtPrimFactory.create(getSourceSection(), null));
          }
          break;
        case "isNil":
          return replace(IsNilNodeGen.create(getSourceSection(), argumentNodes[0]));
        case "notNil":
          return replace(NotNilNodeGen.create(getSourceSection(), argumentNodes[0]));
        case "ticks":
          if (receiver == SystemPrims.SystemModule) {
            return replace(TicksPrimFactory.create(getSourceSection(), argumentNodes[0]));
          }
          break;
        case "createPromisePair":
          if (receiver == ActorClasses.ActorModule) {
            return replace(CreatePromisePairPrimFactory.create(getSourceSection(), argumentNodes[0]));
          }
          break;
      }
      return makeSend();
    }

    private PreevaluatedExpression makeEagerBinaryPrim(final BinaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      Tagging.addTags(argumentNodes[0], Tags.PRIMITIVE_ARGUMENT);
      Tagging.addTags(argumentNodes[1], Tags.PRIMITIVE_ARGUMENT);

      PreevaluatedExpression result = replace(new EagerBinaryPrimitiveNode(
          prim.getSourceSection(), selector, argumentNodes[0], argumentNodes[1], prim));
      Tagging.addTags(prim, Tags.EAGERLY_WRAPPED);
      VM.insertInstrumentationWrapper((Node) result);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      VM.insertInstrumentationWrapper(argumentNodes[1]);
      return result;
    }

    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "at:":
          if (arguments[0] instanceof SArray) {
            return makeEagerBinaryPrim(AtPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "new:":
          if (arguments[0] instanceof SClass && ((SClass) arguments[0]).isArray()) {
            return makeEagerBinaryPrim(NewPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "doIndexes:":
          if (arguments[0] instanceof SArray) {
            return makeEagerBinaryPrim(DoIndexesPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "do:":
          if (arguments[0] instanceof SArray) {
            return makeEagerBinaryPrim(DoPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "putAll:":
          return makeEagerBinaryPrim(PutAllNodeFactory.create(getSourceSection(), null, null, SizeAndLengthPrimFactory.create(null, null)));
        case "whileTrue:": {
          if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode &&
              unwrapIfNecessary(argumentNodes[0]) instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) unwrapIfNecessary(argumentNodes[1]);
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileTrueStaticBlocksNode(
                (BlockNode) unwrapIfNecessary(argumentNodes[0]), argBlockNode,
                (SBlock) arguments[0],
                argBlock, getSourceSection()));
          }
          break; // use normal send
        }
        case "whileFalse:":
          if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode &&
              unwrapIfNecessary(argumentNodes[0]) instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) unwrapIfNecessary(argumentNodes[1]);
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileFalseStaticBlocksNode(
                (BlockNode) unwrapIfNecessary(argumentNodes[0]), argBlockNode,
                (SBlock) arguments[0], argBlock, getSourceSection()));
          }
          break; // use normal send
        case "and:":
        case "&&":
          if (arguments[0] instanceof Boolean) {
            if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode) {
              return replace(AndMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(), argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(AndBoolMessageNodeFactory.create(getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
        case "or:":
        case "||":
          if (arguments[0] instanceof Boolean) {
            if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode) {
              return replace(OrMessageNodeGen.create((SBlock) arguments[1],
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(OrBoolMessageNodeGen.create(
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;

        case "value:":
          if (arguments[0] instanceof SBlock) {
            return makeEagerBinaryPrim(ValueOnePrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "ifTrue:":
          return replace(IfMessageNodeGen.create(true, getSourceSection(),
              argumentNodes[0], argumentNodes[1]));
        case "ifFalse:":
          return replace(IfMessageNodeGen.create(false, getSourceSection(),
              argumentNodes[0], argumentNodes[1]));
        case "to:":
          if (arguments[0] instanceof Long) {
            return makeEagerBinaryPrim(ToPrimNodeGen.create(getSourceSection(), null, null));
          }
          break;

        case "createActorFromValue:": {
          if (arguments[0] == ActorClasses.ActorModule) {
            return replace(CreateActorPrimFactory.create(getSourceSection(),
                argumentNodes[0], argumentNodes[1], IsValueFactory.create(getSourceSection(), null)));
          }
          break;
        }
        case "whenResolved:": {
          if (arguments[0] instanceof SPromise) {
            return makeEagerBinaryPrim(WhenResolvedPrimFactory.create(
                getSourceSection(), null, null));
          }
          break;
        }

        // TODO: find a better way for primitives, use annotation or something
        case "<":
          return makeEagerBinaryPrim(LessThanPrimFactory.create(getSourceSection(), null, null));
        case "<=":
          return makeEagerBinaryPrim(LessThanOrEqualPrimNodeGen.create(getSourceSection(), null, null));
        case ">":
          return makeEagerBinaryPrim(GreaterThanPrimNodeGen.create(getSourceSection(), null, null));
        case ">=":
          return makeEagerBinaryPrim(GreaterThanOrEqualPrimNodeGen.create(getSourceSection(), null, null));
        case "+":
          return makeEagerBinaryPrim(AdditionPrimFactory.create(getSourceSection(), null, null));
        case "-":
          return makeEagerBinaryPrim(SubtractionPrimFactory.create(getSourceSection(), null, null));
        case "*":
          return makeEagerBinaryPrim(MultiplicationPrimFactory.create(getSourceSection(), null, null));
        case "=":
          return makeEagerBinaryPrim(EqualsPrimFactory.create(getSourceSection(), null, null));
        case "<>":
          return makeEagerBinaryPrim(UnequalsPrimFactory.create(getSourceSection(), null, null));
// TODO: this is not a correct primitive, new an UnequalsUnequalsPrim...
//        case "~=":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              UnequalsPrimFactory.create(null, null)));
        case "==":
          return makeEagerBinaryPrim(EqualsEqualsPrimFactory.create(getSourceSection(), null, null));
        case "bitXor:":
          return makeEagerBinaryPrim(BitXorPrimFactory.create(getSourceSection(), null, null));
        case "//":
          return makeEagerBinaryPrim(DoubleDivPrimFactory.create(getSourceSection(), null, null));
        case "%":
          return makeEagerBinaryPrim(ModuloPrimFactory.create(getSourceSection(), null, null));
        case "rem:":
          return makeEagerBinaryPrim(RemainderPrimFactory.create(getSourceSection(), null, null));
        case "/":
          return makeEagerBinaryPrim(DividePrimFactory.create(getSourceSection(), null, null));
        case "&":
          return makeEagerBinaryPrim(BitAndPrimFactory.create(getSourceSection(), null, null));

        // eagerly but cautious:
        case "<<":
          if (arguments[0] instanceof Long) {
            return makeEagerBinaryPrim(LeftShiftPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case ">>>":
          if (arguments[0] instanceof Long) {
            return makeEagerBinaryPrim(UnsignedRightShiftPrimFactory.create(getSourceSection(), null, null));
          }
          break;
        case "max:":
          if (arguments[0] instanceof Long) {
            return makeEagerBinaryPrim(MaxIntPrimNodeGen.create(getSourceSection(), null, null));
          }
          break;
      }
      return makeSend();
    }

    private PreevaluatedExpression makeEagerTernaryPrim(final TernaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      PreevaluatedExpression result = replace(new EagerTernaryPrimitiveNode(
          prim.getSourceSection(), selector, argumentNodes[0],
          argumentNodes[1], argumentNodes[2], prim));
      VM.insertInstrumentationWrapper(prim);
      return result;
    }

    protected PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
        case "at:put:":
          if (arguments[0] instanceof SArray) {
            return makeEagerTernaryPrim(AtPutPrimFactory.create(
                getSourceSection(), null, null, null));
          }
          break;
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeGen.create(getSourceSection(),
              arguments[0], arguments[1], arguments[2], argumentNodes[0],
              argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (TypesGen.isLong(arguments[0]) &&
              (TypesGen.isLong(arguments[1]) ||
                  TypesGen.isDouble(arguments[1])) &&
              TypesGen.isSBlock(arguments[2])) {
            return replace(IntToDoMessageNodeGen.create(getSourceSection(),
                argumentNodes[0], argumentNodes[1], argumentNodes[2]));
          }
          break;
        case "downTo:do:":
          if (TypesGen.isLong(arguments[0]) &&
              (TypesGen.isLong(arguments[1]) ||
                  TypesGen.isDouble(arguments[1])) &&
              TypesGen.isSBlock(arguments[2])) {
            return replace(IntDownToDoMessageNodeGen.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
        case "substringFrom:to:":
          if (arguments[0] instanceof String) {
            return makeEagerTernaryPrim(SubstringPrimFactory.create(
                getSourceSection(), null, null, null));
          }
          break;
        case "invokeOn:with:":
          return replace(InvokeOnPrimFactory.create(getSourceSection(),
              argumentNodes[0], argumentNodes[1], argumentNodes[2],
              ToArgumentsArrayNodeGen.create(null, null)));
        case "value:with:":
          if (arguments[0] instanceof SBlock) {
            return makeEagerTernaryPrim(ValueTwoPrimFactory.create(
                getSourceSection(), null, null, null));
          }
          break;
        case "new:withAll:":
          if (arguments[0] == Classes.valueArrayClass) {
            return makeEagerTernaryPrim(NewImmutableArrayNodeGen.create(
                getSourceSection(), null, null, null));
          }
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeQuaternary(
        final Object[] arguments) {
      switch (selector.getString()) {
        case "to:by:do:":
          return replace(IntToByDoMessageNodeGen.create(this,
              (SBlock) arguments[3], argumentNodes[0], argumentNodes[1],
              argumentNodes[2], argumentNodes[3]));
      }
      return makeSend();
    }
  }

  @Instrumentable(factory = MessageSendNodeWrapper.class)
  private static final class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(selector, arguments, source);
      assert source != null;
    }

    /**
     * For wrapper use only.
     */
    protected UninitializedMessageSendNode(final UninitializedMessageSendNode wrappedNode) {
      super(wrappedNode.selector, null, null);
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      VM.insertInstrumentationWrapper(this);

      ISpecialSend rcvrNode = (ISpecialSend) unwrapIfNecessary(argumentNodes[0]);
      Tagging.addTags(argumentNodes[0], Tags.VIRTUAL_INVOKE_RECEIVER);
      AbstractDispatchNode dispatch;

      if (rcvrNode.isSuperSend()) {
        dispatch = UninitializedDispatchNode.createSuper(
            getSourceSection(), selector, (ISuperReadNode) rcvrNode);
      } else {
        dispatch = UninitializedDispatchNode.createLexicallyBound(
            getSourceSection(), selector, rcvrNode.getEnclosingMixinId());
      }

      GenericMessageSendNode node = new GenericMessageSendNode(selector,
        argumentNodes, dispatch, getSourceSection());
      replace(node);
      VM.insertInstrumentationWrapper(node);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return node;
    }
  }

  private static final class UninitializedSymbolSendNode
    extends AbstractUninitializedMessageSendNode {

    protected UninitializedSymbolSendNode(final SSymbol selector, final SourceSection source) {
      super(selector, new ExpressionNode[0], source);
      assert source != null;
    }

    @Override
    public boolean isSpecialSend() {
      return false;
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      // should never be reached with isSuperSend() returning always false
      throw new RuntimeException("A symbol send should never be a special send.");
    }

    @Override
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock argBlock = (SBlock) arguments[1];
            return replace(new WhileWithDynamicBlocksNode((SBlock) arguments[0],
                argBlock, true, getSourceSection()));
          }
          break;
        }
        case "whileFalse:":
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileWithDynamicBlocksNode(
                (SBlock) arguments[0], argBlock, false, getSourceSection()));
          }
          break; // use normal send
      }

      return super.specializeBinary(arguments);
    }
  }

  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    @Child private AbstractDispatchNode dispatchNode;

    private static final String[] VIRTUAL_INVOKE = new String[] {Tags.VIRTUAL_INVOKE};
    private static final String[] NOT_A = new String[] {Tags.UNSPECIFIED_INVOKE};

    private GenericMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode, final SourceSection source) {
      super(arguments, Tagging.cloneAndUpdateTags(source, VIRTUAL_INVOKE, NOT_A));
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
    }

    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    public void adoptNewDispatchListHead(final AbstractDispatchNode newHead) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode = insert(newHead);
    }

    public void replaceDispatchListHead(
        final GenericDispatchNode replacement) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      String file = "";
      if (getSourceSection() != null) {
        file = " " + getSourceSection().getSource().getName();
        file += ":" + getSourceSection().getStartLine();
        file += ":" + getSourceSection().getStartColumn();
      }

      return "GMsgSend(" + selector.getString() + file + ")";
    }

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }
  }
}
