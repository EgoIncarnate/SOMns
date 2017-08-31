package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;


@GenerateNodeFactory
@Primitive(primitive = "objHashcode:")
@Primitive(primitive = "stringHashcode:")
public abstract class HashPrim extends UnaryExpressionNode {
  public HashPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Specialization
  @TruffleBoundary
  public final long doString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  @TruffleBoundary
  public final long doSSymbol(final SSymbol receiver) {
    return receiver.getString().hashCode();
  }

  @Specialization
  public final long doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
